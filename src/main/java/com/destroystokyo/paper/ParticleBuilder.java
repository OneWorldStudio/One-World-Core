package com.destroystokyo.paper;

import org.bukkit.Particle;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility wrapper for older Paper package names.
 */
public class ParticleBuilder extends com.oneworldstudiomc.paper.ParticleBuilder {
    public ParticleBuilder(@NotNull final Particle particle) {
        super(particle);
    }
}
