# MMO Skill Tree - Deep Analysis

**Mod Name:** MMOSkillTree
**Version:** 0.8.8
**Author:** ZiggFreed
**Architecture:** Java Plugin with Asset Pack

## Overview

A comprehensive MMO-style skill progression system inspired by mcMMO. Features skill XP tracking, leveling, skill trees with unlockable nodes, quests, XP boosts, leaderboards, and extensive configuration.

---

## File Structure

```
MMOSkillTree-0.8.8.jar
├── manifest.json
├── com/ziggfreed/mmoskilltree/
│   ├── MMOSkillTreePlugin.java        # Main plugin
│   ├── api/
│   │   └── MMOSkillTreeAPI.java       # Public API
│   ├── command/
│   │   ├── GetXpCommand.java
│   │   ├── SetXpCommand.java
│   │   ├── BulkSetXpCommand.java
│   │   ├── SkillTreeCommand.java
│   │   ├── BoostCommand.java
│   │   ├── QuestCommand.java
│   │   └── ...
│   ├── config/
│   │   ├── SkillConfig.java
│   │   ├── SkillTreeConfig.java
│   │   ├── QuestConfig.java
│   │   ├── XpMapsConfig.java
│   │   ├── MobKillXpConfig.java
│   │   ├── LuckConfig.java
│   │   └── CommandRewardsConfig.java
│   ├── data/
│   │   ├── SkillComponent.java        # Player skill data
│   │   └── SkillType.java             # Skill enum
│   ├── event/
│   │   ├── BreakBlockEventSystem.java
│   │   ├── PlaceBlockEventSystem.java
│   │   ├── CombatDamageEventSystem.java
│   │   ├── CombatXpEventSystem.java
│   │   ├── MobKillEventSystem.java
│   │   ├── CraftRecipeEventSystem.java
│   │   └── PickupItemEventSystem.java
│   ├── quest/
│   │   └── QuestComponent.java
│   ├── service/
│   │   ├── SkillService.java
│   │   ├── SkillTreeService.java
│   │   ├── XpBoostService.java
│   │   ├── LeaderboardDataStore.java
│   │   └── QuestRewardExecutor.java
│   ├── reward/
│   │   └── RewardEffectRegistry.java
│   └── i18n/
│       ├── LocalizationConfig.java
│       └── Messages.java
└── Common/UI/Custom/                   # Admin config UI
```

---

## Manifest with Permissions

```json
{
  "Group": "Ziggfreed",
  "Name": "MMOSkillTree",
  "Version": "0.8.8",
  "Description": "A Hytale version of mcMMO",
  "Main": "com.ziggfreed.mmoskilltree.MMOSkillTreePlugin",
  "IncludesAssetPack": true,
  "Permissions": [
    { "node": "mmoskilltree.skill.*", "default": "true" },
    { "node": "mmoskilltree.skill.mining", "default": "true" },
    { "node": "mmoskilltree.skill.woodcutting", "default": "true" },
    { "node": "mmoskilltree.command.xp", "default": "true" },
    { "node": "mmoskilltree.command.setxp", "default": "op" },
    { "node": "mmoskilltree.admin", "default": "false" }
    // ... more permissions
  ]
}
```

---

## Plugin Initialization

### MMOSkillTreePlugin.java

```java
public class MMOSkillTreePlugin extends JavaPlugin {
    private static MMOSkillTreePlugin instance;
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    protected void setup() {
        Path configDir = Paths.get("mods", "mmoskilltree");

        // Load all configs from mods/mmoskilltree/
        SkillConfig.getInstance().load();
        XpMapsConfig.getInstance().load();
        LuckConfig.getInstance().load();
        MobKillXpConfig.getInstance().load();
        SkillTreeConfig.getInstance().load();
        CommandRewardsConfig.getInstance().load();
        QuestConfig.getInstance().load();  // loads from quests/ subfolder
        XpBoostService.getInstance().load();
        LeaderboardDataStore.getInstance().load();

        // Register components with persistence
        SkillComponent.TYPE = getEntityStoreRegistry().registerComponent(
            SkillComponent.class, "mmoskilltree:skills", SkillComponent.CODEC);

        QuestComponent.TYPE = getEntityStoreRegistry().registerComponent(
            QuestComponent.class, "mmoskilltree:quests", QuestComponent.CODEC);

        // Register custom interaction
        getCodecRegistry(Interaction.CODEC).register(
            "mmo_xp_token_consume", XpTokenConsumeInteraction.class,
            XpTokenConsumeInteraction.CODEC);

        // Register commands
        registerCommands();

        // Register event systems (ticking systems that process events)
        getEntityStoreRegistry().registerSystem(new BreakBlockEventSystem());
        getEntityStoreRegistry().registerSystem(new CombatDamageEventSystem());
        getEntityStoreRegistry().registerSystem(new CombatXpEventSystem());
        getEntityStoreRegistry().registerSystem(new CraftRecipeEventSystem());
        getEntityStoreRegistry().registerSystem(new MobKillEventSystem());
        getEntityStoreRegistry().registerSystem(new PickupItemEventSystem());
        getEntityStoreRegistry().registerSystem(new PlaceBlockEventSystem());

        // Register player events
        getEventRegistry().register(PlayerConnectEvent.class, this::onPlayerConnect);
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // Shutdown hook for saving data
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LeaderboardDataStore.getInstance().save();
            XpBoostService.getInstance().save();
            PendingQuestRewardStore.getInstance().save();
        }, "MMOSkillTree-Shutdown"));
    }
}
```

