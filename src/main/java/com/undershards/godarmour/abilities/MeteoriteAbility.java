package com.undershards.godarmour.abilities;

import com.undershards.godarmour.GodArmourPlugin;
import com.undershards.godarmour.api.MeteorImpactEvent;
import com.undershards.godarmour.config.ConfigManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * The METEORITE POWER ability.
 *
 * <p>When a full-set player lands a melee hit, a meteor spawns high
 * above the victim, animates downwards as particles, and explodes on
 * impact: particles, sounds, radial damage and configurable
 * knockback.</p>
 *
 * <p>Safety guarantees: a per-player cooldown, a per-world limit of
 * active meteors, one self-cancelling task per meteor, fixed particle
 * counts and full cancellation on plugin disable.</p>
 */
public final class MeteoriteAbility {

    /** Blocks the meteor falls per tick. */
    private static final double FALL_SPEED = 1.25;
    /** Hard cap for one meteor animation (ticks) - never runs away. */
    private static final int MAX_TICKS = 400;

    private final GodArmourPlugin plugin;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Set<UUID> meteorDisabled = new HashSet<>();
    private final Map<UUID, Integer> activePerWorld = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    /** True while impact damage is being applied (prevents chaining). */
    private boolean applyingMeteorDamage;

    public MeteoriteAbility(GodArmourPlugin plugin) {
        this.plugin = plugin;
    }

    private ConfigManager config() {
        return plugin.getConfigManager();
    }

    // ------------------------------------------------------------------
    // Activation
    // ------------------------------------------------------------------

    /**
     * Attempts to trigger a meteor automatically after a successful
     * melee hit. Applies cooldown and per-world limits.
     *
     * @param attacker full-set player
     * @param victim   the entity that was hit
     * @return true if a meteor was launched
     */
    public boolean tryActivate(Player attacker, LivingEntity victim) {
        if (!plugin.isPluginActive() || !config().isMeteorEnabled()) {
            return false;
        }
        if (meteorDisabled.contains(attacker.getUniqueId())) {
            return false;
        }
        if (getRemainingCooldownMillis(attacker.getUniqueId()) > 0L) {
            return false;
        }
        World world = victim.getWorld();
        if (countActive(world) >= config().getMeteorMaxPerWorld()) {
            return false;
        }
        Location impact = findSafeImpactLocation(victim.getLocation());
        startCooldown(attacker.getUniqueId());
        beginStrike(attacker, impact);
        return true;
    }

    /**
     * Launches a meteor directly onto a player (admin command).
     * Bypasses the personal cooldown but still respects the per-world
     * limit.
     *
     * @param target target player
     * @return true if a meteor was launched
     */
    public boolean commandStrike(Player target) {
        if (!plugin.isPluginActive() || !config().isMeteorEnabled()) {
            return false;
        }
        World world = target.getWorld();
        if (countActive(world) >= config().getMeteorMaxPerWorld()) {
            return false;
        }
        Location impact = findSafeImpactLocation(target.getLocation());
        beginStrike(null, impact);
        return true;
    }

    /**
     * Clamps the impact location into the world's build limits so the
     * meteor never spawns or strikes outside the world.
     */
    private Location findSafeImpactLocation(Location location) {
        Location impact = location.clone();
        impact.setYaw(0.0f);
        impact.setPitch(0.0f);
        World world = impact.getWorld();
        if (world == null) {
            return impact;
        }
        double y = Math.max(world.getMinHeight(), Math.min(impact.getY(), world.getMaxHeight() - 1));
        impact.setY(y);
        return impact;
    }

    // ------------------------------------------------------------------
    // Meteor flight & impact
    // ------------------------------------------------------------------

    private void beginStrike(@Nullable Player attacker, Location impact) {
        World world = impact.getWorld();
        if (world == null) {
            return;
        }
        activePerWorld.merge(world.getUID(), 1, Integer::sum);

        ConfigManager config = config();
        double startY = Math.min(impact.getY() + config.getMeteorFallHeight(), world.getMaxHeight() - 1);
        Location meteor = impact.clone();
        meteor.setY(startY);

        if (config.isSoundsEnabled()) {
            world.playSound(impact, config.getMeteorFallingSound(),
                    config.getMeteorSoundVolume(), config.getMeteorSoundPitch());
        }

        BukkitTask task = new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                ticks++;
                if (!plugin.isEnabled() || ticks > MAX_TICKS || impact.getWorld() == null) {
                    conclude();
                    return;
                }
                meteor.add(0.0, -FALL_SPEED, 0.0);
                drawTrail(world, meteor);
                if (meteor.getY() <= impact.getY()) {
                    applyImpact(attacker, impact);
                    conclude();
                }
            }

            private void conclude() {
                cancel();
                activePerWorld.merge(world.getUID(), -1, Integer::sum);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        tasks.add(task);
    }

    private void drawTrail(World world, Location location) {
        if (!config().isParticlesEnabled()) {
            return;
        }
        world.spawnParticle(Particle.LAVA, location, 3, 0.15, 0.15, 0.15, 0.0);
        world.spawnParticle(Particle.FLAME, location, 6, 0.25, 0.25, 0.25, 0.02);
    }

