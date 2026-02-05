package com.alexispace.hyvehicles.entity;

import com.alexispace.hyvehicles.definition.SeatDefinition;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.util.Vec3;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Base class for all vehicle entities.
 * 
 * <p>This class handles the core functionality shared by all vehicles:</p>
 * <ul>
 *   <li>Position and rotation</li>
 *   <li>Velocity and movement</li>
 *   <li>Rider/passenger management</li>
 *   <li>Input handling</li>
 * </ul>
 * 
 * <p>Subclasses implement type-specific physics:</p>
 * <ul>
 *   <li>{@link WaterVehicle} - Buoyancy and water drag</li>
 *   <li>{@code GroundVehicle} - Wheel friction and terrain (Phase 2)</li>
 * </ul>
 * 
 * <h2>Entity Hierarchy (Inspired by MTS)</h2>
 * <pre>
 * MTS Hierarchy:              HytaleVehicles Hierarchy:
 * AEntityA_Base               BaseVehicle (this class)
 * AEntityB_Existing              ├── WaterVehicle
 * AEntityC_Renderable            ├── GroundVehicle (Phase 2)
 * AEntityD_Definable             └── (Custom types via VehicleTypeCreator)
 * AEntityE_Interactable
 * AEntityF_Multipart
 * AEntityG_Towable
 * </pre>
 * 
 * @since 1.0
 * @author alexispace
 */
public abstract class BaseVehicle {
    
    // ==================== Identity ====================
    
    /**
     * Unique instance ID for this spawned vehicle.
     */
    protected final UUID instanceId;
    
    /**
     * The vehicle definition from JSON.
     */
    protected final VehicleDefinition definition;
    
    // ==================== Transform ====================
    
    /**
     * Current world position.
     */
    protected final Vec3 position;
    
    /**
     * Previous tick position (for interpolation).
     */
    protected final Vec3 prevPosition;
    
    /**
     * Current rotation (yaw in degrees).
     */
    protected float rotationYaw;
    
    /**
     * Previous tick rotation (for interpolation).
     */
    protected float prevRotationYaw;
    
    // ==================== Physics ====================
    
    /**
     * Current velocity.
     */
    protected final Vec3 velocity;
    
    /**
     * Angular velocity (rotation speed in degrees/tick).
     */
    protected float angularVelocity;
    
    // ==================== Passengers ====================
    
    /**
     * Passengers in each seat (null if empty).
     * Index corresponds to seats list in definition.
     */
    protected final List<PassengerInfo> passengers;
    
    /**
     * Current driver (or null if no driver).
     */
    protected PassengerInfo driver;
    
    // ==================== Input State ====================
    
    /**
     * Current input from driver.
     */
    protected VehicleInput currentInput = new VehicleInput();
    
    // ==================== State ====================

    /**
     * Whether this vehicle has been destroyed.
     */
    protected boolean destroyed = false;

    /**
     * Tick counter since spawn.
     */
    protected long ticksExisted = 0;

    // ==================== Collision ====================

    /**
     * Collision checker for block collision detection.
     * Set by VehicleEntityBridge using World data.
     */
    protected CollisionChecker collisionChecker = CollisionChecker.NONE;

    // ==================== Entity Reference ====================

    /**
     * Reference to the Hytale ECS entity.
     * Used to detect when the entity is destroyed by the game's death system.
     */
    protected Ref<EntityStore> entityRef;

    /**
     * Reference to the world this vehicle is in.
     * Used for destruction effects (particles, item drops).
     */
    protected com.hypixel.hytale.server.core.universe.world.World world;

    /**
     * Track if rowing animation is currently playing.
     * Used to avoid redundant animation calls.
     */
    protected boolean isAnimationPlaying = false;

    // ==================== Constructor ====================
    
