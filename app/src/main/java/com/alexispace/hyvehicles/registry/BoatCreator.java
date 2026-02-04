package com.alexispace.hyvehicles.registry;

import com.alexispace.hyvehicles.api.VehicleTypeCreator;
import com.alexispace.hyvehicles.definition.SeatDefinition;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.entity.WaterVehicle;
import com.alexispace.hyvehicles.util.Vec3;

/**
 * Built-in creator for BOAT type vehicles.
 * 
 * <p>Handles water vehicles with buoyancy physics.</p>
 * 
 * @since 1.0
 * @author alexispace
 */
public class BoatCreator implements VehicleTypeCreator {
    
    public static final BoatCreator INSTANCE = new BoatCreator();
    
    @Override
    public String getTypeName() {
        return "BOAT";
    }
    
    @Override
    public BaseVehicle createVehicle(VehicleDefinition definition, Vec3 position, float yaw) {
        return new WaterVehicle(definition, position, yaw);
    }
    
    @Override
    public ValidationResult validate(VehicleDefinition definition) {
        // Check for at least one seat
        if (definition.seats == null || definition.seats.isEmpty()) {
            return ValidationResult.error("BOAT must have at least one seat");
        }
        
        // Check for a driver seat
        boolean hasDriver = false;
        for (SeatDefinition seat : definition.seats) {
            if (seat.isDriver) {
                hasDriver = true;
                break;
            }
        }
        
        if (!hasDriver) {
            return ValidationResult.error("BOAT must have a driver seat (isDriver: true)");
        }
        
        // Check physics values are reasonable
        if (definition.buoyancy <= 0) {
            return ValidationResult.error("buoyancy must be positive");
        }
        
        if (definition.waterDrag <= 0 || definition.waterDrag > 1) {
            return ValidationResult.error("waterDrag must be between 0 and 1");
        }
        
        if (definition.maxSpeed <= 0) {
            return ValidationResult.error("maxSpeed must be positive");
        }
        
        return ValidationResult.OK;
    }
}
