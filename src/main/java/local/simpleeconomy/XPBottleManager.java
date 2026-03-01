package local.simpleeconomy;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Manages XP bottle creation and rank-based exhaustion
 */
public class XPBottleManager {

    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;
    
    // Tracks exhaustion per player: UUID -> exhaustion end time (ms)
    private final Map<UUID, Long> playerExhaustion = new HashMap<>();
    
    // Rank-based exhaustion times (in minutes)
    private final Map<String, Integer> rankExhaustionTimes = new HashMap<>();
    
    public XPBottleManager(JavaPlugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        setupRankExhaustion();
    }
    
    /**
     * Setup rank-based exhaustion times
     * Based on LuckPerms groups on your server
     */
    private void setupRankExhaustion() {
        // Your actual LuckPerms ranks from /lp listgroups
        rankExhaustionTimes.put("sovereign", 0);   // Weight 50 - No exhaustion
        rankExhaustionTimes.put("warlord", 0);     // Weight 40 - No exhaustion
        rankExhaustionTimes.put("tactician", 1);   // Weight 30 - 1 minute
        rankExhaustionTimes.put("militant", 2);    // Weight 20 - 2 minutes
        rankExhaustionTimes.put("scout", 5);       // Weight 10 - 5 minutes
        rankExhaustionTimes.put("default", 10);    // Weight 0  - 10 minutes (fallback)
    }
    
