package com.alexispace.hyvehicles.api;

import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.util.Vec3;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Collection;

/**
 * Main API for HytaleVehicles.
 * Content packs use this interface to register vehicles and spawn them.
 * 
 * <p>This API is designed to remain stable across minor versions.
 * Breaking changes will only occur in major version updates with deprecation warnings.</p>
 * 
 * <h2>Usage Example (Content Pack)</h2>
 * <pre>{@code
 * public class MyVehiclePack extends JavaPlugin {
 *     @Override
 *     public void setup() {
 *         VehicleAPI api = HytaleVehiclesPlugin.getAPI();
 *         
 *         // Register vehicles from JSON
 *         VehicleDefinition boat = loadDefinition("vehicles/my_boat.json");
 *         api.registerVehicle(boat);
 *     }
 * }
 * }</pre>
 * 
 * <h2>Usage Example (Custom Vehicle Type)</h2>
 * <pre>{@code
 * public class HovercraftPack extends JavaPlugin {
 *     @Override
 *     public void setup() {
 *         VehicleAPI api = HytaleVehiclesPlugin.getAPI();
 *         
 *         // Register custom vehicle type with Java behavior
 *         api.registerVehicleCreator("HOVERCRAFT", new HovercraftCreator());
 *         
 *         // Now JSON files can use type: "HOVERCRAFT"
 *     }
 * }
 * }</pre>
 * 
 * @since 1.0
 * @author alexispace
 */
public interface VehicleAPI {
    
    /**
     * Current API version. Increment on breaking changes.
     * Content packs can check this to ensure compatibility.
     */
    int API_VERSION = 1;
    
    /**
     * Register a vehicle definition.
     * The vehicle type must have a registered creator (either built-in or custom).
     * 
     * @param definition The vehicle definition to register
     * @throws IllegalArgumentException if the vehicle ID is already registered
     * @throws IllegalArgumentException if the vehicle type has no registered creator
     * @since 1.0
     */
    void registerVehicle(VehicleDefinition definition);
    
    /**
     * Spawn a vehicle at the specified location with a visible Hytale entity.
     *
     * <p>This creates both the internal vehicle physics simulation AND a visible
     * Hytale entity in the world that players can see and interact with.</p>
     *
     * @param vehicleId The vehicle ID (e.g., "hyvehicles:simple_boat")
     * @param position The world position to spawn at
     * @param yaw The initial rotation (degrees)
     * @param world The Hytale world to spawn the entity in
     * @return A handle to the spawned vehicle, or null if spawn failed
     * @throws IllegalArgumentException if the vehicle ID is not registered
     * @since 1.0
     */
    VehicleHandle spawnVehicle(String vehicleId, Vec3 position, float yaw, World world);

    /**
     * Spawn a vehicle using a CommandBuffer for deferred entity creation.
     * This is used by interactions that need deferred entity spawning.
     *
     * @param vehicleId The vehicle ID
     * @param position The world position to spawn at
     * @param yaw The initial rotation (degrees)
     * @param commandBuffer The command buffer for deferred operations
     * @return A handle to the spawned vehicle, or null if spawn failed
     * @since 1.0
     */
    VehicleHandle spawnVehicleWithCommandBuffer(String vehicleId, Vec3 position, float yaw, CommandBuffer commandBuffer);
    
    /**
     * Get all registered vehicle IDs.
     * 
     * @return An unmodifiable collection of vehicle IDs
     * @since 1.0
     */
    Collection<String> getRegisteredVehicles();
    
    /**
     * Get all registered vehicle type names.
     * Built-in types include: BOAT, CART (Phase 2), CAR (Phase 2)
     * 
     * @return An unmodifiable collection of type names
     * @since 1.0
     */
    Collection<String> getRegisteredTypes();
    
    /**
     * Check if a vehicle type has a registered creator.
     * 
     * @param typeName The type name (e.g., "BOAT", "HOVERCRAFT")
     * @return true if a creator is registered for this type
     * @since 1.0
     */
    boolean isTypeRegistered(String typeName);
    
    /**
     * Register a custom vehicle type creator.
     * This allows content packs to define new vehicle types with custom Java behavior.
     * 
     * <p>Creators registered later take precedence (like MTS's AItemPartCreator).
     * This allows packs to override built-in types if needed.</p>
     * 
     * @param typeName The type name (e.g., "HOVERCRAFT")
     * @param creator The creator that handles this type
     * @throws IllegalArgumentException if typeName is null or empty
     * @since 1.0
     */
    void registerVehicleCreator(String typeName, VehicleTypeCreator creator);
    
    /**
     * Get a vehicle definition by ID.
     * 
     * @param vehicleId The vehicle ID
     * @return The definition, or null if not found
     * @since 1.0
     */
    VehicleDefinition getVehicleDefinition(String vehicleId);
}
