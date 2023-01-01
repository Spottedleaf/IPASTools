package ca.spottedleaf.ipastools.command;

import ca.spottedleaf.ipastools.IPASTools;
import ca.spottedleaf.ipastools.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ASCommand implements TabExecutor {

    public static final TextColor OPTIONAL_ARG_COLOUR = NamedTextColor.GREEN;
    public static final TextColor REQUIRED_ARG_COLOUR = NamedTextColor.DARK_GREEN;
    public static final TextColor HELP_DESCRIPTION_COLOUR = NamedTextColor.WHITE;
    public static final TextColor COMMAND_ERROR_COLOUR = NamedTextColor.RED;
    public static final TextColor COMMAND_SUCCESS_COLOUR = NamedTextColor.GREEN;
    public static final TextColor COMMAND_NAME_COLOUR = NamedTextColor.AQUA;

    public static final String PERMISSION_PREFIX = "as.command.as";

    private final IPASTools plugin;
    private final Map<String, ASSubCommand> subCommandMap;

    public ASCommand(final IPASTools plugin) {
        this.plugin = plugin;

        this.subCommandMap = Map.of(
                "help", new ASCommandHelp(),
                "reload", new ASCommandReload(plugin),
                "lookup", new ASCommandLookup(plugin),
                "ban", new ASCommandBan(plugin),
                "unban", new ASCommandUnBan(plugin)
        );
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command, @NotNull final String label,
                             @NotNull final String[] args) {
        if (!sender.hasPermission(PERMISSION_PREFIX)) {
            sender.sendMessage("You do not have permission to execute this command.");
            return true;
        }

        String cmdName = args.length == 0 ? null : args[0].toLowerCase(Locale.ROOT);
        ASSubCommand cmd = cmdName == null ? null : this.subCommandMap.get(cmdName);
        if (cmd == null) {
            cmd = this.subCommandMap.get("help");
            cmdName = "help";
        }

        if (!sender.hasPermission(PERMISSION_PREFIX + cmdName)) {
            sender.sendMessage("You do not have permission to execute this command.");
            return true;
        }

        return cmd.onCommand(sender, command, cmdName, Util.trim(args, 1));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command,
                                                @NotNull final String label, @NotNull final String[] args) {
        if (!sender.hasPermission(PERMISSION_PREFIX)) {
            return Collections.emptyList();
        }
        if (args.length == 0) {
            return Util.sort(this.subCommandMap.keySet());
        } else if (args.length == 1) {
            return Util.getAllSorted(args[0], this.subCommandMap.keySet());
        } else {
            final String cmdName = args[0].toLowerCase(Locale.ROOT);
            final ASSubCommand cmd = this.subCommandMap.get(cmdName);

            if (cmd == null || !sender.hasPermission(PERMISSION_PREFIX + cmdName)) {
                return Collections.emptyList();
            }

            return cmd.onTabComplete(sender, command, args[0], Util.trim(args, 1));
        }
    }

    public static interface ASSubCommand extends TabExecutor {

        public Component getHelp();

    }

    private final class ASCommandHelp implements ASSubCommand {
        @Override
        public Component getHelp() {
            return Component.text()
                    .append(Component.text().content("Arguments: ").color(HELP_DESCRIPTION_COLOUR))
                    .append(
                            Component.text().content("[command name]").color(OPTIONAL_ARG_COLOUR)
                                    .hoverEvent(
                                            Component.text()
                                                    .content(
                                                            "Optional argument which indicates what sub command to receive help for" + "\n"
                                                            + "Example: /as help help - Will print only this line"
                                                    )
                                                    .color(HELP_DESCRIPTION_COLOUR)
                                                    .build()
                                    )
                    )
                    .append(
                            Component.text().content(" - Prints help information for all sub commands, or optionally just a specific command.")
                                    .color(HELP_DESCRIPTION_COLOUR)
                    )
                    .build();
        }

        @Override
        public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command, @NotNull final String label,
                                 @NotNull final String[] args) {
            final List<String> helpTargets;
            if (args.length == 0) {
                helpTargets = new ArrayList<>(ASCommand.this.subCommandMap.keySet());
                sender.sendMessage(Component.text().content("Hover over arguments for specific details and examples for them.").color(HELP_DESCRIPTION_COLOUR));
            } else {
                helpTargets = new ArrayList<>(Arrays.asList(args[0].toLowerCase(Locale.ROOT)));
            }

            for (final String cmdName : helpTargets) {
                final ASSubCommand cmd = ASCommand.this.subCommandMap.get(cmdName);
                if (cmd == null) {
                    sender.sendMessage(Component.text().content("No such sub command: " + cmdName).color(COMMAND_ERROR_COLOUR));
                } else {
                    sender.sendMessage(
                            Component.text()
                                    .append(Component.text().content(cmdName).color(COMMAND_NAME_COLOUR))
                                    .append(Component.text().content(" - ").color(HELP_DESCRIPTION_COLOUR))
                                    .append(cmd.getHelp())
                    );
                }
            }

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command,
                                                    @NotNull final String label, @NotNull final String[] args) {
            if (args.length == 0) {
                return Util.sort(ASCommand.this.subCommandMap.keySet());
            } else if (args.length == 1) {
                return Util.getAllSorted(args[0], ASCommand.this.subCommandMap.keySet());
            } else {
                return Collections.emptyList();
            }
        }
    }
}
