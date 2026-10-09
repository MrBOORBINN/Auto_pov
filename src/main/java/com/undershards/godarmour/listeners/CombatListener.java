package com.undershards.godarmour.listeners;

import com.undershards.godarmour.GodArmourPlugin;
import com.undershards.godarmour.config.ConfigManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Defensive abilities and the melee trigger for the meteor ability.
 *
 * <p>Damage reduction is cause-specific and never touches events that
 * another plugin already cancelled. Meteor damage itself flows through
 * the normal damage pipeline, so protection plugins and vanilla PvP
 * rules keep working.</p>
 */
public final class CombatListener implements Listener {

    private final GodArmourPlugin plugin;

    public CombatListener(GodArmourPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.isPluginActive()) {
            return;
        }
        if (!plugin.getArmourManager().hasValidSet(player)) {
            return;
        }
        ConfigManager config = plugin.getConfigManager();
        double reduction = switch (event.getCause()) {
            case FALL -> config.getFallDamageReduction();
            case FIRE, FIRE_TICK, LAVA -> config.getFireDamageReduction();
            case PROJECTILE -> config.getProjectileDamageReduction();
            default -> config.getGeneralDamageReduction();
        };
        if (reduction <= 0.0) {
            return;
        }
        if (reduction >= 100.0) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(Math.max(0.0, event.getDamage() * (1.0 - reduction / 100.0)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        // Never let meteor damage re-trigger another meteor.
        if (plugin.getMeteoriteAbility().isApplyingMeteorDamage()) {
            return;
        }
        if (!plugin.isPluginActive() || !plugin.getConfigManager().isMeteorEnabled()) {
            return;
        }
        // Melee hits only - arrows, splash potions, thorns, etc. do not
        // count as "hitting an enemy".
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (victim.equals(attacker)) {
            return;
        }
        if (!plugin.getArmourManager().hasValidSet(attacker)) {
            return;
        }
        plugin.getMeteoriteAbility().tryActivate(attacker, victim);
    }
}
