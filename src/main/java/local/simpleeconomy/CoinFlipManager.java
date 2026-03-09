package local.simpleeconomy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages active coin-flip offers.
 *
 * Flow:
 *  1. Player runs /cf <amount>  → openCreateGUI shows Heads/Tails choice.
 *  2. Player picks a side       → creator's money is held (withdrawn); flip added to list.
 *  3. /cf (no args)             → openListGUI shows all pending flips.
 *  4. Another player clicks one → openConfirmGUI (Confirm / Cancel).
 *  5. Confirm                   → coin flip executed; winner gets 2× amount; broadcast.
 *  6. Flips older than 5 min auto-expire and return money to creator.
 */
public class CoinFlipManager {

    public enum Side { HEADS, TAILS }

    public static class CoinFlip {
        public final UUID id;
        public final UUID creatorUuid;
        public final String creatorName;
        public final double amount;
        public final Side creatorSide;
        public final long createdAt;

        CoinFlip(UUID creatorUuid, String creatorName, double amount, Side creatorSide) {
            this.id = UUID.randomUUID();
            this.creatorUuid = creatorUuid;
            this.creatorName = creatorName;
            this.amount = amount;
            this.creatorSide = creatorSide;
            this.createdAt = System.currentTimeMillis();
        }

        public Side joinerSide() {
            return creatorSide == Side.HEADS ? Side.TAILS : Side.HEADS;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > 5 * 60 * 1000L; // 5 minutes
        }
    }

    // -----------------------------------------------------------------------

    private final Economy economy;
    /** flip id → flip */
    private final Map<UUID, CoinFlip> flips = new LinkedHashMap<>();

    // Pending create: player chose amount but hasn't picked a side yet
    private final Map<UUID, Double> pendingCreate = new HashMap<>();

    // Pending join: player is looking at confirm dialog for a flip
    private final Map<UUID, UUID> pendingJoin = new HashMap<>(); // joiner → flipId

