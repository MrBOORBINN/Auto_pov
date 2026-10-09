package com.undershards.godarmour.api;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a GOD ARMOUR meteor reaches the ground, before any damage
 * is dealt.
 *
 * <p>Protection plugins can integrate by listening to this event:
 * cancel it entirely or remove individual entities from the affected
 * list (for example players inside a protected region).</p>
 */
public class MeteorImpactEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player attacker;
    private final Location location;
    private final List<LivingEntity> affectedEntities;
    private boolean cancelled;

    public MeteorImpactEvent(@Nullable Player attacker, @NotNull Location location,
                             @NotNull List<LivingEntity> affectedEntities) {
        this.attacker = attacker;
        this.location = location.clone();
        this.affectedEntities = new ArrayList<>(affectedEntities);
    }

    /**
     * @return the player who called the meteor down, or null for
     *         command-triggered strikes from the console
     */
    public @Nullable Player getAttacker() {
        return attacker;
    }

    /**
     * @return impact location (cloned)
     */
    public @NotNull Location getLocation() {
        return location.clone();
    }

    /**
     * Live, mutable list of entities that will be damaged. Listeners
     * may remove entities to spare them.
     *
     * @return affected entities
     */
    public @NotNull List<LivingEntity> getAffectedEntities() {
        return affectedEntities;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
