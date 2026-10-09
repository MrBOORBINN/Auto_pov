package com.undershards.godarmour.armour;

import com.undershards.godarmour.GodArmourPlugin;
import com.undershards.godarmour.config.ConfigManager;
import com.undershards.godarmour.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Creates, validates and manages the GOD ARMOUR set.
 *
 * <p>Items are identified through a {@link PersistentDataType#STRING}
 * value in their {@link org.bukkit.persistence.PersistentDataContainer};
 * plain renamed items are never recognised. All attribute modifiers are
 * owned through plugin {@link NamespacedKey}s and only modifiers owned
 * by this plugin are ever removed.</p>
 */
public final class ArmourManager {

    private final GodArmourPlugin plugin;
    private final NamespacedKey pieceKey;
    private final NamespacedKey maxHealthKey;
    private final NamespacedKey knockbackKey;

    public ArmourManager(GodArmourPlugin plugin) {
        this.plugin = plugin;
        this.pieceKey = new NamespacedKey(plugin, "god_armour_piece");
        this.maxHealthKey = new NamespacedKey(plugin, "god_armour_max_health");
        this.knockbackKey = new NamespacedKey(plugin, "god_armour_knockback_resistance");
    }

    // ------------------------------------------------------------------
    // Item creation & validation
    // ------------------------------------------------------------------

    /**
     * Creates a fresh GOD ARMOUR piece with name, lore, PDC tag,
     * enchantments, unbreakability and glint from the config.
     *
     * @param piece piece to create
     * @return the item
     */
    public ItemStack createPiece(ArmourPiece piece) {
        ConfigManager config = plugin.getConfigManager();
        ItemStack item = new ItemStack(config.getMaterial(piece));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Text.colorize(config.getCustomName()));

        String healthText = formatNumber(config.getMaxHealth());
        List<Component> lore = new ArrayList<>();
        for (String line : config.getLore()) {
            lore.add(Text.colorize(line.replace("%max_health%", healthText)));
        }
        meta.lore(lore);

        meta.getPersistentDataContainer().set(pieceKey, PersistentDataType.STRING, piece.getTagValue());

        for (Map.Entry<Enchantment, Integer> entry : config.getEnchantments(piece).entrySet()) {
            meta.addEnchant(entry.getKey(), entry.getValue(), true);
        }
        if (config.isUnbreakable()) {
            meta.setUnbreakable(true);
        }
        if (config.isArmourGlow()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Validates a single item and resolves which GOD ARMOUR piece it is.
     *
     * @param item item to check (may be null)
     * @return the piece or null if the item is not genuine GOD ARMOUR
     */
    public ArmourPiece getPiece(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String tag = meta.getPersistentDataContainer().get(pieceKey, PersistentDataType.STRING);
        return ArmourPiece.fromTagValue(tag);
    }

    /**
     * @param item item to check (may be null)
     * @return true if the item is any genuine GOD ARMOUR piece
     */
    public boolean isGodPiece(ItemStack item) {
        return getPiece(item) != null;
    }

    // ------------------------------------------------------------------
    // Equipment queries
    // ------------------------------------------------------------------

    /**
     * @param player player
     * @param piece  piece
     * @return the item currently worn in the piece's slot (may be null)
     */
    public ItemStack getEquippedItem(Player player, ArmourPiece piece) {
        PlayerInventory inventory = player.getInventory();
        return switch (piece) {
            case HELMET -> inventory.getHelmet();
            case CHESTPLATE -> inventory.getChestplate();
            case LEGGINGS -> inventory.getLeggings();
            case BOOTS -> inventory.getBoots();
        };
    }

    private void setEquippedItem(Player player, ArmourPiece piece, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        switch (piece) {
            case HELMET -> inventory.setHelmet(item);
            case CHESTPLATE -> inventory.setChestplate(item);
            case LEGGINGS -> inventory.setLeggings(item);
            case BOOTS -> inventory.setBoots(item);
        }
    }

    /**
     * @param player player
     * @param piece  piece
     * @return true if a genuine item of exactly that piece is worn
     */
    public boolean isPieceEquipped(Player player, ArmourPiece piece) {
        return getPiece(getEquippedItem(player, piece)) == piece;
    }

    /**
     * @param player player
     * @return true if all four genuine pieces are worn
     */
    public boolean hasFullSet(Player player) {
        for (ArmourPiece piece : ArmourPiece.values()) {
            if (!isPieceEquipped(player, piece)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether the set bonus counts as active for this player,
     * honouring armour.require-full-set.
     *
     * @param player player
     * @return true if the set bonus should apply
     */
    public boolean hasValidSet(Player player) {
        if (plugin.getConfigManager().isRequireFullSet()) {
            return hasFullSet(player);
        }
        for (ArmourPiece piece : ArmourPiece.values()) {
            if (isPieceEquipped(player, piece)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Attribute reconciliation
    // ------------------------------------------------------------------

    /**
     * Re-applies or removes the set's attribute bonuses depending on
     * what the player currently wears and whether the plugin is active.
     * Safe to call at any time: it never duplicates modifiers and only
     * touches modifiers owned by this plugin.
     *
     * @param player player to reconcile
     */
    public void refreshPlayer(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (plugin.isPluginActive() && hasValidSet(player)) {
            applyMaxHealth(player);
            applyKnockbackResistance(player);
        } else {
            clearAttributes(player);
        }
    }

    private void applyMaxHealth(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.MAX_HEALTH);
        if (instance == null) {
            return;
        }
        removeOwnedModifiers(instance);
        double target = plugin.getConfigManager().getMaxHealth();
        double delta = target - instance.getBaseValue();
        if (Math.abs(delta) >= 0.01) {
            instance.addModifier(new AttributeModifier(
                    maxHealthKey, delta, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        clampHealth(player);
    }

    private void applyKnockbackResistance(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (instance == null) {
            return;
        }
        removeOwnedModifiers(instance);
        double value = plugin.getConfigManager().getKnockbackResistance();
        if (value <= 0.0) {
            return;
        }
        instance.addModifier(new AttributeModifier(
                knockbackKey, value, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }

    /**
     * Sets a player's maximum health to an explicit amount (used by
     * /godarmour sethealth). The modifier is owned by this plugin.
     *
     * @param player target player
     * @param amount new max health in HP
     * @return true if applied
     */
    public boolean setMaxHealth(Player player, double amount) {
        if (player.isDead()) {
            return false;
        }
        AttributeInstance instance = player.getAttribute(Attribute.MAX_HEALTH);
        if (instance == null) {
            return false;
        }
        removeOwnedModifiers(instance);
        double delta = amount - instance.getBaseValue();
        if (Math.abs(delta) >= 0.01) {
            instance.addModifier(new AttributeModifier(
                    maxHealthKey, delta, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        clampHealth(player);
        return true;
    }

    /**
     * Removes every plugin-owned attribute modifier from the player
     * and clamps the health to the (possibly lowered) maximum.
     *
     * @param player player
     */
    public void clearAttributes(Player player) {
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            removeOwnedModifiers(health);
        }
        AttributeInstance knockback = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            removeOwnedModifiers(knockback);
        }
        clampHealth(player);
    }

    /**
     * Clamps the player's current health to the attribute maximum.
     * Never sets health above the maximum.
     *
     * @param player player
     */
    public void clampHealth(Player player) {
        if (player.isDead()) {
            return;
        }
        AttributeInstance instance = player.getAttribute(Attribute.MAX_HEALTH);
        if (instance == null) {
            return;
        }
        double max = instance.getValue();
        if (player.getHealth() > max) {
            player.setHealth(max);
        }
    }

    private void removeOwnedModifiers(AttributeInstance instance) {
        String namespace = plugin.getName().toLowerCase(Locale.ROOT);
        for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
            if (modifier.getKey().getNamespace().equals(namespace)) {
                instance.removeModifier(modifier);
            }
        }
    }

    // ------------------------------------------------------------------
    // Give / equip helpers
    // ------------------------------------------------------------------

    /**
     * Gives one piece to a player, dropping it at their feet if the
     * inventory is full.
     *
     * @param player player
     * @param piece  piece
     */
    public void givePiece(Player player, ArmourPiece piece) {
        giveItem(player, createPiece(piece));
    }

    /**
     * Gives the full set to a player.
     *
     * @param player player
     */
    public void giveFullSet(Player player) {
        for (ArmourPiece piece : ArmourPiece.values()) {
            giveItem(player, createPiece(piece));
        }
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    /**
     * Moves matching GOD ARMOUR pieces from the player's storage
     * inventory into the correct armour slots.
     *
     * @param player player
     * @return number of pieces equipped
     */
    public int equipFromInventory(Player player) {
        int equipped = 0;
        PlayerInventory inventory = player.getInventory();
        for (ArmourPiece piece : ArmourPiece.values()) {
            if (isPieceEquipped(player, piece)) {
                continue;
            }
            ItemStack[] storage = inventory.getStorageContents();
            for (int slot = 0; slot < storage.length; slot++) {
                if (getPiece(storage[slot]) == piece) {
                    inventory.setItem(slot, null);
                    setEquippedItem(player, piece, storage[slot]);
                    equipped++;
                    break;
                }
            }
        }
        if (equipped > 0) {
            refreshPlayer(player);
        }
        return equipped;
    }

    // ------------------------------------------------------------------
    // Optional custom recipes
    // ------------------------------------------------------------------

    /**
     * @param piece piece
     * @return the namespaced key of the piece's custom recipe
     */
    public NamespacedKey recipeKey(ArmourPiece piece) {
        return new NamespacedKey(plugin, "god_" + piece.getConfigKey());
    }

    /**
     * Registers the optional custom recipes (base piece surrounded by
     * 8 filler items). Any previously registered recipes are removed
     * first so reloads never duplicate them.
     */
    public void registerRecipes() {
        unregisterRecipes();
        ConfigManager config = plugin.getConfigManager();
        if (!config.isRecipeEnabled()) {
            return;
        }
        Material filler = config.getRecipeFiller();
        if (!filler.isItem()) {
            plugin.getLogger().warning("Recipe filler material is not a valid item; skipping custom recipes.");
            return;
        }
        for (ArmourPiece piece : ArmourPiece.values()) {
            Material base = config.getMaterial(piece);
            if (!base.isItem()) {
                continue;
            }
            try {
                ShapedRecipe recipe = new ShapedRecipe(recipeKey(piece), createPiece(piece));
                recipe.shape("FFF", "FPF", "FFF");
                recipe.setIngredient('F', filler);
                recipe.setIngredient('P', base);
                Bukkit.addRecipe(recipe);
            } catch (IllegalStateException ex) {
                plugin.getLogger().warning("Could not register recipe for " + piece + ": " + ex.getMessage());
            }
        }
    }

    /**
     * Removes all custom recipes registered by this plugin.
     */
    public void unregisterRecipes() {
        for (ArmourPiece piece : ArmourPiece.values()) {
            Bukkit.removeRecipe(recipeKey(piece));
        }
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
