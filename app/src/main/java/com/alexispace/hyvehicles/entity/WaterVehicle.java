package com.alexispace.hyvehicles.entity;

import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.util.Vec3;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;

public class WaterVehicle extends BaseVehicle {
    private static final float GRAVITY = 20.0f;
    private static final float DEFAULT_WATER_LEVEL = 64.0f;
    private final float spawnY;
    private static final float WATER_OFFSET = 0.2f;
    private boolean inWater = false;
    private float waterLevel = 64.0f;
    private float targetY;
    private String currentAnimationDirection = null;

    public WaterVehicle(VehicleDefinition definition, Vec3 position, float yaw) {
        super(definition, position, yaw);
        this.spawnY = position.y;
        this.targetY = position.y;
    }

    @Override
    protected void updatePhysics(float deltaTime) {
        this.detectWater();
        if (this.inWater) {
            this.updateWaterPhysics(deltaTime);
        } else {
            this.updateAirPhysics(deltaTime);
        }
        this.velocity.x *= this.definition.friction;
        this.velocity.z *= this.definition.friction;
    }

    private void detectWater() {
        this.waterLevel = this.getWaterLevelAt(this.position.x, this.position.z);
        this.inWater = this.position.y <= this.waterLevel + 0.5f;
    }

    private float getWaterLevelAt(float x, float z) {
        return this.spawnY;
    }

    private void updateWaterPhysics(float deltaTime) {
        this.targetY = this.spawnY - WATER_OFFSET;
        float distanceToTarget = this.targetY - this.position.y;
        if (Math.abs(distanceToTarget) > 0.01f) {
            float springForce = distanceToTarget * 15.0f;
            this.velocity.y += springForce * deltaTime;
        }
        this.velocity.y *= 0.8f;
        this.velocity.x *= this.definition.waterDrag;
        this.velocity.z *= this.definition.waterDrag;
        this.velocity.y = Math.max(-2.0f, Math.min(2.0f, this.velocity.y));
    }

    private void updateAirPhysics(float deltaTime) {
        this.velocity.y -= GRAVITY * deltaTime;
        this.velocity.y = Math.max(-50.0f, this.velocity.y);
    }

    @Override
    protected void processDriverInput(float deltaTime) {
        if (this.inWater) {
            super.processDriverInput(deltaTime);
        } else {
            this.angularVelocity *= 0.95f;
        }
    }

    @Override
    protected void updateRowingAnimation() {
        if (this.ticksExisted % 20L == 0L && this.driver != null) {
            System.out.println("[AnimDebug] speed=" + String.format("%.4f", this.actualSpeed)
                + " inWater=" + this.inWater
                + " forward=" + this.isMovingForward
                + " threshold=1.0E-4 shouldAnimate=" + (this.actualSpeed > MIN_ANIMATION_SPEED && this.inWater));
        }
        if (this.actualSpeed > MIN_ANIMATION_SPEED && this.inWater) {
            if (this.isMovingForward) {
                this.playRowingAnimation("forward", this.actualSpeed);
            } else {
                this.playRowingAnimation("backward", this.actualSpeed);
            }
        } else {
            this.stopRowingAnimation();
        }
    }

    private void playRowingAnimation(String direction, float speed) {
        float animationSpeed = Math.min(speed * 20.0f, 2.0f);
        if (!this.isAnimationPlaying || !direction.equals(this.currentAnimationDirection)) {
            System.out.println("[RowingAnim] START: " + direction
                + " | speed=" + String.format("%.3f", speed)
                + " | animSpeed=" + String.format("%.2f", animationSpeed) + "x");
            this.isAnimationPlaying = true;
            this.currentAnimationDirection = direction;
            if (this.entityRef != null && this.world != null) {
                try {
                    Store store = this.world.getEntityStore().getStore();
                    System.out.println("[RowingAnim] DEBUG: entityRef=" + this.entityRef
                        + ", isValid=" + this.entityRef.isValid()
                        + ", driver=" + (this.driver != null ? this.driver.passengerId : "null"));
                    if (this.entityRef.isValid()) {
                        try {
                            NetworkId netId = (NetworkId) store.getComponent(this.entityRef, NetworkId.getComponentType());
                            System.out.println("[RowingAnim] DEBUG: Vehicle NetworkId=" + (netId != null ? netId.getId() : "null"));
                        } catch (Exception ex) {
                            System.out.println("[RowingAnim] DEBUG: Could not get NetworkId: " + ex.getMessage());
                        }
                    }
                    AnimationUtils.playAnimation(this.entityRef, AnimationSlot.Movement, "rowing", store);
                    System.out.println("[RowingAnim] Triggered Hytale 'rowing' animation (Action slot)");
                } catch (IllegalStateException e) {
                    // Silently ignore threading errors - animation is cosmetic
                    // This happens when vehicle entity is in a different WorldThread
                } catch (Exception e) {
                    System.out.println("[RowingAnim] ERROR triggering animation: " + e.getMessage());
                }
            } else {
                System.out.println("[RowingAnim] SKIP: entityRef=" + this.entityRef + ", world=" + this.world);
            }
        }
    }

    private void stopRowingAnimation() {
        if (this.isAnimationPlaying) {
            System.out.println("[RowingAnim] STOP: Idle (was: " + this.currentAnimationDirection + ")");
            this.isAnimationPlaying = false;
            this.currentAnimationDirection = null;
            if (this.entityRef != null && this.world != null) {
                try {
                    Store store = this.world.getEntityStore().getStore();
                    AnimationUtils.stopAnimation(this.entityRef, AnimationSlot.Movement, store);
                    System.out.println("[RowingAnim] Stopped Hytale animation (Action slot)");
                } catch (IllegalStateException e) {
                    // Silently ignore threading errors - animation is cosmetic
                } catch (Exception e) {
                    System.out.println("[RowingAnim] ERROR stopping animation: " + e.getMessage());
                }
            }
        }
    }

    @Override
    protected void onDismount(BaseVehicle.PassengerInfo passenger) {
        System.out.println("[WaterVehicle] onDismount called - forcing animation stop");
        this.stopRowingAnimation();
        this.isAnimationPlaying = false;
        this.currentAnimationDirection = null;
        this.actualSpeed = 0.0f;
        System.out.println("[WaterVehicle] Animation state cleared on dismount");
    }

    public boolean isInWater() {
        return this.inWater;
    }

    public float getWaterLevel() {
        return this.waterLevel;
    }
}
