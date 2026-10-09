package com.undershards.godarmour.config;

import com.undershards.godarmour.GodArmourPlugin;
import com.undershards.godarmour.armour.ArmourPiece;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.config.ConfigurationSection;
import org.bukkit.config.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

/**
 * Loads, validates and caches every value from config.yml.
 *
 * <p>Every numeric value is range-checked; missing or invalid values
 * fall back to safe defaults and produce a console warning.</p>
 */
public final class ConfigManager {

    /** One configurable, infinite potion effect. */
    public record EffectSetting(PotionEffectType type, String name, int level) {
    }

    private static final List<String> DEFAULT_LORE = List.of(
            "&7Forged beyond the heavens.",
            "&8Full set: &c%max_health% HP&8, divine effects",
            "&8and a falling &cmeteor&8.");

    /** Config key -> potion effect type, in display order. */
    private static final LinkedHashMap<String, PotionEffectType> EFFECT_TYPES = new LinkedHashMap<>();
    /** Config key -> default display level (roman numeral). */
    private static final LinkedHashMap<String, Integer> EFFECT_DEFAULT_LEVELS = new LinkedHashMap<>();

    static {
        EFFECT_TYPES.put("speed", PotionEffectType.SPEED);
        EFFECT_TYPES.put("strength", PotionEffectType.STRENGTH);
        EFFECT_TYPES.put("resistance", PotionEffectType.RESISTANCE);
        EFFECT_TYPES.put("fire-resistance", PotionEffectType.FIRE_RESISTANCE);
        EFFECT_TYPES.put("regeneration", PotionEffectType.REGENERATION);
        EFFECT_TYPES.put("jump-boost", PotionEffectType.JUMP_BOOST);
        EFFECT_TYPES.put("night-vision", PotionEffectType.NIGHT_VISION);

        EFFECT_DEFAULT_LEVELS.put("speed", 2);
        EFFECT_DEFAULT_LEVELS.put("strength", 2);
        EFFECT_DEFAULT_LEVELS.put("resistance", 2);
        EFFECT_DEFAULT_LEVELS.put("fire-resistance", 1);
        EFFECT_DEFAULT_LEVELS.put("regeneration", 1);
        EFFECT_DEFAULT_LEVELS.put("jump-boost", 2);
        EFFECT_DEFAULT_LEVELS.put("night-vision", 1);
    }

    private final GodArmourPlugin plugin;

    // general
    private boolean generalEnabled;
    // armour
    private double maxHealth;
    private boolean requireFullSet;
    private String customName;
    private List<String> lore;
    private boolean unbreakable;
    private final Map<ArmourPiece, Material> materials = new EnumMap<>(ArmourPiece.class);
    private final Map<ArmourPiece, Map<Enchantment, Integer>> enchantments = new EnumMap<>(ArmourPiece.class);
    private boolean recipeEnabled;
    private Material recipeFiller;
    // effects
    private boolean effectsEnabled;
    private int refreshIntervalTicks;
    private List<EffectSetting> effects = List.of();
    // meteor
    private boolean meteorEnabled;
    private int meteorCooldownSeconds;
    private double meteorRadius;
    private double meteorDamage;
    private int meteorMaxPerWorld;
    private double meteorKnockback;
    private int meteorFallHeight;
    private Sound meteorFallingSound;
    private Sound meteorImpactSound;
    private float meteorSoundVolume;
    private float meteorSoundPitch;
    // defence
    private double fallDamageReduction;
    private double fireDamageReduction;
    private double projectileDamageReduction;
    private double generalDamageReduction;
    private double knockbackResistance;
    // visuals
    private boolean particlesEnabled;
    private boolean soundsEnabled;
    private boolean armourGlow;
    private Particle auraParticle;
    // messages
    private String prefix;

