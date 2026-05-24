package eu.koolfreedom.staff;

import eu.koolfreedom.KoolAnarchyMod;
import eu.koolfreedom.util.FLog;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;

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

}
