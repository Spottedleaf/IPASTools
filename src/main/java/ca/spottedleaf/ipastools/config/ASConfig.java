package ca.spottedleaf.ipastools.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class ASConfig {

    public final double raidThreshold;

    public ASConfig(final FileConfiguration config) {
        this.raidThreshold = config.getDouble("raid-threshold");
    }
}
