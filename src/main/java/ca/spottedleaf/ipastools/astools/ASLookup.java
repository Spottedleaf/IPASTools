package ca.spottedleaf.ipastools.astools;

import ca.spottedleaf.ipastools.IPASTools;
import ca.spottedleaf.ipastools.util.Util;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ASLookup {

    public static final ASEntry NO_MATCH = new ASEntry(0L, 0, 0, "Unknown");
    private static final Comparator<ASEntry> ENTRY_COMPARATOR = (final ASEntry e1, final ASEntry e2) -> {
        return Long.compare(e1.address, e2.address);
    };

    private static final Logger LOGGER = IPASTools.getInstance().getLogger();

    private final File cacheFile;
    private final Executor updateScheduler;
    private final AtomicReference<Date> invalidateTime = new AtomicReference<>();
    private volatile ASEntry[] entries;

    public ASLookup(final File cacheFile, final Executor updateScheduler) {
        this.cacheFile = cacheFile.getAbsoluteFile();
        this.updateScheduler = updateScheduler;
        this.loadFromCache();
    }

    private void updateCacheIfNeeded() {
        final Date time = this.invalidateTime.get();
        final Instant now = Instant.now();
        if (Date.from(now).after(time)) {
            if (!this.invalidateTime.compareAndSet(time, Date.from(now.plus(1L, ChronoUnit.DAYS)))) {
                return;
            }
            this.updateScheduler.execute(this::forceUpdateCache);
        }
    }

    private void forceSaveCache() {
        final Date invalidateOn = this.invalidateTime.get();
        final ASEntry[] entries = this.entries;

        final File temp = new File(this.cacheFile.getParentFile(), this.cacheFile.getName() + ".tmp" + new Random().nextDouble());
        try {
            temp.getParentFile().mkdirs();
            temp.createNewFile();

            final PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(temp)));

            out.println(Util.DATE_FORMAT.format(invalidateOn));

            final Set<String> seenASName = new HashSet<>();
            for (final ASEntry entry : entries) {
                out.print(Util.toIPv4String((int)entry.address));
                out.print(' '); out.print(entry.subnet);
                out.print(' '); out.print(entry.ASNumber);
                if (seenASName.add(entry.ASName)) {
                    out.print(' '); out.println(entry.ASName);
                } else {
                    out.println();
                }
            }

            out.close();
            Files.move(temp.toPath(), this.cacheFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to save ASLookup cache to: " + temp.getAbsolutePath(), ex);
        } finally {
            temp.delete();
        }

    }

    public void forceUpdateCache() {
        try {
            final HttpClient client = HttpClient.newHttpClient();
            HttpRequest asNumReq = HttpRequest.newBuilder(new URI("https://thyme.apnic.net/current/data-used-autnums")).GET().build();
            HttpRequest rawTableReq = HttpRequest.newBuilder(new URI("https://thyme.apnic.net/current/data-raw-table")).GET().build();

            final CompletableFuture<String> asNumRes = client.sendAsync(asNumReq, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);
            final CompletableFuture<String> rawTableRes = client.sendAsync(rawTableReq, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body);

            final List<String> asNum = asNumRes.join().lines().toList();
            final List<String> rawTable = rawTableRes.join().lines().toList();

            final Map<Integer, String> asLookup = new HashMap<>();

            for (final String entry : asNum) {
                int start = 0;
                int end;
                for (char c;(c = entry.charAt(start)) == ' ' || c == '\t' || Character.isWhitespace(c); ++start);
                end = start + 1;
                for (char c;!((c = entry.charAt(end)) == ' ' || c == '\t' || Character.isWhitespace(c)); ++end);
                int start2 = end;
                for (char c;(c = entry.charAt(start2)) == ' ' || c == '\t' || Character.isWhitespace(c); ++start2);

                final int number = Integer.parseInt(entry.substring(start, end));
                final String name = entry.substring(start2);

                asLookup.put(Integer.valueOf(number), name);
            }

            final List<ASEntry> entries = new ArrayList<>();

            for (final String entry : rawTable) {
                int end = 0;
                for (char c;!((c = entry.charAt(end)) == ' ' || c == '\t' || Character.isWhitespace(c)); ++end);
                int start2 = end;
                for (char c;(c = entry.charAt(start2)) == ' ' || c == '\t' || Character.isWhitespace(c); ++start2);

                final String ipAndSubnet = entry.substring(0, end);
                final int num = Integer.parseInt(entry.substring(start2));

                final String name = asLookup.get(Integer.valueOf(num));

                final String[] split = Util.split(ipAndSubnet, '/');
                final long ip = 0xFFFFFFFFL & Util.getAddress(split[0]);
                final int subnet = Integer.parseInt(split[1]);

                entries.add(new ASEntry(ip, subnet, num, name));
            }

            final ASEntry[] entriesArray = entries.toArray(new ASEntry[0]);
            Arrays.sort(entriesArray, ENTRY_COMPARATOR);

            this.invalidateTime.set(Date.from(Instant.now().plus(1L, ChronoUnit.DAYS)));
            this.entries = entriesArray;

            this.forceSaveCache();
        } catch (final Exception ex) {
            // assume I/O issue
            this.invalidateTime.set(Date.from(Instant.now().plus(1L, ChronoUnit.HOURS)));
            LOGGER.log(Level.SEVERE, "Failed to load ASLookup data from remote", ex);
        }
    }

    public ASEntry lookup(final Inet4Address ip) {
        return this.lookup(Util.toIPInt(ip));
    }

    public ASEntry lookup(final byte[] ip) {
        return this.lookup(Util.toIPInt(ip));
    }

    public ASEntry lookup(final int ip) {
        final long ipMasked = ip & 0xFFFFFFFFL;

        this.updateCacheIfNeeded();

        final ASEntry[] entries = this.entries;
        if (entries == null) {
            // failed to load, no cache to fall back on
            return null;
        }
        final ASEntry probe = new ASEntry(ipMasked, 32, 0, null);
        int idx = Arrays.binarySearch(entries, probe, ENTRY_COMPARATOR);

        if (idx < 0) {
            idx = (-idx - 1) - 1;

            if (idx < 0 || idx >= entries.length) {
                return NO_MATCH;
            }
        }

        while (idx >= 0) {
            final ASEntry closest = entries[idx--];

            if (closest.address > ipMasked) {
                break;
            }

            if (closest.matches(ipMasked)) {
                return closest;
            }
        }

        return NO_MATCH;
    }

    public String lookupASName(final int number) {
        final ASEntry[] entries = this.entries;
        if (entries == null) {
            return null;
        }

        // Yes, this is slow. No, I don't care.
        for (final ASEntry entry : entries) {
            if (entry.ASNumber == number) {
                return entry.ASName;
            }
        }

        return null;
    }

    // Format: address subnet ASNumber [ASName]
    // Note: ASName is absent if it has been listed already

    private void loadFromCache() {
        if (!this.cacheFile.isFile()) {
            this.invalidateTime.set(Date.from(Instant.now().plus(1L, ChronoUnit.DAYS)));
            this.forceUpdateCache();
            return;
        }

        try {
            final List<String> input = Files.readAllLines(this.cacheFile.toPath(), StandardCharsets.UTF_8);

            final int headerLines = 1;
            final Date invalidateTime = Util.DATE_FORMAT.parse(input.get(0));

            final Map<Integer, String> asLookup = new HashMap<>();
            final ASEntry[] entries = new ASEntry[input.size() - headerLines];

            for (int i = 0; i < entries.length; ++i) {
                final String line = input.get(headerLines + i);
                final String[] split = Util.split(line, ' ');

                final long address = Util.getAddress(split[0]) & 0xFFFFFFFFL;
                final int subnet = Integer.parseInt(split[1]);
                final int number = Integer.parseInt(split[2]);
                String name = split.length > 3 ? String.join(" ", Arrays.copyOfRange(split, 3, split.length)) : null;

                if (name != null) {
                    asLookup.put(Integer.valueOf(number), name);
                } else {
                    name = asLookup.get(Integer.valueOf(number));
                }

                entries[i] = new ASEntry(address, subnet, number, name);
            }

            Arrays.sort(entries, ENTRY_COMPARATOR);

            this.entries = entries;
            this.invalidateTime.set(invalidateTime);
        } catch (final Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to load ASLookup up cache from file: '" + this.cacheFile.getAbsolutePath() + "', attempting to load from source", ex);
            this.forceUpdateCache();
            return;
        }
    }

    public static record ASEntry(long address, int subnet, int ASNumber, String ASName) {

        public boolean matches(final long ip) {
            final long mask = ((1L << this.subnet) - 1L) << (32 - this.subnet);
            return (mask & address) == (mask & ip);
        }

        public String addressStr() {
            return Util.toIPv4String((int)this.address);
        }

        public String description() {
            return "Owned by '" + this.ASName() + "' (" + this.ASNumber() + ") for range " + this.addressStr() + "/" + this.subnet;
        }
    }
}
