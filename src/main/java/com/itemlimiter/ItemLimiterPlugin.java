package com.itemlimiter;

import com.itemlimiter.commands.UIRCommand;
import com.itemlimiter.gui.ItemLimitGUI;
import com.itemlimiter.listeners.ArmorListener;
import com.itemlimiter.listeners.CombatListener;
import com.itemlimiter.listeners.CraftingListener;
import com.itemlimiter.listeners.ToolListener;
import com.itemlimiter.managers.ItemManager;
import com.itemlimiter.listeners.LimitListener;
import com.itemlimiter.managers.NotificationManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemLimiterPlugin extends JavaPlugin {

    private ItemManager itemManager;
    private NotificationManager notificationManager;
    private ItemLimitGUI itemLimitGUI;

    @Override
    public void onEnable() {
        // Config
        saveDefaultConfig();

        // Managers
        itemManager         = new ItemManager(this);
        notificationManager = new NotificationManager(this);
        itemLimitGUI        = new ItemLimitGUI(this, itemManager);

        itemManager.loadRestrictedItems();
        itemManager.loadLimitedItems();

        // Listeners
        getServer().getPluginManager().registerEvents(
                new CraftingListener(this, itemManager, notificationManager), this);
        getServer().getPluginManager().registerEvents(
                new CombatListener(this, itemManager, notificationManager), this);
        getServer().getPluginManager().registerEvents(
                new ArmorListener(this, itemManager, notificationManager), this);
        getServer().getPluginManager().registerEvents(
                new ToolListener(this, itemManager, notificationManager), this);
        getServer().getPluginManager().registerEvents(
                new LimitListener(this, itemManager, notificationManager), this);
        // Commands
        UIRCommand cmd = new UIRCommand(this, itemManager, itemLimitGUI);
        getCommand("uitem").setExecutor(cmd);
        getCommand("uitem").setTabCompleter(cmd);

        getLogger().info("ItemLimiter v" + getDescription().getVersion() + " enabled. "
                + itemManager.getRestrictedItems().size() + " restricted item(s), "
                + itemManager.getLimitedItems().size() + " limited item(s).");
    }

    @Override
    public void onDisable() {
        if (itemManager != null) {
            itemManager.saveRestrictedItems();
            itemManager.saveLimitedItems();
        }
        getLogger().info("ItemLimiter disabled.");
    }

    // ── Convenience re-load called by /il reload ──────────────────────────────

    public void fullReload() {
        reloadConfig();
        itemManager.loadRestrictedItems();
        itemManager.loadLimitedItems();
        notificationManager.reload();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public ItemManager getItemManager() {
        return itemManager;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public ItemLimitGUI getItemLimitGUI() {
        return itemLimitGUI;
    }
}
