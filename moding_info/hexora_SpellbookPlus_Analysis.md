# Spellbook Plus - Deep Analysis

**Mod Name:** Spellbook Plus
**Version:** 1.0.4
**Author:** Hexora
**Architecture:** Java Plugin with Asset Pack

## Overview

A comprehensive node-based spell programming system for Hytale. Features mana management, spell hotbar, projectile system, custom UI (spell wheel), and multiple ECS components/systems.

---

## File Structure

```
Spellbook-Plus-1.0.4.jar
├── manifest.json
├── io/hexora/spellbook/
│   ├── SpellbookPlugin.java           # Main plugin
│   ├── SpellCastHandler.java          # Spell execution
│   ├── CastInput.java / CastResult.java
│   ├── api/
│   │   ├── ICooldownManager.java
│   │   ├── IManaManager.java
│   │   ├── ISpellExecutor.java
│   │   ├── ISpellStorage.java
│   │   └── ISpellValidator.java
│   ├── components/
│   │   ├── ManaComponent.java         # Player mana
│   │   ├── CooldownComponent.java     # Spell cooldowns
│   │   ├── SpellHotbarComponent.java  # Equipped spells
│   │   ├── SpellLifetimeComponent.java
│   │   └── ProjectileDataComponent.java
│   ├── interactions/
│   │   ├── SpellCastInteraction.java  # Staff casting
│   │   ├── SpellScrollInteraction.java
│   │   ├── SpellWheelInteraction.java
│   │   └── SpellProjectileHitInteraction.java
│   ├── systems/
│   │   ├── ManaHudUpdateSystem.java
│   │   ├── SpellLifetimeSystem.java
│   │   ├── CooldownCleanupSystem.java
│   │   ├── EffectBatchSystem.java
│   │   └── SpellProjectileDamageSystem.java
│   ├── nodes/handlers/               # Node-based spell system
│   │   ├── OnCastNodeHandler.java
│   │   ├── OnHitNodeHandler.java
│   │   ├── DamageNodeHandler.java
│   │   ├── HealNodeHandler.java
│   │   ├── SpawnProjectileNodeHandler.java
│   │   ├── TeleportNodeHandler.java
│   │   └── ...
│   ├── entities/
│   │   └── SpellEntitySpawner.java    # Projectile spawning
│   └── config/, registry/, schema/, ...
└── Common/UI/Custom/
    ├── SpellWheelPage.ui              # Spell selection wheel
    ├── SpellManaHud.ui                # Mana bar HUD
    ├── SpellSlotButton.ui
    └── *.png                          # UI textures
```

---

## Plugin Initialization

### SpellbookPlugin.java

```java
public class SpellbookPlugin extends JavaPlugin {
    private ComponentType<EntityStore, ManaComponent> manaComponentType;
    private ComponentType<EntityStore, CooldownComponent> cooldownComponentType;
    private ComponentType<EntityStore, SpellLifetimeComponent> spellLifetimeComponentType;
    private ComponentType<EntityStore, ProjectileDataComponent> projectileDataComponentType;
    private ComponentType<EntityStore, SpellHotbarComponent> spellHotbarComponentType;

    protected void setup() {
        loadConfig();
        I18n.init();
        initializeRegistries();      // Node & trigger registries
        initializeManagers();        // Cooldown, mana, hotbar managers
        registerInteractions();      // Custom interaction types
        registerComponents();        // ECS components
        registerSystems();           // Ticking systems
        registerEventListeners();    // Player events
        registerCommands();          // /spellbook command
        registerScrollItems();       // Dynamic scroll items
    }
}
```

---

## Custom ECS Components

### ManaComponent

