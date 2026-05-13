package com.itemlimiter.listeners;

import com.itemlimiter.ItemLimiterPlugin;
import com.itemlimiter.managers.ItemManager;
import com.itemlimiter.managers.NotificationManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * When a player attacks with a banned weapon the damage is reset to what a
 * bare hand would deal.
 *
 * <p>Bare-hand base damage in vanilla 1.20.1 is <strong>1.0</strong> (half a
 * heart before armor reduction).  We deliberately do <em>not</em> set it to
 * exactly 0 so that the hit still registers (knockback, sounds, etc.) while
 * providing zero meaningful combat advantage.
 *
 * <p>Status effects (Strength, Weakness) that the <em>player</em> currently
 * has are intentionally preserved – they affect the player, not the weapon.
 */
public class CombatListener implements Listener {

    /**
     * Vanilla bare-hand base attack damage.
     * Source: {@code generic.attack_damage} base value with empty hand = 1.0.
     */
    private static final double HAND_BASE_DAMAGE = 1.0;

    private final ItemLimiterPlugin plugin;
    private final ItemManager itemManager;
    private final NotificationManager notificationManager;

    public CombatListener(ItemLimiterPlugin plugin,
                          ItemManager itemManager,
                          NotificationManager notificationManager) {
        this.plugin              = plugin;
        this.itemManager         = itemManager;
        this.notificationManager = notificationManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (hasBypass(attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!itemManager.isRestricted(weapon)) return;

        // ── Calculate what bare-hand damage would be ──────────────────────────
        //
        // We start from the player's generic.attack_damage base value (which is
        // always 1.0 for players regardless of held item) rather than hard-coding
        // a literal, so that any server-wide attribute modifiers are respected.
        double handDamage = HAND_BASE_DAMAGE;
        AttributeInstance attr = attacker.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attr != null) {
            // The attribute already reflects the equipped weapon's bonus; we only
            // want the base (no-weapon) value.
            handDamage = attr.getBaseValue(); // base = 1.0, no weapon modifiers
        }

        // Override the damage.  Armor on the target and the normal damage
        // pipeline still apply, exactly as with a bare hand.
        event.setDamage(handDamage);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private boolean hasBypass(Player player) {
        return player.hasPermission("uir.bypass");
    }
}