    /**
     * Create a new vehicle entity.
     * 
     * @param definition The vehicle definition
     * @param position Initial world position
     * @param yaw Initial rotation (degrees)
     */
    public BaseVehicle(VehicleDefinition definition, Vec3 position, float yaw) {
        this.instanceId = UUID.randomUUID();
        this.definition = definition;
        
        this.position = new Vec3(position);
        this.prevPosition = new Vec3(position);
        this.rotationYaw = yaw;
        this.prevRotationYaw = yaw;
        
        this.velocity = new Vec3(0, 0, 0);
        this.angularVelocity = 0;
        
        // Initialize passenger slots
        this.passengers = new ArrayList<>();
        for (int i = 0; i < definition.seats.size(); i++) {
            passengers.add(null);
        }
    }
    
    // ==================== Lifecycle ====================
    
    /**
     * Called every server tick (20 times per second).
     * 
     * @param deltaTime Time since last tick in seconds (usually 0.05)
     */
    public void tick(float deltaTime) {
        if (destroyed) return;
        
        ticksExisted++;
        
        // Save previous state for interpolation
        prevPosition.set(position);
        prevRotationYaw = rotationYaw;
        
        // Handle input from driver
        if (driver != null) {
            processDriverInput(deltaTime);
        }
        
        // Update physics (implemented by subclasses)
        updatePhysics(deltaTime);
        
        // Apply velocity to position
        applyVelocity(deltaTime);
        
        // Apply angular velocity to rotation
        rotationYaw += angularVelocity * deltaTime;
        rotationYaw = normalizeAngle(rotationYaw);
    }
    
    /**
     * Override in subclasses to implement type-specific physics.
     * 
     * @param deltaTime Time since last tick in seconds
     */
    protected abstract void updatePhysics(float deltaTime);
    
    /**
     * Process input from the driver.
     *
     * @param deltaTime Time since last tick in seconds
     */
    protected void processDriverInput(float deltaTime) {
        // === BRAKE (SPACE key) - slow down significantly ===
        if (currentInput.brake) {
            // Apply strong brake - reduce velocity by 50% per second
            float brakeFactor = 1.0f - (0.5f * deltaTime * 20f); // 50% reduction per tick
            if (brakeFactor < 0.5f) brakeFactor = 0.5f;
            velocity.x *= brakeFactor;
            velocity.z *= brakeFactor;
            angularVelocity *= brakeFactor;
            return; // Don't process other input while braking
        }

        // Forward/backward acceleration
        if (currentInput.forward > 0) {
            float accel = definition.acceleration * currentInput.forward * deltaTime;
            accelerateForward(accel);
        } else if (currentInput.backward > 0) {
            float accel = definition.acceleration * currentInput.backward * deltaTime * 0.5f; // Reverse is slower
            accelerateForward(-accel);
        }

        // Turning
        if (currentInput.turnLeft > 0) {
            angularVelocity += definition.turnRate * currentInput.turnLeft * deltaTime;
        }
        if (currentInput.turnRight > 0) {
            angularVelocity -= definition.turnRate * currentInput.turnRight * deltaTime;
        }

        // Apply turn friction
        angularVelocity *= 0.9f;
    }
    
    /**
     * Accelerate in the forward direction.
     */
    protected void accelerateForward(float amount) {
        float rad = (float) Math.toRadians(rotationYaw);
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);
        
        velocity.x += sin * amount;
        velocity.z += cos * amount;
        
