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
    private CoinFlipManager coinFlipManager;
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
            getCommand("banknote").setTabCompleter(new BankNoteTabCompleter());
            Bukkit.getPluginManager().registerEvents(new BankNoteListener(bankNoteManager), this);
            getLogger().info("Bank notes enabled.");

            // /bal [player] command
            BalanceCommand balCmd = new BalanceCommand(economy);
            getCommand("balance").setExecutor(balCmd);
            getCommand("balance").setTabCompleter(balCmd);

            // Coin flip
            coinFlipManager = new CoinFlipManager(economy);
            CoinFlipGUI coinFlipGUI = new CoinFlipGUI(coinFlipManager, this);
            CoinFlipCommand coinFlipCmd = new CoinFlipCommand(coinFlipManager, coinFlipGUI);
            getCommand("coinflip").setExecutor(coinFlipCmd);
            getCommand("coinflip").setTabCompleter(coinFlipCmd);
            getCommand("cf").setExecutor(coinFlipCmd);
            getCommand("cf").setTabCompleter(coinFlipCmd);
            Bukkit.getPluginManager().registerEvents(coinFlipGUI, this);
            // Expire old flips every 30 seconds
            Bukkit.getScheduler().runTaskTimer(this, () -> coinFlipManager.expireOldFlips(), 600L, 600L);
            getLogger().info("Coin flip system enabled.");
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