    private void applyImpact(@Nullable Player attacker, Location impact) {
        World world = impact.getWorld();
        if (world == null) {
            return;
        }
        ConfigManager config = config();
        double radius = config.getMeteorRadius();

        if (config.isSoundsEnabled()) {
            world.playSound(impact, config.getMeteorImpactSound(),
                    config.getMeteorSoundVolume(), config.getMeteorSoundPitch() * 0.9f);
        }
        if (config.isParticlesEnabled()) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, impact, 1);
            world.spawnParticle(Particle.FLAME, impact, 35, radius / 2.5, 0.8, radius / 2.5, 0.03);
            world.spawnParticle(Particle.LAVA, impact, 20, radius / 2.5, 0.8, radius / 2.5, 0.0);
            world.spawnParticle(Particle.SMOKE, impact, 25, radius / 2.5, 1.0, radius / 2.5, 0.02);
        }

        List<LivingEntity> affected = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(impact, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.equals(attacker) || living.isDead() || living.isInvulnerable()) {
                continue;
            }
            affected.add(living);
        }

        // Protection plugins (or any other listener) may cancel the
        // impact or remove individual entities from the affected list.
        MeteorImpactEvent event = new MeteorImpactEvent(attacker, impact, affected);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        double damage = config.getMeteorDamage();
        double knockback = config.getMeteorKnockback();
        applyingMeteorDamage = true;
        try {
            // Snapshot the list: listeners may have removed entities and
            // damage() below fires more events while we iterate.
            for (LivingEntity living : List.copyOf(event.getAffectedEntities())) {
                if (living.isDead() || !living.isValid()) {
                    continue;
                }
                if (damage > 0.0) {
                    if (attacker != null) {
                        living.damage(damage, attacker);
                    } else {
                        living.damage(damage);
                    }
                }
                applyKnockback(living, impact, knockback);
            }
        } finally {
            applyingMeteorDamage = false;
        }
    }

    private void applyKnockback(LivingEntity entity, Location impact, double strength) {
        if (strength <= 0.0 || entity.isDead()) {
            return;
        }
        Vector push = entity.getLocation().toVector().subtract(impact.toVector());
        push.setY(0.0);
        if (push.lengthSquared() < 1.0E-4) {
            push = new Vector(0.0, 0.0, 1.0);
        }
        push.normalize().multiply(strength);
        push.setY(Math.max(0.4, strength * 0.5));

        Vector velocity = entity.getVelocity().add(push);
        velocity.setX(clampComponent(velocity.getX()));
        velocity.setY(clampComponent(velocity.getY()));
        velocity.setZ(clampComponent(velocity.getZ()));
        entity.setVelocity(velocity);
    }

    private static double clampComponent(double value) {
        return Math.max(-4.0, Math.min(4.0, value));
    }

    // ------------------------------------------------------------------
    // Cooldowns, toggles, bookkeeping
    // ------------------------------------------------------------------

    private void startCooldown(UUID id) {
        cooldownUntil.put(id, System.currentTimeMillis() + config().getMeteorCooldownSeconds() * 1000L);
    }

    /**
     * @param id player uuid
     * @return remaining cooldown in milliseconds (0 = ready)
     */
    public long getRemainingCooldownMillis(UUID id) {
        Long until = cooldownUntil.get(id);
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    /**
     * @param id player uuid
     * @return remaining cooldown in whole seconds (0 = ready)
     */
    public long getRemainingCooldownSeconds(UUID id) {
        return (getRemainingCooldownMillis(id) + 999L) / 1000L;
    }

    /**
     * @param world world
     * @return number of meteors currently falling in that world
     */
    public int countActive(World world) {
        return Math.max(0, activePerWorld.getOrDefault(world.getUID(), 0));
    }

    /**
     * Toggles a player's personal meteor toggle.
     *
     * @param id player uuid
     * @return true if the meteor is now disabled for them
     */
    public boolean toggleMeteorDisabled(UUID id) {
        if (!meteorDisabled.add(id)) {
            meteorDisabled.remove(id);
            return false;
        }
        return true;
    }

    /**
     * @param id player uuid
     * @return true if the player toggled their meteor off
     */
    public boolean isMeteorDisabled(UUID id) {
        return meteorDisabled.contains(id);
    }

    /**
     * @return true while meteor impact damage is being applied; the
     *         combat listener uses this to avoid re-triggering meteors
     *         from meteor damage
     */
    public boolean isApplyingMeteorDamage() {
        return applyingMeteorDamage;
    }

    /**
     * Forgets per-player state on disconnect.
     *
     * @param player quitting player
     */
    public void onPlayerQuit(Player player) {
        cooldownUntil.remove(player.getUniqueId());
        meteorDisabled.remove(player.getUniqueId());
    }

    /**
     * Cancels every running meteor task and clears all bookkeeping.
     * Called on plugin disable.
     */
    public void shutdown() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        activePerWorld.clear();
        cooldownUntil.clear();
        meteorDisabled.clear();
    }
}
