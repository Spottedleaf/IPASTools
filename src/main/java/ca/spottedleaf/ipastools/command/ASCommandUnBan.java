package ca.spottedleaf.ipastools.command;

import ca.spottedleaf.ipastools.IPASTools;
import ca.spottedleaf.ipastools.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.List;

import static ca.spottedleaf.ipastools.command.ASCommand.*;

public final class ASCommandUnBan implements ASSubCommand {

    private final IPASTools plugin;

    public ASCommandUnBan(final IPASTools plugin) {
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
                                                                The AS unban target, required argument.
                                                                Example: /as unban 853c80ef-3c37-49fd-aa49-938b674adae6 - Unbans the AS number last used by player
                                                                Example: /as unban 853c80ef3c3749fdaa49938b674adae6 - Unbans the AS number last used by player
                                                                Example: /as unban jeb_ - Unbans the AS number last used by player
                                                                Example: /as unban 1.1.1.1 - Unbans the AS number associated with the IP
                                                                Example: /as unban 13335 - Unbans the AS number provided
                                                                """
                                                )
                                                .color(HELP_DESCRIPTION_COLOUR)
                                                .build()
                                )
                )
                .append(
                        Component.text().content(" - Unbans an AS number.")
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

        ASCommandLookup.handleErrors(sender, args[0], ASCommandLookup.lookupAS(args[0]))
                .thenAccept((final Integer res) -> {
                            if (res == null) {
                                return;
                            }

                            final int ASNumber = res.intValue();
                            final String ASName = ASCommandUnBan.this.plugin.getLookup().lookupASName(ASNumber);

                            final boolean unbanned = ASCommandUnBan.this.plugin.getBans().removeBanEntry(ASNumber);
                            ASCommandUnBan.this.plugin.getBans().saveToFileAsync();

                            if (unbanned) {
                                sender.sendMessage(
                                        Component.text()
                                                .content("Unbanned AS number " + ASNumber + ", AS name '" + (ASName == null ? "Unknown AS Number" : ASName) + "'")
                                                .color(COMMAND_SUCCESS_COLOUR)
                                                .build()
                                );
                            } else {
                                sender.sendMessage(
                                        Component.text()
                                                .content("No ban for AS number " + ASNumber + ", AS name '" + (ASName == null ? "Unknown AS Number" : ASName) + "'")
                                                .color(COMMAND_ERROR_COLOUR)
                                                .build()
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