```java
public class ManaComponent implements Component<EntityStore> {
    public static final BuilderCodec<ManaComponent> CODEC = BuilderCodec.builder(
        ManaComponent.class, ManaComponent::new
    )
    .append(new KeyedCodec("CurrentMana", Codec.INTEGER),
        (c, v) -> c.currentMana = v, c -> c.currentMana).add()
    .append(new KeyedCodec("MaxMana", Codec.INTEGER),
        (c, v) -> c.maxMana = v, c -> c.maxMana).add()
    .build();

    private int currentMana = 100;
    private int maxMana = 100;

    public boolean consume(int amount) {
        if (currentMana < amount) return false;
        currentMana -= amount;
        return true;
    }

    public void addMana(int amount) {
        setCurrentMana(currentMana + amount);
    }

    public float getManaPercentage() {
        return maxMana <= 0 ? 0f : (float)currentMana / (float)maxMana;
    }
}
```

### Component Registration

```java
// Register 5 custom components
manaComponentType = getEntityStoreRegistry().registerComponent(
    ManaComponent.class, ManaComponent::new);

cooldownComponentType = getEntityStoreRegistry().registerComponent(
    CooldownComponent.class, CooldownComponent::new);

spellHotbarComponentType = getEntityStoreRegistry().registerComponent(
    SpellHotbarComponent.class, "SpellHotbar", SpellHotbarComponent.CODEC);
```

---

## Custom Ticking Systems

### System Registration

```java
getEntityStoreRegistry().registerSystem((ISystem)new SpellLifetimeSystem());
getEntityStoreRegistry().registerSystem((ISystem)new EffectBatchSystem(effectBatcher));
getEntityStoreRegistry().registerSystem((ISystem)new CooldownCleanupSystem(cooldownManager));
getEntityStoreRegistry().registerSystem((ISystem)new ManaHudUpdateSystem());
getEntityStoreRegistry().registerSystem((ISystem)new SpellProjectileDamageSystem());
```

---

## Custom Interactions

### SpellCastInteraction

```java
public class SpellCastInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<SpellCastInteraction> CODEC = BuilderCodec.builder(
        SpellCastInteraction.class, SpellCastInteraction::new,
        SimpleInstantInteraction.CODEC
    ).build();

    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        // Get player and spell from hotbar
        PlayerRef playerRef = commandBuffer.getComponent(entityRef, PlayerRef.getComponentType());
        SpellDefinition spell = hotbarManager.getEquippedSpell(playerId, spellSlot);

        // Check mana
        EntityStatMap statMap = commandBuffer.getComponent(entityRef, EntityStatMap.getComponentType());
        int manaIndex = EntityStatType.getAssetMap().getIndex(ManaUtil.getManaStatName());
        EntityStatValue manaValue = statMap.get(manaIndex);

        if (manaValue.get() < manaCost) {
            NotificationHelper.notEnoughMana(playerId, manaCost, manaValue.get());
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Launch projectile or execute spell graph
        if (projectileId != null) {
            launchProjectile(commandBuffer, entityRef, playerId, projectileId, quality, damage);
            statMap.subtractStatValue(manaIndex, manaCost);
        } else {
            CastResult result = castHandler.cast(playerId, spell, input);
        }
    }
}
```

### Interaction Registration

```java
getCodecRegistry(Interaction.CODEC).register(
    "SpellCastInteraction", SpellCastInteraction.class, SpellCastInteraction.CODEC);
getCodecRegistry(Interaction.CODEC).register(
    "SpellScrollInteraction", SpellScrollInteraction.class, SpellScrollInteraction.CODEC);
getCodecRegistry(Interaction.CODEC).register(
    "SpellWheelInteraction", SpellWheelInteraction.class, SpellWheelInteraction.CODEC);
```

---

## Projectile Spawning

### SpellEntitySpawner