    /**
     * Withdraw XP and create a bottle
     * @return true if successful, false if exhausted or error
     */
    public boolean withdrawXP(Player player, int xpAmount) {
        // Check if player is exhausted
        if (isExhausted(player)) {
            long remainingMs = playerExhaustion.get(player.getUniqueId()) - System.currentTimeMillis();
            long minutes = remainingMs / 60000;
            long seconds = (remainingMs % 60000) / 1000;
            player.sendMessage("§cYou are exhausted! Wait §6" + minutes + "m " + seconds + "s §cbefore using XP again.");
            return false;
        }
        
        // Check if player has enough XP
        if (player.getTotalExperience() < xpAmount) {
            player.sendMessage("§cYou don't have enough XP! You need §6" + xpAmount + 
                             "§c but only have §6" + player.getTotalExperience());
            return false;
        }
        
        // Remove XP from player - subtract and force client update
        int currentXP = player.getTotalExperience();
        int newXP = currentXP - xpAmount;
        player.setTotalExperience(newXP);
        
        // Reset experience progress to force proper recalculation
        player.setExp(0.0f);
        player.setLevel(0);
        
        // Recalculate level and experience from new total
        int tempXP = newXP;
        int level = 0;
        
        // Calculate level from XP (Minecraft formula)
        while (tempXP > 0) {
            int xpForLevel;
            if (level <= 15) {
                xpForLevel = 2 * level + 7;
            } else if (level <= 30) {
                xpForLevel = 5 * level - 38;
            } else {
                xpForLevel = 9 * level - 158;
            }
            if (tempXP >= xpForLevel) {
                tempXP -= xpForLevel;
                level++;
            } else {
                break;
            }
        }
        
        // Set calculated level and progress
        player.setLevel(level);
        player.setExp((float) tempXP / (player.getExpToLevel() > 0 ? player.getExpToLevel() : 1));
        
        // Add exhaustion based on rank
        applyExhaustion(player);
        
        // Create bottle
        ItemStack bottle = createXPBottle(xpAmount);
        
        // Give bottle to player (or drop if inventory full)
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), bottle);
            player.sendMessage("§aBottle dropped at your feet (inventory full).");
        } else {
            player.getInventory().addItem(bottle);
        }
        
        player.sendMessage("§a✓ Created XP bottle with §6" + xpAmount + "§a XP!");
        
        // Get exhaustion time
        int exhaustionMinutes = getPlayerExhaustionTime(player);
        if (exhaustionMinutes > 0) {
            player.sendMessage("§cExhaustion: §6" + exhaustionMinutes + " minutes§c before next XP withdrawal");
        }
        
        return true;
    }
    
    /**
     * Create an XP bottle item with NBT data
     */
    public ItemStack createXPBottle(int xpAmount) {
        ItemStack bottle = new ItemStack(Material.EXPERIENCE_BOTTLE, 1);
        ItemMeta meta = bottle.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§b§lXP Bottle - " + xpAmount + " XP");
            
            List<String> lore = new ArrayList<>();
            lore.add("§7Right-click to consume");
            lore.add("§7Restores §b" + xpAmount + "§7 experience");
            meta.setLore(lore);
            bottle.setItemMeta(meta);
        }
        
        // Store XP amount in NBT using PersistentDataContainer
        try {
            meta = bottle.getItemMeta();
            meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "xp_amount"),
                org.bukkit.persistence.PersistentDataType.INTEGER,
                xpAmount
            );
            bottle.setItemMeta(meta);
        } catch (Exception e) {
            // Fallback if NBT fails
            System.out.println("[SimpleEconomy] Warning: Could not store XP in NBT: " + e.getMessage());
        }
        
        return bottle;
    }
    
    /**
     * Consume an XP bottle and restore XP to player
     */
    public void consumeXPBottle(Player player, int xpAmount) {
        int currentXP = player.getTotalExperience();
        int newXP = currentXP + xpAmount;
        
        System.out.println("[SimpleEconomy] consumeXPBottle: current=" + currentXP + " adding=" + xpAmount + " new=" + newXP);
        
        // Set new total XP
        player.setTotalExperience(newXP);
        
        // Reset and recalculate level from total XP
        player.setExp(0.0f);
        player.setLevel(0);
        
        // Calculate level from XP (Minecraft formula)
        int tempXP = newXP;
        int level = 0;
        
        while (tempXP > 0) {
            int xpForLevel;
            if (level <= 15) {
                xpForLevel = 2 * level + 7;
            } else if (level <= 30) {
                xpForLevel = 5 * level - 38;
            } else {
                xpForLevel = 9 * level - 158;
            }
            
            if (tempXP >= xpForLevel) {
                tempXP -= xpForLevel;
                level++;
            } else {
                break;
            }
        }
        
        // Set calculated level and progress bar
        player.setLevel(level);
        int expToLevel = player.getExpToLevel();
        if (expToLevel > 0) {
            player.setExp((float) tempXP / expToLevel);
        }
        
        System.out.println("[SimpleEconomy] Set level to " + level + " with " + tempXP + " progress XP");
        
        player.sendMessage("§a✓ Gained §6" + xpAmount + "§a XP!");
        player.sendMessage("§7Now at Level §6" + level + "§7 with §6" + tempXP + "§7/§6" + expToLevel + " §7XP toward next level");
    }
    
    /**
     * Apply exhaustion to a player based on their rank
     */
    private void applyExhaustion(Player player) {
        int exhaustionMinutes = getPlayerExhaustionTime(player);
        
        if (exhaustionMinutes > 0) {
            long exhaustionEndTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(exhaustionMinutes);
            playerExhaustion.put(player.getUniqueId(), exhaustionEndTime);
        }
    }
    
    /**
     * Get exhaustion time in minutes for a player's rank
     */
    private int getPlayerExhaustionTime(Player player) {
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                System.out.println("[SimpleEconomy] User not found for " + player.getName());
                return rankExhaustionTimes.getOrDefault("default", 10);
            }
            
            // Check ranks from highest to lowest priority
            String primaryGroup = user.getPrimaryGroup();
            System.out.println("[SimpleEconomy] " + player.getName() + " rank: " + primaryGroup);
            
            // Check if rank exists in exhaustion map
            for (String rank : rankExhaustionTimes.keySet()) {
                if (primaryGroup.equalsIgnoreCase(rank)) {
                    int exhaustion = rankExhaustionTimes.get(rank);
                    System.out.println("[SimpleEconomy] Found rank '" + rank + "' -> " + exhaustion + " min exhaustion");
                    return exhaustion;
                }
            }
            
            // Default if rank not found
            System.out.println("[SimpleEconomy] Rank '" + primaryGroup + "' not in config, using default");
            return rankExhaustionTimes.getOrDefault("default", 10);
        } catch (Exception e) {
            System.out.println("[SimpleEconomy] Error getting exhaustion time: " + e.getMessage());
            e.printStackTrace();
            return rankExhaustionTimes.getOrDefault("default", 10);
        }
    }
    
    /**
     * Check if player is currently exhausted
     */
    public boolean isExhausted(Player player) {
        Long exhaustionEnd = playerExhaustion.get(player.getUniqueId());
        if (exhaustionEnd == null) {
            return false;
        }
        
        if (System.currentTimeMillis() > exhaustionEnd) {
            playerExhaustion.remove(player.getUniqueId());
            return false;
        }
        
        return true;
    }
    
    /**
     * Get remaining exhaustion time in minutes for player
     */
    public int getRemainingExhaustionMinutes(Player player) {
        Long exhaustionEnd = playerExhaustion.get(player.getUniqueId());
        if (exhaustionEnd == null || System.currentTimeMillis() > exhaustionEnd) {
            playerExhaustion.remove(player.getUniqueId());
            return 0;
        }
        
        long remainingMs = exhaustionEnd - System.currentTimeMillis();
        return (int) (remainingMs / 60000);
    }
    
    /**
     * Clear exhaustion for a player (admin command)
     */
    public void clearExhaustion(Player player) {
        playerExhaustion.remove(player.getUniqueId());
    }
    
    /**
     * Get XP amount from bottle NBT data
     */
    public int extractXPFromBottle(ItemStack bottle) {
        if (bottle == null) {
            System.out.println("[SimpleEconomy] extractXPFromBottle: bottle is null");
            return 0;
        }
        
        ItemMeta meta = bottle.getItemMeta();
        if (meta == null) {
            System.out.println("[SimpleEconomy] extractXPFromBottle: meta is null");
            return 0;
        }
        
        // Try to get from NBT first (most reliable)
        try {
            Integer xpAmount = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "xp_amount"),
                org.bukkit.persistence.PersistentDataType.INTEGER
            );
            if (xpAmount != null && xpAmount > 0) {
                System.out.println("[SimpleEconomy] extractXPFromBottle: Found NBT value: " + xpAmount);
                return xpAmount;
            } else {
                System.out.println("[SimpleEconomy] extractXPFromBottle: NBT value is null or <= 0");
            }
        } catch (Exception e) {
            System.out.println("[SimpleEconomy] Warning: Could not read XP from NBT: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Fallback: Parse from display name
        if (!meta.hasDisplayName()) {
            System.out.println("[SimpleEconomy] extractXPFromBottle: No display name");
            return 0;
        }
        String displayName = meta.getDisplayName();
        System.out.println("[SimpleEconomy] extractXPFromBottle: Display name: " + displayName);
        
        if (!displayName.contains("XP Bottle")) {
            System.out.println("[SimpleEconomy] extractXPFromBottle: Not an XP Bottle");
            return 0;
        }
        
        try {
            // Parse XP amount from display name: "§b§lXP Bottle - 10000 XP"
            String cleanName = displayName.replaceAll("§[0-9a-fklmnor]", "");
            System.out.println("[SimpleEconomy] extractXPFromBottle: Clean name: " + cleanName);
            
            if (cleanName.contains(" - ") && cleanName.contains(" XP")) {
                int startIdx = cleanName.indexOf(" - ") + 3;
                int endIdx = cleanName.indexOf(" XP");
                String numStr = cleanName.substring(startIdx, endIdx).trim();
                System.out.println("[SimpleEconomy] extractXPFromBottle: Parsed number string: " + numStr);
                int amount = Integer.parseInt(numStr);
                System.out.println("[SimpleEconomy] extractXPFromBottle: Parsed amount: " + amount);
                return Math.max(0, amount);
            }
        } catch (Exception e) {
            System.out.println("[SimpleEconomy] Failed to parse XP from bottle: " + displayName);
            e.printStackTrace();
        }
        
        return 0;
    }
    
    /**
     * Set rank exhaustion time (for configuration)
     */
    public void setRankExhaustion(String rank, int minutes) {
        rankExhaustionTimes.put(rank.toLowerCase(), minutes);
    }
    
    /**
     * Get all rank exhaustion times
     */
    public Map<String, Integer> getRankExhaustionTimes() {
        return new HashMap<>(rankExhaustionTimes);
    }
}
