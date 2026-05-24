package eu.koolfreedom.command.impl;

import eu.koolfreedom.ban.IndefiniteBanSystem;
import eu.koolfreedom.command.KoolCommand;
import eu.koolfreedom.command.annotation.CommandParameters;
import eu.koolfreedom.util.FUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import eu.koolfreedom.KoolAnarchyMod;

import java.util.Arrays;
import java.util.List;

@CommandParameters(
        name = "gtfo",
        description = "Bans the specified player.",
        usage = "/<command> <username> [duration] [reason]"
)
public class GTFOCommand extends KoolCommand
{
    @Override
    public boolean run(CommandSender sender, Player playerSender, Command cmd, String s, String[] args)
    {
        if (!KoolAnarchyMod.isAllowed(sender))
        {
            msg(sender, "<red>You do not have access to execute this command.");
            return true;
        }

        if (args.length == 0) return false;

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.isOnline() && !target.hasPlayedBefore())
        {
            msg(sender, playerNotFound);
            return true;
        }

        // Determine if args[1] is a duration string (e.g. 7d, 1h30m)
        // If it parses cleanly, treat it as duration and shift reason start forward.
        // Otherwise, everything after the name is the reason and the ban is permanent.
        String duration = null;
        int reasonStart = 1;

        if (args.length >= 2 && IndefiniteBanSystem.parseDuration(args[1]) > 0)
        {
            duration = args[1];
            reasonStart = 2;
        }

        // Build the reason — wraps in parentheses to match the original style
        String reason = args.length > reasonStart
                ? " (" + String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length)) + ")"
                : "";

        String fullReason = IndefiniteBanSystem.DEFAULT_REASON + reason;

        // Ban — online players go through banPlayer for IP capture, offline through banOfflinePlayer
        if (target.isOnline() && target.getPlayer() != null)
            plugin.getBanSystem().banPlayer(target.getPlayer(), fullReason, duration);
        else
            plugin.getBanSystem().banOfflinePlayer(target, fullReason, duration);

        // Staff broadcast
        String durationDisplay = duration != null ? duration : "permanent";
        FUtil.staffAction(sender, "Banning <target> (<duration>)",
                Placeholder.unparsed("target", target.getName() != null ? target.getName() : args[0]),
                Placeholder.unparsed("duration", durationDisplay)
        );

        // The fun part — only runs if the player is currently online
        if (target.isOnline() && target.getPlayer() != null)
        {
            Player online = target.getPlayer();

            for (int i = 0; i < 4; i++)
                online.getWorld().strikeLightning(online.getLocation());

            online.setHealth(0);
            online.spawnParticle(Particle.ASH, online.getLocation(), Integer.MAX_VALUE, 1, 1, 1, 1, null, true);

            // Catch any alt accounts on the same IP
            String bannedIp = FUtil.getIp(online);
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(online))
                    .filter(p -> FUtil.getIp(p).equalsIgnoreCase(bannedIp))
                    .forEach(p -> {
                        for (int i = 0; i < 4; i++)
                            p.getWorld().strikeLightning(p.getLocation());

                        p.setHealth(0);
                        p.kick(Component.text("You have been banned.", NamedTextColor.RED));
                    });
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String commandLabel, String[] args)
    {
        if (args.length == 1)
        {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> !name.equalsIgnoreCase(sender.getName()))
                    .toList();
        }

        // Suggest common durations at position 2 if args[1] looks like it could be a duration
        if (args.length == 2)
            return Arrays.asList("1h", "1d", "7d", "30d", "1h30m");

        return List.of();
    }
}