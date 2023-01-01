package ca.spottedleaf.ipastools.astools;

import ca.spottedleaf.ipastools.IPASTools;
import ca.spottedleaf.ipastools.util.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.logging.Level;

public final class ASBans {

    private final File file;
    private final LinkedHashMap<Integer, ASBanEntry> entries = new LinkedHashMap<>();

    public ASBans(final File file) {
        this.file = file;
        this.loadFromFile();
    }

    public String getKickReason(final int ASNumber) {
        final Date now = new Date();
        synchronized (this) {
            final ASBanEntry entry = this.entries.get(Integer.valueOf(ASNumber));

            if (entry != null && entry.hasExpired(now)) {
                this.entries.remove(Integer.valueOf(ASNumber));
                return null;
            }

            return entry == null ? null : entry.kickReason();
        }
    }

    public void addBanEntry(final int ASNumber, final String kickReason, final Date expire) {
        if (kickReason == null) {
            throw new NullPointerException();
        }

        synchronized (this) {
            this.entries.put(Integer.valueOf(ASNumber), new ASBanEntry(ASNumber, kickReason, expire));
        }
    }

    public boolean removeBanEntry(final int ASNumber) {
        synchronized (this) {
            return this.entries.remove(Integer.valueOf(ASNumber)) != null;
        }
    }

    private void loadFromFile() {
        if (!this.file.isFile()) {
            return;
        }
        try {
            final JsonObject json = Util.parseJson(this.file).getAsJsonObject();

            this.loadFromJson(json);
        } catch (final Exception ex) {
            IPASTools.getInstance().getLogger().log(Level.SEVERE, "Failed to read AS ban list from file '" + this.file.getAbsolutePath() + "'", ex);
        }
    }

    private final Object saveLock = new Object();

    public boolean saveToFile() {
        synchronized (this.saveLock) {
            try {
                Util.writeJsonToFile(this.file, this.saveToJson());
                return true;
            } catch (final IOException ex) {
                IPASTools.getInstance().getLogger().log(Level.SEVERE, "Failed to save AS ban list to file '" + this.file.getAbsolutePath() + "'", ex);
                return false;
            }
        }
    }

    public void saveToFileAsync() {
        IPASTools.GENERIC_IO_EXECUTOR.execute(() -> {
            ASBans.this.saveToFile();
        });
    }

    private void loadFromJson(final JsonObject json) {
        Exception ex = null;
        final Date now = new Date();
        synchronized (this) {
            try {
                final LinkedHashMap<Integer, ASBanEntry> newEntries = new LinkedHashMap<>();
                for (final JsonElement elem : json.getAsJsonArray("bans")) {
                    final ASBanEntry entry = ASBanEntry.parse(elem.getAsJsonObject());
                    if (entry.hasExpired(now)) {
                        continue;
                    }
                    newEntries.put(Integer.valueOf(entry.ASNumber()), entry);
                }
            } catch (final Exception e) {
                ex = e;
            }
        }

        if (ex != null) {
            IPASTools.getInstance().getLogger().log(Level.SEVERE, "Failed to load AS ban list", ex);
        }
    }

    private JsonObject saveToJson() {
        final JsonObject ret = new JsonObject();
        final JsonArray bans = new JsonArray();
        ret.add("bans", bans);

        final Date now = new Date();

        synchronized (this) {
            for (final ASBanEntry entry : this.entries.values()) {
                if (entry.hasExpired(now)) {
                    continue;
                }
                bans.add(entry.toJson());
            }
        }

        return ret;
    }

    public static final record ASBanEntry(int ASNumber, String kickReason, Date expire) {

        public static ASBanEntry parse(final JsonObject json) {
            try {
                return new ASBanEntry(
                        json.getAsJsonPrimitive("ASNumber").getAsInt(),
                        json.getAsJsonPrimitive("kickReason").getAsString(),
                        !json.has("expire") ? null : Util.DATE_FORMAT.parse(json.getAsJsonPrimitive("expire").getAsString())
                );
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        public boolean hasExpired(final Date date) {
            return this.expire != null && date.after(this.expire);
        }

        public JsonObject toJson() {
            final JsonObject ret = new JsonObject();

            ret.addProperty("ASNumber", Integer.valueOf(this.ASNumber));
            ret.addProperty("kickReason", this.kickReason);
            if (this.expire != null) {
                ret.addProperty("expire", Util.DATE_FORMAT.format(this.expire));
            }

            return ret;
        }
    }
}
