package com.alexispace.hyvehicles.loader;

import com.alexispace.hyvehicles.definition.SeatDefinition;
import com.alexispace.hyvehicles.definition.VehicleDefinition;
import com.alexispace.hyvehicles.util.VehicleLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class VehicleLoader {

    private final VehicleLogger logger;
    private final Gson gson;
    private final BlockyModelParser modelParser;
    private ClassLoader resourceClassLoader;

    public VehicleLoader(VehicleLogger logger) {
        this.logger = logger;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.modelParser = new BlockyModelParser(logger);
    }

    public void setResourceClassLoader(ClassLoader classLoader) {
        this.resourceClassLoader = classLoader;
    }

    public List<VehicleDefinition> loadFromDirectory(Path directory) {
        ArrayList<VehicleDefinition> definitions = new ArrayList<>();
        if (!Files.exists(directory)) {
            this.logger.warning("Vehicle directory does not exist: " + directory);
            return definitions;
        }
        try (Stream<Path> paths = Files.walk(directory, 1)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                 .filter(Files::isRegularFile)
                 .forEach(path -> {
                     try {
                         VehicleDefinition def = this.loadFromFile(path);
                         if (def != null) {
                             definitions.add(def);
                             this.logger.info("Loaded vehicle: " + def.id + " from " + path.getFileName());
                         }
                     } catch (Exception e) {
                         this.logger.severe("Failed to load vehicle from " + path + ": " + e.getMessage());
                     }
                 });
        } catch (IOException e) {
            this.logger.severe("Failed to scan vehicle directory: " + directory + " - " + e.getMessage());
        }
        return definitions;
    }

    public VehicleDefinition loadFromFile(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return this.parseJson(json, path.toString());
    }

    public VehicleDefinition loadFromStream(InputStream stream, String sourceName) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line).append("\n");
            }
            return this.parseJson(json.toString(), sourceName);
        }
    }

    public List<VehicleDefinition> loadFromResources(String resourcePath, ClassLoader classLoader) {
        ArrayList<VehicleDefinition> definitions = new ArrayList<>();
        try {
            URL resourceUrl = classLoader.getResource(resourcePath);
            if (resourceUrl == null) {
                this.logger.warning("Resource path not found: " + resourcePath);
                return definitions;
            }
            Path resourceDir = Paths.get(resourceUrl.toURI());
            return this.loadFromDirectory(resourceDir);
        } catch (Exception e) {
            this.logger.severe("Failed to load resources from " + resourcePath + ": " + e.getMessage());
            return definitions;
        }
    }

    private VehicleDefinition parseJson(String json, String sourceName) {
        try {
            VehicleDefinition def = this.gson.fromJson(json, VehicleDefinition.class);
            if (def.id == null || def.id.isEmpty()) {
                this.logger.warning("Vehicle missing 'id' field: " + sourceName);
                return null;
            }
            if (def.type == null || def.type.isEmpty()) {
                this.logger.warning("Vehicle missing 'type' field: " + sourceName);
                return null;
            }
            if (def.blockyModelPath != null && !def.blockyModelPath.isEmpty()) {
                this.extractSeatsFromModel(def);
            }
            return def;
        } catch (Exception e) {
            this.logger.severe("Failed to parse vehicle JSON: " + sourceName + " - " + e.getMessage());
            return null;
        }
    }

    private void extractSeatsFromModel(VehicleDefinition def) {
        if (this.resourceClassLoader == null) {
            this.logger.warning("Cannot extract seats from blockymodel - no class loader set");
            return;
        }
        this.logger.info("Extracting seats from blockymodel: " + def.blockyModelPath);
        try (InputStream stream = this.resourceClassLoader.getResourceAsStream(def.blockyModelPath)) {
            if (stream == null) {
                this.logger.warning("Blockymodel file not found: " + def.blockyModelPath);
                return;
            }
            List<SeatDefinition> extractedSeats = this.modelParser.extractSeats(stream, def.blockyModelPath, def.blockyModelScale, def.blockyModelScaleY);
            if (!extractedSeats.isEmpty()) {
                for (SeatDefinition seat : extractedSeats) {
                    seat.x += def.seatOffsetX;
                    seat.y += def.seatOffsetY;
                    seat.z += def.seatOffsetZ;
                }
                def.seats = extractedSeats;
                this.logger.info("Replaced seats with " + extractedSeats.size() + " auto-extracted seats from blockymodel (offsets: " + def.seatOffsetX + ", " + def.seatOffsetY + ", " + def.seatOffsetZ + ")");
            } else {
                this.logger.warning("No seats found in blockymodel: " + def.blockyModelPath);
            }
        } catch (Exception e) {
            this.logger.warning("Failed to extract seats from blockymodel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String toJson(VehicleDefinition definition) {
        return this.gson.toJson(definition);
    }
}
