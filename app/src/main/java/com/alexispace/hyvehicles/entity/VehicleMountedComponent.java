package com.alexispace.hyvehicles.entity;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Custom mount component for vehicles.
 * Added to PLAYER entity to indicate they are mounted on a vehicle.
 *
 * This is our own component - we don't use Hytale's MountedComponent
 * because its MountController causes unwanted teleportation behavior.
 *
 * @since 1.0
 * @author alexispace
 */
public class VehicleMountedComponent implements Component<EntityStore> {

    // Component type - set during plugin registration
    private static ComponentType<EntityStore, VehicleMountedComponent> componentType;

    // The vehicle entity this player is mounted on (stored as index for persistence)
    private int vehicleEntityIndex;

    // Seat offset from vehicle position
    private float seatOffsetX;
    private float seatOffsetY;
    private float seatOffsetZ;

    // Runtime reference (not persisted, rebuilt from index)
    private transient Ref<EntityStore> vehicleRef;

    /**
     * BuilderCodec for serialization/persistence.
     */
    public static final BuilderCodec<VehicleMountedComponent> CODEC = createCodec();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BuilderCodec<VehicleMountedComponent> createCodec() {
        BuilderCodec.Builder<VehicleMountedComponent> builder = BuilderCodec.builder(
            VehicleMountedComponent.class,
            VehicleMountedComponent::new
        );

        return ((BuilderCodec.Builder<VehicleMountedComponent>)
            builder.appendInherited(
                new KeyedCodec<>("VehicleEntityIndex", Codec.INTEGER, false),
                (VehicleMountedComponent c, Integer v) -> c.vehicleEntityIndex = v != null ? v : -1,
                (VehicleMountedComponent c) -> c.vehicleEntityIndex,
                (VehicleMountedComponent c, VehicleMountedComponent p) -> c.vehicleEntityIndex = p.vehicleEntityIndex
            ).add()
            .appendInherited(
                new KeyedCodec<>("SeatOffsetX", Codec.FLOAT, false),
                (VehicleMountedComponent c, Float v) -> c.seatOffsetX = v != null ? v : 0f,
                (VehicleMountedComponent c) -> c.seatOffsetX,
                (VehicleMountedComponent c, VehicleMountedComponent p) -> c.seatOffsetX = p.seatOffsetX
            ).add()
            .appendInherited(
                new KeyedCodec<>("SeatOffsetY", Codec.FLOAT, false),
                (VehicleMountedComponent c, Float v) -> c.seatOffsetY = v != null ? v : 0f,
                (VehicleMountedComponent c) -> c.seatOffsetY,
                (VehicleMountedComponent c, VehicleMountedComponent p) -> c.seatOffsetY = p.seatOffsetY
            ).add()
            .appendInherited(
                new KeyedCodec<>("SeatOffsetZ", Codec.FLOAT, false),
                (VehicleMountedComponent c, Float v) -> c.seatOffsetZ = v != null ? v : 0f,
                (VehicleMountedComponent c) -> c.seatOffsetZ,
                (VehicleMountedComponent c, VehicleMountedComponent p) -> c.seatOffsetZ = p.seatOffsetZ
            ).add()
        ).build();
    }

    /**
     * Default constructor (required for codec).
     */
    public VehicleMountedComponent() {
        this.vehicleEntityIndex = -1;
        this.seatOffsetX = 0f;
        this.seatOffsetY = 0f;
        this.seatOffsetZ = 0f;
        this.vehicleRef = null;
    }

    /**
     * Constructor with vehicle reference and seat offset.
     */
    public VehicleMountedComponent(Ref<EntityStore> vehicleRef, float seatX, float seatY, float seatZ) {
        this.vehicleRef = vehicleRef;
        this.vehicleEntityIndex = vehicleRef != null ? vehicleRef.getIndex() : -1;
        this.seatOffsetX = seatX;
        this.seatOffsetY = seatY;
        this.seatOffsetZ = seatZ;
    }

    /**
     * Copy constructor.
     */
    public VehicleMountedComponent(VehicleMountedComponent other) {
        this.vehicleEntityIndex = other.vehicleEntityIndex;
        this.seatOffsetX = other.seatOffsetX;
        this.seatOffsetY = other.seatOffsetY;
        this.seatOffsetZ = other.seatOffsetZ;
        this.vehicleRef = other.vehicleRef;
    }

    @Override
    public Component<EntityStore> clone() {
        return new VehicleMountedComponent(this);
    }

    // Getters
    public int getVehicleEntityIndex() {
        return vehicleEntityIndex;
    }

    public Ref<EntityStore> getVehicleRef() {
        return vehicleRef;
    }

    public float getSeatOffsetX() {
        return seatOffsetX;
    }

    public float getSeatOffsetY() {
        return seatOffsetY;
    }

    public float getSeatOffsetZ() {
        return seatOffsetZ;
    }

    // Setters
    public void setVehicleRef(Ref<EntityStore> ref) {
        this.vehicleRef = ref;
        this.vehicleEntityIndex = ref != null ? ref.getIndex() : -1;
    }

    public void setSeatOffset(float x, float y, float z) {
        this.seatOffsetX = x;
        this.seatOffsetY = y;
        this.seatOffsetZ = z;
    }

    /**
     * Check if the mount is still valid.
     */
    public boolean isValid() {
        return vehicleRef != null && vehicleRef.isValid();
    }

    // Component type management
    public static ComponentType<EntityStore, VehicleMountedComponent> getComponentType() {
        return componentType;
    }

    public static void setComponentType(ComponentType<EntityStore, VehicleMountedComponent> type) {
        componentType = type;
    }

    @Override
    public String toString() {
        return "VehicleMountedComponent{" +
                "vehicleIndex=" + vehicleEntityIndex +
                ", offset=(" + seatOffsetX + ", " + seatOffsetY + ", " + seatOffsetZ + ")" +
                '}';
    }
}
