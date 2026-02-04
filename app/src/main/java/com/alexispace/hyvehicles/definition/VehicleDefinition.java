package com.alexispace.hyvehicles.definition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vehicle definition loaded from JSON.
 * 
 * <p>This class is designed for GSON deserialization. All fields have sensible defaults
 * so that old pack formats continue to work when new fields are added.</p>
 * 
 * <h2>Example JSON</h2>
 * <pre>{@code
 * {
 *   "schemaVersion": 1,
 *   "id": "hyboats:wooden_boat",
 *   "name": "Wooden Boat",
 *   "type": "BOAT",
 *   "maxSpeed": 8.0,
 *   "acceleration": 1.5,
 *   "turnRate": 60.0,
 *   "buoyancy": 1.2,
 *   "waterDrag": 0.92,
 *   "seats": [
 *     { "x": 0, "y": 0.3, "z": 0, "isDriver": true }
 *   ]
 * }
 * }</pre>
 * 
 * @since 1.0
 * @author alexispace
 */
public class VehicleDefinition {
    
    // ==================== Schema ====================
    
    /**
     * Schema version for migration support.
     * Increment when making breaking changes to the JSON format.
     */
    public int schemaVersion = 1;
    
    // ==================== Identity ====================
    
    /**
     * Unique vehicle identifier in format "packid:vehiclename".
     * Example: "hyboats:wooden_boat"
     */
    public String id;
    
    /**
     * Display name shown to players.
     */
    public String name;
    
    /**
     * Optional description for tooltips/UI.
     */
    public String description = "";
    
    // ==================== Type ====================
    
    /**
     * Vehicle type determines which creator handles this vehicle.
     * Built-in types: BOAT, CART, CAR
     * Custom types can be registered via VehicleAPI.registerVehicleCreator()
     */
    public String type;
    
    // ==================== Physics ====================
    
    /**
     * Maximum speed in blocks per second.
     */
    public float maxSpeed = 10f;
    
    /**
     * Acceleration rate in blocks per second squared.
     */
    public float acceleration = 2f;
    
    /**
     * Turn rate in degrees per second.
     */
    public float turnRate = 45f;
    
    /**
     * Friction multiplier applied each tick (0.0 - 1.0).
     * Lower values = more friction = faster stop.
     */
    public float friction = 0.98f;
    
    // ==================== Water Physics (BOAT type) ====================
    
    /**
     * Buoyancy force multiplier.
     * Higher values make the boat float higher.
     */
    public float buoyancy = 1.0f;
    
    /**
     * Water drag multiplier (0.0 - 1.0).
     * Lower values = more drag.
     */
    public float waterDrag = 0.95f;
    
    // ==================== Ground Physics (CART/CAR types) ====================
    
    /**
     * Wheel friction coefficient.
     * Higher values = better grip.
     */
    public float wheelFriction = 0.8f;
    
    /**
     * Suspension strength (future use).
     */
    public float suspensionStrength = 1.0f;
    
    // ==================== Seats ====================
    
    /**
     * List of seat positions.
     * First seat is typically the driver seat.
     * Can be auto-populated from blockyModelPath if specified.
     */
    public List<SeatDefinition> seats = new ArrayList<>();

    /**
     * Path to the .blockymodel file (relative to resources).
     * If specified, seat positions will be auto-extracted from nodes named "seat_*".
     * Example: "Common/Items/Vehicles/SimpleBoat.blockymodel"
     */
    public String blockyModelPath;

    /**
     * Scale factor for converting Blockbench units to game units.
     * Default is 16 (standard Blockbench: 16 units = 1 block).
     * Only used when extracting seats from blockyModelPath.
     */
    public float blockyModelScale = 16.0f;

    /**
     * Separate scale for Y axis (height). If 0, uses blockyModelScale.
     * Useful when height needs different scaling than horizontal positions.
     */
    public float blockyModelScaleY = 0.0f;

    /**
     * Offset to add to all extracted seat X positions.
     */
    public float seatOffsetX = 0.0f;

    /**
     * Offset to add to all extracted seat Y positions.
     */
    public float seatOffsetY = 0.0f;

    /**
     * Offset to add to all extracted seat Z positions.
     */
    public float seatOffsetZ = 0.0f;

    // ==================== Visuals ====================

    /**
     * Hytale model asset ID for the 3D model.
     * This references a model registered in Hytale's asset system.
     * Example: "hytale:entity/boat", "myvehiclepack:custom_boat"
     */
    public String modelId;

    /**
     * Model scale multiplier for the spawned vehicle entity.
     * 1.0 = default size, 2.0 = double size, etc.
     */
    public float modelScale = 1.0f;

    /**
     * Vertical offset to apply to the model position.
     * Use this to compensate for models with center-origin pivot points.
     * Positive values move the model UP, negative values move it DOWN.
     * Example: If model appears to float, use a negative value.
     * If model sinks into ground, use a positive value.
     * Default: 0.0 (no offset - assumes model origin is at bottom)
     */
    public float modelYOffset = 0.0f;

    /**
     * Path to the 3D model file (relative to pack resources).
     * Example: "models/wooden_boat.obj"
     * @deprecated Use modelId to reference Hytale assets instead
     */
    @Deprecated
    public String model;

    /**
     * Path to the texture file (relative to pack resources).
     * Example: "textures/wooden_boat.png"
     */
    public String texture;
    
    // ==================== Collision ====================
    
    /**
     * Collision box width in blocks.
     */
    public float collisionWidth = 1.5f;
    
    /**
     * Collision box height in blocks.
     */
    public float collisionHeight = 1.0f;
    
    /**
     * Collision box length in blocks.
     */
    public float collisionLength = 2.5f;
    
    // ==================== Drops ====================

    /**
     * Item ID to drop when vehicle is destroyed.
     * Example: "HytaleVehicles_SimpleBoat"
     */
    public String dropItemId;

    /**
     * Number of items to drop on destruction.
     */
    public int dropQuantity = 1;

    // ==================== Future Expansion ====================

    /**
     * Extra properties for future features or custom extensions.
     * Content packs can store custom data here.
     */
    public Map<String, Object> extra = new HashMap<>();
    
    // ==================== Helpers ====================
    
    /**
     * Check if this definition has any driver seat defined.
     */
    public boolean hasDriverSeat() {
        return seats.stream().anyMatch(s -> s.isDriver);
    }
    
    /**
     * Get the first driver seat, or null if none.
     */
    public SeatDefinition getDriverSeat() {
        return seats.stream().filter(s -> s.isDriver).findFirst().orElse(null);
    }
    
    /**
     * Get the total number of passengers this vehicle can hold.
     */
    public int getMaxPassengers() {
        return seats.size();
    }
    
    @Override
    public String toString() {
        return "VehicleDefinition{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", modelId='" + modelId + '\'' +
                ", maxSpeed=" + maxSpeed +
                ", seats=" + seats.size() +
                '}';
    }
}
