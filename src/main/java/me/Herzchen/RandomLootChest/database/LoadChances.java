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
      for (int i = 0; i < 100000 && section.isConfigurationSection(String.valueOf(i)); i++) {
         ItemStack item = Objects.requireNonNull(section.getConfigurationSection(String.valueOf(i))).getItemStack("item");
         int chance = Objects.requireNonNull(section.getConfigurationSection(String.valueOf(i))).getInt("chance");
         for (int j = 0; j < chance; j++) {
            for (int k = 0; k < 32000; k++) {
               if (!Main.items.containsKey(k)) { Main.items.put(k, item); break; }
            }
         }
      }
   }

   public void clear() { Main.items.clear(); }
}