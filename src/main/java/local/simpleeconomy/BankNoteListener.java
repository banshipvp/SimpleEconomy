package local.simpleeconomy;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class BankNoteListener implements Listener {

    private final BankNoteManager bankNoteManager;

    public BankNoteListener(BankNoteManager bankNoteManager) {
        this.bankNoteManager = bankNoteManager;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!bankNoteManager.isBankNote(item)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (bankNoteManager.redeem(player, item)) {
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            player.sendMessage("§cInvalid bank note.");
        }
    }
}
