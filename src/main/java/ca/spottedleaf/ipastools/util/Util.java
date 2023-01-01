package ca.spottedleaf.ipastools.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class Util {

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z");

    public static <T> T[] trim(final T[] source, final int by) {
        return Arrays.copyOfRange(source, by, source.length);
    }

    public static List<String> getAllPlayerNames() {
        final List<String> players = new ArrayList<>();

        for (final Player player : Bukkit.getOnlinePlayers()) {
            players.add(player.getName());
        }

        // purge duplicates
        return new ArrayList<>(new HashSet<>(players));
    }

    public static List<String> sort(final Iterable<String> from) {
        final List<String> ret = new ArrayList<>();

        for (final String str : from) {
            ret.add(str);
        }

        ret.sort(String.CASE_INSENSITIVE_ORDER);
        return ret;
    }

    public static List<String> getAllSorted(final String startsWith, final Iterable<String> from) {
        final List<String> ret = new ArrayList<>();

        for (final String str : from) {
            if (str.regionMatches(true, 0, startsWith, 0, startsWith.length())) {
                ret.add(str);
            }
        }

        ret.sort(String.CASE_INSENSITIVE_ORDER);
        return ret;
    }

    // split() but without regex
    // also this split WILL NOT remove TRAILING EMPTY STRINGS
    public static String[] split(final String value, final String separator) {
        if (separator.isEmpty()) {
            throw new IllegalArgumentException("separator cannot be empty");
        }
        final char firstMatch = separator.charAt(0);

        final List<String> ret = new ArrayList<>();

        int currentIndex = 0;
        int lastEnd = 0;
        while ((currentIndex = (value.indexOf(firstMatch, currentIndex))) != -1 && currentIndex < value.length()) {
            if (!value.regionMatches(currentIndex, separator, 0, separator.length())) {
                currentIndex += separator.length();
                continue;
            }

            ret.add(value.substring(lastEnd, currentIndex));
            lastEnd = currentIndex + separator.length();
            currentIndex += separator.length();
        }

        ret.add(value.substring(lastEnd));

        return ret.toArray(new String[0]);
    }

    // split() but without regex
    // also this split WILL NOT remove TRAILING EMPTY STRINGS
    public static String[] split(final String value, final char separator) {
        final List<String> ret = new ArrayList<>();

        int currentIndex = 0;
        int lastEnd = 0;
        while ((currentIndex = (value.indexOf(separator, currentIndex))) != -1 && currentIndex < value.length()) {
            ret.add(value.substring(lastEnd, currentIndex));
            lastEnd = currentIndex + 1;
            currentIndex += 1;
        }

        ret.add(value.substring(lastEnd));

        return ret.toArray(new String[0]);
    }

    public static int getAddress(final String ip) {
        final String[] parts = Util.split(ip, '.');

        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid ip: " + ip);
        }

        int ret = 0;

        for (final String part : parts) {
            ret <<= 8;
            final int parsed = Integer.parseInt(part);
            if (parsed < 0 || parsed >= 256) {
                throw new IllegalArgumentException("Invalid ip: " + ip);
            }
            ret |= parsed;
        }

        return ret;
    }

    public static String toIPv4String(final int addr) {
        return ((addr >>> 24) & 0xFF) + "." +
                ((addr >>> 16) & 0xFF) + "." +
                ((addr >>> 8) & 0xFF) + "." +
                ((addr) & 0xFF);
    }

    public static int toIPInt(final byte[] addr) {
        if (addr.length != 4) {
            throw new IllegalStateException("Address must be 4 bytes in length, not " + addr.length);
        }
        return ByteBuffer.wrap(addr).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    public static int toIPInt(final Inet4Address addr) {
        return toIPInt(addr.getAddress());
    }

    public static String toIPv4String(final Inet4Address addr) {
        return toIPv4String(toIPInt(addr));
    }

    public static UUID parseUUID(final String string) {
        if (string.length() == 32) {
            try {
                final long msb = Long.parseUnsignedLong(string, 0, 16, 16);
                final long lsb = Long.parseUnsignedLong(string, 16, 32, 16);
                return new UUID(msb, lsb);
            } catch (final NumberFormatException ex) {
                throw new IllegalArgumentException("Not a UUID " + string, ex);
            }
        }
        if (string.length() == 36) {
            return UUID.fromString(string);
        }
        throw new IllegalArgumentException("Not a UUID " + string);
    }


    public static JsonElement parseJson(final File file) throws IOException {
        try (final JsonReader reader = new JsonReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(file)), StandardCharsets.UTF_8))) {
            reader.setLenient(true);
            return JsonParser.parseReader(reader);
        }
    }

    // atomic write to file
    public static void writeJsonToFile(final File file, final JsonElement json) throws IOException {
        file.getParentFile().mkdirs();

        final File tempFile = new File(file.getAbsolutePath() + "." + (new Random().nextLong()) + ".tmp");
        tempFile.createNewFile();

        try (final JsonWriter writer = new JsonWriter(new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(tempFile)), StandardCharsets.UTF_8))) {
            writer.setIndent(" ");
            writer.setLenient(true);

            Streams.write(json, writer);
        } catch (final Throwable throwable) {
            tempFile.delete(); // try to clean up garbage files
            sneakyThrow(throwable);
        }

        try {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            tempFile.delete(); // try to clean up garbage files
        }
    }

    public static <T extends Throwable> void sneakyThrow(final Throwable exception) throws T {
        throw (T)exception;
    }
}
