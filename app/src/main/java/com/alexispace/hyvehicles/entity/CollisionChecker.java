package com.alexispace.hyvehicles.entity;

/**
 * Interface for checking block collision in the world.
 *
 * <p>This allows BaseVehicle to check for collisions without
 * directly depending on Hytale's World class.</p>
 *
 * @since 1.0
 */
@FunctionalInterface
public interface CollisionChecker {

    /**
     * Check if a position would collide with solid blocks.
     *
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     * @param width Entity width (X/Z extent)
     * @param height Entity height (Y extent)
     * @return true if there is a collision
     */
    boolean checkCollision(float x, float y, float z, float width, float height);

    /**
     * A no-op collision checker that always returns false (no collision).
     */
    CollisionChecker NONE = (x, y, z, width, height) -> false;
}
