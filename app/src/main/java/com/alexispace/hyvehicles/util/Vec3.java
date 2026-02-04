package com.alexispace.hyvehicles.util;

/**
 * Simple mutable 3D vector for internal physics calculations.
 * Avoids dependency on JOML since Hytale's classloader doesn't see bundled libs.
 */
public class Vec3 {
    public float x;
    public float y;
    public float z;

    public Vec3() {
        this(0, 0, 0);
    }

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vec3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    public void set(Vec3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float[] toArray() {
        return new float[] { x, y, z };
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public float lengthXZ() {
        return (float) Math.sqrt(x * x + z * z);
    }

    @Override
    public String toString() {
        return String.format("Vec3(%.2f, %.2f, %.2f)", x, y, z);
    }
}
