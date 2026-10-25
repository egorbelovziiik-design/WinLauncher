# syntax=docker/dockerfile:1

# ─────────────────────────────────────────────────────────────────────────────
# KINGGRIEF — production образ (Next.js standalone + встроенный SQLite)
# ─────────────────────────────────────────────────────────────────────────────
FROM node:22-bookworm-slim AS base
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1

# 1. Зависимости (включая dev — они нужны для сборки и тестов)
FROM base AS deps
COPY package.json package-lock.json ./
RUN npm ci

# 2. Сборка приложения
FROM base AS builder
ENV NODE_ENV=production
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Значения-заглушки: на этапе сборки база не создаётся и секреты не нужны.
ENV ADMIN_SESSION_SECRET=build-time-placeholder \
    ADMIN_PASSWORD=build-time-placeholder \
    PAYMENT_PROVIDER=mock
RUN npm run build

# 3. Стадия тестов (используется docker-compose.test.yml)
FROM builder AS test
ENV NODE_ENV=test
CMD ["npm", "test"]

# 4. Рантайм
FROM base AS runner
ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0
RUN groupadd --system --gid 1001 nodejs \
 && useradd --system --uid 1001 --gid nodejs --create-home nextjs

COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

RUN mkdir -p /app/data && chown -R nextjs:nodejs /app/data
USER nextjs
VOLUME ["/app/data"]
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=25s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:' + (process.env.PORT || 3000) + '/api/status').then((r) => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"

CMD ["node", "server.js"]
