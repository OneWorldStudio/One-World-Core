package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Bukkit compatibility wrapper for the legacy Spigot entity mount event.
 */
public class EntityMountEvent extends org.spigotmc.event.entity.EntityMountEvent {

    public EntityMountEvent(@NotNull Entity what, @NotNull Entity mount) {
        super(what, mount);
    }
}
