package com.alexispace.hyvehicles.util;

/**
 * Simple logging interface to abstract away the logging implementation.
 * This allows us to use Hytale's HytaleLogger in the plugin while still
 * having testable code that doesn't depend on the Hytale runtime.
 * 
 * @since 1.0
 * @author alexispace
 */
public interface VehicleLogger {
    
    void info(String message);
    void warning(String message);
    void severe(String message);
    
    default void info(String format, Object... args) {
        info(String.format(format.replace("{}", "%s"), args));
    }
    
    default void warning(String format, Object... args) {
        warning(String.format(format.replace("{}", "%s"), args));
    }
    
    default void severe(String format, Object... args) {
        severe(String.format(format.replace("{}", "%s"), args));
    }
    
    /**
     * Simple console-based logger for testing.
     */
    static VehicleLogger console() {
        return new VehicleLogger() {
            @Override
            public void info(String message) {
                System.out.println("[HyVehicles] INFO: " + message);
            }
            
            @Override
            public void warning(String message) {
                System.out.println("[HyVehicles] WARNING: " + message);
            }
            
            @Override
            public void severe(String message) {
                System.err.println("[HyVehicles] SEVERE: " + message);
            }
        };
    }
}
