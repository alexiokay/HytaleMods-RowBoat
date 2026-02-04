package com.alexispace.hyvehicles.loader;

import com.alexispace.hyvehicles.definition.SeatDefinition;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads vehicle definitions from JSON files.
 *
 * <p>Scans the specified directories for .json files and parses them
 * into VehicleDefinition objects.</p>
 *
 * <p>If a vehicle definition specifies a blockyModelPath, seats will be
 * auto-extracted from the model file's seat_* nodes.</p>
 *
 * @since 1.0
 * @author alexispace
 */
public class VehicleLoader {

    private final VehicleLogger logger;
    private final Gson gson;
    private final BlockyModelParser modelParser;
    private ClassLoader resourceClassLoader;

    public VehicleLoader(VehicleLogger logger) {
        this.logger = logger;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        this.modelParser = new BlockyModelParser(logger);
    }

    /**
     * Set the class loader to use for loading resources (blockymodel files).
     * Should be called before loading vehicles.
     */
    public void setResourceClassLoader(ClassLoader classLoader) {
        this.resourceClassLoader = classLoader;
    }
    
    /**
     * Load all vehicle definitions from a directory.
     * 
     * @param directory Path to directory containing .json files
     * @return List of loaded definitions
     */
    public List<VehicleDefinition> loadFromDirectory(Path directory) {
        List<VehicleDefinition> definitions = new ArrayList<>();
        
        if (!Files.exists(directory)) {
            logger.warning("Vehicle directory does not exist: " + directory);
            return definitions;
        }
        
        try (Stream<Path> paths = Files.walk(directory, 1)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                 .filter(Files::isRegularFile)
                 .forEach(path -> {
                     try {
                         VehicleDefinition def = loadFromFile(path);
                         if (def != null) {
                             definitions.add(def);
                             logger.info("Loaded vehicle: " + def.id + " from " + path.getFileName());
                         }
                     } catch (Exception e) {
                         logger.severe("Failed to load vehicle from " + path + ": " + e.getMessage());
                     }
                 });
        } catch (IOException e) {
            logger.severe("Failed to scan vehicle directory: " + directory + " - " + e.getMessage());
        }
        
        return definitions;
    }
    
    /**
     * Load a vehicle definition from a file.
     * 
     * @param path Path to the JSON file
     * @return The parsed definition, or null if failed
     */
    public VehicleDefinition loadFromFile(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return parseJson(json, path.toString());
    }
    
    /**
     * Load a vehicle definition from an input stream.
     * 
     * @param stream The input stream
     * @param sourceName Name for error messages
     * @return The parsed definition
     */
    public VehicleDefinition loadFromStream(InputStream stream, String sourceName) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line).append("\n");
            }
            return parseJson(json.toString(), sourceName);
        }
    }
    
    /**
     * Load vehicle definitions from plugin resources.
     * 
     * @param resourcePath Path within resources (e.g., "vehicles/")
     * @param classLoader The plugin's class loader
     * @return List of loaded definitions
     */
    public List<VehicleDefinition> loadFromResources(String resourcePath, ClassLoader classLoader) {
        List<VehicleDefinition> definitions = new ArrayList<>();
        
        try {
            URL resourceUrl = classLoader.getResource(resourcePath);
            if (resourceUrl == null) {
                logger.warning("Resource path not found: " + resourcePath);
                return definitions;
            }
            
            // For JAR files, we need a different approach
            // This is a simplified version for file system resources
            Path resourceDir = Paths.get(resourceUrl.toURI());
            return loadFromDirectory(resourceDir);
            
        } catch (Exception e) {
            logger.severe("Failed to load resources from " + resourcePath + ": " + e.getMessage());
        }
        
        return definitions;
    }
    
    /**
     * Parse JSON string into a VehicleDefinition.
     */
    private VehicleDefinition parseJson(String json, String sourceName) {
        try {
            VehicleDefinition def = gson.fromJson(json, VehicleDefinition.class);

            // Validate required fields
            if (def.id == null || def.id.isEmpty()) {
                logger.warning("Vehicle missing 'id' field: " + sourceName);
                return null;
            }
            if (def.type == null || def.type.isEmpty()) {
                logger.warning("Vehicle missing 'type' field: " + sourceName);
                return null;
            }

            // Auto-extract seats from blockymodel if specified
            if (def.blockyModelPath != null && !def.blockyModelPath.isEmpty()) {
                extractSeatsFromModel(def);
            }

            return def;

        } catch (Exception e) {
            logger.severe("Failed to parse vehicle JSON: " + sourceName + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Extract seat positions from the blockymodel file.
     * Replaces any manually defined seats with auto-extracted ones.
     */
    private void extractSeatsFromModel(VehicleDefinition def) {
        if (resourceClassLoader == null) {
            logger.warning("Cannot extract seats from blockymodel - no class loader set");
            return;
        }

        logger.info("Extracting seats from blockymodel: " + def.blockyModelPath);

        try (InputStream stream = resourceClassLoader.getResourceAsStream(def.blockyModelPath)) {
            if (stream == null) {
                logger.warning("Blockymodel file not found: " + def.blockyModelPath);
                return;
            }

            List<SeatDefinition> extractedSeats = modelParser.extractSeats(
                stream, def.blockyModelPath, def.blockyModelScale, def.blockyModelScaleY);

            if (!extractedSeats.isEmpty()) {
                // Apply offsets to all extracted seats
                for (SeatDefinition seat : extractedSeats) {
                    seat.x += def.seatOffsetX;
                    seat.y += def.seatOffsetY;
                    seat.z += def.seatOffsetZ;
                }
                // Replace manual seats with extracted ones
                def.seats = extractedSeats;
                logger.info("Replaced seats with " + extractedSeats.size() +
                           " auto-extracted seats from blockymodel (offsets: " +
                           def.seatOffsetX + ", " + def.seatOffsetY + ", " + def.seatOffsetZ + ")");
            } else {
                logger.warning("No seats found in blockymodel: " + def.blockyModelPath);
            }

        } catch (Exception e) {
            logger.warning("Failed to extract seats from blockymodel: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Serialize a VehicleDefinition to JSON.
     * 
     * @param definition The definition to serialize
     * @return JSON string
     */
    public String toJson(VehicleDefinition definition) {
        return gson.toJson(definition);
    }
}
