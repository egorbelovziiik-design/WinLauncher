#!/usr/bin/env bash
# ==============================================================================
# 01-linux-tuning.sh
# Низкоуровневая оптимизация Debian 12/13 и Ubuntu 24.04 LTS под Minecraft-ноду:
# - CPU Governor (performance + AMD P-State EPP)
# - DDR5 Memory Tuning (THP madvise под Java 21 ZGC/G1GC, vm.swappiness=1)
# - NVMe PCIe 5.0 Scheduler (none) + Weekly TRIM
# - 10/25GbE Network Tuning (TCP BBR + fq + 64MB Socket Buffers)
# ==============================================================================
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "[ОШИБКА] Запустите скрипт от имени root: sudo bash $0" >&2
  exit 1
fi

echo "==> [1/6] Очистка фонового шума ОС и установка системных утилит..."
export DEBIAN_FRONTEND=noninteractive

# Удаляем snapd на Ubuntu, если он установлен (источник латентности CPU/IO)
if command -v snap >/dev/null 2>&1; then
  echo "  -> Обнаружен snapd: отключаем и удаляем для исключения микрофризов..."
  systemctl stop snapd.service snapd.socket || true
  apt-get purge -y snapd || true
fi

apt-get update -qq
apt-get install -y --no-install-recommends \
  linux-cpupower \
  ethtool \
  nvme-cli \
  smartmontools \
  rasdaemon \
  sysstat \
  iotop \
  htop \
  jq \
  curl \
  ca-certificates \
  zstd \
  tar

echo "==> [2/6] Включение аппаратного мониторинга памяти DDR5 ECC (rasdaemon)..."
systemctl enable --now rasdaemon || true

echo "==> [3/6] Настройка CPU Governor (performance) и AMD Zen 5 P-State..."
cat >/etc/systemd/system/mc-cpu-performance.service <<'EOF'
[Unit]
Description=Force CPU Governor and Energy Performance Preference to Performance for Minecraft Hosting
After=multi-user.target

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/bin/bash -c '\
  for gov in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do \
    [ -f "$gov" ] && echo performance > "$gov" || true; \
  done; \
  for epp in /sys/devices/system/cpu/cpu*/cpufreq/energy_performance_preference; do \
    [ -f "$epp" ] && echo performance > "$epp" || true; \
  done; \
  if [ -f /sys/kernel/mm/transparent_hugepage/enabled ]; then \
    echo madvise > /sys/kernel/mm/transparent_hugepage/enabled; \
  fi; \
  if [ -f /sys/kernel/mm/transparent_hugepage/defrag ]; then \
    echo defer+madvise > /sys/kernel/mm/transparent_hugepage/defrag; \
  fi'

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now mc-cpu-performance.service

echo "==> [4/6] Настройка параметров ядра sysctl (DDR5 128-512GB + Сеть 10-25GbE BBR)..."
modprobe tcp_bbr || true
echo "tcp_bbr" >/etc/modules-load.d/mc-bbr.conf

cat >/etc/sysctl.d/99-minecraft-hosting.conf <<'EOF'
# --- Память DDR5 (128-512 GB) и JVM (G1GC / Generational ZGC) ---
vm.swappiness = 1
vm.vfs_cache_pressure = 50
vm.dirty_background_ratio = 5
vm.dirty_ratio = 15
vm.max_map_count = 1048576
vm.overcommit_memory = 1
kernel.numa_balancing = 0

# --- Сетевой стек 10-25 Гбит/с (TCP BBR + Low Latency Netty) ---
net.core.default_qdisc = fq
net.ipv4.tcp_congestion_control = bbr
net.core.rmem_max = 67108864
net.core.wmem_max = 67108864
net.core.rmem_default = 1048576
net.core.wmem_default = 1048576
net.ipv4.tcp_rmem = 4096 1048576 67108864
net.ipv4.tcp_wmem = 4096 1048576 67108864
net.ipv4.udp_rmem_min = 65536
net.ipv4.udp_wmem_min = 65536
net.core.netdev_max_backlog = 65536
net.core.somaxconn = 32768
net.ipv4.tcp_max_syn_backlog = 32768
net.ipv4.tcp_fastopen = 3
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 15
net.ipv4.tcp_slow_start_after_idle = 0
net.ipv4.tcp_mtu_probing = 1
net.ipv4.ip_local_port_range = 10240 65535

# --- Базовая защита от SYN-флуда и спуфинга ---
net.ipv4.tcp_syncookies = 1
net.ipv4.tcp_synack_retries = 2
net.ipv4.conf.all.rp_filter = 1
net.ipv4.conf.default.rp_filter = 1
net.ipv4.icmp_echo_ignore_broadcasts = 1

# --- Файловые дескрипторы и inotify для десятков Docker-контейнеров ---
fs.file-max = 2097152
fs.inotify.max_user_instances = 8192
fs.inotify.max_user_watches = 1048576
EOF

sysctl --system

echo "==> [5/6] Оптимизация планировщика ввода-вывода NVMe PCIe 5.0 (scheduler=none)..."
cat >/etc/udev/rules.d/60-nvme-scheduler.rules <<'EOF'
ACTION=="add|change", KERNEL=="nvme[0-9]*n[0-9]*", ATTR{queue/scheduler}="none", ATTR{queue/rq_affinity}="2", ATTR{queue/read_ahead_kb}="128"
EOF

udevadm control --reload-rules && udevadm trigger || true
systemctl enable --now fstrim.timer || true

echo "==> [6/6] Повышение лимитов открытых файлов и потоков (limits.conf)..."
cat >/etc/security/limits.d/99-minecraft-hosting.conf <<'EOF'
* soft nofile 1048576
* hard nofile 1048576
root soft nofile 1048576
root hard nofile 1048576
* soft memlock unlimited
* hard memlock unlimited
EOF

echo ""
echo "[ГОТОВО] Оптимизация ядра Linux завершена."
echo "Рекомендуется добавить в /etc/default/grub параметры:"
echo '  GRUB_CMDLINE_LINUX_DEFAULT="quiet amd_pstate=active transparent_hugepage=madvise nvme_core.default_ps_max_latency_us=0 pcie_aspm=off"'
echo "и выполнить update-grub && reboot перед вводом ноды в продакшен."
