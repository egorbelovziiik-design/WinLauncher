# Установка и запуск ReallyWorld Ultimate на Windows (10 / 11 / Windows Server)

## 1. Системные требования
- **ОС:** Windows 10 / 11 (x64) или Windows Server 2022/2025
- **Java:** **OpenJDK 21 LTS** (рекомендуется [Eclipse Temurin 21](https://adoptium.net/))
- **ОЗУ:** минимум `4 ГБ` свободно для JVM (рекомендуется `8–12 ГБ` для 100+ игроков)
- **Python 3.10+** *(только на этапе первого запуска `install.bat` для автоматической загрузки ядра Purpur/Paper и проверки хешей SHA-512)*

---

## 2. Быстрый старт за 3 шага

1. Распакуйте архив `ReallyWorld-Ultimate-1.21.4-1.21.11.zip` в путь **без кириллицы и пробелов**, например:
   `C:\Servers\ReallyWorldUltimate\`
2. Запустите двойным кликом **`install.bat`** в корне папки:
   - Скрипт автоматически скачает официальный билд **Purpur 1.21.11** (или Paper 1.21.11) в `server\server.jar` и проверит контрольную сумму.
   - Скачает официальные релизы `ViaVersion`, `ViaBackwards`, `LuckPerms`, `PlaceholderAPI`, `WorldEdit`, `WorldGuard`, `TAB`, `GrimAC`, `CoreProtect` с Modrinth API с проверкой SHA-512.
   - Центральный плагин **`ReallyCore.jar`** уже скомпилирован и находится в `server\plugins\ReallyCore.jar`!
3. Перейдите в папку `server\` и запустите **`start.bat`**.

---

## 3. Подключение игроков (1.21.4 — 1.21.11)
- Локально на том же ПК: адрес `127.0.0.1:25565`
- Для входа из интернета откройте порт `25565/TCP` в Брандмауэре Защитника Windows (PowerShell от имени Администратора):
  ```powershell
  New-NetFirewallRule -DisplayName "Minecraft ReallyWorld 25565" -Direction Inbound -LocalPort 25565 -Protocol TCP -Action Allow
  ```

---

## 4. Раздача ресурспака
Готовый кросс-версионный ресурспак находится в `resourcepack\ReallyWorld-ResourcePack-1.21.4-1.21.11.zip` (его точный `SHA-1` уже прописан в `server\server.properties`). Разместите ZIP-файл на любом прямом HTTPS-хостинге (GitHub Releases / CDN / Nginx) и вставьте прямую ссылку в поле `resource-pack=` в `server\server.properties`.
