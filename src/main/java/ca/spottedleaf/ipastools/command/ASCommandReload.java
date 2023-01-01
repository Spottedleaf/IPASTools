package ca.spottedleaf.ipastools.command;

import ca.spottedleaf.ipastools.IPASTools;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.List;

import static ca.spottedleaf.ipastools.command.ASCommand.*;

public final class ASCommandReload implements ASCommand.ASSubCommand {

    private final IPASTools plugin;

    public ASCommandReload(final IPASTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public Component getHelp() {
        return Component.text()
                .append(Component.text().content("No arguments").color(HELP_DESCRIPTION_COLOUR))
                .append(
                        Component.text().content(" - Reloads the config from disk.")
                                .color(HELP_DESCRIPTION_COLOUR)
                )
                .build();
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command, @NotNull final String label,
                             @NotNull final String[] args) {
        if (IPASTools.getInstance().reloadASConfig()) {
            sender.sendMessage(ChatColor.RED + "Failed to reload config");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Reloaded config");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command,
                                                @NotNull final String label, @NotNull final String[] args) {
        return Collections.emptyList();
    }
}
