# Установка и запуск ReallyWorld Ultimate на Linux (Ubuntu / Debian / Docker)

## 1. Подготовка окружения (Ubuntu 22.04 / 24.04 LTS, Debian 12)
```bash
sudo apt update
sudo apt install -y wget apt-transport-https gpg unzip tar python3 curl ufw
# Установка Eclipse Temurin OpenJDK 21 LTS
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | sudo tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update && sudo apt install -y temurin-21-jdk
java -version
```

---

## 2. Развёртывание сборки под выделенным пользователем
```bash
sudo useradd -m -r -s /bin/bash minecraft
sudo mkdir -p /opt/reallyworld
sudo unzip ReallyWorld-Ultimate-1.21.4-1.21.11.zip -d /opt/reallyworld
sudo chown -R minecraft:minecraft /opt/reallyworld

sudo -u minecraft bash -c '
  cd /opt/reallyworld/ReallyWorldUltimate
  chmod +x install.sh scripts/*.sh server/start.sh
  ./install.sh
'
```

---

## 3. Автозапуск через `systemd`
Создайте файл `/etc/systemd/system/reallyworld.service`:
```ini
[Unit]
Description=ReallyWorld Ultimate Minecraft Server (1.21.4-1.21.11)
After=network-online.target
Wants=network-online.target

[Service]
User=minecraft
Group=minecraft
WorkingDirectory=/opt/reallyworld/ReallyWorldUltimate/server
Environment="MIN_RAM=4G"
Environment="MAX_RAM=8G"
Environment="RW_ITEM_SALT=замените-на-случайную-строку-32-символа"
ExecStart=/opt/reallyworld/ReallyWorldUltimate/server/start.sh
Restart=on-failure
RestartSec=10
SuccessExitStatus=0 143

[Install]
WantedBy=multi-user.target
```
Активируйте службу и межсетевой экран:
```bash
sudo ufw allow 25565/tcp
sudo systemctl daemon-reload
sudo systemctl enable --now reallyworld
sudo journalctl -u reallyworld -f
```

---

## 4. Альтернативный запуск в Docker / Docker Compose
```bash
cd ReallyWorldUltimate
cp .env.example .env
# Отредактируйте секрет RW_ITEM_SALT в .env
docker compose up -d --build
```
Для запуска связки с **MariaDB 11.4**:
```bash
docker compose --profile mariadb up -d --build
```
