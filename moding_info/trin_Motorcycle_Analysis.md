# Trin's Motorcycle Mod Analysis

**Mod Name:** Trin's Motorcycle
**Version:** 1.2
**Author:** Trin
**Architecture:** Pure Asset Pack (NO JAVA CODE!)

## Overview

This is a critically important analysis for HytaleVehicles. **Trin's Motorcycle implements a fully functional vehicle using ONLY JSON asset files - no Java plugin code required!**

Hytale has a built-in NPC/Vehicle mounting system that can be configured entirely through JSON. This means basic vehicles can be created without any programming.

---

## Key Discovery

**Hytale's built-in vehicle system handles:**
- Player mounting/dismounting
- Movement physics (speed, acceleration, jumping)
- Animation states (idle, walk, run, jump)
- Sound events (engine sounds at different speeds)
- Model attachment and rotation

**All configured through JSON files!**

---

## File Structure

```
Trins_Motorcycle_1.2.zip
├── manifest.json
├── Common/
│   ├── Model/
│   │   └── trin_motorcycle.blockymodel     # 3D model
│   ├── Animation/
│   │   ├── idle.blockyanim                 # Idle animation
│   │   ├── driving.blockyanim              # Driving animation
│   │   └── ...
│   └── Texture/
│       └── motorcycle_texture.png
└── Server/
    ├── Entity/
    │   └── Entities/
    │       └── trin_motorcycle.json        # Entity definition
    └── Movement/
        └── trin_motorcycle_movement.json   # Physics config
```

---

## MovementConfig - The Physics Engine

The `MovementConfig` JSON defines ALL vehicle physics:

```json
{
  "Id": "trin_motorcycle_movement",

  "BaseSpeed": 15,
  "JumpForce": 12,
  "Acceleration": 0.02,
  "Deceleration": 0.05,
  "MaxSpeed": 25,

  "ForwardSprintSpeedMultiplier": 1.65,
  "BackwardSpeedMultiplier": 0.5,
  "StrafeSpeedMultiplier": 0.3,
  "AirSpeedMultiplier": 1.25,

  "TurnSpeed": 2.5,
  "TurnSpeedWhileMoving": 1.8,

  "Gravity": 30,
  "TerminalVelocity": 50,

  "GroundFriction": 0.9,
  "AirFriction": 0.98,

  "CanJump": true,
  "CanSprint": true,
  "CanSwim": false,
  "CanFly": false
}
```

### Key Physics Parameters

| Parameter | Description | Example Value |
|-----------|-------------|---------------|
| `BaseSpeed` | Normal movement speed | 15 |
| `MaxSpeed` | Maximum speed cap | 25 |
| `JumpForce` | Jump strength | 12 |
| `Acceleration` | How fast to reach max speed | 0.02 |
| `Deceleration` | How fast to slow down | 0.05 |
| `ForwardSprintSpeedMultiplier` | Sprint speed boost | 1.65x |
| `TurnSpeed` | Rotation speed when stationary | 2.5 |
| `TurnSpeedWhileMoving` | Rotation speed while moving | 1.8 |
| `Gravity` | Downward acceleration | 30 |
| `GroundFriction` | Velocity damping on ground | 0.9 |
| `AirFriction` | Velocity damping in air | 0.98 |

---

## Entity Definition

The entity JSON ties everything together:

```json
{
  "Id": "trin_motorcycle",
  "DisplayName": "ui.entity.trin_motorcycle",

  "Model": "Common/Model/trin_motorcycle.blockymodel",

  "MovementConfig": "Server/Movement/trin_motorcycle_movement.json",

  "Mountable": {
    "Enabled": true,
    "SeatCount": 1,
    "Seats": [
      {
        "Index": 0,
        "Position": [0, 0.8, 0],
        "Rotation": [0, 0, 0],
        "IsDriver": true,
        "CanControl": true
      }
    ],
    "MountInteractionRange": 3.0,
    "DismountOffset": [1.5, 0, 0]
  },

  "AnimationSets": {
    "Idle": "Common/Animation/idle.blockyanim",
    "Walk": "Common/Animation/driving.blockyanim",
    "Run": "Common/Animation/driving_fast.blockyanim",
    "Jump": "Common/Animation/jump.blockyanim"
  },

  "SoundEvents": {
    "Idle": "trin:motorcycle_idle",
    "Gas_Low": "trin:motorcycle_gas_low",
    "Gas_Mid": "trin:motorcycle_gas_mid",
    "Gas_High": "trin:motorcycle_gas_high"
  },

  "Collision": {
    "Type": "Box",
    "Size": [1.2, 1.5, 2.5],
    "Offset": [0, 0.75, 0]
  },

  "Health": {
    "MaxHealth": 100,
    "Invulnerable": false
  }
}
```

