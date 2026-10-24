package ru.reallyworld.ultimate.core.storage.model;

public record WarpPoint(
        String name,
        String displayName,
        String category,
        String permission,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String createdBy,
        long createdAt
) {}
