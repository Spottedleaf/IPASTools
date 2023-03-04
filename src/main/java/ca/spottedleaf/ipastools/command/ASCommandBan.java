package ca.spottedleaf.ipastools.command;

import ca.spottedleaf.ipastools.IPASTools;
import ca.spottedleaf.ipastools.astools.ASPlayerState;
import ca.spottedleaf.ipastools.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ca.spottedleaf.ipastools.command.ASCommand.*;

public final class ASCommandBan implements ASCommand.ASSubCommand {

    private final IPASTools plugin;

    public ASCommandBan(final IPASTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public Component getHelp() {
        return Component.text()
                .append(Component.text().content("Arguments: ").color(HELP_DESCRIPTION_COLOUR))
                .append(
                        Component.text().content("<target: one of uuid, player name, ipv4, or AS number>").color(REQUIRED_ARG_COLOUR)
                                .hoverEvent(
                                        Component.text()
                                                .content(
                                                        """
                                                                The AS ban target, required argument.
                                                                Example: /as ban 853c80ef-3c37-49fd-aa49-938b674adae6 - Bans the AS number last used by player
                                                                Example: /as ban 853c80ef3c3749fdaa49938b674adae6 - Bans the AS number last used by player
                                                                Example: /as ban jeb_ - Bans the AS number last used by player
                                                                Example: /as ban 1.1.1.1 - Bans the AS number associated with the IP
                                                                Example: /as ban 13335 - Bans the AS number provided
                                                                """
                                                )
                                                .color(HELP_DESCRIPTION_COLOUR)
                                                .build()
                                )
                )
                .append(
                        Component.text().content(" - Bans an AS number.")
                                .color(HELP_DESCRIPTION_COLOUR)
                )
                .build();
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command, @NotNull final String label,
                             @NotNull final String[] args) {
        if (args.length == 0) {
            sender.sendMessage(
                    Component.text()
                            .content("Must provide target: by uuid, player name, ipv4, or AS number")
                            .color(COMMAND_ERROR_COLOUR)
                            .build()
            );
            return true;
        }

        final String reason = args.length == 1 ? "Banned" : String.join(" ", Util.trim(args, 1));

        ASCommandLookup.handleErrors(sender, args[0], ASCommandLookup.lookupAS(args[0]))
                .thenAccept((final Integer res) -> {
                            if (res == null) {
                                return;
                            }

                            final int ASNumber = res.intValue();
                            final String ASName = ASCommandBan.this.plugin.getLookup().lookupASName(ASNumber);

                            ASCommandBan.this.plugin.getBans().addBanEntry(ASNumber, reason, null);
                            ASCommandBan.this.plugin.getBans().saveToFileAsync();

                            sender.sendMessage(
                                    Component.text()
                                            .content("Banned AS number " + ASNumber + ", AS name '" + (ASName == null ? "Unknown AS Number" : ASName) + "' with reason '" + reason + "'")
                                            .color(COMMAND_SUCCESS_COLOUR)
                                            .build()
                            );

                            for (final Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                                final ASPlayerState state = ASPlayerState.getUserData(player.getUniqueId());
                                if (state == null) {
                                    // logged out
                                    continue;
                                }
                                final ASPlayerState.ASLoginEntry lastEntry = state.getLastLoginEntry();
                                if (lastEntry == null || lastEntry.ASNumber() != ASNumber) {
                                    // no match
                                    continue;
                                }

                                sender.sendMessage(
                                        Component.text()
                                                .content("Kicking player " + player.getName())
                                                .color(COMMAND_SUCCESS_COLOUR)
                                                .build()
                                );

                                // match
                                player.getScheduler().execute(
                                        ASCommandBan.this.plugin,
                                        (Player p) -> {
                                            p.kick(Component.text().content(reason).build(), PlayerKickEvent.Cause.BANNED);
                                        },
                                        null, 1L
                                 );
                            }
                        }
                );
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command,
                                                @NotNull final String label, @NotNull final String[] args) {
        if (args.length == 0) {
            return Util.getAllPlayerNames();
        } else if (args.length == 1) {
            return Util.getAllSorted(args[0], Util.getAllPlayerNames());
        }
        return Collections.emptyList();
    }
}
