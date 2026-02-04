package com.alexispace.hyvehicles.loader;

import com.alexispace.hyvehicles.definition.SeatDefinition;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses .blockymodel files to extract seat positions.
 *
 * <p>Looks for nodes named "seat_*" (e.g., seat_driver, seat_0, seat_passender_1)
 * and extracts their positions, converting from Blockbench units to game units.</p>
 *
 * <h2>Coordinate Mapping</h2>
 * <p>Blockbench uses a different coordinate system than our mounting system:</p>
 * <ul>
 *   <li>Blockbench X (boat length) → Game Z (forward/back)</li>
 *   <li>Blockbench Y (up/down) → Game Y (up/down)</li>
 *   <li>Blockbench Z (left/right) → Game X (left/right)</li>
 * </ul>
 *
 * <h2>Scale</h2>
 * <p>Blockbench typically uses 16 units = 1 block. This parser divides by a
 * configurable scale factor (default 16) to convert to game units.</p>
 *
 * @author alexispace
 * @since 1.0
 */
public class BlockyModelParser {

    private final VehicleLogger logger;

    /** Default Blockbench units per game block */
    private static final float DEFAULT_SCALE = 16.0f;

    public BlockyModelParser(VehicleLogger logger) {
        this.logger = logger;
    }

    /**
     * Parse a blockymodel file and extract seat definitions.
     *
     * @param stream Input stream of the .blockymodel file
     * @param resourcePath Path for logging purposes
     * @param scaleOverride Optional scale override (0 or negative to use default 16)
     * @param scaleYOverride Optional separate scale for Y axis (0 to use scaleOverride)
     * @return List of SeatDefinition extracted from the model, sorted by seat number
     */
    public List<SeatDefinition> extractSeats(InputStream stream, String resourcePath,
                                              float scaleOverride, float scaleYOverride) {
        List<SeatDefinition> seats = new ArrayList<>();

        if (stream == null) {
            logger.warning("Cannot parse blockymodel - stream is null: " + resourcePath);
            return seats;
        }

        try {
            float scale = scaleOverride > 0 ? scaleOverride : DEFAULT_SCALE;
            float scaleY = scaleYOverride > 0 ? scaleYOverride : scale;

            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
            ).getAsJsonObject();

            if (!root.has("nodes")) {
                logger.warning("Blockymodel has no 'nodes' array: " + resourcePath);
                return seats;
            }

            JsonArray nodes = root.getAsJsonArray("nodes");

            // Find the root model node and its origin
            float rootX = 0, rootY = 0, rootZ = 0;
            JsonArray childNodes = null;

            for (JsonElement nodeElement : nodes) {
                JsonObject node = nodeElement.getAsJsonObject();
                if (node.has("children")) {
                    // This is likely the root model node
                    if (node.has("position")) {
                        JsonObject pos = node.getAsJsonObject("position");
                        rootX = pos.get("x").getAsFloat();
                        rootY = pos.get("y").getAsFloat();
                        rootZ = pos.get("z").getAsFloat();
                    }
                    childNodes = node.getAsJsonArray("children");
                    break;
                }
            }

            if (childNodes == null) {
                logger.warning("Blockymodel has no child nodes: " + resourcePath);
                return seats;
            }

            // Search for seat nodes in children
            findSeatNodes(childNodes, seats, scale, scaleY, rootX, rootY, rootZ);

            // Sort seats: driver first, then by number
            seats.sort((a, b) -> {
                if (a.isDriver && !b.isDriver) return -1;
                if (!a.isDriver && b.isDriver) return 1;
                return 0; // Keep original order for same type
            });

            logger.info("Extracted " + seats.size() + " seats from blockymodel: " + resourcePath);
            for (SeatDefinition seat : seats) {
                logger.info("  Seat: x=" + seat.x + ", y=" + seat.y + ", z=" + seat.z +
                           ", isDriver=" + seat.isDriver);
            }

            return seats;

        } catch (Exception e) {
            logger.warning("Failed to parse blockymodel " + resourcePath + ": " + e.getMessage());
            e.printStackTrace();
            return seats;
        }
    }

    /**
     * Recursively search for seat nodes in the node tree.
     */
    private void findSeatNodes(JsonArray nodes, List<SeatDefinition> seats,
                               float scale, float scaleY, float rootX, float rootY, float rootZ) {
        for (JsonElement element : nodes) {
            JsonObject node = element.getAsJsonObject();

            String name = node.has("name") ? node.get("name").getAsString() : "";

            // Check if this is a seat node
            if (name.toLowerCase().startsWith("seat_")) {
                SeatDefinition seat = extractSeatFromNode(node, name, scale, scaleY, rootX, rootY, rootZ);
                if (seat != null) {
                    seats.add(seat);
                }
            }

            // Recurse into children
            if (node.has("children")) {
                findSeatNodes(node.getAsJsonArray("children"), seats, scale, scaleY, rootX, rootY, rootZ);
            }
        }
    }

    /**
     * Extract a SeatDefinition from a seat node.
     */
    private SeatDefinition extractSeatFromNode(JsonObject node, String name,
                                                float scale, float scaleY, float rootX, float rootY, float rootZ) {
        if (!node.has("position")) {
            logger.warning("Seat node '" + name + "' has no position");
            return null;
        }

        JsonObject pos = node.getAsJsonObject("position");
        float bbX = pos.get("x").getAsFloat();
        float bbY = pos.get("y").getAsFloat();
        float bbZ = pos.get("z").getAsFloat();

        // Convert from Blockbench coordinates to game coordinates
        // Blockbench X (length) → Game Z (forward/back, but negated for correct direction)
        // Blockbench Y (up) → Game Y (up)
        // Blockbench Z (width) → Game X (left/right)

        // The position is relative to the model origin, convert to game units
        float gameX = bbZ / scale;   // Blockbench Z → Game X
        float gameY = bbY / scaleY;  // Blockbench Y → Game Y (uses separate Y scale)
        float gameZ = -bbX / scale;  // Blockbench X → Game Z (negated)

        // Determine if this is the driver seat
        boolean isDriver = name.toLowerCase().contains("driver");
        boolean canControl = isDriver; // Only driver can control by default

        SeatDefinition seat = new SeatDefinition();
        seat.x = gameX;
        seat.y = gameY;
        seat.z = gameZ;
        seat.isDriver = isDriver;
        seat.canControl = canControl;

        logger.info("Parsed seat '" + name + "': BB(" + bbX + "," + bbY + "," + bbZ +
                   ") -> Game(" + gameX + "," + gameY + "," + gameZ + ") [scale=" + scale + ", scaleY=" + scaleY + "]");

        return seat;
    }

    /**
     * Parse seats with default scale (16 units per block).
     */
    public List<SeatDefinition> extractSeats(InputStream stream, String resourcePath) {
        return extractSeats(stream, resourcePath, 0, 0);
    }
}
