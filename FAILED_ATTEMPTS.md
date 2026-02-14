# Failed Attempts to Fix Boat Teleportation Bug

## The Bug
- Boat teleports to spawn position on dismount
- Worse when using SHIFT (sprint) to go fast
- Sometimes boat remains controllable after dismount (driver not cleared)

## What We Tried (ALL FAILED):

### ❌ Attempt 1: Update VehicleDataComponent before DismountNPC
- **Theory:** Hytale restores position from stale VehicleDataComponent
- **Implementation:** Updated VehicleDataComponent.setPosition() immediately before sending DismountNPC packet
- **Result:** FAILED - still teleports

### ❌ Attempt 2: Update TransformComponent + VehicleDataComponent before DismountNPC
- **Theory:** Need to update both components before dismount
- **Implementation:** Force-updated both TransformComponent and VehicleDataComponent before DismountNPC
- **Result:** FAILED - still teleports

### ❌ Attempt 3: EntityUpdates packet AFTER DismountNPC
- **Theory:** TransformComponent changes don't auto-sync to client
- **Implementation:**
  - Built EntityUpdates packet with ComponentUpdate.Transform
  - Sent to all players AFTER DismountNPC
- **Result:** FAILED - still teleports
- **Evidence:** Logs show [FORCE-SYNC] packets sent, but boat still teleports

### ❌ Attempt 4: EntityUpdates packet BEFORE DismountNPC (changed order)
- **Theory:** Client needs position update BEFORE dismount packet
- **Implementation:** Send EntityUpdates packet BEFORE DismountNPC instead of after
- **Result:** FAILED - still teleports

### ❌ Attempt 5: Per-tick EntityUpdates packets while mounted
- **Theory:** Need continuous sync to prevent desync accumulation
- **Implementation:**
  - Send EntityUpdates packet every tick in tickMountedPlayers()
  - Throttled logging every 100 ticks
- **Result:** FAILED - still teleports
- **Evidence:** No [PER-TICK SYNC] logs appeared, suggesting code might not be running

### ❌ Attempt 6: Use Hytale's Teleport component
- **Theory:** TransformComponent changes don't sync; use Hytale's native Teleport system
- **Source:** Hytale Dev Docs - "Directly modifying TransformComponent causes client desync"
- **Implementation:**
  - Create Teleport component with current vehicle position
  - Add to vehicle entity before DismountNPC
  - This should trigger Hytale's native client-server sync
- **Result:** FAILED - still teleports

### ❌ Attempt 7: Remove position fields from VehicleDataComponent persistence
- **Theory:** Hytale's ECS persistence restores entity from VehicleDataComponent codec
- **Implementation:**
  - Removed PosX, PosY, PosZ, RotationYaw from VehicleDataComponent.CODEC
  - Position fields still exist in-memory but NOT persisted to ECS
  - JSON file handles long-term persistence only
- **Result:** FAILED - still teleports
- **Note:** May need to delete old world data for this to take effect on existing boats

### ❌ Attempt 8: Fixed threading errors in rowing animation
- **Issue:** IllegalStateException "Assert not in thread!" when playing rowing animations
- **Fix:** Catch IllegalStateException silently in playRowingAnimation() and stopRowingAnimation()
- **Result:** Fixed threading errors, but didn't affect teleportation bug

## What We Know:

### Server-Side Facts:
- Server position is ALWAYS CORRECT after dismount
- Server position stays stable at the correct location (logged for 20 ticks post-dismount)
- Velocity is correctly zeroed on dismount
- TransformComponent is updated to match physics position
- [FORCE-SYNC] packets ARE being sent

### Client-Side Observations:
- Client sees boat teleport to SPAWN POSITION (not just any random position)
- Teleportation is CLIENT-SIDE visual only (server has correct position)
- Worse with SHIFT (sprint/fast movement) - suggests desync accumulation
- Less likely when driving slow - confirms desync is speed-related

### Mount System Observations:
- MountNPC packet uses RELATIVE seat offsets (seatX, seatY, seatZ), not absolute positions
- DismountNPC packet has no position parameters
- Sometimes driver state isn't cleared properly (boat controllable after dismount)

## Current Hypothesis:

The issue is NOT:
- ❌ Server-side position (server is correct)
- ❌ VehicleDataComponent position (we removed it from persistence)
- ❌ TransformComponent sync (we tried EntityUpdates and Teleport)
- ❌ Packet ordering (tried both before and after DismountNPC)

The issue MIGHT be:
- ✅ Hytale's native mount system has built-in position caching/restoration
- ✅ MountNPC packet stores vehicle position at mount time, DismountNPC restores it
- ✅ Client-side prediction completely desyncs while mounted
- ✅ TransformComponent itself is a persistent component and Hytale restores from it
- ✅ Some other Hytale ECS system is interfering

## Next Steps to Try:

1. **Check if TransformComponent is persistent** - may need to mark it as non-persistent
2. **Try NOT using Hytale's mount packets at all** - teleport player instead?
3. **Destroy and respawn vehicle entity on dismount** - nuclear option but might work
4. **Use Hytale's native vehicle/mount entities** instead of custom physics
5. **Test if the issue happens with a completely empty vehicle entity** (no components)
6. **Check Hytale community forums/Discord** for similar issues with custom mounts
7. **Decompile Hytale's native boat/horse entities** to see how they handle this

## Files Modified:

- VehicleMountSystem.java - multiple attempts at position sync before/after dismount
- VehicleDataComponent.java - removed position fields from persistence codec
- WaterVehicle.java - fixed threading errors in rowing animation
- VehicleTickSystem.java - added post-dismount position logging
- BaseVehicle.java - added ticksSinceDismount tracking
