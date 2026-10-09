package com.undershards.godarmour.armour;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The four pieces of the GOD ARMOUR set.
 */
public enum ArmourPiece {

    HELMET("helmet", EquipmentSlot.HEAD, Material.NETHERITE_HELMET),
    CHESTPLATE("chestplate", EquipmentSlot.CHEST, Material.NETHERITE_CHESTPLATE),
    LEGGINGS("leggings", EquipmentSlot.LEGS, Material.NETHERITE_LEGGINGS),
    BOOTS("boots", EquipmentSlot.FEET, Material.NETHERITE_BOOTS);

    private final String configKey;
    private final EquipmentSlot slot;
    private final Material defaultMaterial;

    ArmourPiece(String configKey, EquipmentSlot slot, Material defaultMaterial) {
        this.configKey = configKey;
        this.slot = slot;
        this.defaultMaterial = defaultMaterial;
    }

    /**
     * @return key used in config.yml (armour.pieces.&lt;key&gt;).
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * @return the equipment slot this piece belongs to.
     */
    public EquipmentSlot getSlot() {
        return slot;
    }

    /**
     * @return fallback material if the config is missing or invalid.
     */
    public Material getDefaultMaterial() {
        return defaultMaterial;
    }

    /**
     * @return the value stored in the item's PersistentDataContainer.
     */
    public String getTagValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Resolves a piece from its PersistentDataContainer tag value.
     *
     * @param value stored tag value (may be null)
     * @return the matching piece or null
     */
    public static ArmourPiece fromTagValue(String value) {
        if (value == null) {
            return null;
        }
        for (ArmourPiece piece : values()) {
            if (piece.getTagValue().equals(value)) {
                return piece;
            }
        }
        return null;
    }
}
