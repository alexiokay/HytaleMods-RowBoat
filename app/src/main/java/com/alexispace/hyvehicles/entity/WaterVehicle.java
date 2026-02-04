package com.alexispace.hyvehicles.entity;

import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.util.Vec3;

/**
 * Water-based vehicle with buoyancy physics.
 * 
 * <p>Handles boats and other water craft. Key physics:</p>
 * <ul>
 *   <li>Buoyancy - floats on water surface</li>
 *   <li>Water drag - slows down in water</li>
 *   <li>Gravity - falls when out of water</li>
 *   <li>Wave simulation (future)</li>
 * </ul>
 * 
 * <h2>Configuration via VehicleDefinition</h2>
 * <ul>
 *   <li>{@code buoyancy} - Force multiplier (default 1.0)</li>
 *   <li>{@code waterDrag} - Drag coefficient 0.0-1.0 (default 0.95)</li>
 *   <li>{@code maxSpeed} - Maximum speed in blocks/second</li>
 * </ul>
 * 
 * @since 1.0
 * @author alexispace
 */
public class WaterVehicle extends BaseVehicle {
    
    // ==================== Constants ====================
    
    /**
     * Gravity acceleration in blocks per second squared.
     */
    private static final float GRAVITY = 20.0f;
    
    /**
     * Default water level if spawn position unknown.
     * TODO: Replace with actual water detection from Hytale world.
     */
    private static final float DEFAULT_WATER_LEVEL = 64.0f;

    /**
     * Initial spawn Y - used as reference water level.
     */
    private final float spawnY;
    
    /**
     * How deep the boat sits in water (blocks below surface).
     */
    private static final float WATER_OFFSET = 0.2f;
    
    // ==================== State ====================
    
    /**
     * Whether currently in water.
     */
    private boolean inWater = false;
    
    /**
     * Water surface level at current position.
     */
    private float waterLevel = DEFAULT_WATER_LEVEL;
    
    /**
     * Target Y position (for smooth floating).
     */
    private float targetY;
    
    // ==================== Constructor ====================
    
    public WaterVehicle(VehicleDefinition definition, Vec3 position, float yaw) {
        super(definition, position, yaw);
        this.spawnY = position.y;  // Remember spawn Y as water level reference
        this.targetY = position.y;
    }
    
    // ==================== Physics ====================
    
    @Override
    protected void updatePhysics(float deltaTime) {
        // Detect water at current position
        detectWater();
        
        if (inWater) {
            updateWaterPhysics(deltaTime);
        } else {
            updateAirPhysics(deltaTime);
        }
        
        // Apply horizontal friction
        velocity.x *= definition.friction;
        velocity.z *= definition.friction;
    }
    
    /**
     * Detect if the vehicle is in water.
     * TODO: Replace with actual Hytale water block detection.
     */
    private void detectWater() {
        // Placeholder: check if below water level
        // In actual implementation, we'd query Hytale's world for water blocks
        // or check MovementStates.inFluid
        
        waterLevel = getWaterLevelAt(position.x, position.z);
        inWater = position.y <= waterLevel + 0.5f; // Consider in water if close to surface
    }
    
    /**
     * Get water level at the given XZ position.
     * Uses spawn Y as reference until actual Hytale water detection is implemented.
     */
    private float getWaterLevelAt(float x, float z) {
        // Use spawn Y as reference water level
        // This assumes boats are spawned on water surface
        // TODO: Replace with actual Hytale world water block query
        return spawnY;
    }
    
    /**
     * Update physics while in water.
     */
    private void updateWaterPhysics(float deltaTime) {
        // Keep boat at stable Y level - don't let it sink
        // Target is spawn Y minus small offset to sit in water
        targetY = spawnY - WATER_OFFSET;

        float distanceToTarget = targetY - position.y;

        // Strong spring force to keep at target height
        // This prevents sinking when driving over deeper water
        if (Math.abs(distanceToTarget) > 0.01f) {
            // Push toward target with strong spring force
            float springForce = distanceToTarget * 15.0f; // Strong spring constant
            velocity.y += springForce * deltaTime;
        }

        // Heavy damping to prevent oscillation
        velocity.y *= 0.8f;

        // Apply water drag
        velocity.x *= definition.waterDrag;
        velocity.z *= definition.waterDrag;

        // Clamp vertical velocity - very limited to prevent bouncing
        velocity.y = Math.max(-2.0f, Math.min(2.0f, velocity.y));
    }
    
    /**
     * Update physics while in air (falling).
     */
    private void updateAirPhysics(float deltaTime) {
        // Apply gravity
        velocity.y -= GRAVITY * deltaTime;
        
        // Terminal velocity
        velocity.y = Math.max(-50.0f, velocity.y);
    }
    
    // ==================== Input Processing ====================
    
    @Override
    protected void processDriverInput(float deltaTime) {
        // Only allow control while in water
        if (inWater) {
            super.processDriverInput(deltaTime);
        } else {
            // Limited air control
            angularVelocity *= 0.95f;
        }
    }
    
    // ==================== Getters ====================
    
    public boolean isInWater() {
        return inWater;
    }
    
    public float getWaterLevel() {
        return waterLevel;
    }
}
