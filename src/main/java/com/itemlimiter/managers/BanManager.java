package com.itemlimiter.managers;

import com.itemlimiter.ItemLimiterPlugin;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the list of banned items.
 *
 * Items are stored as NamespacedKey strings (e.g. "minecraft:netherite_sword",
 * "mymod:op_sword") so modded items are fully supported without needing to
 * enumerate every Bukkit {@link org.bukkit.Material} value.
 */
public class BanManager {

    private final ItemLimiterPlugin plugin;
    /** Lower-cased NamespacedKey strings, e.g. "minecraft:diamond_sword". */
    private final Set<String> bannedKeys = new HashSet<>();

    public BanManager(ItemLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void loadBannedItems() {
        bannedKeys.clear();
        List<String> list = plugin.getConfig().getStringList("banned-items");
        for (String entry : list) {
            bannedKeys.add(entry.toLowerCase());
        }
        plugin.getLogger().info("Loaded " + bannedKeys.size() + " banned item(s).");
    }

    public void saveBannedItems() {
        plugin.getConfig().set("banned-items", List.copyOf(bannedKeys));
        plugin.saveConfig();
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the given ItemStack is on the ban list.
     * Null / air stacks always return {@code false}.
     */
    public boolean isBanned(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return bannedKeys.contains(item.getType().getKey().toString().toLowerCase());
    }

    /** Returns {@code true} if the raw NamespacedKey string is banned. */
    public boolean isBannedByKey(String key) {
        return bannedKeys.contains(key.toLowerCase());
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    /**
     * Bans an item by its held ItemStack.
     *
     * @return {@code true} if the item was newly added; {@code false} if it was
     *         already banned or the stack is null/air.
     */
    public boolean addBannedItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return addBannedItemByKey(item.getType().getKey().toString());
    }

    /**
     * Bans an item by its NamespacedKey string.
     *
     * @return {@code true} if the key was newly added.
     */
    public boolean addBannedItemByKey(String key) {
        boolean added = bannedKeys.add(key.toLowerCase());
        if (added) saveBannedItems();
        return added;
    }

    /**
     * Removes a ban by held ItemStack.
     *
     * @return {@code true} if the item was found and removed.
     */
    public boolean removeBannedItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return removeBannedItemByKey(item.getType().getKey().toString());
    }

    /**
     * Removes a ban by NamespacedKey string.
     *
     * @return {@code true} if the key was found and removed.
     */
    public boolean removeBannedItemByKey(String key) {
        boolean removed = bannedKeys.remove(key.toLowerCase());
        if (removed) saveBannedItems();
        return removed;
    }

    /** Returns an unmodifiable view of all banned NamespacedKey strings. */
    public Set<String> getBannedItems() {
        return Collections.unmodifiableSet(bannedKeys);
    }
}
