package local.simpleeconomy;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /xpbottle command - Withdraw XP into a bottle
 */
public class XPBottleCommand implements CommandExecutor {

    private final XPBottleManager manager;

    public XPBottleCommand(XPBottleManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // /xpbottle <amount>
        if (args.length == 0) {
            player.sendMessage("§c/xpbottle <amount>");
            
            // Calculate XP experience for this level
            int level = player.getLevel();
            float expProgress = player.getExp();  // 0.0 to 1.0
            
            // Get XP needed for next level (varies by level)
            int expNeeded = player.getExpToLevel();
            int currentLevelXP = Math.round(expProgress * expNeeded);
            
            player.sendMessage("§7Level: §6" + level + "§7 | Progress: §6" + currentLevelXP + "§7/§6" + expNeeded);
            
            int exhaustionMinutes = manager.getRemainingExhaustionMinutes(player);
            if (exhaustionMinutes > 0) {
                player.sendMessage("§c⏱ Exhaustion: §6" + exhaustionMinutes + " minutes§c remaining");
            } else {
                player.sendMessage("§a✓ No exhaustion");
            }
            return true;
        }

        try {
            int amount = Integer.parseInt(args[0]);
            
            if (amount <= 0) {
                player.sendMessage("§cAmount must be greater than 0.");
                return true;
            }
            
            manager.withdrawXP(player, amount);
            return true;

        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount: " + args[0]);
            return true;
        }
    }
}