---

## Seat Configuration

The `Mountable` section defines how players sit:

```json
{
  "Mountable": {
    "Enabled": true,
    "SeatCount": 2,
    "Seats": [
      {
        "Index": 0,
        "Position": [0, 0.8, 0.3],
        "Rotation": [0, 0, 0],
        "IsDriver": true,
        "CanControl": true,
        "Animation": "sitting_driver"
      },
      {
        "Index": 1,
        "Position": [0, 0.8, -0.5],
        "Rotation": [0, 0, 0],
        "IsDriver": false,
        "CanControl": false,
        "Animation": "sitting_passenger"
      }
    ],
    "MountInteractionRange": 3.0,
    "DismountOffset": [1.5, 0, 0]
  }
}
```

### Seat Parameters

| Parameter | Description |
|-----------|-------------|
| `Position` | Offset from entity center [X, Y, Z] |
| `Rotation` | Euler rotation [Pitch, Yaw, Roll] |
| `IsDriver` | Can this seat control the vehicle? |
| `CanControl` | Has movement control |
| `Animation` | Player animation while seated |

---

## Animation Sets

Animations are linked to movement states:

```json
{
  "AnimationSets": {
    "Idle": "Common/Animation/idle.blockyanim",
    "Walk": "Common/Animation/driving.blockyanim",
    "Run": "Common/Animation/driving_fast.blockyanim",
    "Jump": "Common/Animation/jump.blockyanim",
    "Fall": "Common/Animation/falling.blockyanim",
    "Land": "Common/Animation/land.blockyanim"
  }
}
```

The game automatically transitions between animations based on velocity/state.

---

## Sound Events

Engine sounds change based on speed:

```json
{
  "SoundEvents": {
    "Idle": "trin:motorcycle_idle",
    "Gas_Low": "trin:motorcycle_gas_low",
    "Gas_Mid": "trin:motorcycle_gas_mid",
    "Gas_High": "trin:motorcycle_gas_high",
    "Start": "trin:motorcycle_start",
    "Stop": "trin:motorcycle_stop",
    "Horn": "trin:motorcycle_horn"
  }
}
```

---

## Model File Structure

The `.blockymodel` file defines the 3D model:

```json
{
  "Format": "BlockyModel",
  "Version": 1,

  "Nodes": [
    {
      "Name": "root",
      "Children": ["body", "front_wheel", "back_wheel", "handlebars"]
    },
    {
      "Name": "body",
      "Mesh": "body_mesh",
      "Position": [0, 0, 0]
    },
    {
      "Name": "front_wheel",
      "Mesh": "wheel_mesh",
      "Position": [0, 0.3, 0.8],
      "Pivot": [0, 0.3, 0.8]
    },
    {
      "Name": "back_wheel",
      "Mesh": "wheel_mesh",
      "Position": [0, 0.3, -0.6],
      "Pivot": [0, 0.3, -0.6]
    },
    {
      "Name": "handlebars",
      "Mesh": "handlebar_mesh",
      "Position": [0, 0.9, 0.5],
      "Pivot": [0, 0.9, 0.5]
    }
  ],

  "Meshes": {
    "body_mesh": { /* mesh data */ },
    "wheel_mesh": { /* mesh data */ },
    "handlebar_mesh": { /* mesh data */ }
  },

  "Materials": {
    "motorcycle_material": {
      "Texture": "Common/Texture/motorcycle_texture.png"
    }
  }
}
```

---

## Animation File Structure

The `.blockyanim` file uses quaternion rotations:

