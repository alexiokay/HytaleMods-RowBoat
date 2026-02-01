# HytaleVehicles - Project Overview

## Summary

This document outlines the plan to create vehicle mods for Hytale, leveraging experience from Minecraft Transport Simulator (MTS) development.

---

## Hytale vs Minecraft Modding - Key Differences

| Aspect | Minecraft (MTS) | Hytale |
|--------|-----------------|--------|
| **Mod Location** | Client + Server (users download mods) | **Server-side only** - players join without downloading |
| **Language** | Java 17 (NeoForge) | Java 25 for plugins |
| **Scripting** | Text-based code | Visual scripting + Java plugins |
| **Client mods** | Fully supported | Not supported (by design) |
| **Distribution** | CurseForge, Modrinth | CurseForge, direct to server owners |
| **Mod loader** | Forge/NeoForge/Fabric | Native Hytale API |

### Key Insight
Hytale mods are **server-side only**. Players don't download mods - they just join servers. This changes the distribution and business model significantly.

---

## Business Model

### Monetization is Allowed

Hytale explicitly permits selling mods:
- Paid plugin downloads ✅
- Donations ✅
- Patreon/subscriptions ✅
- Sponsorships ✅

### Revenue Share

| Period | Hypixel's Cut |
|--------|---------------|
| First 2 years (until Jan 2028) | **0%** |
| After 2 years | Up to 20% max |

### Rules to Follow

**Allowed:**
- Selling plugins/content packs
- Cosmetic items
- Quality-of-life features

**Not Allowed:**
- Pay-to-win mechanics (OP weapons, stat boosts)
- NFTs/crypto schemes
- Distributing game files

---

## Chosen Business Model: Base Plugin + Paid Addons

```
HytaleVehicles (Base API) - FREE
├── HyRideable Animals Pack    - $3-5
├── HyBoats Pack               - $3-5
├── HyStables (animal homes)   - $3
├── HyMotorcycles Pack         - $5
├── HyCars Pack                - $5-8
└── Future expansions...
```

### Why This Works

1. **Free base attracts users** - Lower barrier to entry
2. **Paid addons generate revenue** - People pay for content they want
3. **Modular** - Can keep adding packs indefinitely
4. **First to market** - No vehicle mods exist in Hytale yet

---

## Development Roadmap

### Phase 1: Simple Transport (Start Here)

| Mod | Complexity | Notes |
|-----|------------|-------|
| Drivable Animals | Low | Hytale has animals - add riding/control |
| Simple Boats | Low | Basic water physics |
| Animal Stables | Low | Storage structures for animals |

### Phase 2: Basic Vehicles

| Mod | Complexity | Notes |
|-----|------------|-------|
| Bicycles | Low | Stamina-based, no fuel |
| Carts | Low | Animal-pulled, storage |
| Motorcycles | Medium | 2-wheel physics |
| Simple Cars | Medium | 4-wheel, basic engine |

### Phase 3: Advanced (Later)

- Trucks with cargo
- Speedboats
- ATVs/Quads
- Tractors
- Aircraft (far future)

---

## Technical Approach

### Development Stack

| Tool | Version/Choice |
|------|----------------|
| Java | JDK 25+ |
| IDE | IntelliJ IDEA Community |
| Build System | Gradle |
| 3D Models | Blockbench |
| Version Control | Git |

### Hytale Mounts API

Hytale has a built-in mounts system we can leverage:

```java
// Key components in com.hypixel.hytale.builtin.mounts
NPCMountComponent    // Makes entities rideable
MountedComponent     // Tracks rider state
MountedByComponent   // Tracks passengers
BlockMountComponent  // For sittable blocks
```

### No Visual Editor Required

Everything can be done in pure Java:
- Custom entities ✅
- Mount controls ✅
- Vehicle physics ✅
- Sittable blocks ✅

Visual scripting is optional - only useful for complex NPC AI or adventure maps.

---

## Market Analysis

### Current Hytale Mod Landscape (Jan 2026)

**600+ mods available**, but gaps exist:

| Category | Status |
|----------|--------|
| Weapons | Covered (Wonder Weapons) |
| QoL (trash, stacks) | Covered |
| Maps/World | Covered |
| Furniture | Covered |
| Fishing | Covered |
| **Vehicles** | **NOBODY HAS DONE THIS** |
| **Trains/Rails** | Gap |
| **Aircraft** | Gap |
| **Machinery** | Gap |

### Opportunity

We would be **first to market** in the vehicle niche. With MTS experience, we have a significant advantage.

---

## Competition

### Existing Related Mods

| Mod | What it does | How we differ |
|-----|--------------|---------------|
| Mounts+ | Makes any entity rideable | We add custom vehicle entities |
| NPC Control | Control entity states | We focus on player-driven vehicles |

Neither provides actual vehicles - just rideable existing entities.

---

## Project Structure

```
HytaleVehicles/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/alexispace/hyvehicles/
│       │   └── HytaleVehiclesPlugin.java
│       └── resources/
│           └── manifest.json
├── docs/
│   └── PROJECT_OVERVIEW.md    ← This file
├── gradle/
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── README.md
└── settings.gradle.kts
```

---

## Resources & Links

### Official
- [Hytale Modding Strategy](https://hytale.com/news/2025/11/hytale-modding-strategy-and-status)
- [Hytale EULA](https://hytale.com/eula)
- [Server Operator Policies](https://hytale.com/server-policies)

### Documentation
- [Hytale Plugin Docs](https://doc.hytaledev.fr/en/)
- [Hytale Modding Docs](https://britakee-studios.gitbook.io/hytale-modding-documentation)
- [Mounts System API](https://hytale-docs.pages.dev/modding/systems/mounts/)

### Tools
- [Plugin Template](https://github.com/realBritakee/hytale-template-plugin)
- [HTDevLib (utility library)](https://www.curseforge.com/hytale/mods/htdevlib)

### Distribution
- [CurseForge Hytale](https://www.curseforge.com/hytale)
- [Modtale](https://modtale.net/)

---

## Next Steps

1. [ ] Install Java 25
2. [ ] Install IntelliJ IDEA
3. [ ] Install Hytale (for API access)
4. [ ] Build and test empty plugin
5. [ ] Study Mounts API in detail
6. [ ] Create first rideable entity
7. [ ] Create simple boat prototype
8. [ ] Test on local server
9. [ ] Publish to CurseForge

---

## Notes

- Hytale launched Early Access: January 13, 2026
- Game is still rough - expect API changes
- Community is eager for content
- 0% revenue share window = best time to start

---

*Document created: February 2026*
*Project: HytaleVehicles by alexispace*
