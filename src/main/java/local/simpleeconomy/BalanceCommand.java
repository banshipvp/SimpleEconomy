package local.simpleeconomy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final Economy economy;

    public BalanceCommand(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Show own balance
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cUsage: /bal <player>");
                return true;
            }
            double balance = economy.getBalance(player);
            sender.sendMessage("§6Your balance: §e$" + format(balance));
            return true;
        }

        // Show another player's balance
        if (!sender.hasPermission("simpleeconomy.balance.others")) {
            sender.sendMessage("§cYou don't have permission to view others' balances.");
            return true;
        }

        String targetName = args[0];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage("§cPlayer §e" + targetName + " §chas never joined this server.");
            return true;
        }

        double balance = economy.getBalance(target);
        String displayName = target.getName() != null ? target.getName() : targetName;
        sender.sendMessage("§6" + displayName + "§e's balance: §a$" + format(balance));
        return true;
    }

    private String format(double amount) {
        if (amount == Math.floor(amount)) {
            return String.format("%,.0f", amount);
        }
        return String.format("%,.2f", amount);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