    public ConfigManager(GodArmourPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * (Re)loads and validates the whole configuration.
     */
    public void load() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        generalEnabled = c.getBoolean("general.enabled", true);

        maxHealth = boundedDouble(c, "armour.max-health", 80.0, 1.0, 1024.0);
        requireFullSet = c.getBoolean("armour.require-full-set", true);
        customName = stringOrDefault(c, "armour.custom-name", "&c&lGOD ARMOUR");
        lore = stringListOrDefault(c, "armour.lore", DEFAULT_LORE);
        unbreakable = c.getBoolean("armour.unbreakable", false);
        loadPieces(c);
        recipeEnabled = c.getBoolean("armour.recipe.enabled", false);
        recipeFiller = material(c, "armour.recipe.filler", Material.NETHER_STAR);

        effectsEnabled = c.getBoolean("effects.enabled", true);
        refreshIntervalTicks = boundedInt(c, "effects.refresh-interval-ticks", 40, 5, 1200);
        loadEffects(c);

        meteorEnabled = c.getBoolean("meteor.enabled", true);
        meteorCooldownSeconds = boundedInt(c, "meteor.cooldown-seconds", 10, 0, 86_400);
        meteorRadius = boundedDouble(c, "meteor.radius", 5.0, 0.5, 50.0);
        meteorDamage = boundedDouble(c, "meteor.damage", 12.0, 0.0, 1000.0);
        meteorMaxPerWorld = boundedInt(c, "meteor.max-active-per-world", 5, 1, 100);
        meteorKnockback = boundedDouble(c, "meteor.knockback", 1.0, 0.0, 10.0);
        meteorFallHeight = boundedInt(c, "meteor.fall-height", 15, 5, 64);
        meteorFallingSound = sound(c, "meteor.sounds.falling", Sound.ENTITY_WITHER_SHOOT);
        meteorImpactSound = sound(c, "meteor.sounds.impact", Sound.ENTITY_GENERIC_EXPLODE);
        meteorSoundVolume = (float) boundedDouble(c, "meteor.sounds.volume", 1.0, 0.0, 16.0);
        meteorSoundPitch = (float) boundedDouble(c, "meteor.sounds.pitch", 1.0, 0.5, 2.0);

        fallDamageReduction = boundedDouble(c, "defence.fall-damage-reduction", 100.0, 0.0, 100.0);
        fireDamageReduction = boundedDouble(c, "defence.fire-damage-reduction", 100.0, 0.0, 100.0);
        projectileDamageReduction = boundedDouble(c, "defence.projectile-damage-reduction", 25.0, 0.0, 100.0);
        generalDamageReduction = boundedDouble(c, "defence.general-damage-reduction", 0.0, 0.0, 100.0);
        knockbackResistance = boundedDouble(c, "defence.knockback-resistance", 1.0, 0.0, 1.0);

        particlesEnabled = c.getBoolean("visuals.particles", true);
        soundsEnabled = c.getBoolean("visuals.sounds", true);
        armourGlow = c.getBoolean("visuals.armour-glow", true);
        auraParticle = particle(c, "visuals.aura-particle", Particle.END_ROD);

        prefix = stringOrDefault(c, "messages.prefix", "&8[&cGOD ARMOUR&8] ");
    }

    /**
     * Writes a value to config.yml, saves the file and re-validates
     * all cached values. Used by the admin tuning commands.
     *
     * @param path  config path
     * @param value new value
     */
    public void setAndSave(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
        load();
    }

    // ------------------------------------------------------------------
    // Section parsers
    // ------------------------------------------------------------------

