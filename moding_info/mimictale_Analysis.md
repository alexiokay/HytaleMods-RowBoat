# MimicTale - Deep Analysis

**Mod Name:** MimicTale
**Version:** 1.0.6
**Architecture:** Pure Asset Pack (NO Java code!)

## Overview

MimicTale adds mimic enemies - hostile creatures that disguise as chests. This mod demonstrates how to create **complete NPCs** using only JSON configuration, including attacks, animations, loot tables, and AI behavior.

---

## Why This Mod Is Important

Shows you can create complex enemies **without Java code**:
- Multiple attack types with different animations
- Weighted loot tables
- Camera/head tracking
- Physics ("wiggle weights")
- Multiple mimic variants

---

## File Structure

```
MimicTale-1.0.6.zip
├── Server/
│   ├── Drops/NPCs/
│   │   ├── Drop_Mimic_Ancient.json     # High-tier loot
│   │   ├── Drop_Mimic_Crude.json       # Low-tier loot
│   │   ├── Drop_Mimic_HiRoll.json      # Special loot
│   │   ├── Drop_Mimic_Lumberjack.json
│   │   ├── Drop_Mimic_Ruins.json
│   │   └── Drop_Mimic_Village.json
│   └── Item/
│       ├── Animations/NPC/Beast/Mimic/
│       │   └── Mimic_Default.json      # Animation set
│       ├── Interactions/NPCs/Beast/Mimic/
│       │   ├── Mimic_Spike_Nova.json           # AOE attack
│       │   ├── Mimic_Spike_Nova_Damage.json
│       │   ├── Mimic_Swing_Up_Left.json        # Melee attack
│       │   ├── Mimic_Swing_Up_Left_Damage.json
│       │   ├── Mimic_Tongue_Burst_Left.json    # Ranged attack
│       │   └── Mimic_Tongue_Burst_Right.json
│       └── Items/
│           └── Egg_Spawner_Mimic_*.json  # Spawn eggs
└── Common/
    └── [Models, Textures, Animations]
```

---

## NPC Animation Set

### Mimic_Default.json

```json
{
  "Animations": {
    "TongueBurstLeft": {
      "ThirdPerson": "NPC/Beast/Ancient Mimic/Animations/Attacks/Tongue_Burst_Left.blockyanim",
      "ThirdPersonMoving": "NPC/Beast/Ancient Mimic/Animations/Attacks/Tongue_Left_Moving.blockyanim",
      "Looping": false,
      "Speed": 1
    },
    "TongueBurstRight": {
      "ThirdPerson": "NPC/Beast/Ancient Mimic/Animations/Attacks/Tongue_Burst_Right.blockyanim",
      "ThirdPersonMoving": "NPC/Beast/Ancient Mimic/Animations/Attacks/Tongue_Right_Moving.blockyanim",
      "Looping": false,
      "Speed": 1
    },
    "SpikeNova": {
      "ThirdPerson": "NPC/Beast/Ancient Mimic/Animations/Attacks/Spike_Nova.blockyanim",
      "Looping": false,
      "Speed": 1
    },
    "SwingUpLeft": {
      "ThirdPerson": "NPC/Beast/Ancient Mimic/Animations/Attacks/Swing_Up_Left.blockyanim",
      "Looping": false,
      "Speed": 1
    }
  },

  "Camera": {
    "Pitch": {
      "AngleRange": { "Max": 45, "Min": -15 },
      "TargetNodes": ["Head"]
    },
    "Yaw": {
      "AngleRange": { "Max": 30, "Min": -30 },
      "TargetNodes": ["Head"]
    }
  },

  "WiggleWeights": {
    "Pitch": 2,
    "PitchDeceleration": 0.1,
    "Roll": 0.1,
    "RollDeceleration": 0.1,
    "X": 3,
    "XDeceleration": 0.1,
    "Y": 0.1,
    "YDeceleration": 0.1,
    "Z": 0.1,
    "ZDeceleration": 0.1
  }
}
```

### Animation Properties

| Property | Description |
|----------|-------------|
| `ThirdPerson` | Animation file for stationary |
| `ThirdPersonMoving` | Animation file while moving |
| `Looping` | Does animation repeat? |
| `Speed` | Playback speed multiplier |

### Camera Tracking

Allows NPC head to track targets:

