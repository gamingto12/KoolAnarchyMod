package eu.koolfreedom.ban;

import eu.koolfreedom.util.FLog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import eu.koolfreedom.KoolAnarchyMod;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IndefiniteBanSystem
{
    private static IndefiniteBanSystem instance;
    private final File file;
    private final YamlConfiguration config;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Configuration path constants for consistency and maintainability
    private static final String UUID_KEY = "uuid";
    private static final String IPS_KEY = "ips";
    private static final String REASON_KEY = "reason";
    private static final String EXPIRY_KEY = "expires";
    public static final String DEFAULT_REASON = "You've met with a terrible fate, haven't you?";

    private IndefiniteBanSystem()
    {
        KoolAnarchyMod plugin = KoolAnarchyMod.getInstance();
        this.file = new File(plugin.getDataFolder(), "bans.yml");
        if (!file.exists())
        {
            plugin.saveResource("bans.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    private static final Pattern DURATION_PATTERN =
            Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");

    public static IndefiniteBanSystem get()
    {
        if (instance == null)
        {
            instance = new IndefiniteBanSystem();
        }
        return instance;
    }

    public void save()
    {
        try
        {
            config.save(file);
        } catch (IOException e) {
            FLog.error("Failed to save permbans file!", e);
        }
    }

    public void reload()
    {
        try
        {
            config.load(file);
            FLog.info("Loaded {0} permban entries", getBansCount());
        } catch (Exception e)
        {
            FLog.error("Failed to reload permbans file!", e);
        }
    }

    public int getBansCount()
    {
        return config.getKeys(false).size();
    }

    // ======================================================
    // Duration Parsing
    // ======================================================
    /**
     * Parses a readable duration string into milliseconds
     * Supports: 1d, 2h, 30m, 10s, and combinations like 1d12h30m
     *
     * @param input the duration string
     * @return duration in milliseconds, or -1 if the ban is permanent
     */
    public static long parseDuration(String input)
    {
        if (input == null || input.isBlank()) return -1L;

        Matcher matcher = DURATION_PATTERN.matcher(input.trim().toLowerCase());
        if (!matcher.matches()) return -1L;

        long days    = matcher.group(1) != null ? Long.parseLong(matcher.group(1)) : 0;
        long hours   = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : 0;
        long minutes = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : 0;
        long seconds = matcher.group(4) != null ? Long.parseLong(matcher.group(4)) : 0;

        long total = (days * 86_400_000L)
                + (hours * 3_600_000L)
                + (minutes * 60_000L)
                + (seconds * 1_000L);

        return total > 0 ? total : -1L;
    }

    /**
     * Formats a remaining-time duration (ms) into a human-readable string.
     */
    public static String formatDuration(long ms)
    {
        if (ms <= 0) return "permanent";

        long seconds = ms / 1000;
        long days    = seconds / 86400; seconds %= 86400;
        long hours   = seconds / 3600;  seconds %= 3600;
        long minutes = seconds / 60;    seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)    sb.append(days).append("d ");
        if (hours > 0)   sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    // ======================================================
    // Helper Methods
    // ======================================================

    /**
     * Builds a configuration path for a ban entry key.
     *
     * @param name the player name (will be lowercased)
     * @param subkey the subkey (uuid, ips, reason)
     * @return the full configuration path
     */
    private String buildPath(String name, String subkey) {
        return name.toLowerCase() + "." + subkey;
    }

    /**
     * Gets the UUID stored for a banned player.
     */
    private Optional<String> getStoredUuid(String name) {
        String uuid = config.getString(buildPath(name, UUID_KEY));
        return Optional.ofNullable(uuid);
    }

    /**
     * Gets the IPs associated with a banned entry (safe copy).
     */
    @SuppressWarnings("ConstantConditions")
    private List<String> getStoredIps(String name) {
        List<String> ips = config.getStringList(buildPath(name, IPS_KEY));
        return ips != null ? ips : Collections.emptyList();
    }

    /**
     * Returns the expiry timestamp for a ban, -1 if permanent
     * @param name the user's name
     * @return duration
     */
    public long getExpiry(String name)
    {
        return config.getLong(buildPath(name.toLowerCase(), EXPIRY_KEY), -1L);
    }

    /**
     * Returns whether a ban entry has expired (and removes it if so).
     */
    private boolean isExpired(String name)
    {
        long expiry = getExpiry(name);
        if (expiry == -1L) return false; // permanent

        if (System.currentTimeMillis() > expiry)
        {
            // Auto-remove expired ban
            config.set(name.toLowerCase(), null);
            save();
            FLog.info("Auto-expired ban for {0}", name);
            return true;
        }
        return false;
    }

    public void pruneExpired()
    {
        // Snapshot the keys first to avoid ConcurrentModificationException
        List<String> keys = new java.util.ArrayList<>(config.getKeys(false));
        int removed = 0;

        for (String key : keys)
        {
            if (isExpired(key)) removed++;
        }

        if (removed > 0)
        {
            FLog.info("Pruned {0} expired ban entr{1}.", removed, removed == 1 ? "y" : "ies");
        }
    }

    // ======================================================
    // Ban Management
    // ======================================================

    public boolean isBanned(Player player) {
        if (player == null) {
            return false;
        }

        String name = player.getName();
        String uuid = player.getUniqueId().toString();
        String ip = getPlayerIp(player);

        return isNameBanned(name)
                || isUuidBanned(uuid)
                || (ip != null && isIpBanned(ip));
    }

    /**
     * Safely extracts the player's IP address.
     */
    private String getPlayerIp(Player player) {
        try {
            return player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        } catch (Exception e) {
            FLog.warning("Failed to get IP for player {0}", player.getName());
            return null;
        }
    }

    public boolean isNameBanned(String name)
    {
        if (name == null) return false;
        String key = name.toLowerCase();
        if (!config.contains(key)) return false;
        return !isExpired(key); // expire check cleans up automatically
    }

    public boolean isUuidBanned(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }

        final String uuidLower = uuid.toLowerCase();
        return config.getKeys(false).stream()
                .anyMatch(key -> {
                    String stored = getStoredUuid(key).orElse(null);
                    return stored != null && stored.equalsIgnoreCase(uuidLower);
                });
    }

    public boolean isIpBanned(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        return config.getKeys(false).stream()
                .anyMatch(key -> getStoredIps(key).contains(ip));
    }

    // ======================================================
    // Get Ban Info
    // ======================================================

    public String getReason(String name) {
        return config.getString(buildPath(name, REASON_KEY), DEFAULT_REASON);
    }

    public Component getReasonComponent(String name) {
        String reason = getReason(name);
        if (reason.startsWith("<")) {
            // MiniMessage format
            return mm.deserialize(reason);
        } else {
            // Legacy color format (&c, &4, etc.)
            return LegacyComponentSerializer.legacyAmpersand().deserialize(reason);
        }
    }

    /**
     * Returns a human-readable expiry string for a ban.
     * Examples: "permanent", "6d 12h 30m", "expires in 2h"
     */
    public String getExpiryDisplay(String name)
    {
        long expiry = getExpiry(name);
        if (expiry == -1L) return "permanent";
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) return "expired";
        return formatDuration(remaining);
    }

    // ======================================================
    // Ban / Unban
    // ======================================================

    /**
     * Bans an online player.
     *
     * @param player   the player to ban
     * @param reason   ban reason (null = default)
     * @param duration duration string like "7d", "1h30m", or null/empty for permanent
     */
    public void banPlayer(Player player, String reason, String duration)
    {
        Objects.requireNonNull(player, "Player cannot be null");

        String name = player.getName().toLowerCase();
        String uuid = player.getUniqueId().toString();
        String ip   = getPlayerIp(player);

        if (ip == null)
            throw new IllegalArgumentException("Cannot ban player without a valid IP address");

        long expiry = computeExpiry(duration);

        config.set(buildPath(name, UUID_KEY), uuid);
        config.set(buildPath(name, EXPIRY_KEY), expiry);

        List<String> ips = new ArrayList<>(getStoredIps(name));
        if (!ips.contains(ip)) { ips.add(ip); config.set(buildPath(name, IPS_KEY), ips); }

        config.set(buildPath(name, REASON_KEY), reason != null ? reason : DEFAULT_REASON);
        save();
    }

    /** Convenience overload — permanent ban. */
    public void banPlayer(Player player, String reason)
    {
        banPlayer(player, reason, null);
    }

    /**
     * Bans an OfflinePlayer.
     *
     * @param player   the player to ban
     * @param reason   ban reason (null = default)
     * @param duration duration string, or null for permanent
     */
    public void banOfflinePlayer(OfflinePlayer player, String reason, String duration)
    {
        Objects.requireNonNull(player, "Player cannot be null");

        String name = player.getName().toLowerCase();
        String uuid = player.getUniqueId().toString();
        long expiry = computeExpiry(duration);

        config.set(buildPath(name, UUID_KEY), uuid);
        config.set(buildPath(name, EXPIRY_KEY), expiry);

        if (player.isOnline() && player.getPlayer() != null)
        {
            String ip = getPlayerIp(player.getPlayer());
            if (ip != null)
            {
                List<String> ips = new ArrayList<>(getStoredIps(name));
                if (!ips.contains(ip)) { ips.add(ip); config.set(buildPath(name, IPS_KEY), ips); }
            }
        }

        config.set(buildPath(name, REASON_KEY), reason != null ? reason : DEFAULT_REASON);
        save();
    }

    /** Convenience overload — permanent ban. */
    public void banOfflinePlayer(OfflinePlayer player, String reason)
    {
        banOfflinePlayer(player, reason, null);
    }

    /**
     * Computes the expiry epoch timestamp from a duration string.
     * Returns -1 for permanent bans.
     */
    private long computeExpiry(String duration)
    {
        long ms = parseDuration(duration);
        return ms > 0 ? System.currentTimeMillis() + ms : -1L;
    }

    public boolean unbanPlayer(String name)
    {
        if (name == null || name.isEmpty()) return false;
        name = name.toLowerCase();
        if (config.contains(name)) { config.set(name, null); save(); return true; }
        return false;
    }

    public boolean unbanPlayerByUuid(String uuid)
    {
        return findBannedNameByUuid(uuid).filter(this::unbanPlayer).isPresent();
    }

    public boolean unbanPlayerByIp(String ip)
    {
        return findBannedNameByIp(ip).filter(this::unbanPlayer).isPresent();
    }

    public Optional<String> findBannedNameByIp(String ip)
    {
        if (ip == null || ip.isEmpty()) return Optional.empty();
        return config.getKeys(false).stream()
                .filter(key -> getStoredIps(key).contains(ip))
                .findFirst();
    }

    public Optional<String> findBannedNameByUuid(String uuid)
    {
        if (uuid == null || uuid.isEmpty()) return Optional.empty();
        final String uuidLower = uuid.toLowerCase();
        return config.getKeys(false).stream()
                .filter(key -> {
                    String stored = getStoredUuid(key).orElse(null);
                    return stored != null && stored.equalsIgnoreCase(uuidLower);
                })
                .findFirst();
    }
}
