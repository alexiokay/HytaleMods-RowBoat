# Aures Paintings - Deep Analysis

**Mod Name:** Aures Paintings
**Version:** 1.0.0
**Author:** BlackAures
**Architecture:** Pure Asset Pack (NO Java code!)

## Overview

Adds customizable wall paintings in multiple sizes with interactive texture cycling. Demonstrates state-based texture switching using ChangeState interaction.

---

## File Structure

```
Aures_Paintings.zip
├── manifest.json
├── Common/
│   ├── Blocks/
│   │   ├── Aures_Paintings_1x1/
│   │   │   ├── Aures_Painting_1x1.blockymodel
│   │   │   └── Variant_1.png through Variant_10.png
│   │   ├── Aures_Paintings_1x2/
│   │   ├── Aures_Paintings_2x1/
│   │   ├── Aures_Paintings_2x2/
│   │   └── Aures_Paintings_3x2/
│   └── Icons/ItemsGenerated/
│       └── Aures_Furniture_Painting_*.png
└── Server/
    ├── Item/Items/
    │   ├── Aures_Furniture_Painting_1x1.json
    │   ├── Aures_Furniture_Painting_1x2.json
    │   ├── Aures_Furniture_Painting_2x1.json
    │   ├── Aures_Furniture_Painting_2x2.json
    │   └── Aures_Furniture_Painting_3x2.json
    └── Languages/en-US/
        ├── interactionHints.lang
        └── items.lang
```

---

## Painting Item Definition

### Aures_Furniture_Painting_2x2.json

```json
{
  "TranslationProperties": {
    "Name": "items.Aures_Furniture_Painting_2x2.name",
    "Description": "items.Aures_Furniture_Painting.description"
  },
  "PlayerAnimationsId": "Block",
  "Categories": ["Furniture.Signs"],
  "Set": "Furniture_Lumberjack",
  "Icon": "Icons/ItemsGenerated/Aures_Furniture_Painting_2x2.png",
  "IconProperties": {
    "Scale": 0.37,
    "Rotation": [28, 33, 22.5],
    "Translation": [27, -24]
  },
  "Model": "Blocks/Aures_Paintings_2x2/Aures_Painting_2x2.blockymodel",
  "Texture": "Blocks/Aures_Paintings_2x2/Variant_1.png",
  "Scale": 1,

  "BlockType": {
    "DrawType": "Model",
    "Material": "Solid",
    "Opacity": "Transparent",
    "CustomModel": "Blocks/Aures_Paintings_2x2/Aures_Painting_2x2.blockymodel",
    "CustomModelTexture": [{
      "Texture": "Blocks/Aures_Paintings_2x2/Variant_1.png",
      "Weight": 1
    }],
    "CustomModelScale": 1,
    "HitboxType": "Painting_2x2",
    "VariantRotation": "NESW",

    "Support": {
      "North": [{ "FaceType": "Full" }]
    },

    "Gathering": {
      "Soft": { "IsWeaponBreakable": false }
    },

    "BlockSoundSetId": "Wood",
    "BlockParticleSetId": "Wood",
    "ParticleColor": "#793e21",

    "Interactions": {
      "Use": {
        "Interactions": [{
          "Type": "ChangeState",
          "Changes": {
            "default": "Off",
            "Off": "Painting_3",
            "Painting_3": "Painting_4",
            "Painting_4": "Painting_5",
            "Painting_5": "Painting_6",
            "Painting_6": "Painting_7",
            "Painting_7": "Painting_8",
            "Painting_8": "Painting_9",
            "Painting_9": "Painting_10",
            "Painting_10": "default"
          }
        }]
      }
    },

    "State": {
      "Definitions": {
        "On": {
          "InteractionHint": "interactionHints.aures_painting_2x2_variant_1",
          "CustomModel": "Blocks/Aures_Paintings_2x2/Aures_Painting_2x2.blockymodel",
          "CustomModelTexture": [{
            "Texture": "Blocks/Aures_Paintings_2x2/Variant_1.png",
            "Weight": 1
          }]
        },
        "Off": {
          "InteractionHint": "interactionHints.aures_painting_2x2_variant_2",
          "CustomModelTexture": [{
            "Texture": "Blocks/Aures_Paintings_2x2/Variant_2.png",
            "Weight": 1
          }]
        },
        "Painting_3": {
          "InteractionHint": "interactionHints.aures_painting_2x2_variant_3",
          "CustomModelTexture": [{
            "Texture": "Blocks/Aures_Paintings_2x2/Variant_3.png",
            "Weight": 1
          }]
        },
        "Painting_4": {
          "CustomModelTexture": [{
            "Texture": "Blocks/Aures_Paintings_2x2/Variant_4.png",
            "Weight": 1
          }]
        },
        // ... Painting_5 through Painting_10
      }
    },

    "InteractionHint": "interactionHints.aures_painting_2x2_variant_1",
    "InteractionSoundEventId": "SFX_Cloth_Hit",
    "Looping": true
  },

  "Interactions": {
    "Primary": "Block_Primary",
    "Secondary": "Block_Secondary"
  },

  "Recipe": {
    "Input": [
      { "ItemId": "Ingredient_Stick", "Quantity": 4 },
      { "ItemId": "Ingredient_Fabric_Scrap_Linen", "Quantity": 1 }
    ],
    "BenchRequirement": [{
      "Type": "Crafting",
      "Id": "Furniture_Bench",
      "Categories": ["Furniture_Misc"]
    }]
  },

  "Utility": { "Usable": true },
  "Tags": { "Type": ["Deco"] },
  "ItemSoundSetId": "ISS_Blocks_Wood"
}
```