```java
public class SpellEntitySpawner {
    public UUID spawnProjectile(CommandBuffer<EntityStore> commandBuffer, UUID ownerId,
                                String spellId, String projectileType, Vector3d position,
                                Vector3d direction, int damage, float speed) {
        TimeResource timeResource = commandBuffer.getResource(TimeResource.getResourceType());
        String projectileAssetName = resolveProjectileAsset(projectileType);

        // Create projectile holder
        Holder holder = ProjectileComponent.assembleDefaultProjectile(
            timeResource, projectileAssetName, hytalePosition, rotation);

        // Initialize and shoot
        ProjectileComponent projectileComponent = holder.getComponent(
            ProjectileComponent.getComponentType());
        projectileComponent.initialize();
        projectileComponent.shoot(holder, ownerId, position.x, position.y, position.z, yaw, pitch);

        // Add custom spell data
        ProjectileDataComponent spellData = new ProjectileDataComponent();
        spellData.setOwnerId(ownerId);
        spellData.setDamage(damage);
        holder.putComponent(plugin.getProjectileDataComponentType(), spellData);

        // Add lifetime tracking
        SpellLifetimeComponent lifetime = new SpellLifetimeComponent();
        lifetime.setRemainingTicks(200);
        holder.putComponent(plugin.getSpellLifetimeComponentType(), lifetime);

        // Spawn entity
        commandBuffer.addEntity(holder, AddReason.SPAWN);
        return holder.getComponent(UUIDComponent.getComponentType()).getUuid();
    }

    private String resolveProjectileAsset(String projectileType) {
        return switch (projectileType) {
            case "fireball" -> "Spellbook_Fireball";
            case "frost_bolt", "icebolt" -> "Spellbook_Frost_Bolt";
            case "arcane_bolt" -> "Spellbook_Arcane_Bolt";
            default -> projectileType;
        };
    }
}
```

---

## UI System

### SpellWheelPage.ui

```
Group #SpellWheelContainer {
  Background: #000000(0.5);

  Group #WheelCenter {
    Anchor: (Width: 512, Height: 512);

    Group #WheelBackground {
      Anchor: (Left: 0, Top: 0, Width: 512, Height: 512);
      Background: "wheel.png";
    }

    Button #Slot0 {
      Anchor: (Left: 202, Top: 48, Width: 104, Height: 98);
      Style: (
        Default: (Background: "slot.png"),
        Hovered: (Background: "slot-hover-effect.png"),
        Pressed: (Background: "slot-active-effect.png")
      );
      Label #SpellName {
        Anchor: (Left: 19, Top: 39, Width: 70, Height: 20);
        Text: "";
        Style: (Alignment: Center, FontSize: 10, TextColor: #a0c4d8);
      }
    }
    // ... Slots 1-7 around the wheel

    Group #InfoPanel {
      Anchor: (Left: 176, Top: 176, Width: 160, Height: 160);
      Label #InfoName { Text: ""; Style: (FontSize: 12, TextColor: #64d2ff); }
      Label #InfoMana { Text: ""; Style: (TextColor: #4da6ff); }
      Label #InfoCooldown { Text: ""; Style: (TextColor: #ffd700); }
      Label #InfoDamage { Text: ""; Style: (TextColor: #ff6b6b); }

      Button #WriteToScrollBtn {
        Anchor: (Left: 20, Top: 124, Width: 120, Height: 28);
        Style: (
          Default: (Background: #3a5a7c),
          Hovered: (Background: #4a7a9c)
        );
      }
    }
  }
}
```

### SpellManaHud.ui

```
Group #SpellManaContainer {
  Visible: false;
  LayoutMode: Bottom;
  Anchor: (Left: 20, Bottom: 40, Width: 358, Height: 34);

  Group {
    Anchor: (Left: 0, Width: 310, Height: 26);
    Background: "ManaBackground.png";

    ProgressBar #SpellManaBarFill {
      Anchor: (Width: 302, Height: 20);
      BarTexturePath: "ManaBarFill.png";
      Value: 1.0;
    }
  }

  Label #SpellManaText {
    Anchor: (Left: 40, Width: 260, Height: 26);
    Text: "100 / 100";
    Style: (Alignment: Center, FontSize: 14, TextColor: #ffffff);
  }
}
```

---

## API Interfaces

### IManaManager

```java
public interface IManaManager {
    boolean hasMana(UUID playerId, int amount);
    boolean consumeMana(UUID playerId, int amount);
    void addMana(UUID playerId, int amount);
    void setMana(UUID playerId, int amount);
    void setMaxMana(UUID playerId, int amount);
    int getCurrentMana(UUID playerId);
    int getMaxMana(UUID playerId);
    int calculateManaCost(String spellId);
    void removePlayer(UUID playerId);
}
```