        // Clamp to max speed
        float speed = getHorizontalSpeed();
        if (speed > definition.maxSpeed) {
            float scale = definition.maxSpeed / speed;
            velocity.x *= scale;
            velocity.z *= scale;
        }
    }
    
    /**
     * Apply velocity to position with collision checking.
     */
    protected void applyVelocity(float deltaTime) {
        float width = definition.collisionWidth;
        float height = definition.collisionHeight;

        // Try moving in X
        float newX = position.x + velocity.x * deltaTime;
        if (!collisionChecker.checkCollision(newX, position.y, position.z, width, height)) {
            position.x = newX;
        } else {
            velocity.x = 0; // Stop X velocity on collision
        }

        // Try moving in Y
        float newY = position.y + velocity.y * deltaTime;
        if (!collisionChecker.checkCollision(position.x, newY, position.z, width, height)) {
            position.y = newY;
        } else {
            velocity.y = 0; // Stop Y velocity on collision
        }

        // Try moving in Z
        float newZ = position.z + velocity.z * deltaTime;
        if (!collisionChecker.checkCollision(position.x, position.y, newZ, width, height)) {
            position.z = newZ;
        } else {
            velocity.z = 0; // Stop Z velocity on collision
        }
    }

    /**
     * Set the collision checker for block collision detection.
     *
     * @param checker The collision checker to use
     */
    public void setCollisionChecker(CollisionChecker checker) {
        this.collisionChecker = checker != null ? checker : CollisionChecker.NONE;
    }

    /**
     * Set the Hytale entity reference.
     * Used to track when the entity is destroyed by the game's systems.
     *
     * @param ref The entity reference
     */
    public void setEntityRef(Ref<EntityStore> ref) {
        this.entityRef = ref;
    }

    /**
     * Get the Hytale entity reference.
     *
     * @return The entity reference, or null if not set
     */
    public Ref<EntityStore> getEntityRef() {
        return entityRef;
    }

    /**
     * Check if the Hytale entity is still alive/valid.
     *
     * @return true if entity exists and is valid, false if destroyed
     */
    public boolean isEntityAlive() {
        return entityRef != null && entityRef.isValid();
    }

    /**
     * Set the world this vehicle is in.
     *
     * @param world The world reference
     */
    public void setWorld(com.hypixel.hytale.server.core.universe.world.World world) {
        this.world = world;
    }

    /**
     * Get the world this vehicle is in.
     *
     * @return The world reference, or null if not set
     */
    public com.hypixel.hytale.server.core.universe.world.World getWorld() {
        return world;
    }

    // ==================== Mounting ====================
    
    /**
     * Mount a passenger to this vehicle.
     * 
     * @param passengerId The passenger's unique ID
     * @param seatIndex The seat index to mount to
     * @return true if mounting succeeded
     */
    public boolean mount(UUID passengerId, int seatIndex) {
        if (destroyed) return false;
        if (seatIndex < 0 || seatIndex >= passengers.size()) return false;
        if (passengers.get(seatIndex) != null) return false;

        SeatDefinition seatDef = definition.seats.get(seatIndex);
        PassengerInfo passenger = new PassengerInfo(passengerId, seatIndex, seatDef);
        passengers.set(seatIndex, passenger);

        if (seatDef.isDriver) {
            driver = passenger;
            System.out.println("[MOUNT] Set driver for vehicle " + definition.id +
                             " - seat " + seatIndex + ", isDriver=" + seatDef.isDriver +
                             ", hasDriver now=" + (driver != null));
        } else {
            System.out.println("[MOUNT] Mounted passenger (NOT driver) for vehicle " + definition.id +
                             " - seat " + seatIndex + ", isDriver=" + seatDef.isDriver);
        }

        onMount(passenger);
        return true;
    }
    
    /**
     * Dismount a passenger from this vehicle.
     * 
     * @param passengerId The passenger's unique ID
     * @return true if dismounting succeeded
     */
    public boolean dismount(UUID passengerId) {
        for (int i = 0; i < passengers.size(); i++) {
            PassengerInfo passenger = passengers.get(i);
            if (passenger != null && passenger.passengerId.equals(passengerId)) {
                passengers.set(i, null);

                boolean wasDriver = (driver != null && driver.passengerId.equals(passengerId));
                if (wasDriver) {
                    driver = null;
                    currentInput = new VehicleInput(); // Clear input

                    // CRITICAL FIX: Zero out velocity to prevent momentum transfer from dismounting player
                    // Without this, the boat inherits the player's walking velocity when they dismount
                    velocity.x = 0;
                    velocity.y = 0;
                    velocity.z = 0;
                    angularVelocity = 0;

                    System.out.println("[DISMOUNT] Cleared driver for vehicle " + definition.id +
                                     " - seat " + i + ", hasDriver now=" + (driver != null) +
                                     ", velocity zeroed");
                } else {
                    System.out.println("[DISMOUNT] Dismounted passenger (NOT driver) for vehicle " + definition.id +
                                     " - seat " + i);
                }

                onDismount(passenger);
                return true;
            }
        }
        System.out.println("[DISMOUNT] WARNING: Could not find passenger " + passengerId +
                         " in vehicle " + definition.id);
        return false;
    }
    
    /**
     * Called when a passenger mounts.
     * Override in subclasses for custom behavior.
     */
    protected void onMount(PassengerInfo passenger) {
        // TODO: Integrate with Hytale's MountedComponent
    }
    
    /**
     * Called when a passenger dismounts.
     * Override in subclasses for custom behavior.
     */
    protected void onDismount(PassengerInfo passenger) {
        // TODO: Integrate with Hytale's MountedComponent
    }
    
    // ==================== Input ====================
    
    /**
     * Set the driver's input state.
     * Called by the input handler when receiving player input.
     */
    public void setDriverInput(VehicleInput input) {
        this.currentInput = input;
    }
    
    // ==================== Destruction ====================
    
    /**
     * Destroy this vehicle and dismount all passengers.
     */
    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        
        // Dismount all passengers
        for (int i = passengers.size() - 1; i >= 0; i--) {
            PassengerInfo passenger = passengers.get(i);
            if (passenger != null) {
                dismount(passenger.passengerId);
            }
        }
    }
    
    // ==================== Getters ====================
    
    public UUID getInstanceId() { return instanceId; }
    public VehicleDefinition getDefinition() { return definition; }
    public Vec3 getPosition() { return new Vec3(position); }
    public float[] getPositionArray() { return position.toArray(); }
    public Vec3 getPrevPosition() { return new Vec3(prevPosition); }
    public float getRotationYaw() { return rotationYaw; }
    public void setRotationYaw(float yaw) { this.rotationYaw = yaw; }
    public Vec3 getVelocity() { return new Vec3(velocity); }
    public boolean isDestroyed() { return destroyed; }
    public long getTicksExisted() { return ticksExisted; }
    
    public float getHorizontalSpeed() {
        return (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }
    
    public int getPassengerCount() {
        return (int) passengers.stream().filter(p -> p != null).count();
    }
    
    public boolean hasDriver() {
        return driver != null;
    }

    /**
     * Check if the vehicle should animate.
     * Requires BOTH:
     * - Someone sitting in the boat (driver or passenger)
     * - Boat moving above threshold (0.1 blocks/tick = 2 blocks/second)
     */
    public boolean shouldAnimate() {
        return hasDriver() && getHorizontalSpeed() > 0.1f;
    }

    /**
     * Get the current animation state.
     */
    public boolean isAnimationPlaying() {
        return isAnimationPlaying;
    }

    /**
     * Set the animation state (called by animation system).
     */
    public void setAnimationPlaying(boolean playing) {
        this.isAnimationPlaying = playing;
    }

    // ==================== Helpers ====================
    
    /**
     * Normalize an angle to 0-360 range.
     */
    protected float normalizeAngle(float angle) {
        angle = angle % 360;
        if (angle < 0) angle += 360;
        return angle;
    }
    
    // ==================== Inner Classes ====================
    
    /**
     * Information about a mounted passenger.
     */
    public static class PassengerInfo {
        public final UUID passengerId;
        public final int seatIndex;
        public final SeatDefinition seatDef;
        
        public PassengerInfo(UUID passengerId, int seatIndex, SeatDefinition seatDef) {
            this.passengerId = passengerId;
            this.seatIndex = seatIndex;
            this.seatDef = seatDef;
        }
    }
    
    /**
     * Driver input state.
     */
    public static class VehicleInput {
        public float forward = 0;    // 0.0 - 1.0 (W key)
        public float backward = 0;   // 0.0 - 1.0 (S key)
        public float turnLeft = 0;   // 0.0 - 1.0 (A key)
        public float turnRight = 0;  // 0.0 - 1.0 (D key)
        public boolean jump = false; // Space (dismount or other action)
        public boolean brake = false; // Shift (brake)
    }
}
