package com.oneworldstudiomc.paper.entity;

/**
 * Minimal Paper-compatible teleport flags API for plugin binary compatibility.
 */
public interface TeleportFlag {

    enum Relative implements TeleportFlag {
        VELOCITY_X,
        VELOCITY_Y,
        VELOCITY_Z,
        VELOCITY_ROTATION
    }

    enum EntityState implements TeleportFlag {
        RETAIN_VEHICLE,
        RETAIN_PASSENGERS,
        RETAIN_OPEN_INVENTORY
    }
}
