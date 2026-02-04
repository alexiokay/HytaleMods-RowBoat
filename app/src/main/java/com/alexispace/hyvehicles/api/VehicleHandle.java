package com.alexispace.hyvehicles.api;

import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.util.Vec3;

/**
 * Handle to a spawned vehicle.
 * 
 * <p>This provides a safe reference to a vehicle entity that may be destroyed
 * or unloaded. Always check {@link #isValid()} before performing operations.</p>
 * 
 * @since 1.0
 * @author alexispace
 */
public interface VehicleHandle {
    
    /**
     * Get the vehicle ID (e.g., "hyboats:wooden_boat").
     * 
     * @return The vehicle ID
     */
    String getVehicleId();
    
    /**
     * Get the unique instance ID for this spawned vehicle.
     * 
     * @return The instance UUID
     */
    String getInstanceId();
    
    /**
     * Check if this vehicle still exists in the world.
     * 
     * @return true if the vehicle is valid and can be interacted with
     */
    boolean isValid();
    
    /**
     * Get the current position of the vehicle.
     * 
     * @return The world position, or null if invalid
     */
    Vec3 getPosition();
    
    /**
     * Get the current rotation (yaw) of the vehicle.
     * 
     * @return The rotation in degrees
     */
    float getYaw();
    
    /**
     * Get the underlying vehicle entity.
     * 
     * @return The vehicle entity, or null if invalid
     */
    BaseVehicle getVehicle();
    
    /**
     * Remove this vehicle from the world.
     * 
     * @return true if successfully removed
     */
    boolean destroy();
    
    /**
     * Get the number of passengers currently mounted.
     * 
     * @return The passenger count
     */
    int getPassengerCount();
    
    /**
     * Check if the vehicle has a driver.
     * 
     * @return true if someone is in the driver seat
     */
    boolean hasDriver();
}
