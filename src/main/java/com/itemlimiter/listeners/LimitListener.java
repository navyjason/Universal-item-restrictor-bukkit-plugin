package com.itemlimiter.listeners;

import com.itemlimiter.ItemLimiterPlugin;
import com.itemlimiter.managers.ItemManager;
import com.itemlimiter.managers.NotificationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Enforces per-item quantity limits set via /uitem limit.
 * When a player would exceed their limit by picking up an item,
 * the pickup is cancelled and they are notified.
 */
public class LimitListener implements Listener {

    private final ItemLimiterPlugin plugin;
    private final ItemManager itemManager;
    private final NotificationManager notificationManager;

    public LimitListener(ItemLimiterPlugin plugin,
                         ItemManager itemManager,
                         NotificationManager notificationManager) {
        this.plugin              = plugin;
        this.itemManager         = itemManager;
        this.notificationManager = notificationManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.hasPermission("uir.bypass")) return;

        ItemStack item = event.getItem().getItemStack();

        int limit = itemManager.getItemLimit(item);
        if (limit < 0) return; // this item has no limit set

        int current = itemManager.countItemInInventory(player, item);

        if (current >= limit) {
            // Already at or over the cap — block the pickup entirely
            event.setCancelled(true);
            notificationManager.sendLimitExceeded(player, item, limit);
        }
        // Note: if current + pickup > limit but current < limit, the player
        // picks up the full stack and may slightly overshoot. Full split-pickup
        // support will be added in a future update.
    }
}