---

## Key Patterns

### ChangeState Interaction

```json
{
  "Type": "ChangeState",
  "Changes": {
    "default": "Off",           // default → Off
    "Off": "Painting_3",        // Off → Painting_3
    "Painting_3": "Painting_4", // ... cycle continues
    "Painting_10": "default"    // loops back to start
  }
}
```

This creates a cycle: `default → Off → Painting_3 → ... → Painting_10 → default`

### State Definitions

Each state can override block properties:

```json
{
  "State": {
    "Definitions": {
      "StateName": {
        "InteractionHint": "translation.key",    // Tooltip text
        "CustomModel": "path/to/model.blockymodel",
        "CustomModelTexture": [{
          "Texture": "path/to/texture.png",
          "Weight": 1
        }]
      }
    }
  }
}
```

---

## Wall Support

```json
{
  "Support": {
    "North": [{ "FaceType": "Full" }]
  }
}
```

Requires a full block face to the north to stay attached (wall painting).

---

## Painting Sizes

| Size | Hitbox | Dimensions |
|------|--------|------------|
| 1x1 | Painting_1x1 | 1 block wide, 1 block tall |
| 1x2 | Painting_1x2 | 1 block wide, 2 blocks tall |
| 2x1 | Painting_2x1 | 2 blocks wide, 1 block tall |
| 2x2 | Painting_2x2 | 2 blocks wide, 2 blocks tall |
| 3x2 | Painting_3x2 | 3 blocks wide, 2 blocks tall |

---

## Translation Files

### items.lang

```
items.Aures_Furniture_Painting_1x1.name = Painting (1x1)
items.Aures_Furniture_Painting_2x2.name = Painting (2x2)
items.Aures_Furniture_Painting.description = An interactable painting with multiple designs.
```

### interactionHints.lang

```
interactionHints.aures_painting_2x2_variant_1 = Variant 1
interactionHints.aures_painting_2x2_variant_2 = Variant 2
// ... etc
```

---

## Application to HytaleVehicles

| Pattern | Vehicle Application |
|---------|---------------------|
| ChangeState interaction | Vehicle door open/close states |
| State-based texture swap | Damage states, paint colors |
| Multiple state definitions | On/Off, Moving/Stationary, Damaged/Normal |
| InteractionHint per state | "Press E to enter" / "Press E to exit" |
| Wall Support → Support.Down | Vehicle requires ground support |
| Custom hitbox types | Vehicle-specific collision |
| Looping state cycle | Cycling through vehicle modes |

---

## Creating Multi-State Blocks

### Minimal Template

```json
{
  "TranslationProperties": {
    "Name": "items.my_block.name"
  },
  "BlockType": {
    "DrawType": "Model",
    "CustomModel": "path/to/model.blockymodel",

    "Interactions": {
      "Use": {
        "Interactions": [{
          "Type": "ChangeState",
          "Changes": {
            "default": "State_B",
            "State_B": "default"
          }
        }]
      }
    },

    "State": {
      "Definitions": {
        "State_B": {
          "CustomModelTexture": [{
            "Texture": "path/to/alternate_texture.png",
            "Weight": 1
          }]
        }
      }
    }
  }
}
```

---

## Summary

Aures Paintings demonstrates:
- **ChangeState interaction** for cycling through variants
- **State definitions** with texture overrides
- **10 texture variants** per painting size
- **InteractionHint per state** for tooltips
- **Wall support** for hanging blocks
- **Custom hitbox types** for different sizes
- **Pure JSON** - no Java code required!

Perfect template for any block that needs multiple visual states.
