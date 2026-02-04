# Treqy's Explosives - Deep Analysis

**Mod Name:** Treqy's Explosives
**Version:** 1.1.3
**Author:** Treqy
**Architecture:** Pure Asset Pack (NO Java code!)

## Overview

Adds throwable dynamite with configurable explosion effects. This mod demonstrates Hytale's **built-in explosion interaction system** - all configured through JSON.

---

## File Structure

```
Treqy's-Explosives_1.1.3.zip
├── Common/
│   ├── Icons/ItemsGenerated/
│   │   ├── Cluster_Dynamite.png
│   │   ├── Crusher_Dynamite.png
│   │   └── Dynamite.png
│   ├── Items/
│   │   ├── Dynamite.blockymodel
│   │   ├── T_Cluster_Dynamite.png
│   │   ├── T_Crusher_Dynamite.png
│   │   └── T_Dynamite.png
│   └── Sounds/
│       ├── MA_SFXpecial-BombsAndExplosions2_1_MP3.ogg
│       └── MA_SFXpecial-BombsAndExplosions2_2_MP3.ogg
└── Server/
    ├── Audio/SoundEvents/SFX/Projectiles/Player/Bomb/
    │   ├── SFX_Crusher_Dynamite_Explosion.json
    │   └── SFX_Dynamite_Explosion.json
    └── Item/Interactions/
        ├── Explode_Crusher_Dynamite.json
        └── Explode_Dynamite.json
```

---

## Explosion Interaction

### Explode_Dynamite.json

```json
{
  "Type": "Explode",
  "Config": {
    "DamageEntities": true,
    "DamageBlocks": true,
    "BlockDamageRadius": 6,
    "BlockDropChance": 0.4,
    "EntityDamage": 100,
    "EntityDamageRadius": 8,
    "Knockback": {
      "Type": "Point",
      "VelocityConfig": {
        "AirResistance": 0.97,
        "AirResistanceMax": 0.96,
        "Direction": { "X": 0.0, "Y": 5.0, "Z": 0.0 },
        "GroundResistance": 0.94,
        "GroundResistanceMax": 0.3,
        "Threshold": 3.0
      },
      "Force": 10,
      "VelocityType": "Set",
      "VelocityY": 10
    },
    "ItemTool": {
      "Specs": [
        { "Power": 2, "GatherType": "SoftBlocks" },
        { "Power": 2, "GatherType": "Soils" },
        { "Power": 2, "GatherType": "Woods" },
        { "Power": 2, "GatherType": "Rocks" },
        { "Power": 2, "GatherType": "Benches" },
        { "Power": 0.001, "GatherType": "VolcanicRocks" }
      ]
    }
  },
  "Effects": {
    "Particles": [{
      "SystemId": "Explosion_Big",
      "TargetEntityPart": "Entity",
      "Scale": 3
    }],
    "LocalSoundEventId": "SFX_Dynamite_Explosion",
    "WorldSoundEventId": "SFX_Dynamite_Explosion",
    "CameraEffect": "Impact_Strong",
    "WaitForAnimationToFinish": false
  }
}
```

---

## Explosion Config Properties

### Damage Settings

| Property | Type | Description |
|----------|------|-------------|
| `DamageEntities` | bool | Do entities take damage? |
| `DamageBlocks` | bool | Are blocks destroyed? |
| `EntityDamage` | int | Damage amount to entities |
| `EntityDamageRadius` | float | Entity damage range |
| `BlockDamageRadius` | float | Block destruction range |
| `BlockDropChance` | float | Probability blocks drop items (0-1) |

### Knockback Configuration

```json
{
  "Knockback": {
    "Type": "Point",              // Knockback from explosion center
    "Force": 10,                  // Knockback strength
    "VelocityType": "Set",        // Replace or add velocity
    "VelocityY": 10,              // Vertical knockback component
    "VelocityConfig": {
      "AirResistance": 0.97,      // Air drag (0-1)
      "AirResistanceMax": 0.96,   // Max air drag
      "GroundResistance": 0.94,   // Ground friction
      "GroundResistanceMax": 0.3, // Max ground friction
      "Direction": {              // Base direction
        "X": 0.0, "Y": 5.0, "Z": 0.0
      },
      "Threshold": 3.0            // Min velocity before stopping
    }
  }
}
```

### Knockback Types

| Type | Description |
|------|-------------|
| `Point` | Away from explosion center |
| `Direction` | Fixed direction |
| `None` | No knockback |

### VelocityType

| Type | Description |
|------|-------------|
| `Set` | Replace current velocity |
| `Add` | Add to current velocity |

