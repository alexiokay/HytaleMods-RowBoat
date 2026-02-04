package com.alexispace.hyvehicles.config;

import com.hypixel.hytale.builtin.deployables.config.DeployableConfig;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Simple DeployableConfig subclass for vehicle preview ghosts.
 * Only uses base fields (Model, ModelPreview, etc.) without adding any extra fields.
 */
public class VehicleDeployableConfig extends DeployableConfig {

    public static final BuilderCodec<VehicleDeployableConfig> CODEC = BuilderCodec.builder(
        VehicleDeployableConfig.class,
        VehicleDeployableConfig::new,
        DeployableConfig.BASE_CODEC
    ).build();

    public VehicleDeployableConfig() {
    }

    @Override
    public String toString() {
        return "VehicleDeployableConfig{id=" + getId() + "}";
    }
}
