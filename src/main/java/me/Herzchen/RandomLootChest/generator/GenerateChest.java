package me.Herzchen.RandomLootChest.generator;

import me.Herzchen.RandomLootChest.Main;
import me.Herzchen.RandomLootChest.model.ChestType;
import me.Herzchen.RandomLootChest.model.RandomChestInfo;
import me.Herzchen.RandomLootChest.region.Region;
import me.Herzchen.RandomLootChest.region.RegionConfig;
import me.Herzchen.RandomLootChest.util.FindAvaliableLocation;
import me.Herzchen.RandomLootChest.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.material.Chest;
import org.bukkit.material.MaterialData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GenerateChest {
   static BlockFace[] chestFaces = {BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH};

   public void GenerateChest(int time) {
      Main.pl.getServer().getScheduler().scheduleSyncRepeatingTask(Main.pl, this::spawnchest, (long)time * 20L, (long)time * 20L);
   }

   public void spawnchest() {
      Collection<Region> regions = RegionConfig.getInstance().getAllRegions();
      if (regions.isEmpty()) {
         spawnChestGlobal();
         return;
      }
      for (Region region : regions) {
         spawnChestInRegion(region);
      }
   }

   private void spawnChestInRegion(Region region) {
      Location loc = FindAvaliableLocation.FindLocation(region);
      if (loc == null) return;
      ChestType ct = region.getRandomChestType();
      if (ct == null) ct = ChestType.getChestType("default");
      saveChest(loc, ct);
      placeChestBlock(loc, ct);
      broadcastSpawn(loc);
   }

   private void spawnChestGlobal() {
      Location loc = FindAvaliableLocation.FindLocation();
      if (loc == null) return;
      ChestType ct = ChestType.getAllChestTypes().values().stream()
              .skip(FindAvaliableLocation.getRandom(0, ChestType.getAllChestTypes().size() - 1))
              .findFirst().orElseGet(() -> ChestType.getChestType("default"));
      saveChest(loc, ct);
      placeChestBlock(loc, ct);
      broadcastSpawn(loc);
   }

   private void placeChestBlock(Location loc, ChestType ct) {
      loc.getBlock().setType(ct.getMaterial());
      BlockState state = loc.getBlock().getState();
      state.setData(new Chest(chestFaces[FindAvaliableLocation.getRandom(0, 3)]));
      state.update();
   }

   private void broadcastSpawn(Location loc) {
      if (Main.pl.getMessageOnSpawn() != null)
          MessageUtil.broadcastWithPlaceholders(
                  Main.pl.getMessageOnSpawn(),
                  "{X}", String.valueOf(loc.getBlockX()),
                  "{Y}", String.valueOf(loc.getBlockY()),
                  "{Z}", String.valueOf(loc.getBlockZ()));
   }

   void saveChest(Location loc, ChestType ct) {
      MaterialData md = loc.getBlock().getState().getData();
      Main.pl.RandomChests.put(loc, new RandomChestInfo(ct.getKillTime(),
              String.format("%s:%d", md.getItemType(), md.getData()), ct.getId()));
      ct.playSpawnEffect(loc); ct.playSpawnSound(loc);
   }
}