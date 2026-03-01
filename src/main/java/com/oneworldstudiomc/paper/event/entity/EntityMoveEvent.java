package com.oneworldstudiomc.paper.event.entity;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Paper-compatible entity movement event.
 */
public class EntityMoveEvent extends EntityEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private @NotNull Location from;
    private @NotNull Location to;
    private boolean cancelled;

    public EntityMoveEvent(@NotNull final LivingEntity entity, @NotNull final Location from, @NotNull final Location to) {
        super(entity);
        validateLocation(from);
        validateLocation(to);
        this.from = from;
        this.to = to;
    }

    @Override
    public @NotNull LivingEntity getEntity() {
        return (LivingEntity) this.entity;
    }

    public @NotNull Location getFrom() {
        return this.from;
    }

    public void setFrom(@NotNull final Location from) {
        validateLocation(from);
        this.from = from;
    }

    public @NotNull Location getTo() {
        return this.to;
    }

    public void setTo(@NotNull final Location to) {
        validateLocation(to);
        this.to = to;
    }

    public boolean hasChangedPosition() {
        return this.from.getX() != this.to.getX()
                || this.from.getY() != this.to.getY()
                || this.from.getZ() != this.to.getZ();
    }

    public boolean hasExplicitlyChangedPosition() {
        return hasChangedPosition();
    }

    public boolean hasChangedBlock() {
        return this.from.getBlockX() != this.to.getBlockX()
                || this.from.getBlockY() != this.to.getBlockY()
                || this.from.getBlockZ() != this.to.getBlockZ();
    }

    public boolean hasExplicitlyChangedBlock() {
        return hasChangedBlock();
    }

    public boolean hasChangedOrientation() {
        return this.from.getPitch() != this.to.getPitch() || this.from.getYaw() != this.to.getYaw();
    }

    private void validateLocation(@NotNull final Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location world cannot be null");
        }
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
