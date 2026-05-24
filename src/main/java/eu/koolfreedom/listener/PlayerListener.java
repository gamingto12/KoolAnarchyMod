package eu.koolfreedom.listener;

import eu.koolfreedom.KoolAnarchyMod;
import eu.koolfreedom.ban.IndefiniteBanSystem;
import eu.koolfreedom.util.FUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@SuppressWarnings({"ConstantConditions", "deprecation"})
public class PlayerListener implements Listener
{
    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm z")
                    .withZone(ZoneId.of("UTC"));

    public PlayerListener()
    {
        KoolAnarchyMod.getInstance().getServer().getPluginManager()
                .registerEvents(this, KoolAnarchyMod.getInstance());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPermBannedPlayerLogin(PlayerLoginEvent event)
    {
        IndefiniteBanSystem bans = IndefiniteBanSystem.get();

        String currentName = event.getPlayer().getName().toLowerCase();
        String uuid        = event.getPlayer().getUniqueId().toString();

        InetAddress addr = event.getAddress();
        String ip = addr != null ? addr.getHostAddress() : null;

        boolean nameBanned = bans.isNameBanned(currentName);
        boolean uuidBanned = bans.isUuidBanned(uuid);
        boolean ipBanned   = ip != null && bans.isIpBanned(ip);

        if (!(nameBanned || uuidBanned || ipBanned)) return;

        // Resolve which ban entry applies
        String entryName;
        if (nameBanned)       entryName = currentName;
        else if (uuidBanned)  entryName = bans.findBannedNameByUuid(uuid).orElse(currentName);
        else                  entryName = bans.findBannedNameByIp(ip).orElse(currentName);

        String banType    = nameBanned ? "username" : uuidBanned ? "UUID" : "IP address";
        Component reason  = bans.getReasonComponent(entryName);
        long expiry       = bans.getExpiry(entryName);
        boolean permanent = expiry == -1L;

        FileConfiguration cfg = KoolAnarchyMod.getInstance().getConfig();
        String appealUrl = permanent
                ? cfg.getString("indefinite-ban-url", "CONFIGURE_ME")
                : cfg.getString("timed-ban-url",      "CONFIGURE_ME");

        Component kickMessage = permanent
                ? buildPermanentKickMessage(reason, banType, appealUrl)
                : buildTimedKickMessage(reason, banType, appealUrl, expiry);

        event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage);

        // Admin broadcast — note which type it is so staff know at a glance
        String banLabel = permanent ? "permanently" : "temporarily";
        FUtil.broadcast("kfc.admin",
                "<#ffb373><player><gray> tried joining, but they're <label> banned!",
                Placeholder.unparsed("player", event.getPlayer().getName()),
                Placeholder.unparsed("label", banLabel)
        );
    }

    // ======================================================
    // Kick Message Builders
    // ======================================================

    /**
     * Kick message for permanent (indefinite) bans.
     * Directs the player to the indefinite ban appeal template.
     */
    private Component buildPermanentKickMessage(Component reason, String banType, String appealUrl)
    {
        return FUtil.miniMessage("""
                <red><bold>You are permanently banned.</bold>
                
                <gray>Banned by: <white><ban_type>
                <gray>Reason: <white><reason>
                
                <gray>This ban has <red>no set expiry</red><gray>.
                <gray>You may appeal this ban at:
                <gold><appeal_url>
                
                <dark_gray><italic>Note: Permanent ban appeals have a higher bar.\
                 Be honest and specific in your appeal.</italic>
                """,
                Placeholder.component("reason", reason),
                Placeholder.unparsed("ban_type", banType),
                Placeholder.unparsed("appeal_url", appealUrl)
        );
    }

    /**
     * Kick message for timed bans.
     * Shows the exact expiry time and directs to the standard appeal template.
     */
    private Component buildTimedKickMessage(Component reason, String banType, String appealUrl, long expiryEpoch)
    {
        String expiryDisplay = EXPIRY_FORMAT.format(Instant.ofEpochMilli(expiryEpoch));
        long remaining       = expiryEpoch - System.currentTimeMillis();
        String timeLeft      = IndefiniteBanSystem.formatDuration(remaining);

        return FUtil.miniMessage("""
                <red><bold>You are temporarily banned.</bold>
                
                <gray>Banned by: <white><ban_type>
                <gray>Reason: <white><reason>
                
                <gray>Expires: <white><expiry>
                <gray>Time remaining: <white><time_left>
                
                <gray>You may appeal this ban at:
                <gold><appeal_url>
                """,
                Placeholder.component("reason", reason),
                Placeholder.unparsed("ban_type", banType),
                Placeholder.unparsed("appeal_url", appealUrl),
                Placeholder.unparsed("expiry", expiryDisplay),
                Placeholder.unparsed("time_left", timeLeft)
        );
    }
}