package ca.spottedleaf.ipastools.listener;

import ca.spottedleaf.ipastools.IPASTools;
import ca.spottedleaf.ipastools.astools.ASLookup;
import ca.spottedleaf.ipastools.astools.ASPlayerState;
import ca.spottedleaf.ipastools.util.Util;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.UUID;

public final class PlayerConnectionListener implements Listener {

    private final IPASTools plugin;

    public PlayerConnectionListener(final IPASTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncLogin(final AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        final ASPlayerState state = ASPlayerState.acquireAndLoadUserData(event.getUniqueId(), true);

        final InetAddress address = event.getAddress();
        if (!(address instanceof Inet4Address ipv4)) {
            this.plugin.getLogger().info("User " + event.getName() + "(" + event.getUniqueId() + ") is logging in with unknown address type: " + address);
        } else {
            final ASLookup.ASEntry entry = this.plugin.getLookup().lookup(ipv4);
            final String ipv4Str = Util.toIPv4String(ipv4);

            if (entry == null) {
                this.plugin.getLogger().info("User " + event.getName() + "(" + event.getUniqueId() + ":" + ipv4Str + ") is logging in with IPv4: " + ipv4 + ", but AS service is down");
            } else {
                if (entry != ASLookup.NO_MATCH) {
                    final Date now = new Date();
                    state.addLoginHistory(new ASPlayerState.ASLoginEntry(ipv4Str, entry.ASNumber(), entry.ASName(), now));
                    final String kickReason = this.plugin.getBans().getKickReason(entry.ASNumber());
                    if (kickReason != null) {
                        // banned
                        this.plugin.getLogger().info("User " + event.getName() + "(" + event.getUniqueId() + ":" + ipv4Str + ") tried to log in with banned ASEntry: " + entry.description() + ", reason: " + kickReason);

                        event.setKickMessage(kickReason);
                        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
                    } else {
                        this.plugin.getLogger().info("User " + event.getName() + "(" + event.getUniqueId() + ":" + ipv4Str + ") is logging in with ASEntry: " + entry.description());
                    }
                } else {
                    this.plugin.getLogger().info("User " + event.getName() + "(" + event.getUniqueId() + ":" + ipv4Str + ") is logging in with an unknown ASEntry, local/lan?");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        // catch bans issued to AS numbers before the player joined the player list, but had passed the async pre login

        final InetSocketAddress socketAddr = player.getAddress();
        if (socketAddr == null) {
            // not a connection?
            return;
        }

        final Inet4Address addr = socketAddr.getAddress() instanceof Inet4Address ipv4 ? ipv4 : null;
        if (addr == null) {
            // not using ipv4
            return;
        }

        final ASLookup.ASEntry entry = this.plugin.getLookup().lookup(Util.toIPInt(addr));
        if (entry == null || entry == ASLookup.NO_MATCH) {
            // no entry
            return;
        }

        player.getScheduler().execute(this.plugin, () -> {
            final String reason = PlayerConnectionListener.this.plugin.getBans().getKickReason(entry.ASNumber());
            if (reason != null) {
                player.kick(Component.text().content(reason).build(), PlayerKickEvent.Cause.BANNED);
            }
        }, null, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConnectionClose(final PlayerConnectionCloseEvent event) {
        final UUID playerId = event.getPlayerUniqueId();
        IPASTools.GENERIC_IO_EXECUTOR.execute(() -> {
            ASPlayerState.releaseUserData(playerId);
        });
    }
}
