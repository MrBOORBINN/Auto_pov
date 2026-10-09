# GOD ARMOUR

A production-quality **Paper 1.21.x** plugin (Java 21, Maven) that adds a divine
armour set with massive health, infinite potion effects, meteor strikes and
godly defence.

- **Main package:** `com.undershards.godarmour`
- **Target server:** Paper 1.21.10 (`api-version: 1.21`)
- **Build:** `mvn clean package`

---

## Features

### GOD ARMOUR set
Four pieces (Helmet, Chestplate, Leggings, Boots) identified through
**PersistentDataContainer** tags — plain renamed items are never recognised.
Custom name/lore, configurable materials and enchantments, optional custom
crafting recipe, optional particles and enchantment glint. Each worn piece is
validated individually; set bonuses are re-evaluated on every inventory
change, right-click equip, respawn, world change, join and periodically
(handles broken/replaced/missing pieces).

### Maximum health
Full-set players get **80 HP (40 hearts)** by default (configurable). The
bonus is applied as a plugin-owned attribute modifier (keyed by
`NamespacedKey`, removed/re-applied idempotently — no duplicates), current
health is clamped to the maximum and everything is reconciled on death,
respawn, reconnect and plugin enable/disable so nothing is left behind.

### Infinite potion effects
Speed II, Strength II, Resistance II, Fire Resistance I, Regeneration I,
Jump Boost II, Night Vision I — each individually configurable/disableable.
The plugin tracks which effects *it* applied and only removes those; stronger
effects from other plugins are never overwritten.

### Meteorite power
Successful melee hits of a full-set player can call down a meteor above the
enemy: falling particle animation, configurable sounds, impact particles,
radial damage, configurable knockback and cooldown. Capped by
`meteor.max-active-per-world`; all tasks are cancelled on disable.

Other plugins can integrate through the cancellable
`com.undershards.godarmour.api.MeteorImpactEvent` (cancel the impact or remove
individual entities from the affected list).

### Damage & defence
Per-cause damage reduction (fall, fire/lava, projectile, general),
attribute-based knockback resistance — all independently configurable.
Cancelled events from protection plugins are respected.

---

## Building

### Ready-to-use jar

A compiled plugin jar is provided at **`dist/GodArmour-1.0.0.jar`** (Java 21
bytecode, Paper `api-version: 1.21`). Drop it into your Paper 1.21.x server's
`plugins/` folder and start the server.

> Note on that jar: it was produced in a network-restricted environment with
> the Eclipse ECJ 3.46 compiler (`-source/-target 21`) against API stubs whose
> signatures were verified against the real Paper `ver/1.21.10` sources —
> because Maven Central / `repo.papermc.io` were unreachable there, it was
> **not** built with Maven against the official `paper-api` artifact. For
> maximum assurance, rebuild it yourself with the standard steps below; the
> output is equivalent.

### Building from source

Requirements: **JDK 21** and **Maven 3.8+** (internet access to
`repo.papermc.io` and Maven Central).

```bash
mvn clean package
```

The plugin jar is written to:

```
target/GodArmour-1.0.0.jar
```

### Installing on a Paper server

1. Stop your Paper 1.21.10 server.
2. Copy `target/GodArmour-1.0.0.jar` into the server's `plugins/` folder.
3. Start the server — `plugins/GodArmour/config.yml` is generated on first
   start.
4. (Optional) Edit `config.yml`, then run `/godarmour reload`.

---

## Commands

