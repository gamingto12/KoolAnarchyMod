package eu.koolfreedom.command.impl;

import eu.koolfreedom.KoolAnarchyMod;
import eu.koolfreedom.ban.IndefiniteBanSystem;
import eu.koolfreedom.command.KoolCommand;
import eu.koolfreedom.command.annotation.CommandParameters;
import eu.koolfreedom.util.FLog;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

@CommandParameters(
        name = "permbans",
        description = "Manages the indefinite ban list",
        usage = "/<command> [reload|ban <player> [duration] [reason]|remove <type> <value>]",
        aliases = {"indefbans", "indefban", "permban"}
)
public class PermBanCommand extends KoolCommand
{
    @Override
    public boolean run(CommandSender sender, Player playerSender, Command cmd, String s, String[] args)
    {
        IndefiniteBanSystem indefBans = IndefiniteBanSystem.get();

        if (args.length == 0 || !KoolAnarchyMod.isAllowed(sender))
        {
            msg(sender, "<gray>There are <count> permban entries.",
                    Placeholder.unparsed("count", String.valueOf(indefBans.getBansCount())));
            return true;
        }

        switch (args[0].toLowerCase())
        {
            case "reload" -> {
                indefBans.reload();
                msg(sender, "<green>Reloaded the bans file.");
            }

            // /permbans ban <player> [duration] [reason...]
            // duration is optional — if omitted or unparseable, ban is permanent
            // Examples:
            //   /permbans ban Notch                      -> permanent, default reason
            //   /permbans ban Notch 7d                   -> 7 days, default reason
            //   /permbans ban Notch 1h30m Being annoying -> 1h30m, custom reason
            //   /permbans ban Notch Being annoying       -> permanent, reason = "Being annoying"
            case "ban" -> {
                if (!sender.hasPermission("kfc.permbans.ban"))
                {
                    msg(sender, "<red>You don't have permission to ban players.");
                    return true;
                }
                if (args.length < 2)
                {
                    msg(sender, "<red>Usage: /permbans ban <player> [duration] [reason]");
                    return true;
                }

                String targetName = args[1];

                // Determine if args[2] looks like a duration string
                String duration = null;
                int reasonStart = 2;
                if (args.length >= 3 && IndefiniteBanSystem.parseDuration(args[2]) > 0)
                {
                    duration = args[2];
                    reasonStart = 3;
                }

                // Join remaining args as the reason (maybe empty → default)
                String reason = args.length > reasonStart
                        ? String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length))
                        : null;

                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

                try
                {
                    if (target.isOnline() && target.getPlayer() != null)
                        indefBans.banPlayer(target.getPlayer(), reason, duration);
                    else
                        indefBans.banOfflinePlayer(target, reason, duration);

                    String durationDisplay = duration != null ? duration : "permanent";
                    msg(sender, "<green>Banned <player> for <duration>.",
                            Placeholder.unparsed("player", targetName),
                            Placeholder.unparsed("duration", durationDisplay));
                    FLog.info("{0} banned {1} for {2} (reason: {3})",
                            sender.getName(), targetName, durationDisplay,
                            reason != null ? reason : IndefiniteBanSystem.DEFAULT_REASON);
                }
                catch (Exception e)
                {
                    FLog.error("Error banning player", e);
                    msg(sender, "<red>An error occurred while banning the player.");
                }
            }

            case "remove" -> {
                if (!sender.hasPermission("kfc.permbans.remove"))
                {
                    msg(sender, "<red>You don't have permission to remove bans.");
                    return true;
                }
                if (args.length < 3)
                {
                    msg(sender, "<red>Usage: /permbans remove <name|uuid|ip> <value>");
                    return true;
                }

                String type  = args[1].toLowerCase();
                String value = args[2];

                try
                {
                    boolean unbanned = switch (type)
                    {
                        case "name" -> indefBans.unbanPlayer(value);
                        case "uuid" -> indefBans.unbanPlayerByUuid(value);
                        case "ip"   -> indefBans.unbanPlayerByIp(value);
                        default     -> {
                            msg(sender, "<red>Invalid type. Use: name, uuid, or ip");
                            yield false;
                        }
                    };

                    if (unbanned)
                    {
                        msg(sender, "<green>Unbanned by <type>: <value>",
                                Placeholder.unparsed("type", type),
                                Placeholder.unparsed("value", value));
                        FLog.debug("Cleared {0} ({1}) from permbans.yml", value, type);
                    }
                    else if (!type.equals("invalid"))
                    {
                        msg(sender, "<red>No ban found for <type>: <value>",
                                Placeholder.unparsed("type", type),
                                Placeholder.unparsed("value", value));
                    }
                }
                catch (Exception e)
                {
                    FLog.error("Error unbanning player", e);
                    msg(sender, "<red>An error occurred while unbanning the player.");
                }
            }

            default -> msg(sender, "<red>Unknown subcommand. Usage: " + cmd.getUsage());
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String s, String[] args)
    {
        if (!sender.hasPermission("kfc.admin")) return null;

        if (args.length == 1)
            return Arrays.asList("reload", "ban", "remove");

        if (args.length == 2)
        {
            return switch (args[0].toLowerCase())
            {
                case "ban"    -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                case "remove" -> sender.hasPermission("kfc.permbans.remove")
                        ? Arrays.asList("name", "uuid", "ip") : null;
                default -> null;
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("ban"))
            return Arrays.asList("1h", "1d", "7d", "30d", "1h30m");

        if (args.length == 3 && args[0].equalsIgnoreCase("remove"))
        {
            if (!sender.hasPermission("kfc.permbans.remove")) return null;
            if (args[1].equalsIgnoreCase("name"))
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }

        return null;
    }
}