| Property | Description |
|----------|-------------|
| `Pitch.AngleRange` | Vertical look range |
| `Yaw.AngleRange` | Horizontal look range |
| `TargetNodes` | Model bones to rotate |

### Wiggle Weights

Physics-based secondary motion:

| Property | Description |
|----------|-------------|
| `Pitch/Roll` | Rotational wiggle amount |
| `X/Y/Z` | Positional wiggle amount |
| `*Deceleration` | How fast wiggle settles |

---

## Attack Interactions

### Spike Nova (AOE Attack)

```json
{
  "Type": "Simple",
  "Effects": {
    "ItemPlayerAnimationsId": "Mimic_Default",
    "ItemAnimationId": "SpikeNova"
  },
  "RunTime": 1,
  "Next": {
    "Type": "Parallel",
    "Interactions": [
      {
        "Interactions": [{
          "Type": "Selector",
          "RunTime": 0.25,
          "Selector": {
            "Id": "AOECircle",
            "Range": 3
          },
          "HitEntity": {
            "Interactions": [{
              "Type": "Replace",
              "DefaultValue": {
                "Interactions": ["Mimic_Spike_Nova_Damage"]
              },
              "Var": "Melee_Damage"
            }]
          }
        }]
      },
      {
        "Interactions": [{
          "Type": "Simple",
          "RunTime": 0.167,
          "Effects": {}
        }]
      }
    ]
  }
}
```

### Interaction Flow

1. `Simple` - Play animation, wait 1 second
2. `Parallel` - Run multiple interactions simultaneously
3. `Selector` - Target selection (AOE circle, range 3)
4. `HitEntity` - What happens when hitting target
5. `Replace` - Apply damage interaction

### Selector Types

| Selector | Description |
|----------|-------------|
| `AOECircle` | Circular area around attacker |
| `Cone` | Forward-facing cone |
| `Line` | Straight line (projectile) |
| `Self` | Only the attacker |

---

## Loot Tables

### Weighted Random Loot

```json
{
  "Container": {
    "Type": "Choice",
    "Containers": [
      {
        "Type": "Single",
        "Item": {
          "ItemId": "Ingredient_Bar_Gold",
          "QuantityMin": 1,
          "QuantityMax": 3
        },
        "Weight": 100
      },
      {
        "Type": "Single",
        "Item": {
          "ItemId": "Weapon_Sword_Bronze_Ancient"
        },
        "Weight": 15
      },
      {
        "Type": "Single",
        "Item": {
          "ItemId": "Egg_Spawner_Mimic_Ancient"
        },
        "Weight": 25
      }
    ]
  }
}
```

### Loot Table Structure

| Property | Description |
|----------|-------------|
| `Container.Type` | `Choice` = random, `All` = everything |
| `Containers` | List of possible drops |
| `Type: "Single"` | Single item drop |
| `ItemId` | Item to drop |
| `QuantityMin/Max` | Stack size range |
| `Weight` | Relative probability |

### Weight Calculation

```
Probability = Weight / SumOfAllWeights

Example with weights [100, 100, 15, 25]:
- Gold bars: 100/240 = 41.7%
- Health potion: 100/240 = 41.7%
- Sword: 15/240 = 6.25%
- Spawn egg: 25/240 = 10.4%
```

---

## Mimic Variants

| Variant | Description | Loot Tier |
|---------|-------------|-----------|
| Ancient | Temple mimics | High |
| Crude | Basic mimics | Low |
| HiRoll | Rare/special | Very High |
| Lumberjack | Forest themed | Medium |
| Ruins | Dungeon themed | Medium |
| Village | Settlement themed | Medium |

---

## Application to HytaleVehicles

While vehicles are different from enemies, some patterns apply:

| Pattern | Vehicle Application |
|---------|-------------------|
| Animation Sets | Vehicle wheel spin, door open |
| Camera tracking | Turret aiming |
| Wiggle weights | Suspension bounce |
| Spawn eggs | Vehicle spawn items |

---

## Summary

MimicTale shows:
- **Complete NPC creation** without Java code
- **Animation system** with variants (stationary/moving)
- **Attack interactions** with selectors and damage
- **Weighted loot tables** for drops
- **Physics simulation** via wiggle weights
- **Head tracking** for target following

All through JSON configuration only!
