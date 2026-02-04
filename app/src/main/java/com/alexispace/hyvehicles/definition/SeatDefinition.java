package com.alexispace.hyvehicles.definition;

/**
 * Defines a seat position on a vehicle.
 * 
 * <h2>Example JSON</h2>
 * <pre>{@code
 * {
 *   "x": 0.0,
 *   "y": 0.5,
 *   "z": -0.3,
 *   "isDriver": true,
 *   "canControl": true
 * }
 * }</pre>
 * 
 * @since 1.0
 * @author alexispace
 */
public class SeatDefinition {
    
    /**
     * X offset from vehicle center (blocks).
     * Positive = right, Negative = left
     */
    public float x = 0f;
    
    /**
     * Y offset from vehicle center (blocks).
     * Positive = up
     */
    public float y = 0.5f;
    
    /**
     * Z offset from vehicle center (blocks).
     * Positive = forward, Negative = back
     */
    public float z = 0f;
    
    /**
     * Whether this seat is the driver seat.
     * Only one seat should be marked as driver.
     */
    public boolean isDriver = false;
    
    /**
     * Whether a passenger in this seat can control the vehicle.
     * Usually only true for the driver seat.
     */
    public boolean canControl = false;
    
    /**
     * Optional rotation offset (degrees).
     * Allows seats to face different directions.
     */
    public float rotationOffset = 0f;
    
    /**
     * Create a default seat (passenger, center position).
     */
    public SeatDefinition() {
    }
    
    /**
     * Create a seat at the specified position.
     */
    public SeatDefinition(float x, float y, float z, boolean isDriver) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.isDriver = isDriver;
        this.canControl = isDriver;
    }
    
    @Override
    public String toString() {
        return "SeatDefinition{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", isDriver=" + isDriver +
                '}';
    }
}