```json
{
  "Format": "BlockyAnimation",
  "Version": 1,
  "Duration": 1.0,
  "Loop": true,

  "Channels": [
    {
      "Node": "front_wheel",
      "Property": "Rotation",
      "Keyframes": [
        { "Time": 0.0, "Value": [0, 0, 0, 1] },
        { "Time": 0.5, "Value": [0.707, 0, 0, 0.707] },
        { "Time": 1.0, "Value": [1, 0, 0, 0] }
      ],
      "Interpolation": "Linear"
    },
    {
      "Node": "back_wheel",
      "Property": "Rotation",
      "Keyframes": [
        { "Time": 0.0, "Value": [0, 0, 0, 1] },
        { "Time": 0.5, "Value": [0.707, 0, 0, 0.707] },
        { "Time": 1.0, "Value": [1, 0, 0, 0] }
      ],
      "Interpolation": "Linear"
    }
  ]
}
```

### Quaternion Values

Rotation values are quaternions [X, Y, Z, W]:
- `[0, 0, 0, 1]` = No rotation (identity)
- `[0.707, 0, 0, 0.707]` = 90 degrees around X axis
- `[1, 0, 0, 0]` = 180 degrees around X axis

---

## Spawning the Vehicle

To spawn the motorcycle, use the standard entity spawn command:

```
/spawn trin:trin_motorcycle
```

Or create a spawn item:

```json
{
  "Id": "trin_motorcycle_spawn",
  "DisplayName": "ui.item.motorcycle_spawn",
  "Icon": "Icons/motorcycle_icon.png",
  "MaxStack": 1,

  "Interactions": {
    "Secondary": {
      "Interactions": [{
        "Type": "SpawnEntity",
        "EntityId": "trin:trin_motorcycle",
        "SpawnOffset": [0, 0, 2]
      }]
    }
  }
}
```

---

## Implications for HytaleVehicles

### What We Can Do With Pure Assets

1. **Basic land vehicles** - Cars, motorcycles, carts
2. **Multi-seat vehicles** - Driver + passengers
3. **Custom physics** - Speed, acceleration, turning
4. **Animations** - Wheel spin, suspension bounce
5. **Sounds** - Engine states, horns

### What Still Requires Java Code

1. **Boat buoyancy** - Water level detection, floating physics
2. **Custom input handling** - Special controls beyond WASD
3. **Damage systems** - Vehicle taking/dealing damage
4. **Fuel systems** - Consumable resources
5. **Complex physics** - Drifting, realistic suspension

---

## Hybrid Approach for HytaleVehicles

**Recommended strategy:**

1. Use Hytale's built-in `Mountable` system for:
   - Player mounting/dismounting
   - Basic movement controls
   - Seat positions
   - Standard animations

2. Use Java plugin code for:
   - Water vehicles (buoyancy)
   - Custom vehicle types (planes, helicopters)
   - Vehicle registry and spawning
   - Persistence and ownership

---

## Quick Reference

### Minimal Vehicle Entity JSON

```json
{
  "Id": "my_vehicle",
  "Model": "Common/Model/my_vehicle.blockymodel",
  "MovementConfig": "Server/Movement/my_vehicle_movement.json",
  "Mountable": {
    "Enabled": true,
    "SeatCount": 1,
    "Seats": [{
      "Index": 0,
      "Position": [0, 1, 0],
      "IsDriver": true,
      "CanControl": true
    }]
  }
}
```

### Minimal MovementConfig JSON

```json
{
  "Id": "my_vehicle_movement",
  "BaseSpeed": 10,
  "MaxSpeed": 20,
  "Acceleration": 0.05,
  "TurnSpeed": 2.0,
  "CanJump": false
}
```

---

## Summary

**Key Takeaways:**

1. **Hytale has a built-in vehicle/mount system** - No Java needed for basic vehicles!
2. **MovementConfig controls all physics** - Speed, acceleration, turning, jumping
3. **Mountable section defines seats** - Position, rotation, driver/passenger
4. **AnimationSets link to movement states** - Automatic transitions
5. **SoundEvents for audio feedback** - Engine sounds at different speeds

**For HytaleVehicles:**
- Consider using the built-in system for land vehicles
- Reserve Java code for water vehicles (buoyancy) and special features
- This could dramatically simplify the plugin!
