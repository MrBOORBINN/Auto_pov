package com.undershards.godarmour.abilities;

import com.undershards.godarmour.GodArmourPlugin;
import com.undershards.godarmour.config.ConfigManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Applies and refreshes the infinite potion effects of the GOD ARMOUR
 * set.
 *
 * <p>The manager tracks exactly which effects <em>it</em> applied per
 * player ("managed" effects) and only ever removes those. Effects from
 * other plugins with a stronger amplifier are left untouched; the
 * plugin simply waits until they expire and then re-applies its own.</p>
 */
public final class EffectManager {

    private final GodArmourPlugin plugin;

    /** Potion effects applied by this plugin, per player. */
    private final Map<UUID, Set<PotionEffectType>> managed = new HashMap<>();
    /** Players that toggled their set effects off. */
    private final Set<UUID> effectsDisabled = new HashSet<>();

    private BukkitTask task;

    public EffectManager(GodArmourPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts (or restarts) the refresh task with the configured
     * interval.
     */
    public void start() {
        stopTask();
        int interval = Math.max(5, plugin.getConfigManager().getRefreshIntervalTicks());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    /**
     * Restarts the refresh task, e.g. after a config reload.
     */
    public void restart() {
        start();
    }

    /**
     * Cancels the task and removes all managed effects. Called on
     * plugin disable.
     */
    public void shutdown() {
        stopTask();
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeAll(player);
        }
        managed.clear();
        effectsDisabled.clear();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!plugin.isPluginActive()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    /**
     * Reconciles one player: re-checks the set (handles broken,
     * replaced or missing pieces), spawns the aura, and applies or
     * removes the managed potion effects.
     *
     * @param player player to refresh
     */
    public void refreshPlayer(Player player) {
        if (!player.isOnline()) {
            return;
        }
        plugin.getArmourManager().refreshPlayer(player);

        ConfigManager config = plugin.getConfigManager();
        boolean validSet = plugin.getArmourManager().hasValidSet(player);

        if (validSet && config.isParticlesEnabled()) {
            spawnAura(player);
        }
        if (validSet && config.isEffectsEnabled() && !effectsDisabled.contains(player.getUniqueId())) {
            apply(player);
        } else {
            removeAll(player);
        }
    }

    private void apply(Player player) {
        ConfigManager config = plugin.getConfigManager();
        int interval = config.getRefreshIntervalTicks();
        int duration = Math.max(60, interval * 4);
        int refreshBelow = Math.max(40, interval * 2);

        Set<PotionEffectType> mine = managed.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>());
        for (ConfigManager.EffectSetting setting : config.getEffects()) {
            int amplifier = setting.level() - 1;
            PotionEffect current = player.getPotionEffect(setting.type());
            if (current != null && current.getAmplifier() > amplifier) {
                // A stronger effect from another source is active - do not
                // overwrite it and do not claim ownership.
                mine.remove(setting.type());
                continue;
            }
            boolean needsApply = current == null
                    || current.getAmplifier() != amplifier
                    || current.getDuration() <= refreshBelow;
            if (needsApply) {
                player.addPotionEffect(
                        new PotionEffect(setting.type(), duration, amplifier, false, true, true));
            }
            mine.add(setting.type());
        }
    }

    /**
     * Removes only the effects this plugin applied to the player.
     * If another source has since applied a stronger effect of the
     * same type, it is left alone.
     *
     * @param player player
     */
    public void removeAll(Player player) {
        Set<PotionEffectType> mine = managed.remove(player.getUniqueId());
        if (mine == null || mine.isEmpty()) {
            return;
        }
        ConfigManager config = plugin.getConfigManager();
        for (PotionEffectType type : mine) {
            PotionEffect current = player.getPotionEffect(type);
            if (current == null) {
                continue;
            }
            Integer ourLevel = config.getEffectLevel(type);
            if (ourLevel != null && current.getAmplifier() > ourLevel - 1) {
                // Someone replaced our effect with a stronger one - keep it.
                continue;
            }
            player.removePotionEffect(type);
        }
    }

    /**
     * Handles a player disconnect: removes managed effects and forgets
     * the player's toggle state.
     *
     * @param player quitting player
     */
    public void onPlayerQuit(Player player) {
        removeAll(player);
        effectsDisabled.remove(player.getUniqueId());
    }

    /**
     * Toggles a player's personal effects toggle.
     *
     * @param id player uuid
     * @return true if effects are now disabled
     */
    public boolean toggleEffectsDisabled(UUID id) {
        if (!effectsDisabled.add(id)) {
            effectsDisabled.remove(id);
            return false;
        }
        return true;
    }

    /**
     * @param id player uuid
     * @return true if the player toggled their effects off
     */
    public boolean isEffectsDisabled(UUID id) {
        return effectsDisabled.contains(id);
    }

    private void spawnAura(Player player) {
        World world = player.getWorld();
        Location location = player.getLocation();
        world.spawnParticle(plugin.getConfigManager().getAuraParticle(),
                location.getX(), location.getY() + 1.0, location.getZ(),
                4, 0.35, 0.8, 0.35, 0.01);
    }
}
