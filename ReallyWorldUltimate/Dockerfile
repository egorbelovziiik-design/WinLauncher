# =============================================================================
# ReallyWorld Ultimate — Production Dockerfile (Eclipse Temurin Java 21 LTS)
# =============================================================================
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 ca-certificates curl tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r minecraft && useradd -r -g minecraft -d /opt/reallyworld minecraft

WORKDIR /opt/reallyworld
COPY --chown=minecraft:minecraft . /opt/reallyworld/

RUN chmod +x /opt/reallyworld/install.sh \
    /opt/reallyworld/scripts/*.sh \
    /opt/reallyworld/server/start.sh

USER minecraft
EXPOSE 25565/tcp

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD bash -c '</dev/tcp/127.0.0.1/25565' || exit 1

WORKDIR /opt/reallyworld/server
CMD ["bash", "-c", "if [ ! -f server.jar ]; then ../install.sh; fi && exec ./start.sh"]
