package com.alexispace.hyvehicles.api;

import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.entity.BaseVehicle;
import com.alexispace.hyvehicles.util.Vec3;

/**
 * Extension point for custom vehicle types.
 * 
 * <p>This interface allows content packs to define new vehicle types with custom Java behavior,
 * similar to MTS's {@code AItemPartCreator} pattern.</p>
 * 
 * <h2>Built-in Types</h2>
 * <ul>
 *   <li>{@code BOAT} - Water vehicles with buoyancy</li>
 *   <li>{@code CART} - Ground vehicles (animal-pulled) - Phase 2</li>
 *   <li>{@code CAR} - Ground vehicles (engine-powered) - Phase 2</li>
 * </ul>
 * 
 * <h2>Creating Custom Types</h2>
 * <pre>{@code
 * public class HovercraftCreator implements VehicleTypeCreator {
 *     @Override
 *     public String getTypeName() {
 *         return "HOVERCRAFT";
 *     }
 *     
 *     @Override
 *     public BaseVehicle createVehicle(VehicleDefinition definition, Vector3f position, float yaw) {
 *         return new HovercraftVehicle(definition, position, yaw);
 *     }
 *     
 *     @Override
 *     public ValidationResult validate(VehicleDefinition definition) {
 *         if (definition.hoverHeight <= 0) {
 *             return ValidationResult.error("hoverHeight must be positive");
 *         }
 *         return ValidationResult.OK;
 *     }
 * }
 * }</pre>
 * 
 * @since 1.0
 * @author alexispace
 */
public interface VehicleTypeCreator {
    
    /**
     * Get the type name this creator handles.
     * This must match the "type" field in vehicle JSON definitions.
     * 
     * @return The type name (e.g., "BOAT", "HOVERCRAFT")
     */
    String getTypeName();
    
    /**
     * Create a vehicle entity from the definition.
     * Called when spawning a vehicle of this type.
     * 
     * @param definition The vehicle definition from JSON
     * @param position The world position to spawn at
     * @param yaw The initial rotation (degrees)
     * @return A new vehicle entity instance
     */
    BaseVehicle createVehicle(VehicleDefinition definition, Vec3 position, float yaw);
    
    /**
     * Validate a vehicle definition for this type.
     * Called during pack loading to catch configuration errors early.
     * 
     * @param definition The vehicle definition to validate
     * @return The validation result (OK or error with message)
     */
    default ValidationResult validate(VehicleDefinition definition) {
        return ValidationResult.OK;
    }
    
    /**
     * Result of validating a vehicle definition.
     */
    class ValidationResult {
        public static final ValidationResult OK = new ValidationResult(true, null);
        
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
