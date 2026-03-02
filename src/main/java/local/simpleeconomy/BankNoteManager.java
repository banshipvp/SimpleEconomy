package local.simpleeconomy;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class BankNoteManager {

    private final JavaPlugin plugin;
    private final Object economy;
    private final NamespacedKey amountKey;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,###.##");

    public BankNoteManager(JavaPlugin plugin, Object economy) {
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

        if (!depositPlayer(player, amount)) return false;
        player.sendMessage("§aRedeemed bank note for §e$" + moneyFormat.format(amount));
        return true;
    }

    private boolean depositPlayer(Player player, double amount) {
        if (economy == null) return false;
        try {
            Method method = economy.getClass().getMethod("depositPlayer", Player.class, double.class);
            method.invoke(economy, player, amount);
            return true;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Class<?> offlinePlayerClass = Class.forName("org.bukkit.OfflinePlayer");
            Method method = economy.getClass().getMethod("depositPlayer", offlinePlayerClass, double.class);
            method.invoke(economy, player, amount);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
