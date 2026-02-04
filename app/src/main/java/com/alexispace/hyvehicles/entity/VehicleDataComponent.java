package com.alexispace.hyvehicles.entity;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Component that stores vehicle metadata on the entity itself.
 * This data is persisted by Hytale's ECS along with the entity,
 * so it survives world reloads automatically.
 *
 * <p>Contains:</p>
 * <ul>
 *   <li>definitionId - The vehicle definition ID (e.g., "hyvehicles:simple_boat")</li>
 *   <li>dropItemId - The item to drop when destroyed</li>
 *   <li>dropQuantity - How many items to drop</li>
 * </ul>
 *
 * @since 1.0
 * @author alexispace
 */
public class VehicleDataComponent implements Component<EntityStore> {

    // Component type - set during plugin registration
    private static ComponentType<EntityStore, VehicleDataComponent> componentType;

    // Data fields (persisted)
    private String definitionId;
    private String dropItemId;
    private int dropQuantity;

    // Transient control flags (not persisted, set each tick)
    private transient boolean braking;
    private transient boolean boosting;

    // Transient seat occupancy tracking (not persisted)
    private transient java.util.Set<Integer> occupiedSeats = new java.util.HashSet<>();

    /**
     * BuilderCodec for serialization/persistence.
     * This tells Hytale how to save/load this component.
     */
    public static final BuilderCodec<VehicleDataComponent> CODEC = createCodec();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BuilderCodec<VehicleDataComponent> createCodec() {
        BuilderCodec.Builder<VehicleDataComponent> builder = BuilderCodec.builder(
            VehicleDataComponent.class,
            VehicleDataComponent::new
        );

        return ((BuilderCodec.Builder<VehicleDataComponent>)
            builder.appendInherited(
                new KeyedCodec<>("DefinitionId", Codec.STRING, false),
                (VehicleDataComponent c, String v) -> c.definitionId = v != null ? v : "",
                (VehicleDataComponent c) -> c.definitionId,
                (VehicleDataComponent c, VehicleDataComponent p) -> c.definitionId = p.definitionId
            ).add()
            .appendInherited(
                new KeyedCodec<>("DropItemId", Codec.STRING, false),
                (VehicleDataComponent c, String v) -> c.dropItemId = v != null ? v : "",
                (VehicleDataComponent c) -> c.dropItemId,
                (VehicleDataComponent c, VehicleDataComponent p) -> c.dropItemId = p.dropItemId
            ).add()
            .appendInherited(
                new KeyedCodec<>("DropQuantity", Codec.INTEGER, false),
                (VehicleDataComponent c, Integer v) -> c.dropQuantity = v != null ? v : 1,
                (VehicleDataComponent c) -> c.dropQuantity,
                (VehicleDataComponent c, VehicleDataComponent p) -> c.dropQuantity = p.dropQuantity
            ).add()
        ).build();
    }

    /**
     * Default constructor (required for codec/registration).
     */
    public VehicleDataComponent() {
        this.definitionId = "";
        this.dropItemId = "";
        this.dropQuantity = 1;
    }

    /**
     * Constructor with values.
     */
    public VehicleDataComponent(String definitionId, String dropItemId, int dropQuantity) {
        this.definitionId = definitionId != null ? definitionId : "";
        this.dropItemId = dropItemId != null ? dropItemId : "";
        this.dropQuantity = dropQuantity;
    }

    /**
     * Copy constructor for clone().
     */
    public VehicleDataComponent(VehicleDataComponent other) {
        this.definitionId = other.definitionId;
        this.dropItemId = other.dropItemId;
        this.dropQuantity = other.dropQuantity;
    }

    // Required by Component interface
    @Override
    public Component<EntityStore> clone() {
        return new VehicleDataComponent(this);
    }

    // Getters
    public String getDefinitionId() {
        return definitionId;
    }

    public String getDropItemId() {
        return dropItemId;
    }

    public int getDropQuantity() {
        return dropQuantity;
    }

    // Setters (needed for codec)
    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public void setDropItemId(String dropItemId) {
        this.dropItemId = dropItemId;
    }

    public void setDropQuantity(int dropQuantity) {
        this.dropQuantity = dropQuantity;
    }

    // === Control flags (transient, set each tick by VehicleMountSystem) ===

    public boolean isBraking() {
        return braking;
    }

    public void setBraking(boolean braking) {
        this.braking = braking;
    }

    public boolean isBoosting() {
        return boosting;
    }

    public void setBoosting(boolean boosting) {
        this.boosting = boosting;
    }

    /**
     * Reset control flags. Call at the start of each tick.
     */
    public void resetControlFlags() {
        this.braking = false;
        this.boosting = false;
    }

    // === Seat occupancy tracking ===

    /**
     * Find the first available (unoccupied) seat index.
     * @param totalSeats Total number of seats on this vehicle
     * @return Seat index (0-based), or -1 if all seats occupied
     */
    public int findAvailableSeat(int totalSeats) {
        for (int i = 0; i < totalSeats; i++) {
            if (!occupiedSeats.contains(i)) {
                return i;
            }
        }
        return -1; // All seats occupied
    }

    /**
     * Mark a seat as occupied.
     */
    public void occupySeat(int seatIndex) {
        occupiedSeats.add(seatIndex);
    }

    /**
     * Mark a seat as vacant.
     */
    public void vacateSeat(int seatIndex) {
        occupiedSeats.remove(seatIndex);
    }

    /**
     * Check if a seat is occupied.
     */
    public boolean isSeatOccupied(int seatIndex) {
        return occupiedSeats.contains(seatIndex);
    }

    /**
     * Get number of occupied seats.
     */
    public int getOccupiedSeatCount() {
        return occupiedSeats.size();
    }

    /**
     * Clear all seat occupancy (e.g., when vehicle is destroyed).
     */
    public void clearAllSeats() {
        occupiedSeats.clear();
    }

    /**
     * Get the component type. Must be called AFTER registration in plugin setup().
     */
    public static ComponentType<EntityStore, VehicleDataComponent> getComponentType() {
        return componentType;
    }

    /**
     * Set the component type (called during plugin registration).
     */
    public static void setComponentType(ComponentType<EntityStore, VehicleDataComponent> type) {
        componentType = type;
    }

    @Override
    public String toString() {
        return "VehicleDataComponent{" +
                "definitionId='" + definitionId + '\'' +
                ", dropItemId='" + dropItemId + '\'' +
                ", dropQuantity=" + dropQuantity +
                ", braking=" + braking +
                ", boosting=" + boosting +
                '}';
    }
}
