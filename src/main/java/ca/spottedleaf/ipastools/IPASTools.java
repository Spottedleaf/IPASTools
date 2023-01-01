package ca.spottedleaf.ipastools;

import ca.spottedleaf.ipastools.astools.ASBans;
import ca.spottedleaf.ipastools.astools.ASLookup;
import ca.spottedleaf.ipastools.astools.ASPlayerState;
import ca.spottedleaf.ipastools.command.ASCommand;
import ca.spottedleaf.ipastools.config.ASConfig;
import ca.spottedleaf.ipastools.listener.PlayerConnectionListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IPASTools extends JavaPlugin {

    private static final String CONFIG_FILE = "config.yml";

    private static IPASTools instance;

    private static ThreadFactory backgroundExecutor(final String name) {
        return new ThreadFactory() {
            final AtomicLong count = new AtomicLong();

            @Override
            public Thread newThread(final Runnable run) {
                final Thread ret = new Thread(run);
                ret.setName(name + " #" + this.count.getAndIncrement());
                ret.setDaemon(true);
                ret.setPriority(Thread.NORM_PRIORITY - 2);
                ret.setUncaughtExceptionHandler((final Thread t, final Throwable e) -> {
                    final Logger logger = IPASTools.instance == null ? null : IPASTools.instance.getLogger();
                    if (logger != null) {
                        logger.log(Level.SEVERE, "Uncaught exception in thread " + t.getName(), e);
                    } else {
                        synchronized (System.out) {
                            System.out.println("Uncaught exception in thread " + t.getName());
                            e.printStackTrace(System.out);
                        }
                    }
                });

                return ret;
            }
        };
    }

    public static final ExecutorService PROFILE_LOOKUP_EXECUTOR = Executors.newSingleThreadExecutor(backgroundExecutor("IPASTools Profile lookup executor"));
    public static final ExecutorService GENERIC_IO_EXECUTOR = Executors.newSingleThreadExecutor(backgroundExecutor("IPASTools Generic IO executor"));

    private final ExecutorService cacheUpdater = Executors.newSingleThreadExecutor(backgroundExecutor("IPASTools I/O executor"));
    private ASLookup lookup;
    // only care about the opaque property of volatile here
    private volatile ASConfig config;
    private ASBans bans;

    public IPASTools() {
        instance = this;
    }

    public static IPASTools getInstance() {
        return instance;
    }

    public ASLookup getLookup() {
        return this.lookup;
    }

    public ASConfig getASConfig() {
        return this.config;
    }

    public ASBans getBans() {
        return this.bans;
    }

    private File getConfigFile(final String name) {
        return new File(this.getDataFolder(), name);
    }

    private File getOrCreateFile(final String name) throws IOException {
        final File configFile = this.getConfigFile(name);

        if (!configFile.exists()) {
            try (final InputStream in = this.getClass().getResourceAsStream("/".concat(name))) {
                configFile.getParentFile().mkdirs();
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                }
            }
        }

        return configFile;
    }

    public boolean reloadASConfig() {
        try {
            final File configFile = this.getOrCreateFile(CONFIG_FILE);

            final YamlConfiguration config = new YamlConfiguration();

            config.load(configFile);

            this.config = new ASConfig(config);
        } catch (final Exception ex) {
            this.getLogger().log(Level.WARNING, "Failed to reload config", ex);
            return false;
        }

        return true;
    }

    @Override
    public void onLoad() {
        this.reloadASConfig();

        this.getLogger().info("Loading AS lookup from cache, or from source if cache does not exist");
        this.lookup = new ASLookup(new File(this.getDataFolder(), "aslookup.cache"), this.cacheUpdater);
        this.getLogger().info("Finished setting up AS lookup");
        this.getLogger().info("Loading bans from disk");
        this.bans = new ASBans(new File(this.getDataFolder(), "bans.json"));
        this.getLogger().info("Loaded bans from disk");
    }

    @Override
    public void onEnable() {
        final PluginCommand asCMD = this.getCommand("as");
        if (asCMD != null) {
            asCMD.setExecutor(new ASCommand(this));
        } else {
            this.getLogger().warning("Unable to register 'as' command");
        }

        final PluginManager manager = Bukkit.getPluginManager();

        manager.registerEvents(new PlayerConnectionListener(this), this);
    }

    private void shutdownExecutor(final ExecutorService service, final String name) {
        this.getLogger().info("Shutting down " + name);
        service.shutdown();
        try {
            service.awaitTermination(1L, TimeUnit.MINUTES);
        } catch (final InterruptedException ex) {
            this.getLogger().warning("Interrupted while waiting for " + name + " to shut down");
        }
        if (!service.isTerminated()) {
            this.getLogger().warning(name + " is not terminated after waiting for shutdown, skipping");
        } else {
            this.getLogger().info(name + " terminated normally");
        }
    }

    @Override
    public void onDisable() {
        this.getLogger().info("Saving user data...");
        ASPlayerState.saveAllUserData();
        this.getLogger().info("Saved user data");

        this.shutdownExecutor(PROFILE_LOOKUP_EXECUTOR, "Profile lookup executor");
        this.shutdownExecutor(GENERIC_IO_EXECUTOR, "Generic I/O executor");
        this.shutdownExecutor(this.cacheUpdater, "ASLookup I/O executor");

        if (this.bans != null) {
            this.getLogger().info("Saving AS ban list");
            this.bans.saveToFile();
            this.getLogger().info("Saved AS ban list");
        }
    }
}
