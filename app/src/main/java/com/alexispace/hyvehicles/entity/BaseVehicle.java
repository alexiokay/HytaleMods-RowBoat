package com.alexispace.hyvehicles.entity;

import com.alexispace.hyvehicles.definition.SeatDefinition;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.util.Vec3;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class BaseVehicle {

    protected final UUID instanceId;
    protected final VehicleDefinition definition;
    protected final Vec3 position;
    protected final Vec3 prevPosition;
    protected float rotationYaw;
    protected float prevRotationYaw;
    protected final Vec3 velocity;
    protected float angularVelocity;
    protected final List<PassengerInfo> passengers;
    protected PassengerInfo driver;
    protected VehicleInput currentInput = new VehicleInput();
    protected boolean destroyed = false;
    protected long ticksExisted = 0L;
    protected CollisionChecker collisionChecker = CollisionChecker.NONE;
    protected Ref<EntityStore> entityRef;
    protected int networkId = -1;
    protected World world;
    protected boolean isAnimationPlaying = false;
    protected float actualSpeed = 0.0f;
    protected boolean isMovingForward = false;
    protected boolean isTurning = false;
    protected static final float MIN_ANIMATION_SPEED = 1.0E-4f;
    protected int ticksSinceDismount = -1;

    public BaseVehicle(VehicleDefinition definition, Vec3 position, float yaw) {
        this.instanceId = UUID.randomUUID();
        this.definition = definition;
        this.position = new Vec3(position);
        this.prevPosition = new Vec3(position);
        this.rotationYaw = yaw;
        this.prevRotationYaw = yaw;
        this.velocity = new Vec3(0.0f, 0.0f, 0.0f);
        this.angularVelocity = 0.0f;
        this.passengers = new ArrayList<>();
        for (int i = 0; i < definition.seats.size(); i++) {
            this.passengers.add(null);
        }
    }

    public void tick(float deltaTime) {
        if (this.destroyed) return;
        ++this.ticksExisted;
        if (this.ticksSinceDismount >= 0) {
            ++this.ticksSinceDismount;
        }
        this.prevPosition.set(this.position);
        this.prevRotationYaw = this.rotationYaw;
        if (this.driver != null) {
            this.processDriverInput(deltaTime);
        }
        this.updatePhysics(deltaTime);
        this.applyVelocity(deltaTime);
        this.rotationYaw += this.angularVelocity * deltaTime;
        this.rotationYaw = this.normalizeAngle(this.rotationYaw);
        this.updateMovementTracking();
    }

    protected void updateMovementTracking() {
        float deltaX = this.position.x - this.prevPosition.x;
        float deltaZ = this.position.z - this.prevPosition.z;
        this.actualSpeed = (float) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (this.actualSpeed > MIN_ANIMATION_SPEED) {
            double radians = Math.toRadians(this.rotationYaw);
            float forwardX = (float) (-Math.sin(radians));
            float forwardZ = (float) Math.cos(radians);
            float dotProduct = deltaX * forwardX + deltaZ * forwardZ;
            this.isMovingForward = dotProduct > 0.0f;
        } else {
            this.isMovingForward = false;
        }
        this.isTurning = Math.abs(this.angularVelocity) > 0.1f;
        this.updateRowingAnimation();
    }

    protected void updateRowingAnimation() {
    }

    protected abstract void updatePhysics(float deltaTime);

    protected void processDriverInput(float deltaTime) {
        if (this.currentInput.brake) {
            float brakeFactor = 1.0f - 0.5f * deltaTime * 20.0f;
            if (brakeFactor < 0.5f) brakeFactor = 0.5f;
            this.velocity.x *= brakeFactor;
            this.velocity.z *= brakeFactor;
            this.angularVelocity *= brakeFactor;
            return;
        }
        if (this.currentInput.forward > 0.0f) {
            float accel = this.definition.acceleration * this.currentInput.forward * deltaTime;
            this.accelerateForward(accel);
        } else if (this.currentInput.backward > 0.0f) {
            float accel = this.definition.acceleration * this.currentInput.backward * deltaTime * 0.5f;
            this.accelerateForward(-accel);
        }
        if (this.currentInput.turnLeft > 0.0f) {
            this.angularVelocity += this.definition.turnRate * this.currentInput.turnLeft * deltaTime;
        }
        if (this.currentInput.turnRight > 0.0f) {
            this.angularVelocity -= this.definition.turnRate * this.currentInput.turnRight * deltaTime;
        }
        this.angularVelocity *= 0.9f;
    }

    protected void accelerateForward(float amount) {
        float rad = (float) Math.toRadians(this.rotationYaw);
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);
        this.velocity.x += sin * amount;
        this.velocity.z += cos * amount;
        float speed = this.getHorizontalSpeed();
        if (speed > this.definition.maxSpeed) {
            float scale = this.definition.maxSpeed / speed;
            this.velocity.x *= scale;
            this.velocity.z *= scale;
        }
    }

    protected void applyVelocity(float deltaTime) {
        float width = this.definition.collisionWidth;
        float height = this.definition.collisionHeight;

        float newX = this.position.x + this.velocity.x * deltaTime;
        if (!this.collisionChecker.checkCollision(newX, this.position.y, this.position.z, width, height)) {
            this.position.x = newX;
        } else {
            this.velocity.x = 0.0f;
        }

        float newY = this.position.y + this.velocity.y * deltaTime;
        if (!this.collisionChecker.checkCollision(this.position.x, newY, this.position.z, width, height)) {
            this.position.y = newY;
        } else {
            this.velocity.y = 0.0f;
        }

        float newZ = this.position.z + this.velocity.z * deltaTime;
        if (!this.collisionChecker.checkCollision(this.position.x, this.position.y, newZ, width, height)) {
            this.position.z = newZ;
        } else {
            this.velocity.z = 0.0f;
        }
    }

    public void setCollisionChecker(CollisionChecker checker) {
        this.collisionChecker = checker != null ? checker : CollisionChecker.NONE;
    }

    public void setEntityRef(Ref<EntityStore> ref) {
        this.entityRef = ref;
    }

    public Ref<EntityStore> getEntityRef() {
        return this.entityRef;
    }

    public void setNetworkId(int id) {
        this.networkId = id;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public boolean isEntityAlive() {
        return this.entityRef != null && this.entityRef.isValid();
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public World getWorld() {
        return this.world;
    }

    public boolean mount(UUID passengerId, int seatIndex) {
        if (this.destroyed) return false;
        if (seatIndex < 0 || seatIndex >= this.passengers.size()) return false;
        if (this.passengers.get(seatIndex) != null) return false;

        SeatDefinition seatDef = this.definition.seats.get(seatIndex);
        PassengerInfo passenger = new PassengerInfo(passengerId, seatIndex, seatDef);
        this.passengers.set(seatIndex, passenger);

        if (seatDef.isDriver) {
            this.driver = passenger;
            System.out.println("[MOUNT] Set driver for vehicle " + this.definition.id
                + " - seat " + seatIndex + ", isDriver=" + seatDef.isDriver
                + ", hasDriver now=" + (this.driver != null));
        } else {
            System.out.println("[MOUNT] Mounted passenger (NOT driver) for vehicle " + this.definition.id
                + " - seat " + seatIndex + ", isDriver=" + seatDef.isDriver);
        }
        this.onMount(passenger);
        return true;
    }

    public boolean dismount(UUID passengerId) {
        for (int i = 0; i < this.passengers.size(); i++) {
            PassengerInfo passenger = this.passengers.get(i);
            if (passenger == null || !passenger.passengerId.equals(passengerId)) continue;
            this.passengers.set(i, null);
            boolean wasDriver = this.driver != null && this.driver.passengerId.equals(passengerId);
            if (wasDriver) {
                this.driver = null;
                this.currentInput = new VehicleInput();
                this.velocity.x = 0.0f;
                this.velocity.y = 0.0f;
                this.velocity.z = 0.0f;
                this.angularVelocity = 0.0f;
                this.ticksSinceDismount = 0;
                System.out.println("[DISMOUNT] Cleared driver for vehicle " + this.definition.id
                    + " - seat " + i + ", hasDriver now=" + (this.driver != null)
                    + ", velocity zeroed, will log position for next 20 ticks");
            } else {
                System.out.println("[DISMOUNT] Dismounted passenger (NOT driver) for vehicle "
                    + this.definition.id + " - seat " + i);
            }
            this.onDismount(passenger);
            return true;
        }
        System.out.println("[DISMOUNT] WARNING: Could not find passenger " + passengerId
            + " in vehicle " + this.definition.id);
        return false;
    }

    protected void onMount(PassengerInfo passenger) {
    }

    protected void onDismount(PassengerInfo passenger) {
    }

    public void setDriverInput(VehicleInput input) {
        this.currentInput = input;
    }

    public void destroy() {
        if (this.destroyed) return;
        this.destroyed = true;
        for (int i = this.passengers.size() - 1; i >= 0; i--) {
            PassengerInfo passenger = this.passengers.get(i);
            if (passenger != null) {
                this.dismount(passenger.passengerId);
            }
        }
    }

    public UUID getInstanceId() { return this.instanceId; }
    public VehicleDefinition getDefinition() { return this.definition; }
    public Vec3 getPosition() { return new Vec3(this.position); }

    public void setPosition(float x, float y, float z) {
        this.position.x = x;
        this.position.y = y;
        this.position.z = z;
    }

    public float[] getPositionArray() { return this.position.toArray(); }
    public Vec3 getPrevPosition() { return new Vec3(this.prevPosition); }
    public float getRotationYaw() { return this.rotationYaw; }
    public void setRotationYaw(float yaw) { this.rotationYaw = yaw; }
    public Vec3 getVelocity() { return new Vec3(this.velocity); }
    public boolean isDestroyed() { return this.destroyed; }
    public long getTicksExisted() { return this.ticksExisted; }

    public float getHorizontalSpeed() {
        return (float) Math.sqrt(this.velocity.x * this.velocity.x + this.velocity.z * this.velocity.z);
    }

    public int getPassengerCount() {
        return (int) this.passengers.stream().filter(p -> p != null).count();
    }

    public boolean hasDriver() { return this.driver != null; }

    public int getTicksSinceDismount() { return this.ticksSinceDismount; }

    public boolean shouldAnimate() {
        return this.hasDriver() && this.getHorizontalSpeed() > 0.1f;
    }

    public boolean isAnimationPlaying() { return this.isAnimationPlaying; }
    public void setAnimationPlaying(boolean playing) { this.isAnimationPlaying = playing; }

    protected float normalizeAngle(float angle) {
        angle %= 360.0f;
        if (angle < 0.0f) angle += 360.0f;
        return angle;
    }

    public static class VehicleInput {
        public float forward = 0.0f;
        public float backward = 0.0f;
        public float turnLeft = 0.0f;
        public float turnRight = 0.0f;
        public boolean jump = false;
        public boolean brake = false;
    }

    public static class PassengerInfo {
        public final UUID passengerId;
        public final int seatIndex;
        public final SeatDefinition seatDef;

        public PassengerInfo(UUID passengerId, int seatIndex, SeatDefinition seatDef) {
            this.passengerId = passengerId;
            this.seatIndex = seatIndex;
            this.seatDef = seatDef;
        }
    }
}