    public CoinFlipManager(Economy economy) {
        this.economy = economy;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Called by /cf <amount>. Returns false if player can't afford it. */
    public boolean startCreate(Player player, double amount) {
        if (!economy.has(player, amount)) return false;
        if (hasActiveFlip(player.getUniqueId())) return false;
        pendingCreate.put(player.getUniqueId(), amount);
        return true;
    }

    /** Player chose their side — deduct money, create flip. */
    public CoinFlip confirmCreate(Player player, Side side) {
        Double amount = pendingCreate.remove(player.getUniqueId());
        if (amount == null) return null;
        if (!economy.has(player, amount)) return null;
        economy.withdrawPlayer(player, amount);
        CoinFlip flip = new CoinFlip(player.getUniqueId(), player.getName(), amount, side);
        flips.put(flip.id, flip);
        return flip;
    }

    public void cancelCreate(Player player) {
        pendingCreate.remove(player.getUniqueId());
    }

    /** Is amount pending for player (hasn't picked side yet)? */
    public Double getPendingCreateAmount(UUID playerId) {
        return pendingCreate.get(playerId);
    }

    /** Register that joiner is looking at a confirm screen for flipId. */
    public void setPendingJoin(UUID joinerId, UUID flipId) {
        pendingJoin.put(joinerId, flipId);
    }

    public UUID getPendingJoinFlipId(UUID joinerId) {
        return pendingJoin.get(joinerId);
    }

    public void cancelJoin(UUID joinerId) {
        pendingJoin.remove(joinerId);
    }

    /**
     * Joiner confirms: deduct joiner's money, flip coin, pay winner 2×, broadcast.
     * Returns human-readable result string, or null if flip no longer valid.
     */
    public String executeJoin(Player joiner) {
        UUID flipId = pendingJoin.remove(joiner.getUniqueId());
        if (flipId == null) return null;

        CoinFlip flip = flips.remove(flipId);
        if (flip == null) {
            joiner.sendMessage("§cThis flip no longer exists.");
            return null;
        }
        if (!economy.has(joiner, flip.amount)) {
            // Refund creator
            Player creator = Bukkit.getPlayer(flip.creatorUuid);
            if (creator != null) economy.depositPlayer(creator, flip.amount);
            else economy.depositPlayer(Bukkit.getOfflinePlayer(flip.creatorUuid), flip.amount);
            joiner.sendMessage("§cYou don't have §e$" + fmt(flip.amount) + "§c to match this flip.");
            return null;
        }

        economy.withdrawPlayer(joiner, flip.amount);

        // 50/50 flip
        boolean creatorWins = ThreadLocalRandom.current().nextBoolean();
        double pot = flip.amount * 2;

        // ── Rank-based tax: 25% (no rank) → 1% (top rank) ───────────────────
        Player onlineWinner;
        UUID   winnerUuid;
        String winnerName, loserName;
        if (creatorWins) {
            onlineWinner = Bukkit.getPlayer(flip.creatorUuid);
            winnerUuid   = flip.creatorUuid;
            winnerName   = flip.creatorName;
            loserName    = joiner.getName();
        } else {
            onlineWinner = joiner;
            winnerUuid   = joiner.getUniqueId();
            winnerName   = joiner.getName();
            loserName    = flip.creatorName;
        }
        double taxRate   = onlineWinner != null ? getCoinFlipTax(onlineWinner) : 0.25;
        double taxAmount = pot * taxRate;
        double payout    = pot - taxAmount;
        int    taxPct    = (int) Math.round(taxRate * 100);

        if (creatorWins) {
            if (onlineWinner != null) economy.depositPlayer(onlineWinner, payout);
            else economy.depositPlayer(Bukkit.getOfflinePlayer(flip.creatorUuid), payout);
        } else {
            economy.depositPlayer(joiner, payout);
        }

        // Cross-plugin: track coinflip win in the daily challenge
        callSFCoinflipWin(winnerUuid, winnerName, (long) payout);

        String result = String.format(
                "§6❖ §eCoin Flip Result§8: §f%s §7vs §f%s §8| §6$%s pot\n"
                + "§a§l🪙 %s §r§awon §6$%s §8(§c-%d%% tax§8) §7— %s chose §e%s§7, %s chose §e%s§7",
                flip.creatorName, joiner.getName(), fmt(pot),
                winnerName, fmt(payout), taxPct,
                flip.creatorName, flip.creatorSide.name(),
                joiner.getName(), flip.joinerSide().name());

        Bukkit.broadcastMessage("§6§l[CoinFlip] §r" + winnerName + " §awon §6$" + fmt(payout)
                + " §7(§c-" + taxPct + "% §7tax) §aagainst " + loserName + "!");
        return result;
    }

    // ── Tax & cross-plugin hooks ──────────────────────────────────────────────

    /**
     * Returns the coinflip tax rate for the given player based on their LuckPerms
     * group weight. Weight 0 (no rank) = 25%, weight ≥ 100 (top rank) = 1%.
     */
    private double getCoinFlipTax(Player player) {
        try {
            var lp   = net.luckperms.api.LuckPermsProvider.get();
            var user = lp.getUserManager().getUser(player.getUniqueId());
            if (user == null) return 0.25;
            var group  = lp.getGroupManager().getGroup(user.getPrimaryGroup());
            int weight = (group != null) ? group.getWeight().orElse(0) : 0;
            // weight 0 → 25%, weight 100 → 1%
            double tax = 0.25 - (Math.min(weight, 100) * 0.24 / 100.0);
            return Math.max(0.01, tax);
        } catch (Exception e) {
            return 0.25; // LuckPerms unavailable: apply maximum tax
        }
    }

    /**
     * Notifies the SimpleFactions daily challenge system that a coinflip was won.
     * No-op when SimpleFactions is not loaded.
     */
    private void callSFCoinflipWin(UUID winnerUuid, String winnerName, long amount) {
        try {
            org.bukkit.plugin.Plugin sfPlugin = Bukkit.getPluginManager().getPlugin("SimpleFactions");
            if (sfPlugin == null) return;
            
            // Use reflection to avoid direct dependency on SimpleFactions
            var getChallengeManager = sfPlugin.getClass().getMethod("getChallengeManager");
            Object cm = getChallengeManager.invoke(sfPlugin);
            if (cm != null) {
                var increment = cm.getClass().getMethod("increment", UUID.class, String.class, Enum.class, long.class);
                var trackerType = cm.getClass().getEnclosingClass().getField("TrackerType").get(null);
                var coinflipWin = trackerType.getClass().getField("COINFLIP_WIN").get(trackerType);
                increment.invoke(cm, winnerUuid, winnerName, coinflipWin, amount);
            }
        } catch (Exception e) {
            // SimpleFactions not available or method not found; silently continue
        }
    }

    /** Cancel / expire a flip, returning the creator's money. */
    public boolean cancelFlip(UUID creatorId) {
        CoinFlip flip = null;
        for (CoinFlip f : flips.values()) {
            if (f.creatorUuid.equals(creatorId)) { flip = f; break; }
        }
        if (flip == null) return false;
        flips.remove(flip.id);
        Player creator = Bukkit.getPlayer(creatorId);
        if (creator != null) economy.depositPlayer(creator, flip.amount);
        else economy.depositPlayer(Bukkit.getOfflinePlayer(creatorId), flip.amount);
        return true;
    }

    /** Expire old flips (call periodically). Returns number expired. */
    public int expireOldFlips() {
        int count = 0;
        Iterator<CoinFlip> it = flips.values().iterator();
        while (it.hasNext()) {
            CoinFlip flip = it.next();
            if (flip.isExpired()) {
                it.remove();
                Player creator = Bukkit.getPlayer(flip.creatorUuid);
                if (creator != null) economy.depositPlayer(creator, flip.amount);
                else economy.depositPlayer(Bukkit.getOfflinePlayer(flip.creatorUuid), flip.amount);
                count++;
            }
        }
        return count;
    }

    public Collection<CoinFlip> getActiveFlips() {
        return Collections.unmodifiableCollection(flips.values());
    }

    public boolean hasActiveFlip(UUID playerId) {
        return flips.values().stream().anyMatch(f -> f.creatorUuid.equals(playerId));
    }

    public CoinFlip getFlip(UUID flipId) {
        return flips.get(flipId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public static String fmt(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.1fB", amount / 1_000_000_000);
        if (amount >= 1_000_000)     return String.format("%.1fM", amount / 1_000_000);
        if (amount >= 1_000)         return String.format("%.1fK", amount / 1_000);
        return String.format("%.2f", amount);
    }
}
