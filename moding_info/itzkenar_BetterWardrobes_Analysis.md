# BetterWardrobes - Deep Analysis

**Mod Name:** BetterWardrobes
**Version:** 1.0.2
**Author:** iTzKenar
**Architecture:** Pure Asset Pack (NO Java code!)

## Overview

Adds wardrobe furniture that functions as large storage containers (54 slots) with open/close animations. This mod demonstrates how to create **functional furniture with animations** using only JSON.

---

## File Structure

```
BetterWardrobes-1.0.2.zip
├── Common/
│   ├── UI/Custom/
│   │   └── iTzKenar_Better Wardrobes.png
│   └── Blocks/
│       ├── Decorative_Sets/
│       │   ├── Ancient/
│       │   │   ├── Wardrobe.blockymodel
│       │   │   └── Wardrobe_Texture.png
│       │   ├── Crude/
│       │   ├── Desert/
│       │   ├── Feran/
│       │   └── ... (many more)
│       └── Animations/Wardrobe/
│           ├── Wardrobe_Open.blockyanim
│           └── Wardrobe_Close.blockyanim
└── Server/
    └── Item/Items/Furniture/
        ├── Ancient/Furniture_Ancient_Wardrobe.json
        ├── Crude/Furniture_Crude_Wardrobe.json
        ├── Desert/Furniture_Desert_Wardrobe.json
        └── ... (many variants)
```

---

## Container Item Definition

### Furniture_Ancient_Wardrobe.json

```json
{
  "TranslationProperties": {
    "Name": "server.items.Furniture_Ancient_Wardrobe.name"
  },
  "Icon": "Icons/ItemsGenerated/Furniture_Ancient_Wardrobe.png",
  "IconProperties": {
    "Scale": 0.36,
    "Rotation": [22.5, 45, 22.5],
    "Translation": [14.5, -22.7]
  },
  "BlockType": {
    "CustomModel": "Blocks/Decorative_Sets/Ancient/Wardrobe.blockymodel",
    "CustomModelTexture": [{
      "Texture": "Blocks/Decorative_Sets/Ancient/Wardrobe_Texture.png",
      "Weight": 1
    }],
    "DrawType": "Model",
    "Material": "Solid",
    "HitboxType": "Wardrobe",
    "Gathering": {
      "Breaking": { "GatherType": "Woods" }
    },
    "Support": {
      "Down": [{ "FaceType": "Full" }]
    },
    "Flags": { "IsUsable": true },
    "Interactions": {
      "Primary": "Break_Container",
      "Use": "Open_Container"
    },
    "State": {
      "Id": "container",
      "Capacity": 54,
      "Definitions": {
        "CloseWindow": {
          "InteractionSoundEventId": "SFX_Chest_Wooden_Close",
          "CustomModelAnimation": "Blocks/Animations/Wardrobe/Wardrobe_Close.blockyanim"
        },
        "OpenWindow": {
          "InteractionSoundEventId": "SFX_Chest_Wooden_Open",
          "CustomModelAnimation": "Blocks/Animations/Wardrobe/Wardrobe_Open.blockyanim"
        }
      }
    },
    "BlockSoundSetId": "Wood",
    "BlockParticleSetId": "Wood",
    "VariantRotation": "NESW",
    "ParticleColor": "#3e352a"
  },
  "Scale": 0.8,
  "Recipe": {
    "Input": [{ "ResourceTypeId": "Wood_Blackwood_Deadwood", "Quantity": 8 }],
    "BenchRequirement": [{
      "Id": "Builders",
      "Type": "StructuralCrafting",
      "Categories": ["Wardrobe"]
    }]
  },
  "ResourceTypes": [
    { "Id": "Fuel" },
    { "Id": "Charcoal" }
  ],
  "Categories": ["Furniture.Containers"],
  "Tags": {
    "Type": ["Furniture"],
    "Family": ["Ancient"]
  },
  "Set": "Wardrobe",
  "PlayerAnimationsId": "Block",
  "ItemSoundSetId": "ISS_Blocks_Wood"
}
```

---

## Key Properties Explained

### Container State

```json
{
  "State": {
    "Id": "container",     // Uses built-in container behavior
    "Capacity": 54,        // Number of inventory slots
    "Definitions": {       // State-specific behaviors
      "CloseWindow": {
        "InteractionSoundEventId": "SFX_Chest_Wooden_Close",
        "CustomModelAnimation": "Blocks/Animations/Wardrobe/Wardrobe_Close.blockyanim"
      },
      "OpenWindow": {
        "InteractionSoundEventId": "SFX_Chest_Wooden_Open",
        "CustomModelAnimation": "Blocks/Animations/Wardrobe/Wardrobe_Open.blockyanim"
      }
    }
  }
}
```

### Capacity Reference

| Capacity | Equivalent |
|----------|------------|
| 9 | Small chest |
| 27 | Medium chest |
| 54 | Large chest / Wardrobe |

### State Definitions

| State | Triggers When |
|-------|--------------|
| `OpenWindow` | Player opens container UI |
| `CloseWindow` | Player closes container UI |

---

## Block Type Properties

### Visual Properties

