package local.simpleeconomy;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * /coinflip [amount | cancel | list]
 * /cf       — alias registered in plugin.yml
 */
public class CoinFlipCommand implements CommandExecutor, TabCompleter {

    private final CoinFlipManager manager;
    private final CoinFlipGUI gui;

    public CoinFlipCommand(CoinFlipManager manager, CoinFlipGUI gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        // /cf  or  /coinflip  → open list
        if (args.length == 0) {
            gui.openList(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        // /cf cancel
        if (sub.equals("cancel")) {
            if (manager.getPendingCreateAmount(player.getUniqueId()) != null) {
                manager.cancelCreate(player);
                player.sendMessage("§aCoin flip cancelled.");
            } else if (manager.hasActiveFlip(player.getUniqueId())) {
                manager.cancelFlip(player.getUniqueId());
                player.sendMessage("§aCoin flip cancelled. §7Your money has been refunded.");
            } else {
                player.sendMessage("§cYou don't have a pending flip to cancel.");
            }
            return true;
        }

        // /cf <amount>
        double amount;
        try {
            amount = Double.parseDouble(sub);
        } catch (NumberFormatException e) {
            player.sendMessage("§cUsage: §e/cf <amount> §8| §e/cf cancel §8| §e/cf §8(list)");
            return true;
        }

        if (amount < 100) {
            player.sendMessage("§cMinimum coin flip amount is §e$100§c.");
            return true;
        }

        if (amount > 10_000_000) {
            player.sendMessage("§cMaximum coin flip amount is §e$10,000,000§c.");
            return true;
        }

        // startCreate validates balance and checks for existing flip
        if (manager.hasActiveFlip(player.getUniqueId())) {
            player.sendMessage("§cYou already have an active coin flip. Use §e/cf cancel §cto cancel it.");
            return true;
        }
        if (!manager.startCreate(player, amount)) {
            player.sendMessage("§cYou don't have enough money for a §e$" + CoinFlipManager.fmt(amount) + " §cflip.");
            return true;
        }

        gui.openChooseSide(player, amount);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("cancel", "1000", "10000", "100000", "1000000");
        }
        return List.of();
    }
}
