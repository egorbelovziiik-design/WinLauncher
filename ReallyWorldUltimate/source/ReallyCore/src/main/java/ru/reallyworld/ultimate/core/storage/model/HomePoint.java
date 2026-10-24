package ru.reallyworld.ultimate.core.storage.model;

import java.util.UUID;

public record HomePoint(
        UUID playerUuid,
        String name,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        long createdAt
) {}
