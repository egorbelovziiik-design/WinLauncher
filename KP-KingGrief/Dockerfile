# ============================================================
#  КП-KingGrief — образ для работы 24/7 на VPS
#  Сборка:  docker build -t kp-kinggrief .
#  Запуск:  docker compose up -d
# ============================================================

FROM python:3.12-slim AS base

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    TZ=Europe/Moscow

WORKDIR /app

# Системные пакеты: тайм-зоны и sqlite3 (для ручной диагностики БД)
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata sqlite3 ca-certificates \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install "discord.py>=2.4,<3" "python-dotenv>=1.0" "APScheduler>=3.10,<4" "tzdata>=2024.1"

# Сначала код тестов, затем сам бот — для кэширования слоёв
COPY . .

RUN python -m compileall -q . \
    && mkdir -p /app/data /app/logs

# Непривилегированный пользователь
RUN useradd --create-home --shell /usr/sbin/nologin kpbot \
    && chown -R kpbot:kpbot /app
USER kpbot

VOLUME ["/app/data", "/app/logs"]

HEALTHCHECK --interval=5m --timeout=30s --start-period=40s --retries=3 \
    CMD python -c "import pathlib,sys; sys.exit(0 if pathlib.Path('/app/logs/bot.log').exists() else 1)"

CMD ["python", "main.py"]
