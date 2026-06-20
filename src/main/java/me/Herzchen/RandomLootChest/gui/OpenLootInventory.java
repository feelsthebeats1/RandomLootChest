package me.Herzchen.RandomLootChest.gui;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.Herzchen.RandomLootChest.Main;
import me.Herzchen.RandomLootChest.model.ChestType;
import me.Herzchen.RandomLootChest.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.Objects;
import java.util.Random;

public class OpenLootInventory {
   public static OpenLootInventory instance = new OpenLootInventory();

   public static ItemStack getrandomitem(ChestType ct) {
       if (ct != null && ct.getLootTable() != null) {
          ConfigurationSection lt = ct.getLootTable();
          if (lt != null) {
             java.util.Map<Integer, ItemStack> items = new java.util.HashMap<>();
             for (String k : lt.getKeys(false)) {
                ConfigurationSection s = lt.getConfigurationSection(k);
                if (s != null) {
                   ItemStack it = s.getItemStack("item");
                   int ch = s.getInt("chance", 1);
                   for (int i = 0; i < ch; i++) items.put(items.size(), it);
                }
             }
             if (!items.isEmpty()) return items.get(new Random().nextInt(items.size()));
          }
       }
       int all = Main.items.size();
       if (all == 0) return new ItemStack(Material.AIR);
       return Main.items.get(new Random().nextInt(all));
   }

   public static int findavaliablerandomSlot(Inventory inv) {
      for (int i = 0; i < inv.getSize(); i++) {
         int ra = new Random().nextInt(inv.getSize());
         if (inv.getItem(ra) == null) return ra;
      }
      return 1000;
   }

   public void openInvenory(Player player, ChestType chestType) {
       String name = Main.pl.messages.get("chest.inventory_name", "<red>Chúc mừng!");
       int slots = Main.pl.getConfig().getInt("Inventory_Slots", 27);
       int rows = slots / 9;

       Gui gui = Gui.gui()
               .title(MessageUtil.parse(name))
               .rows(rows)
               .disableAllInteractions()
               .create();

       fillInvenory(gui, chestType);
       gui.open(player);
   }

   public static void fillInvenory(Inventory inv) { fillInvenory(inv, null); }

   public static void fillInvenory(Inventory inv, ChestType ct) {
      int n = Main.pl.getConfig().getInt("ItemAmountToAdd");
      for (int i = 0; i < n; i++) {
         int slot = findavaliablerandomSlot(inv);
         if (slot != 1000) inv.setItem(slot, getrandomitem(ct));
      }
   }

   public static void fillInvenory(Gui gui, ChestType ct) {
      int n = Main.pl.getConfig().getInt("ItemAmountToAdd");
      for (int i = 0; i < n; i++) {
         ItemStack item = getrandomitem(ct);
         if (item != null && item.getType() != Material.AIR) {
            GuiItem gi = ItemBuilder.from(item).asGuiItem(event -> event.setCancelled(true));
            gui.addItem(gi);
         }
      }
   }
}