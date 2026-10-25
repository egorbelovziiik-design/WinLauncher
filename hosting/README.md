# WinHost — Собственная платформа премиального Minecraft-хостинга

Полноценный программно-аппаратный стек для запуска собственного приватного или коммерческого Minecraft-хостинга на базе **AMD Zen 5 + DDR5 ECC (128–512+ ГБ) + NVMe PCIe 5.0 + 10/25GbE**.

> Весь проект изолирован внутри директории `hosting/` и никак не затрагивает основной код репозитория `WinLauncher`.

---

## Структура проекта

```text
hosting/
├── ARCHITECTURE_BLUEPRINT.md              # Главный архитектурный проект (Железо DDR5, Linux, Хостинг, Панель)
├── README.md                              # Руководство по быстрому развёртыванию
├── infra/
│   ├── scripts/
│   │   ├── 01-linux-tuning.sh             # Тюнинг ядра Linux, CPU Governor performance, DDR5 THP/ZRAM, NVMe, 25GbE BBR
│   │   ├── 02-ssh-security-and-updates.sh # Харденинг SSH (Ed25519), nftables анти-DDoS, Fail2Ban, Unattended-Upgrades
│   │   └── 03-install-docker-node.sh      # Docker Engine cgroups v2 + изолированная сеть + XFS pquota
│   ├── Caddyfile                          # Автоматический HTTPS (TLS 1.3 + HTTP/3 + HSTS + WSS Proxy)
│   ├── docker-compose.production.yml      # Боевой запуск собственной панели WinHost + Caddy HTTPS
│   └── pelican-alternative/
│       └── docker-compose.pelican.yml     # Альтернативный стек Pelican Panel + Wings + MariaDB + Redis
└── panel/                                 # Собственная Full-Stack платформа WinHost (TypeScript + React 19 + Node + WS + Docker)
    ├── api/                               # Serverless адаптер для облачной витрины и демо-панели на Vercel
    ├── server/                            # Control Plane REST API, TOTP 2FA (RFC 6238), гибридный Docker/Supervisor движок
    └── src/                               # Витрина сайта WinHost + SPA-интерфейс панели клиента и администратора
```

---

## Быстрый старт на боевом физическом сервере (Debian 12/13 или Ubuntu 24.04 LTS)

### Шаг 1. Низкоуровневая оптимизация Linux и защита SSH
```bash
sudo bash hosting/infra/scripts/01-linux-tuning.sh
sudo SSH_PORT=2222 MC_PORT_RANGE=25565-25665 bash hosting/infra/scripts/02-ssh-security-and-updates.sh
```

### Шаг 2. Установка Docker Engine cgroups v2 и подготовка хранилища NVMe
```bash
sudo bash hosting/infra/scripts/03-install-docker-node.sh
```

### Шаг 3. Боевой запуск веб-панели с автоматическим HTTPS (Let's Encrypt TLS 1.3)
```bash
cd hosting/infra
PANEL_DOMAIN=panel.winhost.gg JWT_SECRET=$(openssl rand -hex 32) docker compose -f docker-compose.production.yml up -d --build
```

---

## Локальный запуск и разработка веб-панели

```bash
cd hosting/panel
npm install
npm run dev
```

### Демонстрационные аккаунты WinHost по умолчанию
- **Владелец / Администратор кластера:** `admin@winhost.gg` / `Admin#DDR5!2026`
- **Клиент хостинга:** `alex@winhost.gg` / `Client#MC!2026`
*(В боевом окружении обязательно смените пароль и активируйте двухфакторную защиту TOTP 2FA во вкладке «Безопасность и 2FA»).*