    private void loadPieces(FileConfiguration c) {
        Map<Enchantment, Integer> defaultEnchants = defaultEnchantments();
        for (ArmourPiece piece : ArmourPiece.values()) {
            String base = "armour.pieces." + piece.getConfigKey();
            materials.put(piece, material(c, base + ".material", piece.getDefaultMaterial()));

            Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
            ConfigurationSection section = c.getConfigurationSection(base + ".enchantments");
            if (section != null && !section.getKeys(false).isEmpty()) {
                for (String key : section.getKeys(false)) {
                    Enchantment enchantment = parseEnchantment(key);
                    if (enchantment == null) {
                        plugin.getLogger().warning(
                                "Unknown enchantment '" + key + "' at '" + base + ".enchantments', skipping.");
                        continue;
                    }
                    int level;
                    if (!section.isNumber(key)) {
                        plugin.getLogger().warning(
                                "Invalid level for enchantment '" + key + "' at '" + base + ".enchantments', using 1.");
                        level = 1;
                    } else {
                        level = section.getInt(key, 1);
                        if (level < 1 || level > 255) {
                            plugin.getLogger().warning(
                                    "Level for enchantment '" + key + "' at '" + base
                                            + ".enchantments' is out of range (1-255), using 1.");
                            level = 1;
                        }
                    }
                    enchants.put(enchantment, level);
                }
            } else {
                enchants.putAll(defaultEnchants);
            }
            enchantments.put(piece, Collections.unmodifiableMap(enchants));
        }
    }

    private Map<Enchantment, Integer> defaultEnchantments() {
        Map<Enchantment, Integer> map = new LinkedHashMap<>();
        putDefaultEnchantment(map, "protection", 4);
        putDefaultEnchantment(map, "unbreaking", 3);
        putDefaultEnchantment(map, "mending", 1);
        return map;
    }

    private void putDefaultEnchantment(Map<Enchantment, Integer> map, String name, int level) {
        Enchantment enchantment = parseEnchantment(name);
        if (enchantment != null) {
            map.put(enchantment, level);
        }
    }

    private void loadEffects(FileConfiguration c) {
        List<EffectSetting> list = new ArrayList<>();
        for (Map.Entry<String, PotionEffectType> entry : EFFECT_TYPES.entrySet()) {
            String key = entry.getKey();
            String path = "effects.list." + key;
            boolean enabled = !c.contains(path + ".enabled") || c.getBoolean(path + ".enabled", true);
            int defaultLevel = EFFECT_DEFAULT_LEVELS.getOrDefault(key, 1);
            int level = boundedInt(c, path + ".level", defaultLevel, 1, 256);
            if (enabled) {
                list.add(new EffectSetting(entry.getValue(), key, level));
            }
        }
        this.effects = List.copyOf(list);
    }

    // ------------------------------------------------------------------
    // Validated value helpers
    // ------------------------------------------------------------------

