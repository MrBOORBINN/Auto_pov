# Auto POV

A client-side **Fabric** mod that moves your camera to third person in the moments it
actually helps - and puts it back the way you had it afterwards.

`autopov-1.0.0.jar` in this repository is the finished mod. One jar covers Minecraft
**1.21 - 1.21.11** (Fabric Loader + Fabric API required, Java 21).

## What it does

| Trigger (on by default) | Camera |
|---|---|
| Riding anything (horse, boat, minecart, pig...) | third person |
| Gliding with elytra | third person |
| Eyes underwater | third person |
| In lava | third person |
| Swimming | third person |
| Climbing / sleeping / sneaking / creative flying | off by default |

* When the situation ends, the perspective you had before comes back
  (`restorePrevious: false` always returns to first person instead).
* Short blips are absorbed by small activation/restore delays, so the camera never flickers.
* If you move the camera yourself (F5), the mod stands down until the situation changes again.
* Press **V** to toggle the whole thing on/off while playing (rebindable in Controls).
* Everything is configurable in `config/autopov.json`, which the mod writes on first start:

```json
{
  "enabled": true,
  "perspective": "third_person_back",
  "restorePrevious": true,
  "respectManualOverride": true,
  "showMessages": true,
  "activationDelayTicks": 2,
  "restoreDelayTicks": 10,
  "triggers": {
    "riding": true, "elytra": true, "underwater": true, "lava": true, "swimming": true,
    "climbing": false, "sleeping": false, "sneaking": false, "creativeFlying": false
  }
}
```

## Install

1. Install Fabric Loader and Fabric API for your Minecraft 1.21.x version.
2. Drop `autopov-1.0.0.jar` into `.minecraft/mods/`.
3. Play - the camera takes care of itself.

## How this jar was built and checked

This repository intentionally ships only the jar; the build machinery lives in `.build/`
(a Gradle/loom-free pipeline, because the sandbox it was produced in has no Maven access):

* Java sources are compiled with the Eclipse compiler against **compile-only stubs** whose
  shapes were copied member-by-member from the real Yarn mappings (1.21.4 and 1.21.11),
  the Fabric API sources and fabric-loader.
* The compiled classes are remapped from Yarn names to **intermediary** names with a small
  constant-pool remapper, exactly like a production Fabric jar.
* An 86-assertion headless simulation runs the mod against the stub Minecraft - once with
  Yarn names and again with the shipped intermediary bytecode - for both the pre- and
  post-1.21.9 key binding API shapes.
* Every Minecraft class/field/method the jar references is verified to exist in the
  official intermediary mappings of **all** of 1.21, 1.21.1 ... 1.21.11.
* The key binding is created reflectively by parameter *type*, because Minecraft 1.21.9
  replaced the category `String` with `KeyBinding.Category` - that is what lets one jar
  cover the entire 1.21 line.
