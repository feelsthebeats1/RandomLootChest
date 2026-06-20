package me.Herzchen.RandomLootChest.listener;

import me.Herzchen.RandomLootChest.Main;
import me.Herzchen.RandomLootChest.model.FixedChestInfo;
import me.Herzchen.RandomLootChest.model.Selection;
import me.Herzchen.RandomLootChest.util.FindAvaliableLocation;
import me.Herzchen.RandomLootChest.util.MessageUtil;
import me.Herzchen.RandomLootChest.util.RLCUtils;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class WandListener implements Listener {
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) return;

        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey wandKey = new NamespacedKey(Main.pl, "randomlootchest_wand");
        if (!data.has(wandKey, PersistentDataType.BOOLEAN) || Boolean.FALSE.equals(data.get(wandKey, PersistentDataType.BOOLEAN))) {
            return;
        }
        
        if (!player.hasPermission("randomlootchest.select")) { MessageUtil.send(player, Main.pl.messages.get("wand.no_permission", "<red>Không đủ quyền!")); return; }
        Block clickedBlock = e.getClickedBlock();
        if (clickedBlock == null) return;

        Selection sel = Selection.selections.getOrDefault(player.getUniqueId(), new Selection());
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            sel.pos1 = clickedBlock.getLocation();
            MessageUtil.send(player, Main.pl.messages.getFormatted("wand.pos1_set", "{loc}", RLCUtils.formatLocation(sel.pos1)));
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            sel.pos2 = clickedBlock.getLocation();
            MessageUtil.send(player, Main.pl.messages.getFormatted("wand.pos2_set", "{loc}", RLCUtils.formatLocation(sel.pos2)));
        } else {
            e.setCancelled(true); return;
        }
        Selection.selections.put(player.getUniqueId(), sel);
        e.setCancelled(true);

        if (RLCUtils.isFixedChestType(clickedBlock)) {
            Location loc = clickedBlock.getLocation();
            if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                Main.pl.FixedChests.put(loc, new FixedChestInfo(FindAvaliableLocation.getRandom(
                        Main.pl.getFixedChestUpdateTimeMin(), Main.pl.getFixedChestUpdateTimeMax())));
                MessageUtil.send(player, Main.pl.messages.get("wand.set_fixed", "<green>Đã set rương thành RLC chest!"));
            } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Main.pl.FixedChests.remove(loc);
                MessageUtil.send(player, Main.pl.messages.get("wand.unset_fixed", "<green>Đã unset rương!"));
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) return;

        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey wandKey = new NamespacedKey(Main.pl, "randomlootchest_wand");
        if (data.has(wandKey, PersistentDataType.BOOLEAN) && Boolean.TRUE.equals(data.get(wandKey, PersistentDataType.BOOLEAN))) {
            e.setCancelled(true);
        }
    }
}