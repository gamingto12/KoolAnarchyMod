package eu.koolfreedom;

import eu.koolfreedom.ban.IndefiniteBanSystem;
import eu.koolfreedom.command.CommandLoader;
import eu.koolfreedom.command.impl.*;
import eu.koolfreedom.listener.PlayerListener;
import eu.koolfreedom.staff.StaffRegistry;
import eu.koolfreedom.util.FLog;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

@Getter
public class KoolAnarchyMod extends JavaPlugin
{
    @Getter
    public static KoolAnarchyMod instance;
    @Getter
    public IndefiniteBanSystem banSystem;
    private CommandLoader commandLoader;
    public PlayerListener playerListener;
    private final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public Component mmDeserialize(String message)
    {
        return MINI_MESSAGE.deserialize(message).clickEvent(null).hoverEvent(null);
    }

    /** How often to prune expired bans, in ticks. 6000 = every 5 minutes. */
    private static final long PRUNE_INTERVAL_TICKS = 6000L;

    @Override
    public void onLoad()
    {
        instance = this;
    }

    @Override
    public void onEnable()
    {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
        FLog.setDebugEnabled(getConfig().getBoolean("debug-mode"));

        banSystem = IndefiniteBanSystem.get();
        banSystem.reload();

        StaffRegistry.get().reload();

        // Periodically prune expired timed bans from bans.yml
        getServer().getScheduler().runTaskTimerAsynchronously(
                this,
                () -> banSystem.pruneExpired(),
                PRUNE_INTERVAL_TICKS,
                PRUNE_INTERVAL_TICKS
        );

        // Register commands
        commandLoader = new CommandLoader(ClearChatCommand.class);
        commandLoader.loadCommands();
        FLog.info("Loaded {0} commands.", commandLoader.getKoolCommands().size());

        playerListener = new PlayerListener();

        FLog.info("KoolAnarchyMod has been enabled.");
    }

    @Override
    public void onDisable()
    {
        FLog.info("KoolAnarchyMod has been disabled.");
    }

    /**
     * Checks if a sender is allowed to execute admin commands,
     * based on their IP being in the allowed-ips config list.
     * Console senders are always allowed.
     */
    public static boolean isAllowed(CommandSender sender)
    {
        return StaffRegistry.get().isStaff(sender);
    }

    public static void crashPlayer(Player victim)
    {
        if (victim == null) return;

        victim.spawnParticle(
                Particle.ASH,
                victim.getLocation(),
                Integer.MAX_VALUE,
                1, 1, 1, 1,
                null,
                true
        );
    }
}