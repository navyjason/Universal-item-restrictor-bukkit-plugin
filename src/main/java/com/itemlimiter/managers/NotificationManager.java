package com.itemlimiter.managers;

import com.itemlimiter.ItemLimiterPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sends action-bar notifications to players when they attempt to use a
 * restricted item or exceed inventory limits, with a per-player cooldown to prevent message spam.
 */
public class NotificationManager {

    private final ItemLimiterPlugin plugin;

    /** Last notification timestamp (ms) per player UUID. */
    private final Map<UUID, Long> lastNotified = new HashMap<>();

    private boolean enabled;
    private long cooldownMs;

    // Message templates (colour codes already translated)
    private String msgCannotUseTool;
    private String msgCannotCraft;
    private String msgCannotEquip;
    private String msgLimitExceeded;

    public NotificationManager(ItemLimiterPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        enabled    = plugin.getConfig().getBoolean("settings.notify-player", true);
        cooldownMs = plugin.getConfig().getLong("settings.notify-cooldown-seconds", 3) * 1000L;

        msgCannotUseTool = colorize(plugin.getConfig().getString(
                "messages.cannot-use-tool", "&cYou cannot use &e%item% &cas a tool!"));
        msgCannotCraft   = colorize(plugin.getConfig().getString(
                "messages.cannot-craft", "&cYou cannot use &e%item% &cin crafting!"));
        msgCannotEquip   = colorize(plugin.getConfig().getString(
                "messages.cannot-equip", "&cYou cannot equip &e%item%!"));
        msgLimitExceeded = colorize(plugin.getConfig().getString(
                "messages.limit-exceeded", "&cYou have reached the limit for &e%item%&c! (Max: %limit%)"));
    }

    // ── Public send helpers ───────────────────────────────────────────────────

    public void sendCannotUseTool(Player player, ItemStack item) {
        send(player, resolve(msgCannotUseTool, item, -1));
    }

    public void sendCannotCraft(Player player, ItemStack item) {
        send(player, resolve(msgCannotCraft, item, -1));
    }

    public void sendCannotEquip(Player player, ItemStack item) {
        send(player, resolve(msgCannotEquip, item, -1));
    }

    public void sendLimitExceeded(Player player, ItemStack item, int limit) {
        send(player, resolve(msgLimitExceeded, item, limit));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void send(Player player, String message) {
        if (!enabled) return;

        long now = System.currentTimeMillis();
        UUID uid = player.getUniqueId();
        Long last = lastNotified.get(uid);

        if (last != null && (now - last) < cooldownMs) return;

        lastNotified.put(uid, now);
        // Use action-bar so it doesn't clutter chat
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(message));
    }

    private String resolve(String template, ItemStack item, int limit) {
        String key = (item != null && !item.getType().isAir())
                ? item.getType().getKey().toString()
                : "unknown";
        String result = template.replace("%item%", key);
        if (limit > 0) {
            result = result.replace("%limit%", String.valueOf(limit));
        }
        return result;
    }

    private static String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