---

## Node-Based Spell System

### Node Handlers

```java
// Register spell node handlers
nodeRegistry.register(new OnCastNodeHandler());
nodeRegistry.register(new OnHitNodeHandler());
nodeRegistry.register(new OnExpireNodeHandler());
nodeRegistry.register(new DamageNodeHandler());
nodeRegistry.register(new HealNodeHandler());
nodeRegistry.register(new SpawnProjectileNodeHandler());
nodeRegistry.register(new ApplyEffectNodeHandler());
nodeRegistry.register(new PushNodeHandler());
nodeRegistry.register(new ConditionalNodeHandler());
nodeRegistry.register(new ShieldNodeHandler());
nodeRegistry.register(new TeleportNodeHandler());
```

### Trigger Types

```java
// Register spell triggers
triggerRegistry.register(new OnCastTrigger());
triggerRegistry.register(new OnHitTrigger());
triggerRegistry.register(new OnExpireTrigger());
```

---

## Service Registry Pattern

```java
// Register services for dependency injection
ServiceRegistry.register(SpellbookConfig.class, config);
ServiceRegistry.register(CooldownManager.class, cooldownManager);
ServiceRegistry.register(ManaManager.class, manaManager);
ServiceRegistry.register(SpellHotbarManager.class, hotbarManager);
ServiceRegistry.register(SpellCastHandler.class, castHandler);
ServiceRegistry.register(SpellEntitySpawner.class, entitySpawner);
ServiceRegistry.register(NodeRegistry.class, nodeRegistry);
```

---

## Event Listeners

```java
// Mouse button events for spell casting
HytaleServer.get().getEventBus().register(
    PlayerMouseButtonEvent.class, new SpellbookEventListener());

// Player join/leave
getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, new PlayerJoinListener());
getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, new PlayerLeaveListener());

// Custom packet handler for language sync
ServerManager.get().registerSubPacketHandlers(SpellbookLanguagePacketHandler::new);
```

---

## Item Metadata for Staff Quality

```java
private StaffQuality getStaffQuality(ItemStack item) {
    BsonDocument metadata = item.getMetadata();
    if (metadata != null) {
        BsonValue qualityValue = metadata.get("spellbook:quality");
        if (qualityValue instanceof BsonString) {
            return StaffQuality.fromString(((BsonString)qualityValue).getValue());
        }
    }
    return getQualityFromItemId(item.getItemId());
}

private int getStaffSpellSlot(ItemStack item) {
    BsonDocument metadata = item.getMetadata();
    BsonValue slotValue = metadata.get("spellbook:slot");
    if (slotValue != null && slotValue.isInt32()) {
        return slotValue.asInt32().getValue();
    }
    return 0;
}
```

---

## Key Imports

```java
// ECS Components
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.ISystem;

// Interactions
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;

// Entity Stats (for mana)
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;

// Projectiles
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;

// UI
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

// Events
import com.hypixel.hytale.server.core.event.events.player.*;
```

---

## Application to HytaleVehicles

| Pattern | Vehicle Application |
|---------|---------------------|
| ManaComponent | Fuel component for vehicles |
| SpellHotbarComponent | Vehicle ability hotbar |
| ProjectileComponent.assembleDefaultProjectile() | Spawning vehicle weapons |
| EntityStatMap integration | Vehicle stats (speed, armor) |
| Custom ticking systems | Physics, fuel consumption |
| Service registry | Vehicle service locator |
| UI wheel | Vehicle/weapon selection |
| ProgressBar HUD | Fuel/health display |
| Item metadata | Vehicle customization data |

---

## Summary

Spellbook Plus demonstrates:
- **5 custom ECS components** with codec serialization
- **5 ticking systems** for game logic
- **4 custom interaction types** for different spell methods
- **Node-based spell programming** with extensible handlers
- **Projectile system** with damage and AoE
- **Custom UI** with spell wheel and mana HUD
- **Service registry** for dependency management
- **Item metadata** for staff quality/slot data
- **EntityStatMap integration** for native mana stats
