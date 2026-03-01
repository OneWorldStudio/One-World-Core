package com.oneworldstudiomc.paper;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal Paper-compatible particle builder for plugin runtime checks.
 */
public class ParticleBuilder {

    private final Particle particle;
    private Location location;
    private int count = 1;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private double extra;
    private Object data;

    public ParticleBuilder(@NotNull final Particle particle) {
        this.particle = particle;
    }

    public @NotNull ParticleBuilder location(@NotNull final Location location) {
        this.location = location;
        return this;
    }

    public @NotNull ParticleBuilder count(final int count) {
        this.count = Math.max(0, count);
        return this;
    }

    public @NotNull ParticleBuilder offset(final double x, final double y, final double z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        return this;
    }

    public @NotNull ParticleBuilder extra(final double extra) {
        this.extra = extra;
        return this;
    }

    public @NotNull ParticleBuilder data(@Nullable final Object data) {
        this.data = data;
        return this;
    }

    public @NotNull ParticleBuilder allPlayers() {
        return this;
    }

    public @NotNull ParticleBuilder receivers(final int receivers) {
        return this;
    }

    public void spawn() {
        if (this.location == null) {
            return;
        }
        final World world = this.location.getWorld();
        if (world == null) {
            return;
        }
        if (this.data != null) {
            world.spawnParticle(this.particle, this.location, this.count, this.offsetX, this.offsetY, this.offsetZ, this.extra, this.data);
        } else {
            world.spawnParticle(this.particle, this.location, this.count, this.offsetX, this.offsetY, this.offsetZ, this.extra);
        }
    }
}
