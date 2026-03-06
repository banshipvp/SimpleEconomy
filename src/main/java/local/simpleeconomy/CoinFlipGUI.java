package local.simpleeconomy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI screens for the coin flip system.
 *
 * Screens:
 *   ① Choose Side    — when player runs /cf <amount>
 *   ② Flip List      — shows all active pending flips
 *   ③ Confirm Join   — after clicking a flip in the list
 */
public class CoinFlipGUI implements Listener {

    static final String TITLE_CHOOSE = "§6✦ Choose Your Side";
    static final String TITLE_LIST   = "§6✦ Coin Flip Exchange";
    static final String TITLE_CONFIRM = "§6✦ Confirm Flip";

    private final CoinFlipManager manager;
    private final SimpleEconomyPlugin plugin;

    // flipId page into the list GUI so we can track what each slot maps to
    private final Map<UUID, List<UUID>> listPageCache = new HashMap<>(); // viewer → ordered flip IDs

    public CoinFlipGUI(CoinFlipManager manager, SimpleEconomyPlugin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    // ── Open screens ─────────────────────────────────────────────────────────

    /** Screen ①: player chose amount, now picks Heads or Tails. */
    public void openChooseSide(Player player, double amount) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CHOOSE);

        // Decoration
        ItemStack pane = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        inv.setItem(4, icon(Material.GOLD_INGOT, "§e§lCoin Flip",
                "§7You are betting §6$" + CoinFlipManager.fmt(amount),
                "§7Pick §eHeads §7or §eTails§7.",
                "§8The winner takes §6$" + CoinFlipManager.fmt(amount * 2) + "§8."));

        inv.setItem(11, icon(Material.SUNFLOWER, "§e§lHEADS",
                "§7Click to flip as §eHeads"));
        inv.setItem(15, icon(Material.IRON_NUGGET, "§f§lTAILS",
                "§7Click to flip as §7Tails"));

        inv.setItem(22, icon(Material.BARRIER, "§c§lCancel",
                "§7Cancel this coin flip"));

