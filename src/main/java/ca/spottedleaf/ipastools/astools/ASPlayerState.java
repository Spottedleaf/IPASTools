package ca.spottedleaf.ipastools.astools;

import ca.spottedleaf.ipastools.IPASTools;
import ca.spottedleaf.ipastools.util.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ASPlayerState {

    public final UUID userUniqueId;
    private boolean canSave = true;

    private final Object stateLock = new Object();
    private boolean dirty;

    private final List<ASLoginEntry> loginHistory = new ArrayList<>();

    ASPlayerState(final UUID userUniqueId) {
        this.userUniqueId = userUniqueId;
        // fields default to default values
    }

    ASPlayerState(final UUID userUniqueId, final JsonObject json) {
        this(userUniqueId);
        final int version = json.get("version").getAsInt();

        if (version > VERSION) {
            IPASTools.getInstance().getLogger().warning("Refusing to load user data for player " + userUniqueId.toString() + " since it was created with a newer version of the plugin");
            IPASTools.getInstance().getLogger().warning("New data for " + userUniqueId.toString() + " will not be saved");
            this.canSave = false;
            return;
        }

        final JsonArray loginHistory = json.getAsJsonArray("loginHistory");
        if (loginHistory != null) {
            for (final JsonElement element : loginHistory) {
                this.loginHistory.add(ASLoginEntry.parse(element.getAsJsonObject()));
            }
        }
    }

    // holds state lock
    private boolean needsSaving() {
        return this.dirty;
    }

    public ASLoginEntry getLastLoginEntry() {
        synchronized (this.stateLock) {
            if (this.loginHistory.isEmpty()) {
                return null;
            }
            return this.loginHistory.get(0);
        }
    }

    public void addLoginHistory(final ASLoginEntry history) {
        synchronized (this.stateLock) {
            // purge duplicates
            for (final Iterator<ASLoginEntry> iterator = this.loginHistory.iterator(); iterator.hasNext();) {
                final ASLoginEntry entry = iterator.next();
                if (entry.ASNumber == history.ASNumber && entry.ip.equals(history.ip)) {
                    iterator.remove();
                }
            }

            // add entry
            this.loginHistory.add(history);

            // sort so that order is maintained
            this.loginHistory.sort((final ASLoginEntry e1, final ASLoginEntry e2) -> {
                return e1.lastUsed.compareTo(e2.lastUsed);
            });

            this.dirty = true;
        }
    }

    // just in case
    // increment when breaking changes are made to the format (and then correctly convert older versions)
    private static final int VERSION = 0;

    // holds state lock
    JsonObject writeToJson() {
        final JsonObject ret = new JsonObject();

        if (!this.loginHistory.isEmpty()) {
            final JsonArray loginHistory = new JsonArray();
            ret.add("loginHistory", loginHistory);

            for (final ASLoginEntry entry : this.loginHistory) {
                loginHistory.add(entry.toJson());
            }
        }

        ret.addProperty("version", VERSION);

        return ret;
    }

    private static final ConcurrentHashMap<UUID, ASPlayerState> USER_DATA = new ConcurrentHashMap<>(2048, 0.25f);
    private static final int MAX_USER_DATA_CACHE = 256;
    private static final LinkedHashMap<UUID, ASPlayerState> USER_DATA_CACHE = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<UUID, ASPlayerState> eldest) {
            return this.size() >= MAX_USER_DATA_CACHE;
        }
    };

    private static void cacheEntry(final UUID uniqueId, final ASPlayerState userData) {
        synchronized (USER_DATA_CACHE) {
            USER_DATA_CACHE.put(uniqueId, userData);
        }
    }

    private static ASPlayerState getAndRemoveEntryFromCache(final UUID uniqueId) {
        synchronized (USER_DATA_CACHE) {
            return USER_DATA_CACHE.remove(uniqueId);
        }
    }

    private int referenceCount;

    // MT-Safe
    public static ASPlayerState getUserData(final UUID userId) {
        return USER_DATA.get(userId);
    }

    private static File getDataFileFor(final UUID userId) {
        final File dataFolder = IPASTools.getInstance().getDataFolder();
        return new File(new File(dataFolder, "userdata"), userId.toString().concat(".json"));
    }

    // MT-Safe
    public static ASPlayerState acquireAndLoadUserData(final UUID userUniqueId, final boolean create) {
        // note: this will not block get() calls
        return USER_DATA.compute(userUniqueId, (final UUID keyInMap, ASPlayerState userData) -> {
            if (userData == null) {
                // try cached
                userData = getAndRemoveEntryFromCache(keyInMap);
            }
            if (userData != null) {
                ++userData.referenceCount;
                return userData;
            }

            final File targetFile = getDataFileFor(keyInMap);

            if (!targetFile.exists()) {
                if (!create) {
                    return null;
                }
                // create new
                final ASPlayerState ret = new ASPlayerState(keyInMap);
                ret.referenceCount = 1;
                return ret;
            }

            try {
                final JsonElement json = Util.parseJson(targetFile);
                final ASPlayerState ret;

                if (json instanceof JsonObject) {
                    ret = new ASPlayerState(keyInMap, (JsonObject)json);
                } else {
                    IPASTools.getInstance().getLogger().log(Level.WARNING, "Invalid user data for " + keyInMap.toString() + ", overwriting data");
                    ret = new ASPlayerState(keyInMap);
                }

                ret.referenceCount = 1;
                return ret;
            } catch (final IOException ex) {
                IPASTools.getInstance().getLogger().log(Level.SEVERE, "Failed to read user data for " + keyInMap.toString() + ", overwriting data", ex);
                final ASPlayerState ret = new ASPlayerState(keyInMap);
                ret.referenceCount = 1;
                return ret;
            }
        });
    }

    // MT-Safe
    public static void releaseUserData(final UUID userUniqueId) {
        USER_DATA.computeIfPresent(userUniqueId, (final UUID keyInMap, final ASPlayerState userData) -> {
            if (--userData.referenceCount <= 0 && userData.save()) { // if it doesn't save properly, we can't unload it
                cacheEntry(keyInMap, userData);
                return null;
            }
            // something still has a reference, so keep it alive
            return userData;
        });
    }

    private final Object saveLock = new Object();

    public boolean save() {
        if (!this.canSave) {
            return false;
        }

        // don't allow concurrent writes
        synchronized (this.saveLock) {
            final JsonObject serialized;
            synchronized (this.stateLock) {
                if (!this.needsSaving()) {
                    return true;
                }
                this.dirty = false;
                serialized = this.writeToJson();
            }
            try {
                Util.writeJsonToFile(getDataFileFor(this.userUniqueId), serialized);
                return true;
            } catch (final IOException ex) {
                IPASTools.getInstance().getLogger().log(Level.SEVERE, "Failed to save data for " + this.userUniqueId + ", data will be kept in memory", ex);
                return false;
            }
        }
    }

    public static void saveAllUserData() {
        // items in cache are already saved, do not save them
        for (final ASPlayerState userData : USER_DATA.values()) {
            userData.save();
        }
    }

    public static final record ASLoginEntry(String ip, int ASNumber, String ASName, Date lastUsed) {

        public static ASLoginEntry parse(final JsonObject from) {
            try {
                return new ASLoginEntry(
                        from.getAsJsonPrimitive("ip").getAsString(),
                        from.getAsJsonPrimitive("ASNumber").getAsInt(),
                        from.getAsJsonPrimitive("ASName").getAsString(),
                        Util.DATE_FORMAT.parse(from.getAsJsonPrimitive("lastUsed").getAsString())
                );
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        public JsonObject toJson() {
            final JsonObject ret = new JsonObject();

            ret.addProperty("ip", this.ip);
            ret.addProperty("ASNumber", Integer.valueOf(this.ASNumber));
            ret.addProperty("ASName", this.ASName);
            ret.addProperty("lastUsed", Util.DATE_FORMAT.format(this.lastUsed));

            return ret;
        }
    }
}
