package com.oneworldstudiomc.plugins.back;

public enum BackType {
    TELEPORT,DEATH;

    public boolean isTeleport() {
        return this == TELEPORT;
    }
}