```json
{
  "CustomModel": "Blocks/Decorative_Sets/Ancient/Wardrobe.blockymodel",
  "CustomModelTexture": [{
    "Texture": "Blocks/Decorative_Sets/Ancient/Wardrobe_Texture.png",
    "Weight": 1
  }],
  "DrawType": "Model"     // Use 3D model instead of block
}
```

### DrawType Options

| Type | Description |
|------|-------------|
| `Model` | Custom 3D model |
| `Block` | Standard block rendering |
| `Cross` | X-shaped (plants) |
| `None` | Invisible |

### Physics Properties

```json
{
  "Material": "Solid",           // Collision type
  "HitboxType": "Wardrobe",      // Custom hitbox shape
  "Support": {
    "Down": [{ "FaceType": "Full" }]  // Needs full block below
  }
}
```

### Material Types

| Material | Description |
|----------|-------------|
| `Solid` | Full collision |
| `Liquid` | Water/lava behavior |
| `Gas` | No collision |
| `PassThrough` | Entity walkthrough |

---

## Interactions

```json
{
  "Interactions": {
    "Primary": "Break_Container",    // Left-click: break and drop contents
    "Use": "Open_Container"          // Right-click: open inventory UI
  }
}
```

### Built-in Interaction Types

| Type | Description |
|------|-------------|
| `Break_Container` | Destroy and drop items |
| `Open_Container` | Open inventory UI |
| `Break` | Simple block break |
| `Use` | Generic use action |

---

## Rotation

```json
{
  "VariantRotation": "NESW"    // 4-way rotation (North/East/South/West)
}
```

### Rotation Options

| Value | Rotations |
|-------|-----------|
| `None` | No rotation |
| `NESW` | 4 directions (90° increments) |
| `NE` | 2 directions (opposite) |
| `Full` | Any rotation |

---

## Sound and Particles

```json
{
  "BlockSoundSetId": "Wood",       // Break/place sounds
  "BlockParticleSetId": "Wood",    // Break particles
  "ParticleColor": "#3e352a"       // Particle tint
}
```

### Common Sound/Particle Sets

| Set | Description |
|-----|-------------|
| `Wood` | Wooden sounds/particles |
| `Stone` | Stone sounds/particles |
| `Metal` | Metal sounds/particles |
| `Glass` | Glass sounds/particles |

---

## Recipe

```json
{
  "Recipe": {
    "Input": [{
      "ResourceTypeId": "Wood_Blackwood_Deadwood",
      "Quantity": 8
    }],
    "BenchRequirement": [{
      "Id": "Builders",
      "Type": "StructuralCrafting",
      "Categories": ["Wardrobe"]
    }]
  }
}
```

### Recipe Properties

| Property | Description |
|----------|-------------|
| `Input` | Required materials |
| `ResourceTypeId` | Material category (accepts any matching item) |
| `ItemId` | Specific item required |
| `BenchRequirement` | Crafting station needed |

---

## Categories and Tags

```json
{
  "Categories": ["Furniture.Containers"],  // Creative menu location
  "Tags": {
    "Type": ["Furniture"],
    "Family": ["Ancient"]
  },
  "Set": "Wardrobe"
}
```

Used for:
- Creative menu organization
- Search/filtering
- Crafting station compatibility

---

## Icon Properties

```json
{
  "IconProperties": {
    "Scale": 0.36,                    // Size in inventory
    "Rotation": [22.5, 45, 22.5],     // Euler angles [X, Y, Z]
    "Translation": [14.5, -22.7]      // Offset [X, Y]
  }
}
```

Controls how the item appears in inventory/hotbar.

---

## Wardrobe Variants

| Variant | Theme |
|---------|-------|
| Ancient | Temple/dungeon |
| Crude | Basic/starter |
| Desert | Desert biome |
| Feran | Feran faction |
| Frozen_Castle | Ice/snow |
| Royal_Magic | Magical |
| Human_Ruins | Ruined |
| Jungle | Jungle biome |
| Kweebec | Kweebec faction |
| Lumberjack | Forest |
| Tavern | Building |

---

## Application to HytaleVehicles

| Pattern | Vehicle Application |
|---------|-------------------|
| Container state | Vehicle cargo/trunk |
| Open/Close animations | Door animations |
| State definitions | Enter/exit states |
| Custom model | Vehicle 3D model |
| VariantRotation | Vehicle facing direction |
| Capacity | Storage space |

---

## Creating Custom Furniture

### Minimal Container Block

```json
{
  "TranslationProperties": {
    "Name": "server.items.My_Furniture.name"
  },
  "BlockType": {
    "CustomModel": "Blocks/MyFurniture/model.blockymodel",
    "DrawType": "Model",
    "Material": "Solid",
    "Interactions": {
      "Primary": "Break_Container",
      "Use": "Open_Container"
    },
    "State": {
      "Id": "container",
      "Capacity": 27
    }
  }
}
```

---

## Summary

BetterWardrobes demonstrates:
- **Container state** for inventory blocks
- **Open/Close animations** triggered by state
- **Sound integration** for interactions
- **Custom 3D models** with DrawType: Model
- **Rotation support** with VariantRotation
- **Recipe system** with bench requirements
- **Category/tag system** for organization

This is a template for any storage furniture!
