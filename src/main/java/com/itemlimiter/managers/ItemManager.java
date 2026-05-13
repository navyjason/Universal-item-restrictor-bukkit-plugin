package com.itemlimiter.managers;

import com.itemlimiter.ItemLimiterPlugin;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages restricted items (cannot be used) and limited items (max quantity allowed).
 *
 * Items are stored as NamespacedKey strings (e.g. "minecraft:netherite_sword",
 * "mymod:op_sword") so modded items are fully supported without needing to
 * enumerate every Bukkit {@link org.bukkit.Material} value.
 */
public class ItemManager {

    private final ItemLimiterPlugin plugin;
    /** Lower-cased NamespacedKey strings for restricted items, e.g. "minecraft:diamond_sword". */
    private final Set<String> restrictedKeys = new HashSet<>();
    /** Mapping of item keys to their max quantities. */
    private final Map<String, Integer> limitedItems = new HashMap<>();

    public ItemManager(ItemLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Persistence for Restricted Items ───────────────────────────────────

    public void loadRestrictedItems() {
        restrictedKeys.clear();
        
        // Try new config key first
        List<String> list = plugin.getConfig().getStringList("restricted-items");
        
        // Fallback to old config key for backwards compatibility
        if (list.isEmpty()) {
            list = plugin.getConfig().getStringList("banned-items");
            if (!list.isEmpty()) {
                plugin.getLogger().info("Found old 'banned-items' config, migrating to 'restricted-items'...");
            }
        }
        
        for (String entry : list) {
            restrictedKeys.add(entry.toLowerCase());
        }
        plugin.getLogger().info("Loaded " + restrictedKeys.size() + " restricted item(s).");
    }

    public void saveRestrictedItems() {
        plugin.getConfig().set("restricted-items", List.copyOf(restrictedKeys));
        plugin.saveConfig();
    }

    // ── Persistence for Limited Items ──────────────────────────────────────

    public void loadLimitedItems() {
        limitedItems.clear();
        Map<String, Object> map = plugin.getConfig().getConfigurationSection("limited-items") != null
                ? plugin.getConfig().getConfigurationSection("limited-items").getValues(false)
                : Map.of();
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            try {
                String key = entry.getKey().toLowerCase();
                int qty = ((Number) entry.getValue()).intValue();
                if (qty > 0) {
                    limitedItems.put(key, qty);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid limit config for key: " + entry.getKey());
            }
        }
        plugin.getLogger().info("Loaded " + limitedItems.size() + " limited item(s).");
    }

    public void saveLimitedItems() {
        plugin.getConfig().set("limited-items", limitedItems);
        plugin.saveConfig();
    }

    // ── Query for Restricted Items ─────────────────────────────────────────

    /**
     * Returns {@code true} if the given ItemStack is on the restriction list.
     * Null / air stacks always return {@code false}.
     */
    public boolean isRestricted(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return restrictedKeys.contains(item.getType().getKey().toString().toLowerCase());
    }

    /** Returns {@code true} if the raw NamespacedKey string is restricted. */
    public boolean isRestrictedByKey(String key) {
        return restrictedKeys.contains(key.toLowerCase());
    }

    // ── Query for Limited Items ────────────────────────────────────────────

    /**
     * Returns the max quantity for a limited item, or -1 if not limited.
     */
    public int getItemLimit(ItemStack item) {
        if (item == null || item.getType().isAir()) return -1;
        String key = item.getType().getKey().toString().toLowerCase();
        return limitedItems.getOrDefault(key, -1);
    }

    /** Returns the max quantity for a limited item by key, or -1 if not limited. */
    public int getItemLimitByKey(String key) {
        return limitedItems.getOrDefault(key.toLowerCase(), -1);
    }

    /**
     * Returns how many of this item type the player is carrying.
     */
    public int countItemInInventory(org.bukkit.entity.Player player, ItemStack itemType) {
        if (itemType == null || itemType.getType().isAir()) return 0;
        int count = 0;
        for (ItemStack slot : player.getInventory().getContents()) {
            if (slot != null && slot.getType() == itemType.getType()) {
                count += slot.getAmount();
            }
        }
        return count;
    }

    // ── Mutation for Restricted Items ──────────────────────────────────────

    /**
     * Restricts an item by its held ItemStack.
     *
     * @return {@code true} if the item was newly added; {@code false} if it was
     *         already restricted or the stack is null/air.
     */
    public boolean addRestrictedItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return addRestrictedItemByKey(item.getType().getKey().toString());
    }

    /**
     * Restricts an item by its NamespacedKey string.
     *
     * @return {@code true} if the key was newly added.
     */
    public boolean addRestrictedItemByKey(String key) {
        boolean added = restrictedKeys.add(key.toLowerCase());
        if (added) saveRestrictedItems();
        return added;
    }

    /**
     * Removes a restriction by held ItemStack.
     *
     * @return {@code true} if the item was found and removed.
     */
    public boolean removeRestrictedItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return removeRestrictedItemByKey(item.getType().getKey().toString());
    }

    /**
     * Removes a restriction by NamespacedKey string.
     *
     * @return {@code true} if the key was found and removed.
     */
    public boolean removeRestrictedItemByKey(String key) {
        boolean removed = restrictedKeys.remove(key.toLowerCase());
        if (removed) saveRestrictedItems();
        return removed;
    }

    // ── Mutation for Limited Items ────────────────────────────────────────

    /**
     * Sets a limit on an item by its NamespacedKey string.
     *
     * @param key the item key
     * @param maxQuantity the maximum allowed quantity (must be > 0)
     * @return {@code true} if this changed the limit
     */
    public boolean setItemLimit(String key, int maxQuantity) {
        if (maxQuantity <= 0) return false;
        String lowerKey = key.toLowerCase();
        Integer old = limitedItems.put(lowerKey, maxQuantity);
        boolean changed = old == null || !old.equals(maxQuantity);
        if (changed) saveLimitedItems();
        return changed;
    }

    /**
     * Removes a limit on an item.
     *
     * @return {@code true} if the item was limited and is now unlimited.
     */
    public boolean removeItemLimit(String key) {
        boolean removed = limitedItems.remove(key.toLowerCase()) != null;
        if (removed) saveLimitedItems();
        return removed;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** Returns an unmodifiable view of all restricted NamespacedKey strings. */
    public Set<String> getRestrictedItems() {
        return Collections.unmodifiableSet(restrictedKeys);
    }

    /** Returns an unmodifiable view of all limited items and their max quantities. */
    public Map<String, Integer> getLimitedItems() {
        return Collections.unmodifiableMap(limitedItems);
    }
}
