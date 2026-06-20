package me.Herzchen.RandomLootChest.database;

import me.Herzchen.RandomLootChest.Main;
import me.Herzchen.RandomLootChest.model.ChestType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

public class ChestTypeConfig {
    private static ChestTypeConfig instance;
    private FileConfiguration config;
    private File configFile;

    public ChestTypeConfig() { instance = this; }
    public static ChestTypeConfig getInstance() { return instance; }

    public void setup(Plugin plugin) {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        configFile = new File(plugin.getDataFolder(), "chesttypes.yml");
        if (!configFile.exists()) plugin.saveResource("chesttypes.yml", false);
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reloadConfig() { config = YamlConfiguration.loadConfiguration(configFile); }
    public FileConfiguration getConfig() { return config; }
    public void saveConfig() {
        try { config.save(configFile); }
        catch (IOException e) { Bukkit.getLogger().severe(ChatColor.RED + "Không thể lưu chesttypes.yml!"); }
    }

    public void loadChestTypes() {
        ChestType.clearAll();
        for (String key : config.getKeys(false)) {
            ConfigurationSection s = config.getConfigurationSection(key);
            if (s == null) continue;
            Material mat = Material.matchMaterial(s.getString("material", "CHEST"));
            ChestType type = new ChestType(key, mat, s.getString("displayName", key));
            type.setSpawnTimeMin(s.getInt("spawnTimeMin", 10));
            type.setSpawnTimeMax(s.getInt("spawnTimeMax", 20));
            type.setKillTime(s.getInt("killTime", 60));
            type.setSpawnEffect(s.getString("spawnEffect", "MOBSPAWNER_FLAMES"));
            type.setSpawnSound(s.getString("spawnSound", "NONE"));
            type.setOpenSound(s.getString("openSound", "CHEST_OPEN|BLOCK_CHEST_OPEN"));
            type.setKillEffect(s.getString("killEffect", "EXPLOSION"));
            type.setKillSound(s.getString("killSound", "DIG_GRASS|BLOCK_GRASS_BREAK"));
            String lootTablePath = s.getString("lootTable");
            if (lootTablePath != null && !lootTablePath.isEmpty()) {
                type.setLootTable(Main.pl.db.data.getConfigurationSection(lootTablePath));
            }
            ChestType.registerChestType(type);
        }
        if (ChestType.getAllChestTypes().isEmpty()) {
            ChestType.registerChestType(new ChestType("default", Material.CHEST, "Default Chest"));
        }
    }
}