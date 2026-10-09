package com.undershards.godarmour.listeners;

import com.undershards.godarmour.GodArmourPlugin;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks every way the worn armour can change - inventory clicks,
 * drags, right-click equipping, joining, respawning and world changes -
 * and re-evaluates the set bonuses one tick later, when the change has
 * been applied.
 *
 * <p>Movements are never cancelled: players can freely move GOD ARMOUR
 * items around; the bonuses simply follow the equipment slots.</p>
 */
public final class ArmourListener implements Listener {

    private final GodArmourPlugin plugin;
    /** Players with a refresh already scheduled (deduplication). */
    private final Set<UUID> pendingRefresh = new HashSet<>();

    public ArmourListener(GodArmourPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        scheduleRefresh(player, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        scheduleRefresh(player, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !plugin.getArmourManager().isGodPiece(item)) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            // Right-clicking armour equips it; re-check next tick.
            scheduleRefresh(event.getPlayer(), 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Reconcile: strips stray plugin-owned modifiers (e.g. left by a
        // crash) and re-applies the set bonuses if the player wears them.
        plugin.getArmourManager().refreshPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        pendingRefresh.remove(player.getUniqueId());
        // Remove plugin-owned modifiers and effects so nothing persists
        // in the player's saved data.
        plugin.getArmourManager().clearAttributes(player);
        plugin.getEffectManager().onPlayerQuit(player);
        plugin.getMeteoriteAbility().onPlayerQuit(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // Equipment is finalised around the respawn; give it a moment.
        scheduleRefresh(event.getPlayer(), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        scheduleRefresh(event.getPlayer(), 1L);
    }

    private void scheduleRefresh(Player player, long delayTicks) {
        UUID id = player.getUniqueId();
        if (!pendingRefresh.add(id)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingRefresh.remove(id);
            if (player.isOnline()) {
                plugin.getArmourManager().refreshPlayer(player);
            }
        }, delayTicks);
    }
}
