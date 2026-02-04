# Brotherhood Spellbooks (Wolf Summoning) - Deep Analysis

**Mod Name:** BrotherhoodOfTheWolf_Spellbooks
**Version:** 11.0.0
**Author:** DarkWolf_XP
**Architecture:** Pure Asset Pack (NO Java code!)

## Overview

Adds spellbooks that summon wolf companions. Demonstrates NPC summoning via JSON interactions, companion AI configuration, and variant rarity systems.

---

## File Structure

```
BrotherhoodSpellbooks_MOD_V11.zip
├── manifest.json
├── Common/
│   ├── Blocks/Brotherhood/
│   │   ├── BrotherhoodWolf_Statue.blockymodel
│   │   └── *_Texture.png (multiple variants)
│   ├── Icons/
│   │   ├── CraftingCategories/Arcane/
│   │   └── ItemsGenerated/
│   ├── Items/Weapons/Spellbook/
│   │   ├── BrotherhoodOfTheWolfSpellbook.blockymodel
│   │   └── Weapon_*.png
│   └── NPC/Beast/BrotherhoodWolf/
│       ├── Animations/
│       │   ├── Attacks/Bite.blockyanim
│       │   ├── Damage/Death.blockyanim, Hurt.blockyanim
│       │   ├── Default/Idle.blockyanim, Run.blockyanim, Walk.blockyanim
│       │   ├── Flavor/Howl.blockyanim, Sit.blockyanim
│       │   ├── Fly/Fly.blockyanim
│       │   └── Swim/Swim.blockyanim
│       └── Models/
│           ├── BrotherhoodWolf_Model.blockymodel
│           └── Model_Textures/*.png
└── Server/
    ├── Item/
    │   ├── Block/Sets/Brotherhood_WolfStatues_*.json
    │   └── Items/
    │       ├── Deco_Brotherhood_WolfStatue_*.json
    │       └── Weapon/Spellbook/Weapon_Spellbook_BrotherhoodSpellbook_*.json
    ├── Languages/en-US/server.lang
    ├── Models/BrotherhoodWolf_*.json
    └── NPC/
        ├── Groups/Critters/BrotherhoodWolf_*.json
        └── Roles/
            ├── Brotherhood_WolfSummon_*.json
            └── BrotherhoodWolves/Summoned_Wolf_Ally.json
```

---

## Spellbook Item Definition

### Weapon_Spellbook_BrotherhoodSpellbook_Common.json

```json
{
  "TranslationProperties": {
    "Name": "server.items.Weapon_BrotherhoodSpellbook_Common.name",
    "Description": "server.items.Weapon_BrotherhoodSpellbook_Common.description"
  },
  "Categories": ["Items.Weapons"],
  "Quality": "Common",
  "ItemLevel": 40,
  "Model": "Items/Weapons/Spellbook/BrotherhoodOfTheWolfSpellbook.blockymodel",
  "Texture": "Items/Weapons/Spellbook/Weapon_BrotherhoodOfTheWolfSpellbook_Common.png",
  "PlayerAnimationsId": "Spellbook",
  "MaxStack": 5,
  "Tags": {
    "Type": ["Weapon"],
    "Family": ["Spellbook"]
  },
  "Weapon": {},
  "ItemSoundSetId": "ISS_Weapons_Books",

  "Interactions": {
    "Primary": {
      "Interactions": [{
        "Type": "Charging",
        "Effects": {
          "WorldSoundEventId": "SFX_Skeleton_Mage_Spellbook_Charge",
          "ItemAnimationId": "CastHurlCharging"
        },
        "AllowIndefiniteHold": true,
        "HorizontalSpeedMultiplier": 0.8,
        "CancelOnItemChange": true,
        "RunTime": 0,
        "Next": {
          "0": {
            "Type": "Simple",
            "Effects": { "ItemAnimationId": "Interact" }
          },
          "0.8": {
            "Type": "RunOnBlockTypes",
            "BlockSets": ["Brotherhood_WolfStatues_Common"],
            "MaxCount": 1,
            "Range": 5,
            "Interactions": {
              "Interactions": [{
                "Type": "Serial",
                "Interactions": [
                  {
                    "Type": "SpawnNPC",
                    "EntityId": "Brotherhood_WolfSummon_Common"
                  },
                  {
                    "Type": "DestroyBlock"
                  },
                  {
                    "Type": "ModifyInventory",
                    "AdjustHeldItemQuantity": -1
                  }
                ]
              }]
            },
            "Next": {
              "Type": "Simple",
              "Effects": { "ItemAnimationId": "CastHurlCharged" }
            },
            "Failed": {
              "Type": "Simple",
              "Effects": { "ItemAnimationId": "Interact" }
            }
          }
        },
        "Failed": {
          "Type": "Simple",
          "Effects": { "ItemAnimationId": "Interact" }
        }
      }]
    },
    "Secondary": { /* Same as Primary */ }
  },

  "Recipe": {
    "BenchRequirement": [{
      "Categories": ["Arcane_Misc"],
      "Id": "Arcanebench",
      "RequiredTierLevel": 1
    }],
    "Input": [
      { "ItemId": "Ingredient_Fibre", "Quantity": 20 },
      { "ItemId": "Ingredient_Life_Essence", "Quantity": 5 },
      { "ItemId": "Ingredient_Bar_Iron", "Quantity": 2 }
    ]
  }
}
```

