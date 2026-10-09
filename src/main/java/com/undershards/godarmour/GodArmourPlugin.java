package com.undershards.godarmour;

import com.undershards.godarmour.abilities.EffectManager;
import com.undershards.godarmour.abilities.MeteoriteAbility;
import com.undershards.godarmour.armour.ArmourManager;
import com.undershards.godarmour.commands.GodArmourCommand;
import com.undershards.godarmour.config.ConfigManager;
import com.undershards.godarmour.listeners.ArmourListener;
import com.undershards.godarmour.listeners.CombatListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GOD ARMOUR - main plugin class.
 *
 * <p>Wires together configuration, the armour set, potion effects,
 * the meteor ability, listeners and the command.</p>
 */
public final class GodArmourPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private ArmourManager armourManager;
    private EffectManager effectManager;
    private MeteoriteAbility meteoriteAbility;

    /** Runtime master switch (general.enabled + /godarmour enable|disable). */
    private boolean active;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        configManager.load();
        active = configManager.isGeneralEnabled();

        armourManager = new ArmourManager(this);
        effectManager = new EffectManager(this);
        meteoriteAbility = new MeteoriteAbility(this);

        getServer().getPluginManager().registerEvents(new ArmourListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);

        GodArmourCommand executor = new GodArmourCommand(this);
        PluginCommand command = getCommand("godarmour");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'godarmour' is missing from plugin.yml - commands will not work!");
        }

        armourManager.registerRecipes();
        effectManager.start();

        // Reconcile players that are online because of a /reload.
        for (Player player : Bukkit.getOnlinePlayers()) {
            armourManager.refreshPlayer(player);
        }

        getLogger().info("GOD ARMOUR has been enabled.");
    }

    @Override
    public void onDisable() {
        // Stop all scheduled work first so nothing leaks or touches
        // managers while they are being torn down.
        if (effectManager != null) {
            effectManager.shutdown();
        }
        if (meteoriteAbility != null) {
            meteoriteAbility.shutdown();
        }
        if (armourManager != null) {
            armourManager.unregisterRecipes();
            // Never leave plugin-owned attribute modifiers on players.
            for (Player player : Bukkit.getOnlinePlayers()) {
                armourManager.clearAttributes(player);
            }
        }
        getLogger().info("GOD ARMOUR has been disabled.");
    }

    /**
     * @return whether the plugin's abilities are currently active.
     */
    public boolean isPluginActive() {
        return active;
    }

    /**
     * Toggles the plugin at runtime. When disabled, all plugin-owned
     * attribute modifiers and potion effects are removed immediately.
     * This is a runtime toggle and is not written back to config.yml
     * (which would strip the file's comments on save).
     *
     * @param active new state
     */
    public void setPluginActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        if (!active) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                armourManager.clearAttributes(player);
                effectManager.removeAll(player);
            }
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                armourManager.refreshPlayer(player);
            }
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ArmourManager getArmourManager() {
        return armourManager;
    }

    public EffectManager getEffectManager() {
        return effectManager;
    }

    public MeteoriteAbility getMeteoriteAbility() {
        return meteoriteAbility;
    }
}