---

## Persistent ECS Components

### SkillComponent

```java
public class SkillComponent implements Component<EntityStore> {
    public static ComponentType<EntityStore, SkillComponent> TYPE;

    public static final BuilderCodec<SkillComponent> CODEC = BuilderCodec.builder(
        SkillComponent.class, SkillComponent::new
    )
    // ... codec fields for XP, levels, etc.
    .build();

    public Map<SkillType, Long> xpMap = new HashMap<>();
    private String language;

    public long getXp(SkillType skill) {
        return xpMap.getOrDefault(skill, 0L);
    }

    public int getLevel(SkillType skill) {
        return calculateLevel(getXp(skill));
    }

    public void add(SkillType skill, long amount) {
        xpMap.merge(skill, amount, Long::sum);
    }

    public static int calculateLevel(long xp) {
        // Level calculation formula
    }

    public static long getXpForLevel(int level) {
        // XP requirement calculation
    }
}
```

---

## Public API

### MMOSkillTreeAPI.java

```java
public class MMOSkillTreeAPI {
    public static SkillComponent getSkillComponent(Store<EntityStore> store, Ref<EntityStore> ref) {
        return store.getComponent(ref, SkillComponent.TYPE);
    }

    public static SkillComponent getOrCreateSkillComponent(
            Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> ref) {
        SkillComponent skills = getSkillComponent(store, ref);
        if (skills == null) {
            skills = new SkillComponent();
            commandBuffer.addComponent(ref, SkillComponent.TYPE, skills);
        }
        return skills;
    }

    public static long getXp(Store<EntityStore> store, Ref<EntityStore> ref, SkillType skill) {
        SkillComponent skills = getSkillComponent(store, ref);
        return skills != null ? skills.getXp(skill) : 0L;
    }

    public static int getLevel(Store<EntityStore> store, Ref<EntityStore> ref, SkillType skill) {
        SkillComponent skills = getSkillComponent(store, ref);
        return skills != null ? skills.getLevel(skill) : 1;
    }

    public static boolean addXp(Store<EntityStore> store, Ref<EntityStore> ref,
                                SkillType skill, long amount) {
        SkillComponent skills = getSkillComponent(store, ref);
        if (skills == null) return false;
        skills.add(skill, amount);
        return true;
    }

    public static double getLevelProgress(Store<EntityStore> store, Ref<EntityStore> ref,
                                          SkillType skill) {
        // Returns 0.0-1.0 progress to next level
    }

    public static SkillType[] getSkillTypes() {
        return SkillType.values();
    }
}
```

---

## Event Systems (XP Awarding)

### BreakBlockEventSystem

```java
public class BreakBlockEventSystem implements ISystem<EntityStore> {
    // Listens for block break events and awards Mining/Woodcutting/Excavation XP
    // Based on block type → XP mapping in config
}
```

### CombatXpEventSystem

```java
public class CombatXpEventSystem implements ISystem<EntityStore> {
    // Awards combat XP based on damage dealt
    // Different skills for different weapon types:
    // - Swords, Daggers, Polearms, Staves, Axes, Blunt, Archery, Unarmed
}
```

### MobKillEventSystem

```java
public class MobKillEventSystem implements ISystem<EntityStore> {
    // Awards XP when player kills mobs
    // XP amount based on mob type from config
}
```

---

## Player Event Handling

### On Player Connect

```java
getEventRegistry().register(PlayerConnectEvent.class, event -> {
    PlayerRef playerRef = event.getPlayerRef();
    Holder holder = event.getHolder();

    // Ensure player has skill component
    SkillComponent skills = holder.ensureAndGetComponent(SkillComponent.TYPE);
    holder.ensureAndGetComponent(QuestComponent.TYPE);

    // Set default language
    if (skills.getLanguage() == null) {
        skills.setLanguage(SkillConfig.getInstance().getDefaultLanguage());
    }

    // Deliver pending boost tokens
    int tokenCount = XpBoostService.getInstance().deliverPendingTokens(skills, username);
    if (tokenCount > 0) {
        NotificationUtil.sendNotification(playerRef.getPacketHandler(),
            Message.raw(Messages.get(skills, "notify.tokens_delivered", tokenCount))
                .color(Color.YELLOW));
    }

    // Deliver pending quest rewards
    int questRewardCount = QuestRewardExecutor.deliverPendingRewards(playerRef);
});
```

### On Player Ready

```java
getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
    Player player = event.getPlayer();
    World world = player.getWorld();

    world.execute(() -> {
        // Reapply stat modifiers from skill tree unlocks
        int reapplied = SkillTreeService.reapplyStatRewards(store, ref, playerRef, skills);

        // Validate skill data
        SkillService.validateOnReady(store, ref, playerRef, skills);
    });
});
```

