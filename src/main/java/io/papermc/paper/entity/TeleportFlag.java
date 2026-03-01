package io.papermc.paper.entity;

/**
 * Compatibility wrapper for Paper API package.
 */
public interface TeleportFlag extends com.oneworldstudiomc.paper.entity.TeleportFlag {

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
