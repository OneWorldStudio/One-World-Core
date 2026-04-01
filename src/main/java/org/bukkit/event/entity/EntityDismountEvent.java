package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Bukkit compatibility wrapper for the legacy Spigot entity dismount event.
 */
public class EntityDismountEvent extends org.spigotmc.event.entity.EntityDismountEvent {

    public EntityDismountEvent(@NotNull Entity what, @NotNull Entity dismounted) {
        super(what, dismounted);
    }
}