---

## Key Interaction Patterns

### Charging Interaction

```json
{
  "Type": "Charging",
  "AllowIndefiniteHold": true,
  "HorizontalSpeedMultiplier": 0.8,  // Slow player while charging
  "CancelOnItemChange": true,
  "RunTime": 0,
  "Next": {
    "0": { /* Immediate release */ },
    "0.8": { /* After 0.8 seconds charge */ }
  }
}
```

### RunOnBlockTypes (Target Block Check)

```json
{
  "Type": "RunOnBlockTypes",
  "BlockSets": ["Brotherhood_WolfStatues_Common"],
  "MaxCount": 1,
  "Range": 5,
  "Interactions": { /* Execute if block found */ },
  "Next": { /* Success continuation */ },
  "Failed": { /* No valid block found */ }
}
```

### Serial Interaction (Sequential Actions)

```json
{
  "Type": "Serial",
  "Interactions": [
    { "Type": "SpawnNPC", "EntityId": "Brotherhood_WolfSummon_Common" },
    { "Type": "DestroyBlock" },
    { "Type": "ModifyInventory", "AdjustHeldItemQuantity": -1 }
  ]
}
```

---

## NPC Role Definition

### Brotherhood_WolfSummon_Common.json

```json
{
  "Type": "Variant",
  "Reference": "Summoned_Wolf_Ally",
  "Modify": {
    "MaxHealth": 74,
    "HearingRange": 8,
    "ViewSector": 270,
    "Appearance": "BrotherhoodWolf_Common",
    "FlockArray": ["Risen_Knight"],
    "Attack": "Root_NPC_Wolf_Attack",
    "AttackDistance": 3,
    "DesiredAttackDistanceRange": [2, 2.5],
    "ApplySeparation": true,
    "NameTranslationKey": "server.npcRoles.Skeleton_Knight.name"
  }
}
```

---

## Base NPC Template

### Summoned_Wolf_Ally.json (Abstract Base)

