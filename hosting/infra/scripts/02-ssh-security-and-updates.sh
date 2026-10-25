#!/usr/bin/env bash
# ==============================================================================
# 02-ssh-security-and-updates.sh
# Харденинг SSH, межсетевой экран nftables (защита ноды и игровых портов),
# Fail2Ban и автоматические обновления безопасности без внезапных ребутов.
# ==============================================================================
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "[ОШИБКА] Запустите скрипт от имени root: sudo bash $0" >&2
  exit 1
fi

SSH_PORT="${SSH_PORT:-2222}"
MC_PORT_RANGE="${MC_PORT_RANGE:-25565-25665}"
PANEL_HTTPS_PORT="${PANEL_HTTPS_PORT:-443}"

echo "==> [1/4] Установка nftables, fail2ban и unattended-upgrades..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y --no-install-recommends \
  nftables \
  fail2ban \
  unattended-upgrades \
  apt-listchanges

echo "==> [2/4] Харденинг OpenSSH (ключевая аутентификация Ed25519, порт ${SSH_PORT})..."
# Предупреждение: убедитесь, что публичный SSH-ключ уже добавлен в ~/.ssh/authorized_keys!
mkdir -p /etc/ssh/sshd_config.d
cat >/etc/ssh/sshd_config.d/99-mc-hosting-hardening.conf <<EOF
Port ${SSH_PORT}
Protocol 2
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
ChallengeResponseAuthentication no
PubkeyAuthentication yes
MaxAuthTries 3
MaxSessions 5
LoginGraceTime 30
ClientAliveInterval 300
ClientAliveCountMax 2
X11Forwarding no
AllowAgentForwarding no
AllowTcpForwarding local
Ciphers chacha20-poly1305@openssh.com,aes256-gcm@openssh.com
KexAlgorithms sntrup761x25519-sha512@openssh.com,curve25519-sha256
MACs hmac-sha2-512-etm@openssh.com,hmac-sha2-256-etm@openssh.com
EOF

sshd -t && systemctl reload ssh || systemctl reload sshd

echo "==> [3/4] Настройка Fail2Ban и пакетного фильтра nftables..."
cat >/etc/fail2ban/jail.d/sshd-local.conf <<EOF
[sshd]
enabled = true
port = ${SSH_PORT}
maxretry = 3
findtime = 10m
bantime = 2h
bantime.increment = true
bantime.factor = 4
EOF

systemctl enable --now fail2ban

cat >/etc/nftables.conf <<EOF
#!/usr/sbin/nft -f

flush ruleset

table inet mc_host_filter {
  chain input {
    type filter hook input priority 0; policy drop;

    # Разрешаем loopback и уже установленные соединения
    iif "lo" accept
    ct state established,related accept

    # Сбрасываем повреждённые и мусорные пакеты
    ct state invalid drop
    tcp flags & (fin|syn|rst|psh|ack|urg) == 0 drop
    tcp flags & (fin|syn) == (fin|syn) drop
    tcp flags & (syn|rst) == (syn|rst) drop

    # Ограниченный ICMP для диагностики пинга игроками
    ip protocol icmp icmp type { echo-request, destination-unreachable, time-exceeded } limit rate 20/second accept
    ip6 nexthdr ipv6-icmp limit rate 20/second accept

    # Защищённый порт управления SSH с лимитом новых сессий
    tcp dport ${SSH_PORT} ct state new limit rate 10/minute burst 5 packets accept

    # Веб-панель (HTTP ACME + HTTPS + HTTP/3 QUIC)
    tcp dport { 80, ${PANEL_HTTPS_PORT} } accept
    udp dport ${PANEL_HTTPS_PORT} accept

    # Пул портов Minecraft-серверов (TCP Java Edition + UDP VoiceChat/GeyserMC)
    tcp dport ${MC_PORT_RANGE} ct state new limit rate 150/second burst 300 packets accept
    udp dport ${MC_PORT_RANGE} accept
    udp dport 19132-19232 accept
  }

  chain forward {
    # Docker управляет цепочками контейнеров самостоятельно
    type filter hook forward priority 0; policy accept;
  }

  chain output {
    type filter hook output priority 0; policy accept;
  }
}
EOF

systemctl enable --now nftables

echo "==> [4/4] Настройка автоматических обновлений безопасности (БЕЗ авторебута живых серверов!)..."
cat >/etc/apt/apt.conf.d/52unattended-upgrades-mchost <<'EOF'
Unattended-Upgrade::Origins-Pattern {
  "origin=Debian,codename=${distro_codename}-security,label=Debian-Security";
  "origin=Ubuntu,archive=${distro_codename}-security,label=Ubuntu";
};
Unattended-Upgrade::Remove-Unused-Kernel-Packages "true";
Unattended-Upgrade::Remove-Unused-Dependencies "true";
Unattended-Upgrade::Automatic-Reboot "false";
EOF

cat >/etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT::Periodic::AutocleanInterval "7";
EOF

systemctl enable --now unattended-upgrades

echo "[ГОТОВО] SSH порт=${SSH_PORT}, nftables, Fail2Ban и Unattended-Upgrades успешно настроены."