### On Player Disconnect

```java
getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
    world.execute(() -> {
        // Remove stat modifiers when player leaves
        SkillTreeService.removeAllStatModifiers(store, ref, skills);
    });
});
```

---

## Skill Types

```java
public enum SkillType {
    // Gathering
    MINING,
    WOODCUTTING,
    EXCAVATION,
    HARVESTING,

    // Combat
    SWORDS,
    DAGGERS,
    POLEARMS,
    STAVES,
    AXES,
    BLUNT,
    ARCHERY,
    UNARMED,

    // Other
    DEFENSE,
    ACROBATICS,
    CRAFTING,
    BUILDING
}
```

---

## Configuration Files

### skill-config.json
```json
{
  "defaultLanguage": "en-US",
  "maxLevel": 100,
  "xpMultiplier": 1.0,
  "enabledSkills": ["MINING", "WOODCUTTING", "SWORDS", ...]
}
```

### xp-maps.json
```json
{
  "blocks": {
    "Cobblestone": { "skill": "MINING", "xp": 1 },
    "Iron_Ore": { "skill": "MINING", "xp": 5 },
    "Oak_Log": { "skill": "WOODCUTTING", "xp": 2 }
  }
}
```

### skill-tree.json
```json
{
  "nodes": [
    {
      "id": "mining_efficiency_1",
      "skill": "MINING",
      "levelRequired": 10,
      "parents": [],
      "rewards": [
        { "type": "stat", "stat": "MiningSpeed", "value": 0.1 }
      ]
    }
  ]
}
```

### quests/ folder
```json
{
  "id": "mine_100_iron",
  "name": "Iron Miner",
  "objectives": [
    { "type": "BREAK_BLOCK", "block": "Iron_Ore", "count": 100 }
  ],
  "rewards": [
    { "type": "XP", "skill": "MINING", "amount": 500 }
  ]
}
```

---

## XP Boost System

```java
public class XpBoostService {
    private Map<UUID, List<ActiveBoost>> playerBoosts;
    private Map<String, BoostPermission> permissions;

    public int deliverPendingTokens(SkillComponent skills, String username) {
        // Check for offline-purchased tokens and add them
    }

    public float getBoostMultiplier(UUID playerId, SkillType skill) {
        // Calculate total boost multiplier from active boosts
    }

    public void activateBoost(UUID playerId, BoostToken token) {
        // Activate a boost token
    }
}
```

---

## Stat Modifier System

```java
public class SkillTreeService {
    public static int reapplyStatRewards(Store<EntityStore> store, Ref<EntityStore> ref,
                                         PlayerRef playerRef, SkillComponent skills) {
        // Iterate through unlocked skill tree nodes
        // Apply stat modifiers for each unlocked reward
    }

    public static void removeAllStatModifiers(Store<EntityStore> store, Ref<EntityStore> ref,
                                               SkillComponent skills) {
        // Remove all stat modifiers when player disconnects
        // Prevents stats from persisting incorrectly
    }
}
```

---

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/mmoxp` | `mmoskilltree.command.xp` | View your XP |
| `/setmmoxp` | `mmoskilltree.command.setxp` | Set player XP |
| `/skilltree` | `mmoskilltree.command.skilltree` | Open skill tree UI |
| `/xpdisplay` | `mmoskilltree.command.xpdisplay` | Toggle XP display |
| `/mmoboost` | varies | Manage XP boosts |
| `/mmoquest` | varies | View quests |
| `/mmoconfig` | `mmoskilltree.admin` | Admin config |

---

## Key Imports

```java
// Components
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.ISystem;

// Events
import com.hypixel.hytale.server.core.event.events.player.*;

// World/Entity
import com.hypixel.hytale.server.core.universe.*;
import com.hypixel.hytale.server.core.entity.entities.Player;

// Notifications
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.io.PacketHandler;

// Interactions
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
```

---

## Application to HytaleVehicles

| Pattern | Vehicle Application |
|---------|---------------------|
| Persistent component with CODEC | Vehicle ownership, customization data |
| Public API class | VehicleAPI for other mods |
| Event systems for XP | Vehicle XP/leveling system |
| Config loading pattern | Vehicle config from mods/hyvehicles/ |
| Permission nodes | Vehicle spawning/driving permissions |
| Stat modifier system | Vehicle stat upgrades |
| Leaderboard data store | Racing leaderboards |
| Notification system | Vehicle status messages |
| Shutdown hook | Save vehicle data on server stop |

---

## Summary

MMO Skill Tree demonstrates:
- **Persistent components** saved with player data
- **Public API** for other plugins to access skill data
- **Multiple event systems** for XP tracking
- **External config files** loaded from mods/ folder
- **Permission system** in manifest.json
- **Stat modifier system** with proper cleanup
- **XP boost/token system** with offline delivery
- **Quest system** with objectives and rewards
- **Leaderboard persistence**
- **Internationalization** with language configs