```json
{
  "Type": "Abstract",
  "StartState": "Idle",
  "Parameters": {
    "Appearance": { "Value": "Trork_Hunter" },
    "ViewRange": { "Value": 15 },
    "ViewSector": { "Value": 180 },
    "HearingRange": { "Value": 8 },
    "AbsoluteDetectionRange": { "Value": 2 },
    "AlertedRange": { "Value": 30 },
    "MaxSpeed": { "Value": 10 },
    "RunThreshold": { "Value": 0.3 },

    "Attack": { "Value": "Root_NPC_Wolf_Attack" },
    "AttackDistance": { "Value": 2 },
    "DesiredAttackDistanceRange": { "Value": [1.5, 1.5] },
    "AttackPauseRange": { "Value": [1.5, 2] },

    "CombatStrafeWeight": { "Value": 10 },
    "CombatDirectWeight": { "Value": 10 },
    "CombatBackOffAfterAttack": { "Value": true },
    "CombatBackOffDistanceRange": { "Value": [4, 4] },

    "FlockArray": { "Value": [], "TypeHint": "String" },
    "DisableDamageGroups": { "Value": ["Self", "Player"] },
    "MaxHealth": { "Value": 100 },
    "DespawnTimer": { "Value": 300 }
  },

  "DefaultNPCAttitude": "Ignore",
  "DefaultPlayerAttitude": "Ignore",
  "FlockSpawnTypes": { "Compute": "FlockArray" },
  "FlockAllowedNPC": { "Compute": "FlockArray" },

  "MotionControllerList": [{
    "Type": "Walk",
    "MaxWalkSpeed": { "Compute": "MaxSpeed" },
    "Gravity": 10,
    "MaxFallSpeed": 15,
    "MaxRotationSpeed": 360
  }],

  "Instructions": [
    {
      "Continue": true,
      "Instructions": [
        // Timer for despawn
        {
          "Sensor": { "Type": "Any", "Once": true },
          "Actions": [{
            "Type": "TimerStart",
            "Name": "DespawnTimer",
            "StartValueRange": { "Compute": "makeRange(DespawnTimer)" }
          }]
        },
        // Join player's flock
        {
          "Sensor": {
            "Type": "Self",
            "Filters": [{ "Type": "Flock", "FlockStatus": "NotMember" }]
          },
          "Instructions": [{
            "Sensor": { "Type": "Player", "Range": 20 },
            "Actions": [{ "Type": "JoinFlock", "ForceJoin": true }]
          }]
        },
        // Despawn if timer expires or no player
        {
          "Sensor": {
            "Type": "Or",
            "Sensors": [
              { "Type": "Timer", "Name": "DespawnTimer", "State": "Stopped" },
              {
                "Type": "Self",
                "Filters": [{ "Type": "Flock", "FlockPlayerStatus": "NotMember" }]
              }
            ]
          },
          "Actions": [{ "Type": "Despawn" }]
        },
        // Attack if flock leader (player) is attacked
        {
          "Sensor": { "Type": "InflictedDamage", "Target": "FlockLeader" },
          "Actions": [
            { "Type": "FlockTarget" },
            { "Type": "State", "State": "Combat" }
          ]
        }
      ]
    },
    // Idle state - follow player
    {
      "Sensor": { "Type": "State", "State": "Idle" },
      "Instructions": [{
        "Sensor": {
          "Type": "And",
          "Sensors": [
            { "Type": "FlockLeader" },
            {
              "Type": "Self",
              "Filters": [{ "Type": "Flock", "FlockStatus": "Follower" }]
            }
          ]
        },
        "HeadMotion": { "Type": "Watch" },
        "BodyMotion": {
          "Type": "Seek",
          "SlowDownDistance": 7,
          "StopDistance": 4
        }
      }]
    },
    // Combat state
    {
      "Sensor": { "Type": "State", "State": "Combat" },
      "Instructions": [
        // ... combat AI logic
      ]
    }
  ]
}
```

---

## Rarity Variants

| Variant | Stats | Texture |
|---------|-------|---------|
| Common | 74 HP | BrotherhoodWolf_CommonTexture.png |
| Rare | Higher HP | BrotherhoodWolf_RareTexture.png |
| Legendary | Highest HP | BrotherhoodWolf_LegendaryTexture.png |
| Light | Special | Brotherhood_White.png |
| Dark | Special | Brotherhood_DarkWolf.png |

---

## Wolf Animations

