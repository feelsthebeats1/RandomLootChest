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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class OpenLootInventory {
   public static OpenLootInventory instance = new OpenLootInventory();
   private static final Random RNG = new Random();

    public static ItemStack getrandomitem(ChestType ct) {
        if (ct != null && ct.getLootTable() != null) {
           ConfigurationSection lt = ct.getLootTable();
           if (lt != null) {
              Map<Integer, ItemStack> items = new HashMap<>();
              Map<Integer, Integer> aMin = new HashMap<>();
              Map<Integer, Integer> aMax = new HashMap<>();
              for (String k : lt.getKeys(false)) {
                 ConfigurationSection s = lt.getConfigurationSection(k);
                 if (s != null) {
                    ItemStack it = s.getItemStack("item");
                    int ch = s.getInt("chance", 1);
                    int mn = Math.max(1, s.getInt("amount-min", it != null ? it.getAmount() : 1));
                    int mx = Math.max(1, s.getInt("amount-max", mn));
                    int key = items.size();
                    for (int i = 0; i < ch; i++) {
                        items.put(key, it);
                        aMin.put(key, Math.min(mn, mx));
                        aMax.put(key, Math.max(mn, mx));
                    }
                 }
              }
              if (!items.isEmpty()) {
                  int pick = RNG.nextInt(items.size());
                  ItemStack result = items.get(pick);
                  if (result != null) {
                      result = result.clone();
                      int min = aMin.getOrDefault(pick, 1);
                      int max = aMax.getOrDefault(pick, result.getAmount());
                      if (max >= min) result.setAmount(min + RNG.nextInt(max - min + 1));
                      return result;
                  }
                  return new ItemStack(Material.AIR);
              }
           }
        }
        // Fallback to global pool
        int all = Main.items.size();
        if (all == 0) return new ItemStack(Material.AIR);
        int pick = RNG.nextInt(all);
        ItemStack result = Main.items.get(pick);
        if (result == null) return new ItemStack(Material.AIR);
        result = result.clone();
        int min = Main.itemMin.getOrDefault(pick, 1);
        int max = Main.itemMax.getOrDefault(pick, result.getAmount());
        if (max >= min) result.setAmount(min + RNG.nextInt(max - min + 1));
        return result;
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
      ItemStack template = getrandomitem(ct); // Get one template to clone from
      
      int remaining = n;
      while (remaining > 0) {
         int slot = findavaliablerandomSlot(inv);
         if (slot == 1000) break; // No more slots
         
         ItemStack current = inv.getItem(slot);
         if (current != null && current.isSimilar(template) && 
             current.getAmount() < current.getMaxStackSize()) {
            // Can stack with existing item
            int canAdd = Math.min(remaining, current.getMaxStackSize() - current.getAmount());
            ItemStack toAdd = template.clone();
            toAdd.setAmount(canAdd);
            current.setAmount(current.getAmount() + canAdd);
            remaining -= canAdd;
         } else if (current == null || !current.isSimilar(template)) {
            // Empty slot or different item type - place new item
            ItemStack newItem = template.clone();
            int amountToPlace = Math.min(remaining, newItem.getMaxStackSize());
            newItem.setAmount(amountToPlace);
            inv.setItem(slot, newItem);
            remaining -= amountToPlace;
         }
         // If current is same item type but full, we'll try next slot on next iteration
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