    private double boundedDouble(FileConfiguration c, String path, double def, double min, double max) {
        if (!c.isNumber(path)) {
            if (c.contains(path)) {
                plugin.getLogger().warning("Invalid number at '" + path + "', using default " + def + ".");
            }
            return def;
        }
        double value = c.getDouble(path);
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            plugin.getLogger().warning(
                    "Value at '" + path + "' is out of range [" + min + ", " + max + "], using default " + def + ".");
            return def;
        }
        return value;
    }

    private int boundedInt(FileConfiguration c, String path, int def, int min, int max) {
        if (!c.isNumber(path)) {
            if (c.contains(path)) {
                plugin.getLogger().warning("Invalid integer at '" + path + "', using default " + def + ".");
            }
            return def;
        }
        int value = c.getInt(path);
        if (value < min || value > max) {
            plugin.getLogger().warning(
                    "Value at '" + path + "' is out of range [" + min + ", " + max + "], using default " + def + ".");
            return def;
        }
        return value;
    }

    private String stringOrDefault(FileConfiguration c, String path, String def) {
        String value = c.getString(path);
        if (value == null || value.isBlank()) {
            return def;
        }
        return value;
    }

    private List<String> stringListOrDefault(FileConfiguration c, String path, List<String> def) {
        List<String> value = c.getStringList(path);
        if (value.isEmpty()) {
            return List.copyOf(def);
        }
        return List.copyOf(value);
    }

    private Material material(FileConfiguration c, String path, Material def) {
        String raw = c.getString(path);
        if (raw == null || raw.isBlank()) {
            return def;
        }
        Material parsed = Material.matchMaterial(raw.trim());
        if (parsed == null) {
            plugin.getLogger().warning("Unknown material '" + raw + "' at '" + path + "', using default " + def + ".");
            return def;
        }
        return parsed;
    }

    private Sound sound(FileConfiguration c, String path, Sound def) {
        String raw = c.getString(path);
        if (raw == null || raw.isBlank()) {
            return def;
        }
        Sound parsed = parseSound(raw);
        if (parsed == null) {
            plugin.getLogger().warning("Unknown sound '" + raw + "' at '" + path + "', using default.");
            return def;
        }
        return parsed;
    }

    private Particle particle(FileConfiguration c, String path, Particle def) {
        String raw = c.getString(path);
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return Particle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown particle '" + raw + "' at '" + path + "', using default " + def + ".");
            return def;
        }
    }

    /**
     * Parses a sound from config. Accepts both enum style
     * ("ENTITY_WITHER_SHOOT") and id style ("entity.wither.shoot",
     * optionally with a namespace).
     *
     * @param raw raw config value
     * @return the sound or null if unknown
     */
    public static Sound parseSound(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', '.');
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        if (namespacedKey == null) {
            return null;
        }
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT).get(namespacedKey);
    }

    private Enchantment parseEnchantment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.minecraft(name.trim().toLowerCase(Locale.ROOT));
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public boolean isGeneralEnabled() {
        return generalEnabled;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public boolean isRequireFullSet() {
        return requireFullSet;
    }

    public String getCustomName() {
        return customName;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean isUnbreakable() {
        return unbreakable;
    }

    public Material getMaterial(ArmourPiece piece) {
        return materials.getOrDefault(piece, piece.getDefaultMaterial());
    }

    public Map<Enchantment, Integer> getEnchantments(ArmourPiece piece) {
        return enchantments.getOrDefault(piece, Map.of());
    }

    public boolean isRecipeEnabled() {
        return recipeEnabled;
    }

    public Material getRecipeFiller() {
        return recipeFiller;
    }

    public boolean isEffectsEnabled() {
        return effectsEnabled;
    }

    public int getRefreshIntervalTicks() {
        return refreshIntervalTicks;
    }

    public List<EffectSetting> getEffects() {
        return effects;
    }

    /**
     * @param type potion effect type
     * @return configured display level or null if not configured
     */
    public Integer getEffectLevel(PotionEffectType type) {
        for (EffectSetting setting : effects) {
            if (setting.type().equals(type)) {
                return setting.level();
            }
        }
        return null;
    }

    public boolean isMeteorEnabled() {
        return meteorEnabled;
    }

    public int getMeteorCooldownSeconds() {
        return meteorCooldownSeconds;
    }

    public double getMeteorRadius() {
        return meteorRadius;
    }

    public double getMeteorDamage() {
        return meteorDamage;
    }

    public int getMeteorMaxPerWorld() {
        return meteorMaxPerWorld;
    }

    public double getMeteorKnockback() {
        return meteorKnockback;
    }

    public int getMeteorFallHeight() {
        return meteorFallHeight;
    }

    public Sound getMeteorFallingSound() {
        return meteorFallingSound;
    }

    public Sound getMeteorImpactSound() {
        return meteorImpactSound;
    }

    public float getMeteorSoundVolume() {
        return meteorSoundVolume;
    }

    public float getMeteorSoundPitch() {
        return meteorSoundPitch;
    }

    public double getFallDamageReduction() {
        return fallDamageReduction;
    }

    public double getFireDamageReduction() {
        return fireDamageReduction;
    }

    public double getProjectileDamageReduction() {
        return projectileDamageReduction;
    }

    public double getGeneralDamageReduction() {
        return generalDamageReduction;
    }

    public double getKnockbackResistance() {
        return knockbackResistance;
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public boolean isSoundsEnabled() {
        return soundsEnabled;
    }

    public boolean isArmourGlow() {
        return armourGlow;
    }

    public Particle getAuraParticle() {
        return auraParticle;
    }

    public String getPrefix() {
        return prefix;
    }
}