        player.openInventory(inv);
    }

    /** Screen ②: all active pending flips. */
    public void openList(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_LIST);

        Collection<CoinFlipManager.CoinFlip> active = manager.getActiveFlips();
        List<UUID> flipIds = new ArrayList<>();

        ItemStack pane = pane(Material.PURPLE_STAINED_GLASS_PANE);
        // Border
        for (int i = 0; i < 9; i++) inv.setItem(i, pane);
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
        for (int i = 0; i < 54; i += 9) { inv.setItem(i, pane); inv.setItem(i + 8, pane); }

        int slot = 10;
        for (CoinFlipManager.CoinFlip flip : active) {
            if (slot >= 44) break;
            if (slot % 9 == 8) slot++;
            if (slot % 9 == 0) slot++;

            flipIds.add(flip.id);
            Material icon = flip.creatorSide == CoinFlipManager.Side.HEADS ? Material.SUNFLOWER : Material.IRON_NUGGET;
            long secondsLeft = 300 - (System.currentTimeMillis() - flip.createdAt) / 1000;
            inv.setItem(slot, icon(icon, "§6§l" + flip.creatorName + "'s Flip",
                    "§7Amount: §6$" + CoinFlipManager.fmt(flip.amount),
                    "§7Their side: §e" + flip.creatorSide.name(),
                    "§7Your side: §f" + flip.joinerSide().name(),
                    "§8Pot: §6$" + CoinFlipManager.fmt(flip.amount * 2),
                    "§8Expires in: §f" + secondsLeft + "s",
                    "§a▸ Click to join!"));
            slot++;
        }

        if (flipIds.isEmpty()) {
            inv.setItem(22, icon(Material.PAPER, "§7No Active Flips",
                    "§7Use §e/cf <amount> §7to create one!"));
        }

        listPageCache.put(player.getUniqueId(), flipIds);
        player.openInventory(inv);
    }

    /** Screen ③: confirm joining a flip. */
    public void openConfirm(Player player, CoinFlipManager.CoinFlip flip) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CONFIRM);

        ItemStack pane = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        inv.setItem(4, icon(Material.GOLD_INGOT, "§6§l" + flip.creatorName + "'s Flip",
                "§7Cost: §6$" + CoinFlipManager.fmt(flip.amount),
                "§7" + flip.creatorName + " chose: §e" + flip.creatorSide.name(),
                "§7You get: §f" + flip.joinerSide().name(),
                "§8Win pot: §6$" + CoinFlipManager.fmt(flip.amount * 2)));

        inv.setItem(11, icon(Material.LIME_DYE, "§a§l✔ Confirm",
                "§7Pay §6$" + CoinFlipManager.fmt(flip.amount) + " §7and flip!"));
        inv.setItem(15, icon(Material.RED_DYE, "§c§l✘ Cancel",
                "§7Return to the list"));

        manager.setPendingJoin(player.getUniqueId(), flip.id);
        player.openInventory(inv);
    }

    // ── Click handler ────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        String title = e.getView().getTitle();

        if (!title.equals(TITLE_CHOOSE) && !title.equals(TITLE_LIST) && !title.equals(TITLE_CONFIRM)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null
                || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String name = clicked.getItemMeta().getDisplayName();

        // ── Screen ①: Choose Side ────────────────────────────────────────
        if (title.equals(TITLE_CHOOSE)) {
            if (name.contains("HEADS")) {
                CoinFlipManager.CoinFlip flip = manager.confirmCreate(player, CoinFlipManager.Side.HEADS);
                handleCreated(player, flip);
            } else if (name.contains("TAILS")) {
                CoinFlipManager.CoinFlip flip = manager.confirmCreate(player, CoinFlipManager.Side.TAILS);
                handleCreated(player, flip);
            } else if (name.contains("Cancel")) {
                manager.cancelCreate(player);
                player.closeInventory();
                player.sendMessage("§cCoin flip cancelled.");
            }
            return;
        }

        // ── Screen ②: Flip List ──────────────────────────────────────────
        if (title.equals(TITLE_LIST)) {
            List<UUID> ids = listPageCache.get(player.getUniqueId());
            if (ids == null) return;
            int slot = e.getRawSlot();
            // Convert slot to list index (accounting for border)
            // Slots 10-44 (skipping edges)
            int listIndex = toListIndex(slot);
            if (listIndex < 0 || listIndex >= ids.size()) return;
            UUID flipId = ids.get(listIndex);
            CoinFlipManager.CoinFlip flip = manager.getFlip(flipId);
            if (flip == null) { openList(player); return; } // stale, refresh
            if (flip.creatorUuid.equals(player.getUniqueId())) {
                if (e.isRightClick()) {
                    boolean cancelled = manager.cancelFlip(player.getUniqueId());
                    if (cancelled) {
                        player.sendMessage("§aCoin flip cancelled. §7Your money has been refunded.");
                    } else {
                        player.sendMessage("§cThat flip is no longer active.");
                    }
                    Bukkit.getScheduler().runTaskLater(plugin, () -> openList(player), 1L);
                    return;
                }
                player.sendMessage("§cYou can't join your own flip. §7Right-click to cancel it.");
                return;
            }
            player.closeInventory();
            openConfirm(player, flip);
            return;
        }

        // ── Screen ③: Confirm Join ───────────────────────────────────────
        if (title.equals(TITLE_CONFIRM)) {
            if (name.contains("Confirm")) {
                player.closeInventory();
                String result = manager.executeJoin(player);
                if (result != null) {
                    for (String line : result.split("\n")) player.sendMessage(line);
                }
            } else if (name.contains("Cancel")) {
                manager.cancelJoin(player.getUniqueId());
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openList(player), 1L);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        listPageCache.remove(player.getUniqueId());
        // If close choose-side without picking, refund is via cancelCreate (amount stays pending but no money taken yet)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void handleCreated(Player player, CoinFlipManager.CoinFlip flip) {
        player.closeInventory();
        if (flip == null) {
            player.sendMessage("§cFailed to create flip. Check your balance.");
            return;
        }
        player.sendMessage("§a✓ Coin flip created! §e$" + CoinFlipManager.fmt(flip.amount)
                + " §aheld in escrow. Waiting for a challenger...");
        player.sendMessage("§7Your side: §e" + flip.creatorSide.name()
                + " §8| §7Use §e/cf §7to view the list.");
    }

    private static int toListIndex(int slot) {
        // valid slots: row 1-4, col 1-7 → slots 10-16, 19-25, 28-34, 37-43
        int row = slot / 9;
        int col = slot % 9;
        if (row < 1 || row > 4 || col < 1 || col > 7) return -1;
        return (row - 1) * 7 + (col - 1);
    }

    private static ItemStack icon(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material mat) {
        ItemStack p = new ItemStack(mat);
        ItemMeta m = p.getItemMeta();
        m.setDisplayName("§r");
        p.setItemMeta(m);
        return p;
    }
}
