package com.itemlimiter.listeners;

import com.itemlimiter.ItemLimiterPlugin;
import com.itemlimiter.managers.ItemManager;
import com.itemlimiter.managers.NotificationManager;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents banned items from being used as crafting ingredients.
 *
 * <p>Two events are intercepted for robustness:
 * <ul>
 *   <li>{@link PrepareItemCraftEvent} – clears the result preview so the player
 *       can see immediately that the recipe is blocked.</li>
 *   <li>{@link CraftItemEvent} – hard-cancels the actual craft as a safety net
 *       (e.g. in case another plugin re-sets the result after PrepareItemCraft).</li>
 * </ul>
 *
 * <p>Note: banned items can still <em>be crafted</em> (as the output), they just
 * cannot be placed in the ingredient grid.
 */
public class CraftingListener implements Listener {

    private final ItemLimiterPlugin plugin;
    private final ItemManager itemManager;
    private final NotificationManager notificationManager;

    public CraftingListener(ItemLimiterPlugin plugin,
                            ItemManager itemManager,
                            NotificationManager notificationManager) {
        this.plugin              = plugin;
        this.itemManager         = itemManager;
        this.notificationManager = notificationManager;
    }

    // ── PrepareItemCraftEvent – clear result preview ──────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        ItemStack banned = findBannedIngredient(event.getInventory().getMatrix());
        if (banned == null) return;

        // Clear the output slot so the recipe shows as invalid
        event.getInventory().setResult(null);

        // Notify the viewing players (usually just one)
        for (HumanEntity viewer : event.getInventory().getViewers()) {
            if (viewer instanceof Player player && !hasBypass(player)) {
                notificationManager.sendCannotCraft(player, banned);
            }
        }
    }

    // ── CraftItemEvent – hard cancel ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (hasBypass(player)) return;

        ItemStack banned = findBannedIngredient(event.getInventory().getMatrix());
        if (banned == null) return;

        event.setCancelled(true);
        notificationManager.sendCannotCraft(player, banned);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the first banned ingredient found in the crafting matrix,
     * or {@code null} if no ingredient is banned.
     */
    private ItemStack findBannedIngredient(ItemStack[] matrix) {
        for (ItemStack ingredient : matrix) {
            if (ingredient != null && itemManager.isRestricted(ingredient)) {
                return ingredient;
            }
        }
        return null;
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("uir.bypass");
    }
}