---

## Block Destruction

### ItemTool Specs

Controls which blocks can be destroyed and how easily:

```json
{
  "ItemTool": {
    "Specs": [
      { "Power": 2, "GatherType": "SoftBlocks" },
      { "Power": 2, "GatherType": "Soils" },
      { "Power": 2, "GatherType": "Woods" },
      { "Power": 2, "GatherType": "Rocks" },
      { "Power": 2, "GatherType": "Benches" },
      { "Power": 0.001, "GatherType": "VolcanicRocks" }
    ]
  }
}
```

### GatherTypes

| GatherType | Examples |
|------------|----------|
| `SoftBlocks` | Leaves, grass |
| `Soils` | Dirt, sand, gravel |
| `Woods` | Wood planks, logs |
| `Rocks` | Stone, ores |
| `Benches` | Crafting stations |
| `VolcanicRocks` | Obsidian, basalt |

### Power Levels

| Power | Effect |
|-------|--------|
| 0.001 | Almost no damage |
| 1 | Normal damage |
| 2 | Double damage |
| 5+ | One-shot most blocks |

---

## Visual Effects

### Particles

```json
{
  "Particles": [{
    "SystemId": "Explosion_Big",    // Built-in particle system
    "TargetEntityPart": "Entity",   // Where particles spawn
    "Scale": 3                      // Size multiplier
  }]
}
```

### Built-in Particle Systems

| SystemId | Description |
|----------|-------------|
| `Explosion_Big` | Large explosion |
| `Explosion_Small` | Small explosion |
| `Smoke_Puff` | Smoke effect |
| `Fire_Burst` | Fire effect |

### Camera Effects

| Effect | Description |
|--------|-------------|
| `Impact_Strong` | Heavy screen shake |
| `Impact_Medium` | Medium shake |
| `Impact_Light` | Light shake |

---

## Sound Events

### SFX_Dynamite_Explosion.json

```json
{
  "Sounds": [
    {
      "SoundPath": "Sounds/BombsAndExplosions2_1_MP3.ogg",
      "Weight": 1
    },
    {
      "SoundPath": "Sounds/BombsAndExplosions2_2_MP3.ogg",
      "Weight": 1
    }
  ],
  "Volume": 1.0,
  "VolumeVariation": 0.1,
  "Pitch": 1.0,
  "PitchVariation": 0.2,
  "Range": 50
}
```

### Sound Properties

| Property | Description |
|----------|-------------|
| `Sounds` | List of sound files (random selection) |
| `Weight` | Relative probability |
| `Volume` | Base volume (0-1) |
| `VolumeVariation` | Random variation |
| `Pitch` | Playback speed |
| `PitchVariation` | Random pitch variation |
| `Range` | Audible distance |

---

## Dynamite Variants

### Standard Dynamite
- 6 block destruction radius
- 8 block entity damage radius
- 100 damage
- 40% block drop chance

### Crusher Dynamite
- Higher block damage
- Lower entity damage
- Good for mining

### Cluster Dynamite
- Multiple smaller explosions
- Wider area coverage

---

## Creating Custom Explosives

```json
{
  "Type": "Explode",
  "Config": {
    "DamageEntities": true,
    "DamageBlocks": true,
    "BlockDamageRadius": 3,
    "EntityDamage": 50,
    "EntityDamageRadius": 5,
    "BlockDropChance": 1.0,
    "Knockback": {
      "Type": "Point",
      "Force": 5
    },
    "ItemTool": {
      "Specs": [
        { "Power": 1, "GatherType": "SoftBlocks" }
      ]
    }
  },
  "Effects": {
    "Particles": [{ "SystemId": "Explosion_Small", "Scale": 1 }],
    "LocalSoundEventId": "SFX_My_Explosion",
    "CameraEffect": "Impact_Light"
  }
}
```

---

## Application to HytaleVehicles

| Pattern | Vehicle Application |
|---------|-------------------|
| Explosion interaction | Vehicle destruction |
| Knockback config | Collision physics |
| Block damage | Vehicle/terrain interaction |
| Camera effects | Impact feedback |
| Particle systems | Smoke, fire, debris |

---

## Summary

Treqy's Explosives demonstrates:
- **Built-in `Type: "Explode"` interaction** - No code needed!
- **Configurable damage** for entities and blocks
- **Knockback physics** with resistance settings
- **Block type targeting** via GatherType
- **Particle and sound effects** integration
- **Camera shake** for impact feedback

This is essential knowledge for vehicle destruction effects!
