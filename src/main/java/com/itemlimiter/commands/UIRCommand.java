package com.itemlimiter.commands;

import com.itemlimiter.ItemLimiterPlugin;
import com.itemlimiter.managers.ItemManager;
import com.itemlimiter.gui.ItemLimitGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles the {@code /uitem} (aliases: {@code /ui})
 * command family.
 *
 * <pre>
 * /uitem restrict [item_key]     – restrict the held item, or a key typed directly
 * /uitem unrestrict [item_key]   – unrestrict the held item, or a key typed directly
 * /uitem limit <item_key> <qty>  – set max quantity for an item
 * /uitem removelimit <item_key>  – remove quantity limit from an item
 * /uitem list                    – print all restricted items
 * /uitem listlimit               – print all limited items
 * /uitem reload                  – reload config from disk
 * /uitem gui                     – open the management GUI
 * </pre>
 *
 * All sub-commands require {@code uir.admin} permission (default: op).
 */
public class UIRCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "uir.admin";

    private static final String PREFIX =
            ChatColor.DARK_AQUA + "[" + ChatColor.AQUA + "UIR"
                    + ChatColor.DARK_AQUA + "] " + ChatColor.RESET;

    private final ItemLimiterPlugin plugin;
    private final ItemManager itemManager;
    private final ItemLimitGUI itemLimitGUI;

    public UIRCommand(ItemLimiterPlugin plugin,
                      ItemManager itemManager,
                      ItemLimitGUI itemLimitGUI) {
        this.plugin         = plugin;
        this.itemManager    = itemManager;
        this.itemLimitGUI   = itemLimitGUI;
    }

    // ── CommandExecutor ───────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "restrict"    -> doRestrict(sender, args);
            case "unrestrict"  -> doUnrestrict(sender, args);
            case "limit"       -> doLimit(sender, args);
            case "removelimit" -> doRemoveLimit(sender, args);
            case "list"        -> doList(sender);
            case "listlimit"   -> doListLimit(sender);
            case "reload"      -> doReload(sender);
            case "gui"         -> doGui(sender);
            default            -> sendHelp(sender, label);
        }

        return true;
    }

    // ── Sub-commands ──────────────────────────────────────────────────────────

    private void doRestrict(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            String key = args[1].toLowerCase();
            if (itemManager.isRestrictedByKey(key)) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + key + " is already restricted.");
                return;
            }
            itemManager.addRestrictedItemByKey(key);
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Restricted: " + ChatColor.WHITE + key);
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED
                    + "Console must supply an item key: /uitem restrict <namespace:id>");
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            sender.sendMessage(PREFIX + ChatColor.RED
                    + "Hold an item or specify a key: /uitem restrict <namespace:id>");
            return;
        }

        String key = held.getType().getKey().toString();
        if (itemManager.isRestricted(held)) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + key + " is already restricted.");
            return;
        }

        itemManager.addRestrictedItem(held);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Restricted: " + ChatColor.WHITE + key);
    }

    private void doUnrestrict(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            String key = args[1].toLowerCase();
            if (!itemManager.isRestrictedByKey(key)) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + key + " is not on the restriction list.");
                return;
            }
            itemManager.removeRestrictedItemByKey(key);
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Unrestricted: " + ChatColor.WHITE + key);
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED
                    + "Console must supply a key: /uitem unrestrict <namespace:id>");
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            sender.sendMessage(PREFIX + ChatColor.RED
                    + "Hold an item or specify a key: /uitem unrestrict <namespace:id>");
            return;
        }

        String key = held.getType().getKey().toString();
        if (!itemManager.isRestricted(held)) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + key + " is not on the restriction list.");
            return;
        }

        itemManager.removeRestrictedItem(held);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Unrestricted: " + ChatColor.WHITE + key);
    }

    private void doLimit(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /uitem limit <item_key> <quantity>");
            return;
        }

        String key = args[1].toLowerCase();
        try {
            int qty = Integer.parseInt(args[2]);
            if (qty <= 0) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Quantity must be greater than 0.");
                return;
            }
            itemManager.setItemLimit(key, qty);
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Limited: " + ChatColor.WHITE + key 
                    + ChatColor.GREEN + " to " + ChatColor.WHITE + qty);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Invalid quantity: " + args[2]);
        }
    }

    private void doRemoveLimit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /uitem removelimit <item_key>");
            return;
        }

        String key = args[1].toLowerCase();
        if (itemManager.getItemLimitByKey(key) <= 0) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + key + " does not have a limit.");
            return;
        }

        itemManager.removeItemLimit(key);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Removed limit for: " + ChatColor.WHITE + key);
    }

    private void doList(CommandSender sender) {
        Set<String> items = itemManager.getRestrictedItems();
        if (items.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "The restriction list is empty.");
            return;
        }

        sender.sendMessage(PREFIX + ChatColor.GOLD + "Restricted items (" + items.size() + "):");
        items.stream()
                .sorted()
                .forEach(key -> sender.sendMessage(
                        ChatColor.DARK_GRAY + "  • " + ChatColor.WHITE + key));
    }

    private void doListLimit(CommandSender sender) {
        Map<String, Integer> items = itemManager.getLimitedItems();
        if (items.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "No items have quantity limits.");
            return;
        }

        sender.sendMessage(PREFIX + ChatColor.GOLD + "Limited items (" + items.size() + "):");
        items.entrySet().stream()
                .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .forEach(e -> sender.sendMessage(
                        ChatColor.DARK_GRAY + "  • " + ChatColor.WHITE + e.getKey()
                                + ChatColor.GRAY + " (max: " + ChatColor.AQUA + e.getValue()
                                + ChatColor.GRAY + ")"));
    }

    private void doReload(CommandSender sender) {
        plugin.fullReload();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuration reloaded. "
                + itemManager.getRestrictedItems().size() + " restricted item(s), "
                + itemManager.getLimitedItems().size() + " limited item(s).");
    }

    private void doGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "The GUI can only be opened by players.");
            return;
        }
        itemLimitGUI.open(player);
    }

    // ── TabCompleter ──────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {

        if (!sender.hasPermission(PERM)) return List.of();

        if (args.length == 1) {
            List<String> subs = Arrays.asList("restrict", "unrestrict", "limit", "removelimit", 
                                             "list", "listlimit", "reload", "gui");
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // For unrestrict and removelimit, suggest existing items
            if (args[0].equalsIgnoreCase("unrestrict")) {
                String partial = args[1].toLowerCase();
                return itemManager.getRestrictedItems().stream()
                        .filter(k -> k.startsWith(partial))
                        .sorted()
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("removelimit")) {
                String partial = args[1].toLowerCase();
                return itemManager.getLimitedItems().keySet().stream()
                        .filter(k -> k.startsWith(partial))
                        .sorted()
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(PREFIX + ChatColor.GOLD + "Commands:");
        line(sender, label, "restrict [key]",    "Restrict held item or item by key");
        line(sender, label, "unrestrict [key]",  "Unrestrict held item or item by key");
        line(sender, label, "limit <key> <qty>", "Set max quantity for an item");
        line(sender, label, "removelimit <key>", "Remove quantity limit from an item");
        line(sender, label, "list",              "List all restricted items");
        line(sender, label, "listlimit",         "List all limited items");
        line(sender, label, "reload",            "Reload config from disk");
        line(sender, label, "gui",               "Open the management GUI");
    }

    private void line(CommandSender sender, String label, String args, String desc) {
        sender.sendMessage(
                ChatColor.AQUA + "  /" + label + " " + args
                        + ChatColor.DARK_GRAY + " – "
                        + ChatColor.GRAY + desc);
    }
}
