package ca.spottedleaf.ipastools.command;

import ca.spottedleaf.ipastools.IPASTools;
import ca.spottedleaf.ipastools.astools.ASLookup;
import ca.spottedleaf.ipastools.astools.ASPlayerState;
import ca.spottedleaf.ipastools.util.Util;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static ca.spottedleaf.ipastools.command.ASCommand.*;

public final class ASCommandLookup implements ASCommand.ASSubCommand {

    private final IPASTools plugin;

    public ASCommandLookup(final IPASTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public Component getHelp() {
        return Component.text()
                .append(Component.text().content("Arguments: ").color(HELP_DESCRIPTION_COLOUR))
                .append(
                        Component.text().content("<target: one of uuid, online player, ipv4, or AS number>").color(REQUIRED_ARG_COLOUR)
                                .hoverEvent(
                                        Component.text()
                                                .content(
                                                        """
                                                                The lookup target, required argument.
                                                                Example: /as lookup 853c80ef-3c37-49fd-aa49-938b674adae6 - Looks up player by UUID
                                                                Example: /as lookup 853c80ef3c3749fdaa49938b674adae6 - Looks up player by UUID without dashes
                                                                Example: /as lookup jeb_ - Looks up player by name
                                                                Example: /as lookup 1.1.1.1 - Looks up directly by IP entry
                                                                Example: /as lookup 13335 - Looks up directly by AS number
                                                                """
                                                )
                                                .color(HELP_DESCRIPTION_COLOUR)
                                                .build()
                                )
                )
                .append(
                        Component.text().content(" [player limit]").color(OPTIONAL_ARG_COLOUR)
                                .hoverEvent(
                                        Component.text()
                                                .content(
                                                        """
                                                                The player limit. The format is "<offline or online>[,count]"
                                                                By default, this is "offline." Offline will show players
                                                                who are not logged in, and online will show players who are
                                                                logged in. If count is absent, then it is defaulted to 20.
                                                                Example: /as lookup jeb_ offline,10 - Looks up AS by player name,
                                                                showing only the last 10 online and offline users who have
                                                                the same AS number.
                                                                Example: /as lookup 1.1.1.1 online,10 - Looks up AS by IPv4,
                                                                showing only the last 10 online users who have the same AS number.
                                                                """
                                                )
                                                .color(HELP_DESCRIPTION_COLOUR)
                                                .build()
                                )
                )
                .append(
                        Component.text().content(" - Look up users who share a common AS number.")
                                .color(HELP_DESCRIPTION_COLOUR)
                )
                .build();
    }

    public static enum ASLookupResultType {
        SUCCESS, NO_ENTRY, NO_PLAYER_BY_NAME, NO_LOGIN_DATA,
    }

    public static record ASLookupResult(ASLookupResultType type, int value) {}

    public static CompletableFuture<ASLookupResult> lookupAS(final String input) {
        try {
            // direct AS number
            final int number = Integer.parseInt(input);
            return CompletableFuture.completedFuture(new ASLookupResult(ASLookupResultType.SUCCESS, number));
        } catch (final NumberFormatException ex) {
            // not a number, continue
        }

        try {
            // from IPv4
            final int ip = Util.getAddress(input);
            final ASLookup.ASEntry entry = IPASTools.getInstance().getLookup().lookup(ip);
            if (entry == null || entry == ASLookup.NO_MATCH) {
                return CompletableFuture.completedFuture(new ASLookupResult(ASLookupResultType.NO_ENTRY, 0));
            } else {
                return CompletableFuture.completedFuture(new ASLookupResult(ASLookupResultType.SUCCESS, entry.ASNumber()));
            }
        } catch (final IllegalArgumentException ex) {
            // not an ipv4
        }

        // must be either UUID or username at this point
        // we need to parse to UUID if username so we can look up the player data

        CompletableFuture<UUID> uuidLookup;

        try {
            // try to parse UUID
            uuidLookup = CompletableFuture.completedFuture(Util.parseUUID(input));
        } catch (final IllegalArgumentException ex) {
            // must be a username

            final PlayerProfile profile = Bukkit.createProfile(null, input);

            uuidLookup = CompletableFuture.supplyAsync(() -> {
                profile.complete(false);

                return profile.getId();
            }, IPASTools.PROFILE_LOOKUP_EXECUTOR);
        }

        return uuidLookup.thenApplyAsync((final UUID playerId) -> {
            if (playerId == null) {
                return new ASLookupResult(ASLookupResultType.NO_PLAYER_BY_NAME, 0);
            }
            final ASPlayerState state = ASPlayerState.acquireAndLoadUserData(playerId, false);
            try {
                if (state == null) {
                    return new ASLookupResult(ASLookupResultType.NO_LOGIN_DATA, 0);
                }
                final ASPlayerState.ASLoginEntry loginEntry = state.getLastLoginEntry();
                if (loginEntry == null) {
                    return new ASLookupResult(ASLookupResultType.NO_LOGIN_DATA, 0);
                }

                return new ASLookupResult(ASLookupResultType.SUCCESS, loginEntry.ASNumber());
            } finally {
                ASPlayerState.releaseUserData(playerId);
            }
        }, IPASTools.GENERIC_IO_EXECUTOR);
    }

    public static CompletableFuture<Integer> handleErrors(final CommandSender sender, final String input, final CompletableFuture<ASLookupResult> rawResult) {
        return rawResult.thenApply((final ASLookupResult res) -> {
            final ASLookupResultType type = res.type;
            if (type == ASLookupResultType.SUCCESS) {
                return Integer.valueOf(res.value);
            }
            // error, message
            final Component msg;
            switch (type) {
                case NO_ENTRY: {
                    msg = Component.text().content("No AS number for ip: " + input).color(COMMAND_ERROR_COLOUR).build();
                    break;
                }
                case NO_PLAYER_BY_NAME: {
                    msg = Component.text().content("No player with name: " + input).color(COMMAND_ERROR_COLOUR).build();
                    break;
                }
                case NO_LOGIN_DATA: {
                    msg = Component.text().content("Player '" + input + "' has no login data").color(COMMAND_ERROR_COLOUR).build();
                    break;
                }
                default: {
                    msg = Component.text().content("Unknown error: " + type).color(COMMAND_ERROR_COLOUR).build();
                    break;
                }
            }

            sender.sendMessage(msg);

            return (Integer)null;
        });
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command, @NotNull final String label,
                             @NotNull final String[] args) {
        /*
                                                                        Example: /as lookup 853c80ef-3c37-49fd-aa49-938b674adae6 - Looks up player by UUID
                                                                Example: /as lookup 853c80ef3c3749fdaa49938b674adae6 - Looks up player by UUID without dashes
                                                                Example: /as lookup jeb_ - Looks up player by name
                                                                Example: /as lookup 1.1.1.1 - Looks up directly by IP entry
                                                                Example: /as lookup 13335 - Looks up directly by AS number
         */



        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command,
                                                @NotNull final String label, @NotNull final String[] args) {
        if (args.length == 0) {
            return Util.getAllPlayerNames();
        } else if (args.length == 1) {
            return Util.getAllSorted(args[0], Util.getAllPlayerNames());
        } else if (args.length == 2) {
            return Util.getAllSorted(args[1], Arrays.asList("online", "offline"));
        }

        return Collections.emptyList();
    }
}
