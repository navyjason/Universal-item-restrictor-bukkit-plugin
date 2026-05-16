package com.itemlimiter.listeners;

import com.itemlimiter.ItemLimiterPlugin;
import com.itemlimiter.managers.ItemManager;
import com.itemlimiter.managers.NotificationManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
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

    // Raw slot indices as reported by InventoryView / InventoryClickEvent.getRawSlot()
    // for the player's survival inventory (InventoryType.CRAFTING).
    // Layout: result(0), crafting(1-4), armor(5-8), main(9-35), hotbar(36-44), offhand(45)
    private static final int HELMET_SLOT     = 5;
    private static final int CHESTPLATE_SLOT = 6;
    private static final int LEGGINGS_SLOT   = 7;
    private static final int BOOTS_SLOT      = 8;

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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        // Some right-click-in-air interactions supply null; check the player's hands
        if (item == null || item.getType().isAir()) {
            ItemStack main = player.getInventory().getItemInMainHand();
            ItemStack off = player.getInventory().getItemInOffHand();
            try {
                if (event.getHand() == EquipmentSlot.OFF_HAND) item = off; else item = main;
            } catch (Exception e) {
                item = (main != null && !main.getType().isAir()) ? main : off;
            }
        }
        if (item == null || item.getType().isAir()) return;
        if (!itemManager.isRestricted(item)) return;

        // Block the equip action and notify
        event.setCancelled(true);
        notificationManager.sendCannotEquip(player, item);

        // Immediately remove any restricted armour that may have been equipped
        PlayerInventory invNow = player.getInventory();
        ItemStack[] armourNow = invNow.getArmorContents();
        boolean changedNow = false;
        for (int i = 0; i < armourNow.length; i++) {
            if (armourNow[i] != null && itemManager.isRestricted(armourNow[i])) {
                java.util.Map<Integer, ItemStack> leftover = invNow.addItem(armourNow[i].clone());
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                armourNow[i] = null;
                changedNow = true;
            }
        }
        if (changedNow) invNow.setArmorContents(armourNow);

        // Remove restricted armour on next tick in case it slipped through
        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerInventory inv2 = player.getInventory();
            ItemStack[] armour2 = inv2.getArmorContents();
            boolean changed2 = false;
            for (int i = 0; i < armour2.length; i++) {
                if (armour2[i] != null && itemManager.isRestricted(armour2[i])) {
                    java.util.Map<Integer, ItemStack> leftover = inv2.addItem(armour2[i].clone());
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                    armour2[i] = null;
                    changed2 = true;
                }
            }
            if (changed2) inv2.setArmorContents(armour2);
        });
    }

    // ── InventoryClickEvent ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (hasBypass(player)) return;

        // SlotType.ARMOR covers all vanilla armour slots regardless of which
        // container the player currently has open, making this reliable without
        // hardcoding view-specific raw slot numbers for the click cases.
        boolean clickedArmorSlot = event.getSlotType() == InventoryType.SlotType.ARMOR;
        int rawSlot = event.getRawSlot();

        switch (event.getAction()) {

            // Player explicitly places cursor item into an armour slot
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> {
                if (clickedArmorSlot) {
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
                if (clickedArmorSlot) {
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
