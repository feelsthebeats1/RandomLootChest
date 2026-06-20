package me.Herzchen.RandomLootChest.model;

import me.Herzchen.RandomLootChest.util.EffectWrapper;
import me.Herzchen.RandomLootChest.util.SoundWrapper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public class ChestType {
    private String id;
    private Material material;
    private String displayName;
    private int spawnTimeMin, spawnTimeMax, killTime;
    private String spawnEffect, spawnSound, openSound, killEffect, killSound;
    private ConfigurationSection lootTable;
    private transient EffectWrapper cachedSpawnEffect, cachedKillEffect;
    private transient SoundWrapper cachedSpawnSound, cachedOpenSound, cachedKillSound;
    private static final Map<String, ChestType> CHEST_TYPES = new HashMap<>();

    public ChestType(String id, Material material, String displayName) {
        this.id = id; this.material = material; this.displayName = displayName;
        this.spawnTimeMin = 10; this.spawnTimeMax = 20; this.killTime = 60;
        this.spawnEffect = "MOBSPAWNER_FLAMES"; this.spawnSound = "NONE";
        this.openSound = "CHEST_OPEN|BLOCK_CHEST_OPEN"; this.killEffect = "EXPLOSION";
        this.killSound = "DIG_GRASS|BLOCK_GRASS_BREAK";
    }

    public String getId() { return id; }
    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public int getSpawnTimeMin() { return spawnTimeMin; }
    public void setSpawnTimeMin(int v) { spawnTimeMin = v; }
    public int getSpawnTimeMax() { return spawnTimeMax; }
    public void setSpawnTimeMax(int v) { spawnTimeMax = v; }
    public int getKillTime() { return killTime; }
    public void setKillTime(int v) { killTime = v; }
    public String getSpawnEffect() { return spawnEffect; }
    public void setSpawnEffect(String v) { spawnEffect = v; cachedSpawnEffect = null; }
    public String getSpawnSound() { return spawnSound; }
    public void setSpawnSound(String v) { spawnSound = v; cachedSpawnSound = null; }
    public String getOpenSound() { return openSound; }
    public void setOpenSound(String v) { openSound = v; cachedOpenSound = null; }
    public String getKillEffect() { return killEffect; }
    public void setKillEffect(String v) { killEffect = v; cachedKillEffect = null; }
    public String getKillSound() { return killSound; }
    public void setKillSound(String v) { killSound = v; cachedKillSound = null; }
    public ConfigurationSection getLootTable() { return lootTable; }
    public void setLootTable(ConfigurationSection v) { lootTable = v; }

    public void playSpawnEffect(Location loc) {
        if (cachedSpawnEffect == null && spawnEffect != null)
            cachedSpawnEffect = EffectWrapper.createNotNull(spawnEffect, null, s -> null);
        if (cachedSpawnEffect != null) cachedSpawnEffect.play(loc);
    }
    public void playSpawnSound(Location loc) {
        if (cachedSpawnSound == null && spawnSound != null)
            cachedSpawnSound = SoundWrapper.createNotNull(spawnSound, null, s -> null);
        if (cachedSpawnSound != null) cachedSpawnSound.play(loc);
    }
    public void playOpenSound(Location loc) {
        if (cachedOpenSound == null && openSound != null)
            cachedOpenSound = SoundWrapper.createNotNull(openSound, null, s -> null);
        if (cachedOpenSound != null) cachedOpenSound.play(loc);
    }
    public void playKillEffect(Location loc) {
        if (cachedKillEffect == null && killEffect != null)
            cachedKillEffect = EffectWrapper.createNotNull(killEffect, null, s -> null);
        if (cachedKillEffect != null) cachedKillEffect.play(loc);
    }
    public void playKillSound(Location loc) {
        if (cachedKillSound == null && killSound != null)
            cachedKillSound = SoundWrapper.createNotNull(killSound, null, s -> null);
        if (cachedKillSound != null) cachedKillSound.play(loc);
    }

    public static void registerChestType(ChestType type) { CHEST_TYPES.put(type.getId(), type); }
    public static ChestType getChestType(String id) { return CHEST_TYPES.get(id); }
    public static Map<String, ChestType> getAllChestTypes() { return CHEST_TYPES; }
    public static void clearAll() { CHEST_TYPES.clear(); }
}