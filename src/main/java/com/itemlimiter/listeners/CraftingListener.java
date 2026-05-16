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
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

/**
 * Prevents banned items from being used as crafting or smithing ingredients.
 *
 * <h3>Covered stations</h3>
 * <ul>
 *   <li><strong>Crafting table / 2x2 inventory grid</strong> –
 *       {@link PrepareItemCraftEvent} clears the result preview; {@link CraftItemEvent}
 *       hard-cancels as a safety net.</li>
 *   <li><strong>Smithing table</strong> –
 *       {@link PrepareSmithingEvent} clears the result preview; {@link SmithItemEvent}
 *       hard-cancels the upgrade. All three input slots (template, base item,
 *       addition/upgrade material) are checked.</li>
 * </ul>
 *
 * <p>Note: banned items can still <em>be crafted as the output</em> – they just
 * cannot be used as ingredients in either station.
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

    // ── Crafting table ────────────────────────────────────────────────────────

    /**
     * Clears the crafting result preview when any ingredient is banned.
     * The player sees an empty output slot immediately, signalling the recipe
     * is blocked.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        ItemStack banned = findBannedIngredient(event.getInventory().getMatrix());
        if (banned == null) return;

        event.getInventory().setResult(null);

        for (HumanEntity viewer : event.getInventory().getViewers()) {
            if (viewer instanceof Player player && !hasBypass(player)) {
                notificationManager.sendCannotCraft(player, banned);
            }
        }
    }

    /**
     * Hard-cancels the actual craft as a safety net in case another plugin
     * re-sets the result after {@link PrepareItemCraftEvent}.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (hasBypass(player)) return;

        ItemStack banned = findBannedIngredient(event.getInventory().getMatrix());
        if (banned == null) return;

        event.setCancelled(true);
        notificationManager.sendCannotCraft(player, banned);
    }

    // ── Smithing table ────────────────────────────────────────────────────────

    /**
     * Clears the smithing result preview when any input slot contains a banned
     * item. Covers the template slot, the base-equipment slot, and the
     * addition/upgrade-material slot.
     *
     * <p>{@link PrepareSmithingEvent} cannot be cancelled directly; clearing
     * the result is the correct way to block the operation at the preview stage.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        ItemStack banned = findBannedSmithingInput(inv);
        if (banned == null) return;

        event.setResult(null);

        for (HumanEntity viewer : inv.getViewers()) {
            if (viewer instanceof Player player && !hasBypass(player)) {
                notificationManager.sendCannotCraft(player, banned);
            }
        }
    }

    /**
     * Hard-cancels the smithing operation itself as a safety net, matching the
     * dual-event pattern used for crafting tables.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmithItem(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (hasBypass(player)) return;

        SmithingInventory inv = event.getInventory();
        ItemStack banned = findBannedSmithingInput(inv);
        if (banned == null) return;

        event.setCancelled(true);
        notificationManager.sendCannotCraft(player, banned);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the first banned ingredient in a crafting matrix,
     * or {@code null} if none are banned.
     */
    private ItemStack findBannedIngredient(ItemStack[] matrix) {
        for (ItemStack ingredient : matrix) {
            if (ingredient != null && itemManager.isRestricted(ingredient)) {
                return ingredient;
            }
        }
        return null;
    }

    /**
     * Checks all smithing input slots for a banned item.
     *
     * <p>In Minecraft 1.20+, {@link SmithingInventory} has three input slots:
     * <ol>
     *   <li>Slot 0 – smithing template</li>
     *   <li>Slot 1 – base equipment piece</li>
     *   <li>Slot 2 – upgrade material (e.g. netherite ingot)</li>
     * </ol>
     * The result sits at slot 3 and is never an ingredient, so we skip it.
     *
     * @return the first banned input stack, or {@code null}
     */
    private ItemStack findBannedSmithingInput(SmithingInventory inv) {
        // getContents() returns all slots including the result at the last index.
        // We iterate only the input slots (all except the last).
        ItemStack[] contents = inv.getContents();
        int inputSlots = Math.max(0, contents.length - 1); // exclude result slot
        for (int i = 0; i < inputSlots; i++) {
            ItemStack item = contents[i];
            if (item != null && itemManager.isRestricted(item)) {
                return item;
            }
        }
        return null;
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("uir.bypass");
    }
}
