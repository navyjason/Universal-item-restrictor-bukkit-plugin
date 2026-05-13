package com.itemlimiter.listeners;

import com.itemlimiter.ItemLimiterPlugin;
import com.itemlimiter.managers.ItemManager;
import com.itemlimiter.managers.NotificationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Prevents banned items from occupying a player's armour slots.
 *
 * <h3>Covered equip vectors</h3>
 * <ul>
 *   <li>Left-click placing into a slot</li>
 *   <li>Shift-click auto-equip</li>
 *   <li>Drag-split into an armour slot</li>
 *   <li>Hotkey number-swap into an armour slot</li>
 *   <li>Player logs in with already-equipped banned armour
 *       (moves piece to inventory on join)</li>
 * </ul>
 *
 * <p>Items can still exist freely in non-armour inventory slots.
 */
public class ArmorListener implements Listener {

    // Raw slot indices in the PlayerInventory view.
    // (These are the INVENTORY raw slots, not the InventoryView raw slots.)
    private static final int HELMET_SLOT     = 39;
    private static final int CHESTPLATE_SLOT = 38;
    private static final int LEGGINGS_SLOT   = 37;
    private static final int BOOTS_SLOT      = 36;

    private final ItemLimiterPlugin plugin;
    private final ItemManager itemManager;
    private final NotificationManager notificationManager;

    public ArmorListener(ItemLimiterPlugin plugin,
                         ItemManager itemManager,
                         NotificationManager notificationManager) {
        this.plugin              = plugin;
        this.itemManager         = itemManager;
        this.notificationManager = notificationManager;
    }

    // ── Login check ───────────────────────────────────────────────────────────

    /**
     * Removes any banned armour already on the player when they join.
     * Items are moved to their inventory (or dropped if full).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        PlayerInventory inv = player.getInventory();
        ItemStack[] armour = inv.getArmorContents();
        boolean changed = false;

        for (int i = 0; i < armour.length; i++) {
            if (armour[i] != null && itemManager.isRestricted(armour[i])) {
                // Try to give back to inventory; drop on ground if no room
                java.util.Map<Integer, ItemStack> leftover =
                        inv.addItem(armour[i].clone());
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                armour[i] = null;
                changed   = true;
            }
        }

        if (changed) {
            inv.setArmorContents(armour);
        }
    }

    // ── InventoryClickEvent ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (hasBypass(player)) return;

        int rawSlot = event.getRawSlot();

        switch (event.getAction()) {

            // Player explicitly places cursor item into a slot
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> {
                if (isArmorSlot(rawSlot)) {
                    ItemStack cursor = event.getCursor();
                    if (cursor != null && itemManager.isRestricted(cursor)) {
                        event.setCancelled(true);
                        notificationManager.sendCannotEquip(player, cursor);
                    }
                }
            }

            // Shift-click: auto-equip into armour slot
            case MOVE_TO_OTHER_INVENTORY -> {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && itemManager.isRestricted(clicked)
                        && isArmorItem(clicked)) {
                    event.setCancelled(true);
                    notificationManager.sendCannotEquip(player, clicked);
                }
            }

            // Hotkey (1–9) swap while hovering an armour slot
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                if (isArmorSlot(rawSlot)) {
                    ItemStack hotbarItem = getHotbarItem(player, event.getHotbarButton());
                    if (hotbarItem != null && itemManager.isRestricted(hotbarItem)) {
                        event.setCancelled(true);
                        notificationManager.sendCannotEquip(player, hotbarItem);
                    }
                }
            }

            default -> { /* not interested */ }
        }
    }

    // ── InventoryDragEvent ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (hasBypass(player)) return;

        ItemStack dragged = event.getOldCursor();
        if (dragged == null || dragged.getType().isAir()) return;
        if (!itemManager.isRestricted(dragged)) return;

        for (int slot : event.getRawSlots()) {
            if (isArmorSlot(slot)) {
                event.setCancelled(true);
                notificationManager.sendCannotEquip(player, dragged);
                return;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isArmorSlot(int rawSlot) {
        return rawSlot == HELMET_SLOT
                || rawSlot == CHESTPLATE_SLOT
                || rawSlot == LEGGINGS_SLOT
                || rawSlot == BOOTS_SLOT;
    }

    /**
     * Determines if an ItemStack can go into an armour slot by querying the
     * Material's EquipmentSlot.  Works for all vanilla materials and for modded
     * items registered by hybrid server implementations (Mohist, Arclight, etc.)
     * that set a valid EquipmentSlot on their Material entries.
     */
    private boolean isArmorItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        try {
            EquipmentSlot slot = item.getType().getEquipmentSlot();
            return slot == EquipmentSlot.HEAD
                    || slot == EquipmentSlot.CHEST
                    || slot == EquipmentSlot.LEGS
                    || slot == EquipmentSlot.FEET;
        } catch (Exception e) {
            // Fallback: name-based heuristic for mods that don't set equipment slot
            String name = item.getType().name().toUpperCase();
            return name.endsWith("_HELMET")
                    || name.endsWith("_CHESTPLATE")
                    || name.endsWith("_LEGGINGS")
                    || name.endsWith("_BOOTS")
                    || name.equals("TURTLE_HELMET")
                    || name.equals("CARVED_PUMPKIN");
        }
    }

    private ItemStack getHotbarItem(Player player, int hotbarSlot) {
        if (hotbarSlot < 0) return null;
        return player.getInventory().getItem(hotbarSlot);
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("uir.bypass");
    }
}
