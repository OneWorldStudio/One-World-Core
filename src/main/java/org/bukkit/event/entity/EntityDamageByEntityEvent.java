package org.bukkit.event.entity;

import com.google.common.base.Function;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Called when an entity is damaged by an entity
 */
public class EntityDamageByEntityEvent extends EntityDamageEvent {
    private final Entity damager;
    private final boolean critical;

    public EntityDamageByEntityEvent(@NotNull final Entity damager, @NotNull final Entity damagee, @NotNull final DamageCause cause, final double damage) {
        this(damager, damagee, cause, damage, false);
    }

    public EntityDamageByEntityEvent(@NotNull final Entity damager, @NotNull final Entity damagee, @NotNull final DamageCause cause, final double damage, final boolean critical) {
        super(damagee, cause, damage);
        this.damager = damager;
        this.critical = critical;
    }

    public EntityDamageByEntityEvent(@NotNull final Entity damager, @NotNull final Entity damagee, @NotNull final DamageCause cause, @NotNull final Map<DamageModifier, Double> modifiers, @NotNull final Map<DamageModifier, ? extends Function<? super Double, Double>> modifierFunctions) {
        this(damager, damagee, cause, modifiers, modifierFunctions, false);
    }

    public EntityDamageByEntityEvent(@NotNull final Entity damager, @NotNull final Entity damagee, @NotNull final DamageCause cause, @NotNull final Map<DamageModifier, Double> modifiers, @NotNull final Map<DamageModifier, ? extends Function<? super Double, Double>> modifierFunctions, final boolean critical) {
        super(damagee, cause, modifiers, modifierFunctions);
        this.damager = damager;
        this.critical = critical;
    }

    /**
     * Returns the entity that damaged the defender.
     *
     * @return Entity that damaged the defender.
     */
    @NotNull
    public Entity getDamager() {
        return damager;
    }

    /**
     * Gets whether this damage was caused by a critical melee hit.
     *
     * @return true if this was a critical hit
     */
    public boolean isCritical() {
        return this.critical;
    }
}
