package me.Herzchen.RandomLootChest.database;

import me.Herzchen.RandomLootChest.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import java.util.Objects;

public class LoadChances {
   public static LoadChances instance = new LoadChances();

   public ConfigurationSection itdb() {
      return Main.pl.db.data.getConfigurationSection("ItemDatabase");
   }

   public void loaditems() {
      clear();
      ConfigurationSection section = itdb();
      if (section == null) return;
      int idx = 0;
      for (int i = 0; i < 100000 && section.isConfigurationSection(String.valueOf(i)); i++) {
         ItemStack item = Objects.requireNonNull(section.getConfigurationSection(String.valueOf(i))).getItemStack("item");
         int chance = Objects.requireNonNull(section.getConfigurationSection(String.valueOf(i))).getInt("chance");
         int aMin = Math.max(1, section.getConfigurationSection(String.valueOf(i)).getInt("amount-min", 1));
         int aMax = Math.max(1, section.getConfigurationSection(String.valueOf(i)).getInt("amount-max", item != null ? item.getAmount() : 1));
         for (int j = 0; j < chance; j++) {
            Main.items.put(idx, item);
            Main.itemMin.put(idx, aMin);
            Main.itemMax.put(idx, aMax);
            idx++;
         }
      }
      if (idx == 0) {
         Main.pl.getLogger().warning("Global loot table (ItemDatabase) is empty! Placeholder items may be given.");
      }
   }

   public void clear() { Main.items.clear(); Main.itemMin.clear(); Main.itemMax.clear(); }
}