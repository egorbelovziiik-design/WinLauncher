#!/usr/bin/env bash
# ==============================================================================
# 03-install-docker-node.sh
# Установка Docker Engine для физической ноды Minecraft-хостинга:
# - cgroups v2 для точных лимитов CPU и DDR5 RAM
# - Лог-ротация контейнеров
# - Подготовка директории /var/lib/mc-servers (под раздел XFS с pquota)
# - Предзагрузка образов Eclipse Temurin Java 21 / Java 17 / Java 8
# ==============================================================================
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "[ОШИБКА] Запустите скрипт от имени root: sudo bash $0" >&2
  exit 1
fi

echo "==> [1/4] Установка официального репозитория Docker Engine..."
install -m 0755 -d /etc/apt/keyrings
. /etc/os-release

curl -fsSL "https://download.docker.com/linux/${ID}/gpg" -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/${ID} \
  ${VERSION_CODENAME} stable" >/etc/apt/sources.list.d/docker.list

apt-get update -qq
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo "==> [2/4] Подготовка хранилища /var/lib/mc-servers и конфига демона Docker..."
mkdir -p /var/lib/mc-servers/{volumes,backups}
mkdir -p /etc/docker

cat >/etc/docker/daemon.json <<'EOF'
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "25m",
    "max-file": "3"
  },
  "storage-driver": "overlay2",
  "live-restore": true,
  "userland-proxy": false,
  "default-ulimits": {
    "nofile": {
      "Name": "nofile",
      "Hard": 1048576,
      "Soft": 1048576
    }
  }
}
EOF

systemctl enable --now docker

echo "==> [3/4] Создание изолированной сети контейнеров Minecraft..."
docker network inspect mc_isolated_net >/dev/null 2>&1 || \
  docker network create \
    --driver bridge \
    --subnet 172.28.0.0/16 \
    --opt com.docker.network.bridge.name=mcbr0 \
    mc_isolated_net

echo "==> [4/4] Предзагрузка базовых образов JVM (Java 21 LTS и Java 17 LTS)..."
docker pull eclipse-temurin:21-jre-noble
docker pull eclipse-temurin:17-jre-jammy

echo "[ГОТОВО] Нода Docker готова к размещению контейнеров Minecraft!"
echo "Примечание по квотам диска: смонтируйте NVMe RAID-1 массив в /var/lib/mc-servers с опциями:"
echo "  xfs defaults,noatime,nodiratime,logbsize=256k,pquota 0 2"