| Command | Permission | Console | Description |
|---|---|---|---|
| `/godarmour` / `/godarmour help` | `godarmour.use` | yes | Show help |
| `/godarmour give <player> [piece]` | `godarmour.give` | yes | Give full set or one piece |
| `/godarmour equip` | `godarmour.equip` | no | Equip pieces from your inventory |
| `/godarmour stats` | `godarmour.stats` | no | Show your set status |
| `/godarmour effects toggle` | `godarmour.use` | no | Toggle your set potion effects |
| `/godarmour meteor toggle` | `godarmour.meteor` | no | Toggle your meteor ability |
| `/godarmour meteor <player>` | `godarmour.admin` | yes | Strike a player with a meteor |
| `/godarmour meteor radius <blocks>` | `godarmour.admin` | yes | Set impact radius (1–50) |
| `/godarmour meteor damage <amount>` | `godarmour.admin` | yes | Set impact damage (0–1000 HP) |
| `/godarmour sethealth <player> <amount>` | `godarmour.admin` | yes | Set a player's max health (1–1024) |
| `/godarmour setcooldown <seconds>` | `godarmour.admin` | yes | Set the meteor cooldown (0–86400) |
| `/godarmour reload` | `godarmour.reload` | yes | Reload configuration & recipes |
| `/godarmour enable` / `disable` | `godarmour.admin` | yes | Runtime toggle (not persisted) |
| `/godarmour recipe` | `godarmour.use` | yes | Show the custom recipe |

Alias: `/ga`. Tab completion is provided for all sub-commands.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `godarmour.use` | true | help / effects toggle / recipe |
| `godarmour.equip` | true | `/godarmour equip` |
| `godarmour.stats` | true | `/godarmour stats` |
| `godarmour.meteor` | true | `/godarmour meteor toggle` |
| `godarmour.give` | op | `/godarmour give` |
| `godarmour.reload` | op | `/godarmour reload` |
| `godarmour.admin` | op | enable/disable, sethealth, setcooldown, meteor tuning & strikes (implies use/give/reload) |

Admin permissions default to **op** only — normal players never get them.

---

## Configuration

Everything lives in `plugins/GodArmour/config.yml` (generated with
documentation comments). All numbers are validated; missing or invalid
values fall back to safe defaults and log a console warning. Highlights:

- `armour.max-health` — set max health in HP (default `80.0` = 40 hearts)
- `armour.require-full-set` — `false` lets a single piece activate bonuses
- `armour.pieces.<piece>.material/enchantments` — item creation
- `armour.recipe.enabled/filler` — optional custom shaped recipe
- `effects.list.<effect>.enabled/level` — per-effect configuration
- `meteor.*` — cooldown, radius, damage, per-world cap, knockback, sounds
- `defence.*` — per-cause damage reduction + knockback resistance
- `visuals.*` — particles, sounds, glint, aura particle
- `messages.prefix` — chat prefix

## Project layout

```
pom.xml
src/main/java/com/undershards/godarmour/
├── GodArmourPlugin.java            # main class, lifecycle, runtime toggle
├── abilities/
│   ├── EffectManager.java          # infinite potion effects + aura
│   └── MeteoriteAbility.java       # meteor ability (tasks, cooldowns, limits)
├── api/
│   └── MeteorImpactEvent.java      # integration point for protection plugins
├── armour/
│   ├── ArmourManager.java          # item creation/validation, attributes, recipes
│   └── ArmourPiece.java            # the four pieces
├── commands/
│   └── GodArmourCommand.java       # executor + tab completion
├── config/
│   └── ConfigManager.java          # validated config loading
├── listeners/
│   ├── ArmourListener.java         # equipment/join/quit/respawn/world changes
│   └── CombatListener.java         # defence + meteor trigger
└── util/
    └── Text.java                   # colour/message helper
src/main/resources/
├── plugin.yml
└── config.yml
```

## Technical notes

- Attribute bonuses use `AttributeModifier`s keyed by plugin
  `NamespacedKey`s (the 1.21.2+ replacement for UUID-based modifiers); the
  plugin only ever removes modifiers in its own namespace, so other plugins'
  attribute changes stay intact.
- Modifiers are removed on quit and plugin disable, so they are not
  persisted into player save data. If the server is hard-killed, stray
  modifiers are detected and stripped on the next join.
- Meteor damage flows through the standard damage pipeline
  (`LivingEntity#damage`), so PvP rules, protection plugins and other
  listeners keep working; cancelled events are respected.
- All scheduled tasks are tracked and cancelled on disable; particle counts
  are fixed and small.
