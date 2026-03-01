package local.simpleeconomy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class BankNoteManager {

    private final JavaPlugin plugin;
    private final Economy economy;
    private final NamespacedKey amountKey;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,###.##");

    public BankNoteManager(JavaPlugin plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.amountKey = new NamespacedKey(plugin, "banknote_amount");
    }

    public ItemStack createBankNote(double amount) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();

        meta.setDisplayName("§a§lBank Note §7(§f$" + moneyFormat.format(amount) + "§7)");
        List<String> lore = new ArrayList<>();
        lore.add("§7Right-click to redeem");
        lore.add("§7Value: §a$" + moneyFormat.format(amount));
        lore.add("§6Tradeable money item");
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(amountKey, PersistentDataType.DOUBLE, amount);

        paper.setItemMeta(meta);
        return paper;
    }

    public boolean isBankNote(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(amountKey, PersistentDataType.DOUBLE);
    }

    public double getAmount(ItemStack item) {
        if (!isBankNote(item)) return 0;
        Double amount = item.getItemMeta().getPersistentDataContainer().get(amountKey, PersistentDataType.DOUBLE);
        return amount == null ? 0 : amount;
    }

    public boolean redeem(Player player, ItemStack item) {
        double amount = getAmount(item);
        if (amount <= 0) return false;

        economy.depositPlayer(player, amount);
        player.sendMessage("§aRedeemed bank note for §e$" + moneyFormat.format(amount));
        return true;
    }
}
