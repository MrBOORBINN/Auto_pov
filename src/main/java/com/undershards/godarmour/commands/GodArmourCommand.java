package com.undershards.godarmour.commands;

import com.undershards.godarmour.GodArmourPlugin;
import com.undershards.godarmour.abilities.MeteoriteAbility;
import com.undershards.godarmour.armour.ArmourManager;
import com.undershards.godarmour.armour.ArmourPiece;
import com.undershards.godarmour.config.ConfigManager;
import com.undershards.godarmour.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * Executor and tab-completer for /godarmour.
 *
 * <p>Every sub-command validates its arguments and permissions, works
 * from the console where applicable, and never assumes the sender is a
 * player.</p>
 */
public final class GodArmourCommand implements TabExecutor {

    private static final List<String> PIECE_NAMES = List.of("helmet", "chestplate", "leggings", "boots");

    private final GodArmourPlugin plugin;

    public GodArmourCommand(GodArmourPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                if (require(sender, "godarmour.use")) {
                    sendHelp(sender);
                }
            }
            case "give" -> handleGive(sender, args);
            case "equip" -> handleEquip(sender);
            case "stats" -> handleStats(sender);
            case "reload" -> handleReload(sender);
            case "enable" -> handleToggle(sender, true);
            case "disable" -> handleToggle(sender, false);
            case "effects" -> handleEffects(sender, args);
            case "meteor" -> handleMeteor(sender, args);
            case "sethealth" -> handleSetHealth(sender, args);
            case "setcooldown" -> handleSetCooldown(sender, args);
            case "recipe" -> handleRecipe(sender);
            default -> msg(sender, "&cUnknown sub-command &7'" + args[0] + "'&c. Use &7/" + label + " help&c.");
        }
        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!require(sender, "godarmour.give")) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "&cUsage: &7/godarmour give <player> [helmet|chestplate|leggings|boots]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "&cPlayer &7" + args[1] + "&c is not online.");
            return;
        }
        ArmourManager armour = plugin.getArmourManager();
        if (args.length >= 3) {
            ArmourPiece piece = parsePiece(args[2]);
            if (piece == null) {
                msg(sender, "&cUnknown piece &7'" + args[2]
                        + "'&c. Valid pieces: helmet, chestplate, leggings, boots.");
                return;
            }
            armour.givePiece(target, piece);
            msg(sender, "&aGave &cGOD " + capitalize(piece.getConfigKey()) + "&a to &7" + target.getName() + "&a.");
        } else {
            armour.giveFullSet(target);
            msg(sender, "&aGave the full &cGOD ARMOUR&a set to &7" + target.getName() + "&a.");
        }
        msg(target, "&aYou received &cGOD ARMOUR&a.");
    }

    private void handleEquip(CommandSender sender) {
        if (!require(sender, "godarmour.equip")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            msg(sender, "&cOnly players can equip armour.");
            return;
        }
        if (!plugin.isPluginActive()) {
            msg(player, "&cGOD ARMOUR is currently disabled.");
            return;
        }
        int equipped = plugin.getArmourManager().equipFromInventory(player);
        if (equipped == 0) {
            msg(player, "&cNo GOD ARMOUR pieces were found in your inventory.");
        } else {
            msg(player, "&aEquipped &c" + equipped + "&a GOD ARMOUR piece(s).");
        }
    }

    private void handleStats(CommandSender sender) {
        if (!require(sender, "godarmour.stats")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            msg(sender, "&cOnly players can view their stats.");
            return;
        }
        ArmourManager armour = plugin.getArmourManager();
        MeteoriteAbility meteor = plugin.getMeteoriteAbility();

        msg(sender, "&c&lGOD ARMOUR &8- &7Status");
        msg(sender, "&8Plugin: " + (plugin.isPluginActive() ? "&aenabled" : "&cdisabled"));
        for (ArmourPiece piece : ArmourPiece.values()) {
            boolean worn = armour.isPieceEquipped(player, piece);
            msg(sender, "&8" + capitalize(piece.getConfigKey()) + ": "
                    + (worn ? "&aequipped" : "&7missing"));
        }
        msg(sender, "&8Set bonus active: " + (armour.hasValidSet(player) ? "&ayes" : "&cno"));

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            msg(sender, "&8Max health: &c" + formatNumber(maxHealth.getValue())
                    + " HP &8(base " + formatNumber(maxHealth.getBaseValue()) + ")");
        }
        msg(sender, "&8Effects: " + (plugin.getEffectManager().isEffectsDisabled(player.getUniqueId())
                ? "&ctoggled off" : "&aon"));

        String meteorState;
        if (meteor.isMeteorDisabled(player.getUniqueId())) {
            meteorState = "&ctoggled off";
        } else {
            long cooldown = meteor.getRemainingCooldownSeconds(player.getUniqueId());
            meteorState = cooldown > 0 ? "&ecooldown " + cooldown + "s" : "&aready";
        }
        msg(sender, "&8Meteor: " + meteorState);
    }

    private void handleReload(CommandSender sender) {
        if (!require(sender, "godarmour.reload")) {
            return;
        }
        plugin.getConfigManager().load();
        // A reload re-syncs the runtime toggle with general.enabled.
        plugin.setPluginActive(plugin.getConfigManager().isGeneralEnabled());
        plugin.getArmourManager().registerRecipes();
        plugin.getEffectManager().restart();
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getArmourManager().refreshPlayer(player);
        }
        msg(sender, "&aConfiguration reloaded.");
    }

    private void handleToggle(CommandSender sender, boolean enable) {
        if (!require(sender, "godarmour.admin")) {
            return;
        }
        if (plugin.isPluginActive() == enable) {
            msg(sender, enable ? "&eGOD ARMOUR is already enabled." : "&eGOD ARMOUR is already disabled.");
            return;
        }
        plugin.setPluginActive(enable);
        if (enable) {
            msg(sender, "&aGOD ARMOUR enabled. Set bonuses are active again.");
        } else {
            msg(sender, "&cGOD ARMOUR disabled. All set bonuses were removed.");
        }
    }

    private void handleEffects(CommandSender sender, String[] args) {
        if (!require(sender, "godarmour.use")) {
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("toggle")) {
            msg(sender, "&cUsage: &7/godarmour effects toggle");
            return;
        }
        if (!(sender instanceof Player player)) {
            msg(sender, "&cOnly players can toggle their effects.");
            return;
        }
        boolean disabled = plugin.getEffectManager().toggleEffectsDisabled(player.getUniqueId());
        if (disabled) {
            plugin.getEffectManager().removeAll(player);
            msg(player, "&cGOD ARMOUR potion effects disabled.");
        } else {
            plugin.getEffectManager().refreshPlayer(player);
            msg(player, "&aGOD ARMOUR potion effects enabled.");
        }
    }

    private void handleMeteor(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "&cUsage: &7/godarmour meteor <toggle|player|radius <blocks>|damage <amount>>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "toggle" -> {
                if (!require(sender, "godarmour.meteor")) {
                    return;
                }
                if (!(sender instanceof Player player)) {
                    msg(sender, "&cOnly players can toggle their meteor.");
                    return;
                }
                boolean disabled = plugin.getMeteoriteAbility().toggleMeteorDisabled(player.getUniqueId());
                msg(player, disabled ? "&cMeteor ability disabled." : "&aMeteor ability enabled.");
            }
            case "radius" -> {
                if (!require(sender, "godarmour.admin")) {
                    return;
                }
                if (args.length < 3) {
                    msg(sender, "&cUsage: &7/godarmour meteor radius <blocks>");
                    return;
                }
                Double value = parseDouble(sender, args[2], 1.0, 50.0);
                if (value == null) {
                    return;
                }
                plugin.getConfigManager().setAndSave("meteor.radius", value);
                msg(sender, "&aMeteor impact radius set to &c" + formatNumber(value) + "&a blocks.");
            }
            case "damage" -> {
                if (!require(sender, "godarmour.admin")) {
                    return;
                }
                if (args.length < 3) {
                    msg(sender, "&cUsage: &7/godarmour meteor damage <amount>");
                    return;
                }
                Double value = parseDouble(sender, args[2], 0.0, 1000.0);
                if (value == null) {
                    return;
                }
                plugin.getConfigManager().setAndSave("meteor.damage", value);
                msg(sender, "&aMeteor damage set to &c" + formatNumber(value) + " HP&a.");
            }
            default -> {
                if (!require(sender, "godarmour.admin")) {
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    msg(sender, "&cPlayer &7" + args[1] + "&c is not online.");
                    return;
                }
                if (plugin.getMeteoriteAbility().commandStrike(target)) {
                    msg(sender, "&aMeteor inbound for &7" + target.getName() + "&a.");
                    msg(target, "&cA meteor is falling on you!");
                } else {
                    msg(sender, "&cCould not launch a meteor (ability disabled or per-world limit reached).");
                }
            }
        }
    }

    private void handleSetHealth(CommandSender sender, String[] args) {
        if (!require(sender, "godarmour.admin")) {
            return;
        }
        if (args.length < 3) {
            msg(sender, "&cUsage: &7/godarmour sethealth <player> <amount>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "&cPlayer &7" + args[1] + "&c is not online.");
            return;
        }
        Double amount = parseDouble(sender, args[2], 1.0, 1024.0);
        if (amount == null) {
            return;
        }
        if (target.isDead()) {
            msg(sender, "&cThat player is dead; wait for them to respawn.");
            return;
        }
        if (!plugin.getArmourManager().setMaxHealth(target, amount)) {
            msg(sender, "&cCould not change the max health of &7" + target.getName() + "&c.");
            return;
        }
        msg(sender, "&aMax health of &7" + target.getName() + "&a set to &c" + formatNumber(amount) + " HP&a.");
        msg(target, "&aYour max health was set to &c" + formatNumber(amount) + " HP&a.");
    }

    private void handleSetCooldown(CommandSender sender, String[] args) {
        if (!require(sender, "godarmour.admin")) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "&cUsage: &7/godarmour setcooldown <seconds>");
            return;
        }
        Integer seconds = parseInt(sender, args[1], 0, 86_400);
        if (seconds == null) {
            return;
        }
        plugin.getConfigManager().setAndSave("meteor.cooldown-seconds", seconds);
        msg(sender, "&aMeteor cooldown set to &c" + seconds + "s&a.");
    }

    private void handleRecipe(CommandSender sender) {
        if (!require(sender, "godarmour.use")) {
            return;
        }
        ConfigManager config = plugin.getConfigManager();
        if (!config.isRecipeEnabled()) {
            msg(sender, "&eThe custom recipe is disabled in &7config.yml&e (&7armour.recipe.enabled&e).");
            return;
        }
        msg(sender, "&c&lGOD ARMOUR &7custom recipe:");
        msg(sender, "&8Surround the base armour piece with 8x &f"
                + prettyMaterial(config.getRecipeFiller()) + "&8 in a crafting table:");
        for (ArmourPiece piece : ArmourPiece.values()) {
            msg(sender, "&8- GOD " + capitalize(piece.getConfigKey()) + ": &7"
                    + prettyMaterial(config.getMaterial(piece)));
        }
    }

    // ------------------------------------------------------------------
    // Help
    // ------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        msg(sender, "&c&lGOD ARMOUR &8- &7Commands");
        helpLine(sender, "godarmour.use", "&7/godarmour help &8- &fShow this help.");
        helpLine(sender, "godarmour.give",
                "&7/godarmour give <player> [piece] &8- &fGive armour (full set if no piece).");
        helpLine(sender, "godarmour.equip", "&7/godarmour equip &8- &fEquip GOD ARMOUR from your inventory.");
        helpLine(sender, "godarmour.stats", "&7/godarmour stats &8- &fShow your GOD ARMOUR status.");
        helpLine(sender, "godarmour.use", "&7/godarmour effects toggle &8- &fToggle your set potion effects.");
        helpLine(sender, "godarmour.meteor", "&7/godarmour meteor toggle &8- &fToggle your meteor ability.");
        helpLine(sender, "godarmour.use", "&7/godarmour recipe &8- &fShow the custom recipe.");
        helpLine(sender, "godarmour.reload", "&7/godarmour reload &8- &fReload the configuration.");
        helpLine(sender, "godarmour.admin", "&7/godarmour enable|disable &8- &fToggle the whole plugin.");
        helpLine(sender, "godarmour.admin", "&7/godarmour meteor <player> &8- &fStrike a player with a meteor.");
        helpLine(sender, "godarmour.admin", "&7/godarmour meteor radius <blocks> &8- &fSet the impact radius.");
        helpLine(sender, "godarmour.admin", "&7/godarmour meteor damage <amount> &8- &fSet the impact damage.");
        helpLine(sender, "godarmour.admin", "&7/godarmour sethealth <player> <hp> &8- &fSet a player's max health.");
        helpLine(sender, "godarmour.admin", "&7/godarmour setcooldown <seconds> &8- &fSet the meteor cooldown.");
    }

    private void helpLine(CommandSender sender, String permission, String line) {
        if (sender.hasPermission(permission)) {
            msg(sender, line);
        }
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            addSubs(subs, sender, "godarmour.use", "help", "effects", "recipe");
            addSubs(subs, sender, "godarmour.give", "give");
            addSubs(subs, sender, "godarmour.equip", "equip");
            addSubs(subs, sender, "godarmour.stats", "stats");
            addSubs(subs, sender, "godarmour.reload", "reload");
            addSubs(subs, sender, "godarmour.meteor", "meteor");
            addSubs(subs, sender, "godarmour.admin", "enable", "disable", "sethealth", "setcooldown");
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "give" -> sender.hasPermission("godarmour.give")
                        ? filter(playerNames(), args[1]) : List.of();
                case "effects" -> sender.hasPermission("godarmour.use")
                        ? filter(List.of("toggle"), args[1]) : List.of();
                case "meteor" -> {
                    List<String> options = new ArrayList<>();
                    if (sender.hasPermission("godarmour.meteor")) {
                        options.add("toggle");
                    }
                    if (sender.hasPermission("godarmour.admin")) {
                        options.add("radius");
                        options.add("damage");
                        options.addAll(playerNames());
                    }
                    yield filter(options, args[1]);
                }
                case "sethealth" -> sender.hasPermission("godarmour.admin")
                        ? filter(playerNames(), args[1]) : List.of();
                case "setcooldown" -> sender.hasPermission("godarmour.admin")
                        ? filter(List.of("10"), args[1]) : List.of();
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "give" -> sender.hasPermission("godarmour.give")
                        ? filter(PIECE_NAMES, args[2]) : List.of();
                case "sethealth" -> sender.hasPermission("godarmour.admin")
                        ? filter(List.of("20", "40", "80"), args[2]) : List.of();
                case "meteor" -> {
                    if (!sender.hasPermission("godarmour.admin")) {
                        yield List.of();
                    }
                    if (args[1].equalsIgnoreCase("radius")) {
                        yield filter(List.of("5"), args[2]);
                    }
                    if (args[1].equalsIgnoreCase("damage")) {
                        yield filter(List.of("12"), args[2]);
                    }
                    yield List.of();
                }
                default -> List.of();
            };
        }
        return List.of();
    }

    private void addSubs(List<String> subs, CommandSender sender, String permission, String... names) {
        if (sender.hasPermission(permission)) {
            for (String name : names) {
                subs.add(name);
            }
        }
    }

    private List<String> playerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                result.add(option);
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        msg(sender, "&cYou do not have permission to use that command.");
        return false;
    }

    private void msg(CommandSender sender, String message) {
        Text.send(sender, plugin.getConfigManager().getPrefix(), message);
    }

    private ArmourPiece parsePiece(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "helmet" -> ArmourPiece.HELMET;
            case "chestplate", "chest" -> ArmourPiece.CHESTPLATE;
            case "leggings", "legs" -> ArmourPiece.LEGGINGS;
            case "boots" -> ArmourPiece.BOOTS;
            default -> null;
        };
    }

    private Double parseDouble(CommandSender sender, String raw, double min, double max) {
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            msg(sender, "&c'" + raw + "' is not a valid number.");
            return null;
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            msg(sender, "&cValue must be between &7" + formatNumber(min) + "&c and &7"
                    + formatNumber(max) + "&c.");
            return null;
        }
        return value;
    }

    private Integer parseInt(CommandSender sender, String raw, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            msg(sender, "&c'" + raw + "' is not a valid integer.");
            return null;
        }
        if (value < min || value > max) {
            msg(sender, "&cValue must be between &7" + min + "&c and &7" + max + "&c.");
            return null;
        }
        return value;
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String capitalize(String input) {
        if (input.isEmpty()) {
            return input;
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    private static String prettyMaterial(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
