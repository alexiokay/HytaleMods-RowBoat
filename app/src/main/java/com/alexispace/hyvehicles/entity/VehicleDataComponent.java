package com.alexispace.hyvehicles.entity;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashSet;
import java.util.Set;

public class VehicleDataComponent implements Component<EntityStore> {

    private static ComponentType<EntityStore, VehicleDataComponent> componentType;

    private String definitionId;
    private String dropItemId;
    private int dropQuantity;
    private float posX;
    private float posY;
    private float posZ;
    private float rotationYaw;

    private transient boolean braking;
    private transient boolean boosting;

    private transient Set<Integer> occupiedSeats = new HashSet<>();

    public static final BuilderCodec<VehicleDataComponent> CODEC;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BuilderCodec<VehicleDataComponent> createCodec() {
        // CRITICAL: DO NOT persist position fields in ECS!
        // Hytale's persistence system restores entities from this codec,
        // which causes teleportation to spawn position on dismount.
        // Position is managed by JSON persistence only!
        BuilderCodec.Builder builder = BuilderCodec.builder(VehicleDataComponent.class, VehicleDataComponent::new);
        return ((BuilderCodec.Builder<VehicleDataComponent>) builder.appendInherited(
            new KeyedCodec("DefinitionId", Codec.STRING, false),
            (c, v) -> { ((VehicleDataComponent) c).definitionId = v != null ? (String) v : ""; },
            c -> ((VehicleDataComponent) c).definitionId,
            (c, p) -> { ((VehicleDataComponent) c).definitionId = ((VehicleDataComponent) p).definitionId; }
        ).add().appendInherited(
            new KeyedCodec("DropItemId", Codec.STRING, false),
            (c, v) -> { ((VehicleDataComponent) c).dropItemId = v != null ? (String) v : ""; },
            c -> ((VehicleDataComponent) c).dropItemId,
            (c, p) -> { ((VehicleDataComponent) c).dropItemId = ((VehicleDataComponent) p).dropItemId; }
        ).add().appendInherited(
            new KeyedCodec("DropQuantity", Codec.INTEGER, false),
            (c, v) -> { ((VehicleDataComponent) c).dropQuantity = v != null ? (Integer) v : 1; },
            c -> ((VehicleDataComponent) c).dropQuantity,
            (c, p) -> { ((VehicleDataComponent) c).dropQuantity = ((VehicleDataComponent) p).dropQuantity; }
        ).add()).build();
        // REMOVED: PosX, PosY, PosZ, RotationYaw persistence
        // These fields still exist for in-memory tracking, but are NOT saved to ECS
    }

    public VehicleDataComponent() {
        this.definitionId = "";
        this.dropItemId = "";
        this.dropQuantity = 1;
    }

    public VehicleDataComponent(String definitionId, String dropItemId, int dropQuantity) {
        this.definitionId = definitionId != null ? definitionId : "";
        this.dropItemId = dropItemId != null ? dropItemId : "";
        this.dropQuantity = dropQuantity;
    }

    public VehicleDataComponent(VehicleDataComponent other) {
        this.definitionId = other.definitionId;
        this.dropItemId = other.dropItemId;
        this.dropQuantity = other.dropQuantity;
        this.posX = other.posX;
        this.posY = other.posY;
        this.posZ = other.posZ;
        this.rotationYaw = other.rotationYaw;
    }

    @Override
    public Component<EntityStore> clone() {
        return new VehicleDataComponent(this);
    }

    public String getDefinitionId() {
        return this.definitionId;
    }

    public String getDropItemId() {
        return this.dropItemId;
    }

    public int getDropQuantity() {
        return this.dropQuantity;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public void setDropItemId(String dropItemId) {
        this.dropItemId = dropItemId;
    }

    public void setDropQuantity(int dropQuantity) {
        this.dropQuantity = dropQuantity;
    }

    public float getPosX() { return this.posX; }
    public float getPosY() { return this.posY; }
    public float getPosZ() { return this.posZ; }
    public float getRotationYaw() { return this.rotationYaw; }

    public void setPosition(float x, float y, float z, float yaw) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.rotationYaw = yaw;
    }

    public boolean hasStoredPosition() {
        return this.posX != 0f || this.posY != 0f || this.posZ != 0f;
    }

    public boolean isBraking() {
        return this.braking;
    }

    public void setBraking(boolean braking) {
        this.braking = braking;
    }

    public boolean isBoosting() {
        return this.boosting;
    }

    public void setBoosting(boolean boosting) {
        this.boosting = boosting;
    }

    public void resetControlFlags() {
        this.braking = false;
        this.boosting = false;
    }

    public int findAvailableSeat(int totalSeats) {
        for (int i = 0; i < totalSeats; i++) {
            if (!this.occupiedSeats.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    public void occupySeat(int seatIndex) {
        this.occupiedSeats.add(seatIndex);
    }

    public void vacateSeat(int seatIndex) {
        this.occupiedSeats.remove(seatIndex);
    }

    public boolean isSeatOccupied(int seatIndex) {
        return this.occupiedSeats.contains(seatIndex);
    }

    public int getOccupiedSeatCount() {
        return this.occupiedSeats.size();
    }

    public void clearAllSeats() {
        this.occupiedSeats.clear();
    }

    public static ComponentType<EntityStore, VehicleDataComponent> getComponentType() {
        return componentType;
    }

    public static void setComponentType(ComponentType<EntityStore, VehicleDataComponent> type) {
        componentType = type;
    }

    @Override
    public String toString() {
        return "VehicleDataComponent{definitionId='" + this.definitionId + "', dropItemId='" + this.dropItemId + "', dropQuantity=" + this.dropQuantity + ", braking=" + this.braking + ", boosting=" + this.boosting + "}";
    }

    static {
        CODEC = VehicleDataComponent.createCodec();
    }
}
