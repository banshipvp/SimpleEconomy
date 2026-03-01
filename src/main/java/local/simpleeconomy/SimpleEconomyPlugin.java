package local.simpleeconomy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * SimpleEconomy - Handles XP bottles and economy features
 */
public class SimpleEconomyPlugin extends JavaPlugin {

    private XPBottleManager xpBottleManager;
    private BankNoteManager bankNoteManager;
    private LuckPerms luckPerms;
    private Economy economy;

    @Override
    public void onEnable() {
        // Get LuckPerms API
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            getLogger().severe("LuckPerms is not loaded! Disabling SimpleEconomy.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize XP bottle manager
        xpBottleManager = new XPBottleManager(this, luckPerms);

        // Setup economy (Vault) for banknotes
        if (setupEconomy()) {
            bankNoteManager = new BankNoteManager(this, economy);
            getCommand("banknote").setExecutor(new BankNoteCommand(bankNoteManager));
            Bukkit.getPluginManager().registerEvents(new BankNoteListener(bankNoteManager), this);
            getLogger().info("Bank notes enabled.");
        } else {
            getLogger().warning("Vault economy not found. Bank notes disabled.");
        }

        // Register commands
        getCommand("xpbottle").setExecutor(new XPBottleCommand(xpBottleManager));

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new XPBottleListener(xpBottleManager), this);

        getLogger().info("SimpleEconomy enabled successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleEconomy disabled.");
    }

    public XPBottleManager getXPBottleManager() {
        return xpBottleManager;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public Economy getEconomy() {
        return economy;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }
}
