package com.alexispace.hyvehicles.definition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VehicleDefinition {
    public int schemaVersion = 1;
    public String id;
    public String name;
    public String description = "";
    public String type;
    public float maxSpeed = 10.0f;
    public float acceleration = 2.0f;
    public float turnRate = 45.0f;
    public float friction = 0.98f;
    public float buoyancy = 1.0f;
    public float waterDrag = 0.95f;
    public float wheelFriction = 0.8f;
    public float suspensionStrength = 1.0f;
    public List<SeatDefinition> seats = new ArrayList<>();
    public String blockyModelPath;
    public float blockyModelScale = 16.0f;
    public float blockyModelScaleY = 0.0f;
    public float seatOffsetX = 0.0f;
    public float seatOffsetY = 0.0f;
    public float seatOffsetZ = 0.0f;
    public String modelId;
    public float modelScale = 1.0f;
    public float modelYOffset = 0.0f;
    @Deprecated
    public String model;
    public String texture;
    public float collisionWidth = 1.5f;
    public float collisionHeight = 1.0f;
    public float collisionLength = 2.5f;
    public String dropItemId;
    public int dropQuantity = 1;
    public Map<String, Object> extra = new HashMap<>();

    public boolean hasDriverSeat() {
        return this.seats.stream().anyMatch(s -> s.isDriver);
    }

    public SeatDefinition getDriverSeat() {
        return this.seats.stream().filter(s -> s.isDriver).findFirst().orElse(null);
    }

    public int getMaxPassengers() {
        return this.seats.size();
    }

    @Override
    public String toString() {
        return "VehicleDefinition{id='" + this.id + "', name='" + this.name + "', type='" + this.type + "', modelId='" + this.modelId + "', maxSpeed=" + this.maxSpeed + ", seats=" + this.seats.size() + "}";
    }
}
