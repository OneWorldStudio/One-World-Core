package com.oneworldstudiomc.paper.event.entity;

import java.util.Collections;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityTransformEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Paper-compatible event fired when an entity is transformed by lightning.
 */
public class EntityZapEvent extends EntityTransformEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    private final LightningStrike bolt;
    private final Entity replacementEntity;

    public EntityZapEvent(@NotNull Entity entity, @NotNull LightningStrike bolt, @NotNull Entity replacementEntity) {
        super(entity, Collections.singletonList(replacementEntity), TransformReason.LIGHTNING);
        this.bolt = bolt;
        this.replacementEntity = replacementEntity;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    public LightningStrike getBolt() {
        return this.bolt;
    }

    @NotNull
    public Entity getReplacementEntity() {
        return this.replacementEntity;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
