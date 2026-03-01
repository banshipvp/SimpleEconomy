package local.simpleeconomy;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Listener for XP bottle pickup
 */
public class XPBottleListener implements Listener {

    private final XPBottleManager manager;

    public XPBottleListener(XPBottleManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        
        ItemStack item = event.getItem().getItemStack();
        
        // Check if this is our custom XP bottle
        if (item.getType() != Material.EXPERIENCE_BOTTLE) return;
        if (!item.hasItemMeta()) return;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        String displayName = meta.getDisplayName();
        if (displayName == null || displayName.isEmpty()) return;
        
        // Check if it's our XP bottle (remove color codes for comparison)
        String cleanName = displayName.replaceAll("§[0-9a-fklmnor]", "");
        if (!cleanName.contains("XP Bottle")) return;
        
        // Extract XP amount
        int xpAmount = manager.extractXPFromBottle(item);
        System.out.println("[SimpleEconomy] Bottle picked up - Extracted XP: " + xpAmount);
        
        if (xpAmount > 0) {
            System.out.println("[SimpleEconomy] Giving " + xpAmount + " XP to " + player.getName());
            
            // Use consumeXPBottle to properly add the full XP amount
            manager.consumeXPBottle(player, xpAmount);
            
            // Remove the item from the ground
            event.getItem().remove();
            event.setCancelled(true);
        } else {
            System.out.println("[SimpleEconomy] WARNING: Could not extract XP from bottle!");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // XP bottles drop naturally on death
    }

    @EventHandler
    public void onExpBottleBreak(ExpBottleEvent event) {
        ThrownExpBottle thrown = event.getEntity();
        ItemStack thrownItem = thrown.getItem();

        if (thrownItem == null || thrownItem.getType() != Material.EXPERIENCE_BOTTLE) return;
        if (!thrownItem.hasItemMeta()) return;

        ItemMeta meta = thrownItem.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return;

        String cleanName = meta.getDisplayName().replaceAll("§[0-9a-fklmnor]", "");
        if (!cleanName.contains("XP Bottle")) return;

        int xpAmount = manager.extractXPFromBottle(thrownItem);
        if (xpAmount <= 0) return;

        Player target = getNearestPlayer(thrown.getLocation());
        if (target == null) return;

        // Prevent vanilla random XP from this splash
        event.setExperience(0);

        // Give exact stored XP amount to nearest player (@p behavior)
        manager.consumeXPBottle(target, xpAmount);
    }

    private Player getNearestPlayer(Location location) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : location.getWorld().getPlayers()) {
            double dist = player.getLocation().distanceSquared(location);
            if (dist < nearestDistance) {
                nearestDistance = dist;
                nearest = player;
            }
        }

        return nearest;
    }
}
