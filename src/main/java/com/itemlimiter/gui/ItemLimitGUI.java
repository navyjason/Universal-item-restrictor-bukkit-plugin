package com.itemlimiter.gui;

import com.itemlimiter.ItemLimiterPlugin;
import com.itemlimiter.managers.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Placeholder class for the UIR management GUI.
 *
 * <p>Currently opens a bare 54-slot inventory to confirm wiring is correct.
 * The full GUI implementation (showing restricted items, limited items, click-to-remove,
 * pagination, etc.) will be added in a future iteration.
 *
 * <h3>Planned features</h3>
 * <ul>
 *   <li>Page-able grid of restricted items (click item to remove from list)</li>
 *   <li>Page-able grid of limited items (click item to adjust limit)</li>
 *   <li>Dedicated "drop item here to restrict" slot</li>
 *   <li>Dedicated "drop item here to set limit" slot</li>
 *   <li>Search / filter bar</li>
 *   <li>Navigation buttons (prev / next page)</li>
 *   <li>Info pane showing statistics</li>
 * </ul>
 */
public class ItemLimitGUI {

    /** Title shown in the inventory; used to identify the GUI in click events. */
    public static final String GUI_TITLE =
            ChatColor.DARK_RED + "" + ChatColor.BOLD + "✦ UIR Manager";

    private final ItemLimiterPlugin plugin;
    private final ItemManager itemManager;

    public ItemLimitGUI(ItemLimiterPlugin plugin, ItemManager itemManager) {
        this.plugin       = plugin;
        this.itemManager  = itemManager;
    }

    /**
     * Opens the GUI for the given player.
     *
     * <p>Currently shows a placeholder layout.  Replace the body of this method
     * with real GUI logic when the full implementation is built.
     */
    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        // ── Placeholder border ────────────────────────────────────────────────
        ItemStack border = makeBorderPane();
        for (int slot : getBorderSlots()) {
            gui.setItem(slot, border);
        }

        // ── "Coming soon" notice in the centre ───────────────────────────────
        ItemStack notice = new ItemStack(Material.PAPER);
        ItemMeta  meta   = notice.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "GUI coming soon!");
            meta.setLore(List.of(
                    ChatColor.GRAY + "Use commands for now:",
                    ChatColor.AQUA  + "/ui restrict   " + ChatColor.GRAY + "– restrict item",
                    ChatColor.AQUA  + "/ui unrestrict " + ChatColor.GRAY + "– unrestrict item",
                    ChatColor.AQUA  + "/ui limit      " + ChatColor.GRAY + "– set quantity limit",
                    ChatColor.AQUA  + "/ui list       " + ChatColor.GRAY + "– view restriction list"
            ));
            notice.setItemMeta(meta);
        }
        gui.setItem(22, notice); // centre slot of a 54-slot chest

        player.openInventory(gui);
        player.sendMessage(ChatColor.DARK_AQUA + "[UIR] "
                + ChatColor.YELLOW + "Full GUI coming soon – use /ui commands in the meantime.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack makeBorderPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta  meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /** Returns the slot indices that form the outer border of a 54-slot chest. */
    private int[] getBorderSlots() {
        return new int[]{
                0,  1,  2,  3,  4,  5,  6,  7,  8,
                9,                              17,
                18,                             26,
                27,                             35,
                36,                             44,
                45, 46, 47, 48, 49, 50, 51, 52, 53
        };
    }
}