```
Common/NPC/Beast/BrotherhoodWolf/Animations/
├── Attacks/
│   └── Bite.blockyanim
├── Damage/
│   ├── Death.blockyanim, Death2.blockyanim, Death3.blockyanim
│   └── Hurt.blockyanim (through Hurt5.blockyanim)
├── Default/
│   ├── Idle.blockyanim
│   ├── Walk.blockyanim, Walk_Backward.blockyanim
│   ├── Run.blockyanim
│   ├── Jump.blockyanim, Jump_Far.blockyanim
│   ├── Fall.blockyanim
│   ├── Crouch.blockyanim, Crouch_Walk.blockyanim
│   ├── Spawn.blockyanim
│   ├── Sleep.blockyanim, Wake.blockyanim
│   └── Alerted.blockyanim, Laydown.blockyanim
├── Flavor/
│   ├── Greet.blockyanim, Howl.blockyanim
│   ├── Scratch.blockyanim, Sit.blockyanim
│   ├── Sniff.blockyanim, Sniff_Ground.blockyanim
│   ├── Threaten.blockyanim, Track.blockyanim
├── Fly/
│   └── Fly.blockyanim, Fly_Idle.blockyanim, Fly_Fast.blockyanim
└── Swim/
    └── Swim.blockyanim, Swim_Idle.blockyanim, Swim_Float.blockyanim
```

---

## Flock System (Companion AI)

### Key Concepts

```json
{
  "FlockArray": ["Risen_Knight"],           // NPC types in this flock
  "FlockSpawnTypes": { "Compute": "FlockArray" },
  "FlockAllowedNPC": { "Compute": "FlockArray" },
  "DisableDamageGroups": ["Self", "Player"] // Don't damage owner
}
```

### Join Player's Flock

```json
{
  "Sensor": {
    "Type": "Self",
    "Filters": [{ "Type": "Flock", "FlockStatus": "NotMember" }]
  },
  "Instructions": [{
    "Sensor": { "Type": "Player", "Range": 20 },
    "Actions": [{ "Type": "JoinFlock", "ForceJoin": true }]
  }]
}
```

### Follow Flock Leader

```json
{
  "Sensor": { "Type": "FlockLeader" },
  "HeadMotion": { "Type": "Watch" },
  "BodyMotion": {
    "Type": "Seek",
    "SlowDownDistance": 7,
    "StopDistance": 4
  }
}
```

### Defend Flock Leader

```json
{
  "Sensor": { "Type": "InflictedDamage", "Target": "FlockLeader" },
  "Actions": [
    { "Type": "FlockTarget" },
    { "Type": "State", "State": "Combat" }
  ]
}
```

---

## Block Sets (Statue Targets)

### Brotherhood_WolfStatues_Common.json

```json
{
  "Blocks": ["Deco_Brotherhood_WolfStatue_Common"]
}
```

The spellbook targets these blocks with `RunOnBlockTypes`.

---

## Built-in Interaction Types

| Type | Description |
|------|-------------|
| `Charging` | Hold to charge, release at different thresholds |
| `RunOnBlockTypes` | Target specific block types in range |
| `Serial` | Execute multiple interactions in sequence |
| `SpawnNPC` | Spawn an NPC entity by ID |
| `DestroyBlock` | Destroy the targeted block |
| `ModifyInventory` | Modify item stack size |
| `Simple` | Play effects/animations |

---

## Application to HytaleVehicles

| Pattern | Vehicle Application |
|---------|---------------------|
| Charging interaction | Vehicle ability charge |
| RunOnBlockTypes | Target vehicle spawn platforms |
| SpawnNPC → Spawn vehicle entity | Vehicle spawning |
| DestroyBlock | Consume spawn item/block |
| ModifyInventory | Consume vehicle key |
| Flock system | Vehicle following player |
| DisableDamageGroups | Vehicle doesn't hurt owner |
| DespawnTimer | Vehicle auto-despawn |
| Combat state | Vehicle defense mode |
| Follow behavior | Vehicle parking/recall |

---

## Summary

Brotherhood Spellbooks demonstrates:
- **Charging interaction** with timed thresholds
- **RunOnBlockTypes** for block targeting
- **Serial interactions** for chained actions
- **SpawnNPC** for entity creation
- **Flock system** for companion following
- **Damage immunity** to owner
- **Despawn timer** for temporary entities
- **Combat AI** that defends the owner
- **Rarity variants** with different stats/textures
- **Complete animation set** for NPC

Perfect template for summoning vehicle companions!
