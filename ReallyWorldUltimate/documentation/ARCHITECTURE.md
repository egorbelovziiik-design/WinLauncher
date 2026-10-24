# Архитектура Исходного Кода ReallyCore (ARCHITECTURE)

## 1. Обзор модульного ядра
Центральный плагин **`ReallyCore`** написан на **Java 21** под **Paper API `1.21.4`–`1.21.11`** с использованием `net.kyori.adventure` (`MiniMessage`) и модульного контракта `ReallyModule`. В проекте **отсутствуют монолитные классы-божества**: код разделён на 36 сфокусированных классов в 7 пакетах:

```text
source/ReallyCore/src/main/java/ru/reallyworld/ultimate/core/
├── ReallyCorePlugin.java        # Оркестратор жизненного цикла 19 модулей и регистрации команд
├── api/                         # Контракт ReallyModule, перечисления CurrencyType и RarityType
├── config/                      # ConfigManager (валидация 25 YAML + анти-арбитраж) и RankDefinition
├── util/                        # TextUtil (MiniMessage), CryptoSignatureUtil (HMAC-SHA256), ItemSerializer
├── storage/                     # StorageModule (SQLite WAL / MariaDB / PostgreSQL) + DTO + 5 репозиториев
├── module/                      # 16 изолированных игровых и инфраструктурных модулей
├── gui/                         # ReallyGuiHolder + MenuModule (18 защищённых русских GUI-меню)
└── command/                     # 6 обработчиков команд и автодополнения (60+ серверных команд)
```

---

## 2. Автономная отказоустойчивость (Standalone + Full Stack)
`ReallyCore` спроектирован по принципу **Dual-Mode Resilience**:
- Если установлены внешние плагины (`LuckPerms`, `Vault`, `PlaceholderAPI`, `WorldGuard`, `TAB`), модуль `IntegrationsModule` автоматически регистрирует провайдер экономики `Vault`, плейсхолдеры `%reallycore_*%`, синхронизирует группы `LuckPerms` и уступает рендеринг таба плагину `TAB`.
- Если администратор запускает сервер только с одним `ReallyCore.jar` на чистом `Paper` или `Purpur`, встроенные модули `PlayerModule`, `SpawnWorldModule` и `ScoreboardTabModule` самостоятельно обеспечивают права рангов, защиту Спавна, Колизей, Автошахту, NPC, голограммы `TextDisplay`, скорборд и `TAB` со 100% работоспособностью!

---

## 3. Сборка из исходников
Проект снабжён конфигурациями как для **Gradle Kotlin DSL** (`build.gradle.kts`), так и для **Apache Maven** (`pom.xml`):
```bash
# Вариант 1: Автоматический скрипт сборки (Linux/macOS)
./scripts/build-all.sh

# Вариант 2: Вручную через Gradle 8.8+ (Java 21)
cd source/ReallyCore && gradle clean test jar

# Вариант 3: Вручную через Maven 3.9+ (Java 21)
cd source/ReallyCore && mvn clean test package
```
Готовый артефакт помещается в `server/plugins/ReallyCore.jar`.
