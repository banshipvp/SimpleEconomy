package local.simpleeconomy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BankNoteTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("simpleeconomy.admin") && !sender.isOp()) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(List.of("give", "1000", "10000", "100000", "1000000"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(onlinePlayers(), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1000", "10000", "100000", "1000000", "5000000"), args[2]);
        }

        return List.of();
    }

    private List<String> onlinePlayers() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
