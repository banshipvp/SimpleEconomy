package local.simpleeconomy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class BankNoteCommand implements CommandExecutor {

    private final BankNoteManager bankNoteManager;

    public BankNoteCommand(BankNoteManager bankNoteManager) {
        this.bankNoteManager = bankNoteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6/banknote <amount>");
            sender.sendMessage("§6/banknote give <player> <amount>");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("simpleeconomy.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /banknote give <player> <amount>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }

            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cAmount must be a number.");
                return true;
            }

            if (amount <= 0) {
                sender.sendMessage("§cAmount must be greater than 0.");
                return true;
            }

            ItemStack note = bankNoteManager.createBankNote(amount);
            target.getInventory().addItem(note);
            sender.sendMessage("§aGave bank note §e$" + args[2] + " §ato " + target.getName());
            target.sendMessage("§aYou received a bank note worth §e$" + args[2]);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cConsole must use: /banknote give <player> <amount>");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[0]);
        } catch (NumberFormatException ex) {
            player.sendMessage("§cAmount must be a number.");
            return true;
        }

        if (amount <= 0) {
            player.sendMessage("§cAmount must be greater than 0.");
            return true;
        }

        if (!player.hasPermission("simpleeconomy.admin")) {
            player.sendMessage("§cNo permission.");
            return true;
        }

        ItemStack note = bankNoteManager.createBankNote(amount);
        player.getInventory().addItem(note);
        player.sendMessage("§aCreated bank note worth §e$" + args[0]);
        return true;
    }
}
