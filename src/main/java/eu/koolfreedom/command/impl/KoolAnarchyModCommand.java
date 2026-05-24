package eu.koolfreedom.command.impl;

import eu.koolfreedom.KoolAnarchyMod;
import eu.koolfreedom.command.KoolCommand;
import eu.koolfreedom.command.annotation.CommandParameters;
import eu.koolfreedom.util.FLog;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static eu.koolfreedom.KoolAnarchyMod.isAllowed;

@CommandParameters(name = "koolanarchymod", description = "Gives information about the plugin or reloads it",
                    usage = "/<command> [reload]", aliases = "kam")
public class KoolAnarchyModCommand extends KoolCommand
{
    @Override
    public boolean run(CommandSender sender, Player playerSender, Command cmd, String commandLabel, String[] args)
    {
        if (args.length == 0 || !isAllowed(sender))
        {
            msg(sender, "<gold><b>KoolAnarchyMod - The Core Plugin of KoolAnarchy");
            msg(sender, "<red>Version: <version>", Placeholder.unparsed("version", plugin.getPluginMeta().getVersion()));
            msg(sender, "<red>Authors: <authors>", Placeholder.unparsed("authors", String.valueOf(plugin.getPluginMeta().getAuthors())));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload"))
        {
            try
            {
                KoolAnarchyMod.getInstance().reloadConfig();
                msg(sender, "<green>KoolAnarchyMod successfully reloaded");
            }
            catch (Exception e)
            {
                FLog.error("Failed to load configuration", e);
                msg(sender, "<red>An error occurred whilst attempting to reload the configuration.");
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, @NotNull Command command, @NotNull String label, String[] args)
    {
        if (isAllowed(sender) && args.length == 1)
        {
            return Collections.singletonList("reload");
        }
        else
        {
            return List.of();
        }
    }
}
