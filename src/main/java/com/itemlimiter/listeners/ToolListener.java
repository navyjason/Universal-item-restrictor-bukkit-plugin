package com.itemlimiter.listeners;

import com.itemlimiter.ItemLimiterPlugin;
import com.itemlimiter.managers.ItemManager;
import com.itemlimiter.managers.NotificationManager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Cancels tool-specific interactions for banned items:
 *
 * <ul>
 *   <li><strong>Block-breaking</strong> ({@link BlockBreakEvent}) – cancelled so the
 *       banned tool gives no mining speed advantage and produces no drops.
 *       The player can still break the block with bare hands by switching
 *       the item away.</li>
 *   <li><strong>Right-click interactions</strong> ({@link PlayerInteractEvent}) –
 *       the item-use action is denied, which prevents:
 *       <ul>
 *         <li>Hoes tilling dirt/grass into farmland</li>
 *         <li>Shovels creating grass paths</li>
 *         <li>Axes stripping logs</li>
 *         <li>Flint &amp; Steel igniting blocks/portals</li>
 *         <li>Bows/crossbows/tridents being drawn</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>Note: combat (sword/axe damage) is handled separately by
 * {@link CombatListener} rather than being outright cancelled, so that hits
 * still register with bare-hand damage.
 */
public class ToolListener implements Listener {

    private final ItemLimiterPlugin plugin;
    private final ItemManager itemManager;
    private final NotificationManager notificationManager;

    public ToolListener(ItemLimiterPlugin plugin,
                        ItemManager itemManager,
                        NotificationManager notificationManager) {
        this.plugin              = plugin;
        this.itemManager         = itemManager;
        this.notificationManager = notificationManager;
    }

    // ── Right-click tool interactions ─────────────────────────────────────────

    /**
     * Denies the item-in-hand use action when the player right-clicks
     * with a banned tool.  Block placement from the off-hand is unaffected.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        // Only intercept right-click actions (tool use)
        switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK, RIGHT_CLICK_AIR -> { /* handled below */ }
            default -> { return; }
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (!itemManager.isRestricted(heldItem)) return;
        if (!isToolItem(heldItem)) return;

        // Deny the item-in-hand use; block interaction (e.g. opening chest) still works.
        event.setUseItemInHand(Event.Result.DENY);
        notificationManager.sendCannotUseTool(player, heldItem);
    }

    // ── Block-breaking ────────────────────────────────────────────────────────

    /**
     * Cancels block-breaking attempts with banned tools.
     *
     * <p>This prevents the tool from providing its speed bonus and ore drops.
     * The block is <em>not</em> broken at all; the player must switch to a
     * different item (including bare hand) to break it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (!itemManager.isRestricted(heldItem)) return;
        if (!isToolItem(heldItem)) return;

        event.setCancelled(true);
        notificationManager.sendCannotUseTool(player, heldItem);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} for vanilla tool types.
     *
     * <p>For modded tools registered on hybrid servers (Mohist, Arclight), the
     * name-based check will also catch items whose Material name follows the
     * standard Bukkit naming convention (e.g. {@code MYMOD_PICKAXE}).  Items
     * that don't follow the convention must be added to the ban list manually;
     * their combat damage will still be capped by {@link CombatListener}.
     */
    private boolean isToolItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        String name = item.getType().name().toUpperCase();

        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_SPADE")      // legacy name
                || name.endsWith("_HOE")
                || name.equals("SHEARS")
                || name.equals("FLINT_AND_STEEL")
                || name.equals("BOW")
                || name.equals("CROSSBOW")
                || name.equals("TRIDENT");
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("uir.bypass");
    }
}
