package eu.koolfreedom.staff;

import eu.koolfreedom.KoolAnarchyMod;
import eu.koolfreedom.util.FLog;
import eu.koolfreedom.util.FUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class StaffRegistry
{
    private static StaffRegistry registry;
    private final File file;
    private final YamlConfiguration config;

    private StaffRegistry()
    {
        KoolAnarchyMod plugin = KoolAnarchyMod.getInstance();
        this.file = new File(plugin.getDataFolder(), "staff.yml");
        if (!file.exists())
        {
            plugin.saveResource("staff.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public static StaffRegistry get()
    {
        if (registry == null) registry = new StaffRegistry();
        return registry;
    }

    public void reload()
    {
        try
        {
            config.load(file);
            FLog.info("Loaded {0} staff entries.", config.getKeys(false).size());
        }
        catch (Exception e)
        {
            FLog.error("Failed to load staff registry", e);
        }
    }

    /**
     * Returns true if the sender is permitted to use KoolAnarchyMod moderation commands
     * Console senders are always permitted.
     * Players must have a matching username AND a matching IP in staff.yml
     */
    public boolean isStaff(CommandSender sender)
    {
        if (!(sender instanceof Player player))
        {
            return true;
        }

        if (player.getAddress() == null) return false;

        String username = player.getName().toLowerCase();
        String ip = player.getAddress().getAddress().getHostAddress();

        if (!config.contains(username)) return false;

        List<String> allowedIps = config.getStringList(username + ".ips");
        return allowedIps.stream().anyMatch(stored -> stored.equalsIgnoreCase(ip));
    }

    /**
     * Returns the StaffRole for a given username, or empty if not in the registry.
     */
    public Optional<StaffRole> getRole(String username)
    {
        if (username == null) return Optional.empty();
        String key = username.toLowerCase();
        if (!config.contains(key)) return Optional.empty();
        String roleStr = config.getString(key + ".role", "STAFF");
        return Optional.of(StaffRole.fromString(roleStr));
    }

    /**
     * Builds a server-wide login message for a staff member.
     * Returns empty if the player is not in the registry.
     */
    public Optional<Component> getLoginMessage(Player player)
    {
        Optional<StaffRole> role = getRole(player.getName());
        if (role.isEmpty()) return Optional.empty();

        String prepend = MiniMessage.miniMessage().serialize(KoolAnarchyMod.getInstance().mmDeserialize(
                "<aqua>" + player.getName() + " is "));

        String suffix = switch (role.get())
        {
            case OWNER -> "the <dark_red>Owner";
            case CO_OWNER -> "the <red>Co-Owner";
            case STAFF -> "a <gold>Staff Member";
        };

        return Optional.of(KoolAnarchyMod.getInstance().mmDeserialize(prepend + suffix));
    }
}
