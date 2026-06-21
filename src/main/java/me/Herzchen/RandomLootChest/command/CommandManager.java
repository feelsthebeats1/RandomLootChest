package me.Herzchen.RandomLootChest.command;

import me.Herzchen.RandomLootChest.Main;
import me.Herzchen.RandomLootChest.gui.OpenLootInventory;
import me.Herzchen.RandomLootChest.model.ChestType;
import me.Herzchen.RandomLootChest.model.FixedChestInfo;
import me.Herzchen.RandomLootChest.model.RandomChestInfo;
import me.Herzchen.RandomLootChest.model.Selection;
import me.Herzchen.RandomLootChest.region.Region;
import me.Herzchen.RandomLootChest.region.RegionConfig;
import me.Herzchen.RandomLootChest.util.FindAvaliableLocation;
import me.Herzchen.RandomLootChest.util.MessageUtil;
import me.Herzchen.RandomLootChest.util.RLCUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.Collection;

@SuppressWarnings("SameReturnValue")
public class CommandManager implements CommandExecutor {
    private final Main plugin;
    private final Map<String, CommandHandler> handlers = new HashMap<>();

    public CommandManager(Main plugin) {
        this.plugin = plugin;        register("wand", this::handleWand); register("set", this::handleSet);
        register("unset", this::handleUnset); register("togglebreak", this::handleToggleBreak);
        register("killall", this::handleKillAll); register("forcespawn", this::handleForceSpawn);
        register("rndtime", this::handleRndTime); register("addchest", this::handleAddChest);
        register("addchesttype", this::handleAddChestType); register("delchest", this::handleDelChest); register("delall", this::handleDelAll);
        register("chesttypes", this::handleChestTypes); register("chesttypeinfo", this::handleChestTypeInfo);
        register("delchesttype", this::handleDelChestType); register("resettimer", this::handleResetTimer);
        register("reload", this::handleReload); register("additem", this::handleAddItem);
        register("regions", this::handleRegions); register("spawn", this::handleSpawn);
        register("spawnregion", this::handleSpawnRegion); register("loottable", this::handleLootTable);
    }

    private void register(String cmd, CommandHandler h) { handlers.put(cmd, h); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String a, String[] args) {
        if (!c.getName().equalsIgnoreCase("rlc")) return false;
        if (plugin.addChestplayers.containsKey(s)) {
            MessageUtil.send(s, plugin.messages.get("wait.cancelled", "<red>Đã hủy <gray>fo_O"));
            plugin.addChestplayers.get(s).cancel();
            plugin.addChestplayers.remove(s);
        }
        if (s instanceof Player p && !p.hasPermission("randomlootchest.general")) {
            MessageUtil.send(s, plugin.messages.get("general.no_permission_general", "<red>Không đủ quyền hạn.")); return true;
        }
        if (args.length == 0) {
            if (s instanceof Player p) sendHelp(p); else sendConsoleHelp((ConsoleCommandSender) s);
            return true;
        }
        CommandHandler h = handlers.get(args[0].toLowerCase());
        if (h != null) return h.handle(s, args);
        MessageUtil.send(s, plugin.messages.get("general.unknown_command", "<red>Lệnh không tồn tại! Sử dụng <yellow>/rlc <red>để xem trợ giúp."));
        return true;
    }

    void sendHelp(Player p) {
        MessageUtil.send(p, plugin.messages.get("command.help_header"));
        MessageUtil.send(p, plugin.messages.get("command.help_commands"));
        String[] keys = {
            "command.help_line_additem", "command.help_line_addchest", "command.help_line_addchesttype",
            "command.help_line_delchest", "command.help_line_delall", "command.help_line_killall",
            "command.help_line_togglebreak", "command.help_line_forcespawn", "command.help_line_rndtime",
            "command.help_line_wand", "command.help_line_set", "command.help_line_unset",
            "command.help_line_chesttypes", "command.help_line_chesttypeinfo", "command.help_line_delchesttype",
            "command.help_line_resettimer", "command.help_line_reload", "command.help_line_spawn",
            "command.help_line_spawnregion", "command.help_line_loottable", "command.help_line_regions"
        };
        for (String k : keys) MessageUtil.send(p, plugin.messages.get(k, ""));
        MessageUtil.send(p, plugin.messages.get("command.help_footer"));
    }

    void sendConsoleHelp(ConsoleCommandSender s) {
        MessageUtil.send(s, plugin.messages.get("command.console_help_header"));
        String[] lines = {
            "<yellow>/rlc delall", "<yellow>/rlc killall", "<yellow>/rlc forcespawn", "<yellow>/rlc rndtime",
            "<yellow>/rlc chesttypes", "<yellow>/rlc chesttypeinfo <italic>loại</italic>", "<yellow>/rlc delchesttype <italic>loại</italic>", "<yellow>/rlc resettimer [random/fixedchest] <italic>loại</italic>", "<yellow>/rlc reload",
            "<yellow>/rlc regions",
            "<yellow>/rlc spawn <italic>loại</italic> <italic>x1,y1,z1</italic> <italic>x2,y2,z2</italic> <italic>world</italic> [số lượng]",
            "<yellow>/rlc spawnregion <italic>loại</italic> <italic>regionId</italic> [số lượng]",
            "<yellow>/rlc loottable <italic>loại</italic>"
        };
        for (String l : lines) MessageUtil.send(s, "<red> " + l);
        MessageUtil.send(s, plugin.messages.get("command.console_help_footer"));
    }

    private boolean handleWand(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { MessageUtil.send(s, plugin.messages.get("general.player_only", "<red>Chỉ người chơi được dùng")); return true; }
        if (!p.hasPermission("randomlootchest.command.wand")) { MessageUtil.send(s, plugin.messages.get("wand.no_permission", "<red>Không đủ quyền!")); return true; }
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta m = wand.getItemMeta();
        m.displayName(MessageUtil.parse("<gold><bold>RLC Wand</bold>"));
        m.lore(List.of(
            MessageUtil.parse("<gray>Chuột trái: Chọn pos1"),
            MessageUtil.parse("<gray>Chuột phải: Chọn pos2"),
            MessageUtil.parse("<gray>Dùng <yellow>/rlc set [type]"),
            MessageUtil.parse("<gray>và <yellow>/rlc unset <gray>để thao tác")
        ));

        // Add NBT tag to identify the wand
        PersistentDataContainer data = m.getPersistentDataContainer();
        NamespacedKey wandKey = new NamespacedKey(Main.pl, "randomlootchest_wand");
        data.set(wandKey, PersistentDataType.BOOLEAN, true);

        wand.setItemMeta(m);
        p.getInventory().addItem(wand); MessageUtil.send(p, plugin.messages.get("wand.received", "<green>Bạn đã nhận được que wand!")); return true;
    }

    private boolean handleSet(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { MessageUtil.send(s, plugin.messages.get("general.player_only", "<red>Chỉ người chơi được dùng")); return true; }
        if (!p.hasPermission("randomlootchest.command.set")) { MessageUtil.send(s, plugin.messages.get("wand.no_permission", "<red>Không đủ quyền!")); return true; }

        String chestTypeId = null;
        if (a.length >= 2 && !a[1].isEmpty()) {
            chestTypeId = a[1];
            if (!chestTypeId.equalsIgnoreCase("default") && ChestType.getChestType(chestTypeId) == null) {
                MessageUtil.send(p, plugin.messages.getFormatted("general.chest_type_not_found",
                        "{loai}", chestTypeId) + " <red>Sẽ dùng default.");
                chestTypeId = null;
            }
        }
        Selection sel = Selection.selections.getOrDefault(p.getUniqueId(), new Selection());
        if (sel == null || sel.pos1 == null || sel.pos2 == null) { MessageUtil.send(p, plugin.messages.get("command.addchest_select_no_region", "<red>Vui lòng chọn vùng bằng wand!")); return true; }
        String finalChestType = chestTypeId;
        ChestType ct = finalChestType != null ? ChestType.getChestType(finalChestType) : null;
        // Use ChestType's spawnTimeMin/Max if available, otherwise fallback to global
        int timeMin = (ct != null) ? ct.getSpawnTimeMin() : plugin.getFixedChestUpdateTimeMin();
        int timeMax = (ct != null) ? ct.getSpawnTimeMax() : plugin.getFixedChestUpdateTimeMax();
        int cnt = 0, already = 0;
        for (int x = Math.min(sel.pos1.getBlockX(), sel.pos2.getBlockX()); x <= Math.max(sel.pos1.getBlockX(), sel.pos2.getBlockX()); x++)
            for (int y = Math.min(sel.pos1.getBlockY(), sel.pos2.getBlockY()); y <= Math.max(sel.pos1.getBlockY(), sel.pos2.getBlockY()); y++)
                for (int z = Math.min(sel.pos1.getBlockZ(), sel.pos2.getBlockZ()); z <= Math.max(sel.pos1.getBlockZ(), sel.pos2.getBlockZ()); z++) {
                    Location loc = new Location(sel.pos1.getWorld(), x, y, z);
                    if (RLCUtils.isFixedChestType(loc.getBlock()))
                        if (plugin.FixedChests.containsKey(loc)) already++;
                        else {
                            plugin.FixedChests.put(loc, new FixedChestInfo(
                                    FindAvaliableLocation.getRandom(timeMin, timeMax),
                                    finalChestType));
                            // Fill loot and play effects
                            Inventory inv = RLCUtils.getInventory(loc.getBlock());
                            if (inv != null) {
                                inv.clear();
                                OpenLootInventory.fillInvenory(inv, ct);
                            }
                            if (plugin.getFixedChestSound() != null) plugin.getFixedChestSound().play(loc);
                            if (plugin.getFixedChestEffect() != null) plugin.getFixedChestEffect().play(loc);
                            cnt++;
                        }
                }
        if (cnt > 0) {
            if (finalChestType != null) MessageUtil.send(p, plugin.messages.getFormatted("command.addchest_set_count",
                    "{so_luong}", String.valueOf(cnt), "{loai}", finalChestType));
            else MessageUtil.send(p, plugin.messages.getFormatted("command.addchest_set_count_no_type",
                    "{so_luong}", String.valueOf(cnt)));
            if (already > 0) MessageUtil.send(p, plugin.messages.getFormatted("command.addchest_already",
                    "{so_luong}", String.valueOf(already)));
        }
        else if (already > 0) MessageUtil.send(p, plugin.messages.getFormatted("command.addchest_all_already",
                "{so_luong}", String.valueOf(already)));
        else MessageUtil.send(p, plugin.messages.get("command.addchest_none_found", "<yellow>Không tìm thấy rương nào!")); return true;
    }

    private boolean handleUnset(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { MessageUtil.send(s, plugin.messages.get("general.player_only", "<red>Chỉ người chơi được dùng")); return true; }
        if (!p.hasPermission("randomlootchest.command.unset")) { MessageUtil.send(s, plugin.messages.get("wand.no_permission", "<red>Không đủ quyền!")); return true; }
        Selection sel = Selection.selections.getOrDefault(p.getUniqueId(), new Selection());
        if (sel == null || sel.pos1 == null || sel.pos2 == null) { MessageUtil.send(p, plugin.messages.get("command.addchest_select_no_region", "<red>Vui lòng chọn vùng bằng wand!")); return true; }
        int cnt = 0, notSet = 0;
        for (int x = Math.min(sel.pos1.getBlockX(), sel.pos2.getBlockX()); x <= Math.max(sel.pos1.getBlockX(), sel.pos2.getBlockX()); x++)
            for (int y = Math.min(sel.pos1.getBlockY(), sel.pos2.getBlockY()); y <= Math.max(sel.pos1.getBlockY(), sel.pos2.getBlockY()); y++)
                for (int z = Math.min(sel.pos1.getBlockZ(), sel.pos2.getBlockZ()); z <= Math.max(sel.pos1.getBlockZ(), sel.pos2.getBlockZ()); z++) {
                    Location loc = new Location(sel.pos1.getWorld(), x, y, z);
                    if (RLCUtils.isFixedChestType(loc.getBlock()))
                        if (plugin.FixedChests.containsKey(loc)) { plugin.FixedChests.remove(loc); cnt++; }
                        else notSet++;
                }
        if (cnt > 0) { MessageUtil.send(p, plugin.messages.getFormatted("command.unset_done", "{so_luong}", String.valueOf(cnt))); if (notSet > 0) MessageUtil.send(p, plugin.messages.getFormatted("command.unset_normal", "{so_luong}", String.valueOf(notSet))); }
        else if (notSet > 0) MessageUtil.send(p, plugin.messages.getFormatted("command.unset_none_with_normal", "{so_luong}", String.valueOf(notSet)));
        else MessageUtil.send(p, plugin.messages.get("command.unset_none", "<yellow>Không tìm thấy rương nào!")); return true;
    }

    private boolean handleToggleBreak(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { sendConsoleHelp((ConsoleCommandSender) s); return true; }
        if (!p.hasPermission("randomlootchest.togglebreak")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        if (plugin.abletobreak.contains(p)) { plugin.abletobreak.remove(p); MessageUtil.send(p, plugin.messages.get("command.togglebreak_off", "<gold>Đã tắt chế độ phá rương <red>Tắt!")); }
        else { plugin.abletobreak.add(p); MessageUtil.send(p, plugin.messages.get("command.togglebreak_on", "<gold>Đã bật chế độ phá rương <green>Bật!")); } return true;
    }

    private boolean handleKillAll(CommandSender s, String[] a) {
        if (s instanceof Player p && !p.hasPermission("randomlootchest.killall")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        plugin.getLootEvent().killallchests(); MessageUtil.send(s, plugin.messages.get("command.killall_done", "<green>Đã xóa tất cả rương!")); return true;
    }

    private boolean handleForceSpawn(CommandSender s, String[] a) {
        if (!s.hasPermission("randomlootchest.forcespawn")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        plugin.getGenerateChest().spawnchest(); return true;
    }

    private boolean handleRndTime(CommandSender s, String[] a) {
        if (!s.hasPermission("randomlootchest.rndtime")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        plugin.randomizeRandomChestsTimeLeft(); plugin.randomizeFixedChestsTimeLeft();
        MessageUtil.send(s, plugin.messages.get("command.rndtime_done", "<yellow>Đã ngẫu nhiên hóa thời gian.")); return true;
    }

    private boolean handleAddChest(CommandSender s, String[] a) {
        if (!s.hasPermission("randomlootchest.fixedchest")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }

        if (a.length >= 2 && a[1] != null && !a[1].isEmpty()) {
            // Có tên: thêm rương cố định theo tên khi nhấn phải chuột vào rương có sẵn hoặc vùng đã set
            String ctName = a[1].toLowerCase();
            if (ChestType.getChestType(ctName) == null) {
                MessageUtil.send(s, plugin.messages.getFormatted("general.chest_type_not_found",
                        "{loai}", ctName));
                return true;
            }
            if (!(s instanceof Player p)) { MessageUtil.send(s, plugin.messages.get("general.player_only", "<red>Chỉ người chơi được dùng")); return true; }
            new WaitChooseChest().start(p, "add", ctName);
            return true;
        }

        // Không có tên: tạo loại rương
        createNewChestType(s, a);
        return true;
    }

    private boolean handleAddChestType(CommandSender s, String[] a) {
        if (!s.hasPermission("randomlootchest.fixedchest")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }

        if (a.length >= 2 && a[1] != null && !a[1].isEmpty()) {
            // Có tên: thêm rương cố định theo tên
            String ctName = a[1].toLowerCase();
            if (ChestType.getChestType(ctName) == null) {
                MessageUtil.send(s, plugin.messages.getFormatted("general.chest_type_not_found",
                        "{loai}", ctName));
                return true;
            }
            if (!(s instanceof Player p)) { MessageUtil.send(s, plugin.messages.get("general.player_only", "<red>Chỉ người chơi được dùng")); return true; }
            new WaitChooseChest().start(p, "add", ctName);
            return true;
        }

        // Không có tên: tạo loại rương
        createNewChestType(s, a);
        return true;
    }

    private void createNewChestType(CommandSender s, String[] a) {
        if (a.length >= 2 && a[1] != null && !a[1].isEmpty()) {
            String ctName = a[1].toLowerCase();
            if (ChestType.getChestType(ctName) != null) {
                MessageUtil.send(s, plugin.messages.getFormatted("command.addchesttype_exists",
                        "{loai}", ctName));
                return;
            }

            org.bukkit.configuration.file.FileConfiguration chestConfig = Main.pl.chestTypeConfig.getConfig();
            chestConfig.set(ctName + ".material", a.length >= 3 ? a[2].toUpperCase() : "CHEST");
            chestConfig.set(ctName + ".displayName", ctName);
            chestConfig.set(ctName + ".spawnTimeMin", 10);
            chestConfig.set(ctName + ".spawnTimeMax", 20);
            chestConfig.set(ctName + ".killTime", a.length >= 4 ? parseInt(a[3]) : 60);
            chestConfig.set(ctName + ".spawnEffect", "MOBSPAWNER_FLAMES");
            chestConfig.set(ctName + ".spawnSound", "NONE");
            chestConfig.set(ctName + ".openSound", "CHEST_OPEN|BLOCK_CHEST_OPEN");
            chestConfig.set(ctName + ".killEffect", "EXPLOSION");
            chestConfig.set(ctName + ".killSound", "DIG_GRASS|BLOCK_GRASS_BREAK");
            chestConfig.set(ctName + ".lootTable", "");
            Main.pl.chestTypeConfig.saveConfig();

            Main.pl.chestTypeConfig.loadChestTypes();
            MessageUtil.send(s, plugin.messages.getFormatted("command.addchesttype_created",
                    "{loai}", ctName));
        } else {
            if (!(s instanceof Player p)) { MessageUtil.send(s, plugin.messages.get("general.player_only", "<red>Chỉ người chơi được dùng")); return; }
            new WaitChooseChest().start(p, "add");
        }
    }

    private boolean handleDelChest(CommandSender s, String[] a) {
        if (!s.hasPermission("randomlootchest.fixedchest")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        if (!(s instanceof Player p)) { MessageUtil.send(s, plugin.messages.get("general.player_only", "<red>Chỉ người chơi được dùng")); return true; }
        new WaitChooseChest().start(p, "del"); return true;
    }

    private boolean handleDelAll(CommandSender s, String[] a) {
        if (s instanceof Player p && !p.hasPermission("randomlootchest.delall")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        for (Map.Entry<Location, FixedChestInfo> e : plugin.FixedChests.entrySet()) {
            if (RLCUtils.isFixedChestType(e.getKey())) {
                Inventory inv = RLCUtils.getInventory(e.getKey());
                if (inv != null) inv.clear();
            }
        }
        plugin.FixedChests.clear(); MessageUtil.send(s, plugin.messages.get("command.delall_done", "<green>Đã xóa toàn bộ rương cố định!")); return true;
    }

    private boolean handleChestTypes(CommandSender s, String[] a) {
        if (s instanceof Player p && !p.hasPermission("randomlootchest.chesttypes")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        MessageUtil.send(s, plugin.messages.get("command.chesttypes_header"));
        for (ChestType t : ChestType.getAllChestTypes().values())
            MessageUtil.send(s, plugin.messages.getFormatted("command.chesttypes_line",
                    "{loai}", t.getId(), "{vat_lieu}", t.getMaterial().name()));
        MessageUtil.send(s, plugin.messages.get("command.chesttypes_footer")); return true;
    }

    // ====== /rlc chesttypeinfo <chestType> ======
    private boolean handleChestTypeInfo(CommandSender s, String[] a) {
        if (s instanceof Player p && !p.hasPermission("randomlootchest.chesttypes")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        if (a.length < 2) { MessageUtil.send(s, plugin.messages.get("command.chesttypeinfo_usage", "<red>Sử dụng: <yellow>/rlc chesttypeinfo <italic>loại</italic>")); return true; }
        ChestType ct = ChestType.getChestType(a[1]);
        if (ct == null) { MessageUtil.send(s, plugin.messages.getFormatted("general.chest_type_not_found", "{loai}", a[1])); return true; }

        MessageUtil.send(s, plugin.messages.getFormatted("command.chesttypeinfo_header", "{loai}", ct.getId()));
        MessageUtil.send(s, plugin.messages.getFormatted("command.chesttypeinfo_name", "{ten}", ct.getDisplayName()));
        MessageUtil.send(s, plugin.messages.getFormatted("command.chesttypeinfo_material", "{vat_lieu}", ct.getMaterial().name()));
        MessageUtil.send(s, plugin.messages.getFormatted("command.chesttypeinfo_spawn_time", "{min}", String.valueOf(ct.getSpawnTimeMin()), "{max}", String.valueOf(ct.getSpawnTimeMax())));
        MessageUtil.send(s, plugin.messages.getFormatted("command.chesttypeinfo_kill_time", "{thoi_gian}", String.valueOf(ct.getKillTime())));
        MessageUtil.send(s, plugin.messages.getFormatted("command.chesttypeinfo_spawn_effect", "{gia_tri}", String.valueOf(ct.getSpawnEffect())));
        MessageUtil.send(s, plugin.messages.getFormatted("command.chesttypeinfo_spawn_sound", "{gia_tri}", String.valueOf(ct.getSpawnSound())));
        MessageUtil.send(s, plugin.messages.getFormatted("command.chesttypeinfo_open_sound", "{gia_tri}", String.valueOf(ct.getOpenSound())));
        MessageUtil.send(s, plugin.messages.getFormatted("command.chesttypeinfo_kill_effect", "{gia_tri}", String.valueOf(ct.getKillEffect())));
        MessageUtil.send(s, plugin.messages.getFormatted("command.chesttypeinfo_kill_sound", "{gia_tri}", String.valueOf(ct.getKillSound())));
        MessageUtil.send(s, plugin.messages.getFormatted("command.chesttypeinfo_loot_table", "{gia_tri}", ct.getLootTable() != null ? "Có" : "Không (dùng global)"));
        MessageUtil.send(s, plugin.messages.get("command.chesttypes_footer"));
        return true;
    }

    // ====== /rlc delchesttype <chestType> ======
    private boolean handleDelChestType(CommandSender s, String[] a) {
        if (!s.hasPermission("randomlootchest.fixedchest")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        if (a.length < 2) { MessageUtil.send(s, plugin.messages.get("command.delchesttype_usage")); return true; }
        String ctName = a[1].toLowerCase();
        ChestType ct = ChestType.getChestType(ctName);
        if (ct == null) { MessageUtil.send(s, plugin.messages.getFormatted("general.chest_type_not_found", "{loai}", ctName)); return true; }
        if (ctName.equals("default")) { MessageUtil.send(s, plugin.messages.get("command.delchesttype_cannot_default")); return true; }

        org.bukkit.configuration.file.FileConfiguration chestConfig = Main.pl.chestTypeConfig.getConfig();
        chestConfig.set(ctName, null);
        Main.pl.chestTypeConfig.saveConfig();
        Main.pl.chestTypeConfig.loadChestTypes();
        MessageUtil.send(s, plugin.messages.getFormatted("command.delchesttype_done", "{loai}", ctName));
        return true;
    }

    private boolean handleResetTimer(CommandSender s, String[] a) {
        if (!s.hasPermission("randomlootchest.resettimer")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        if (a.length < 2) { MessageUtil.send(s, plugin.messages.get("command.resettimer_usage")); return true; }

        String targetType = null; // null = cả hai
        String chestTypeArg;
        if (a[1].equalsIgnoreCase("random") || a[1].equalsIgnoreCase("fixedchest")) {
            targetType = a[1].toLowerCase();
            if (a.length < 3) { MessageUtil.send(s, plugin.messages.get("command.resettimer_usage")); return true; }
            chestTypeArg = a[2];
        } else {
            chestTypeArg = a[1];
        }

        ChestType ct = ChestType.getChestType(chestTypeArg);
        if (ct == null) { MessageUtil.send(s, plugin.messages.getFormatted("general.chest_type_not_found", "{loai}", chestTypeArg)); return true; }

        String chestTypeId = ct.getId();
        int randomCount = 0, fixedCount = 0;

         if (targetType == null || targetType.equals("random")) {
             for (Map.Entry<Location, RandomChestInfo> e : plugin.RandomChests.entrySet())
                 if (isSameChestType(e.getValue().ChestTypeId, chestTypeId)) { e.getValue().Time = 0; randomCount++; }
         }

         if (targetType == null || targetType.equals("fixedchest")) {
             for (Map.Entry<Location, FixedChestInfo> e : plugin.FixedChests.entrySet())
                 if (isSameChestType(e.getValue().ChestTypeId, chestTypeId)) {
                     // Use ChestType's spawnTimeMin/Max if available, otherwise fallback to global
                     ChestType fixedCt = e.getValue().ChestTypeId != null ? ChestType.getChestType(e.getValue().ChestTypeId) : null;
                     int timeMin = (fixedCt != null) ? fixedCt.getSpawnTimeMin() : plugin.getFixedChestUpdateTimeMin();
                     int timeMax = (fixedCt != null) ? fixedCt.getSpawnTimeMax() : plugin.getFixedChestUpdateTimeMax();
                     e.getValue().Time = Math.max(FindAvaliableLocation.getRandom(timeMin, timeMax), 0);
                     fixedCount++;
                 }
         }

        if (targetType == null) {
            MessageUtil.send(s, plugin.messages.getFormatted("command.resettimer_done",
                    "{random}", String.valueOf(randomCount), "{fixed}", String.valueOf(fixedCount), "{loai}", chestTypeId));
        } else if (targetType.equals("random")) {
            MessageUtil.send(s, plugin.messages.getFormatted("command.resettimer_done_random",
                    "{random}", String.valueOf(randomCount), "{loai}", chestTypeId));
        } else {
            MessageUtil.send(s, plugin.messages.getFormatted("command.resettimer_done_fixed",
                    "{fixed}", String.valueOf(fixedCount), "{loai}", chestTypeId));
        }
        return true;
    }

    private boolean isSameChestType(String storedChestTypeId, String chestTypeId) {
        String normalizedStoredId = storedChestTypeId == null ? "default" : storedChestTypeId;
        return normalizedStoredId.equalsIgnoreCase(chestTypeId);
    }

    private boolean handleReload(CommandSender s, String[] a) {
        if (!s.hasPermission("randomlootchest.reload")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        plugin.reloadPlugin(); MessageUtil.send(s, plugin.messages.get("command.reload_done", "<green>Đã reload config, chest types và database!")); return true;
    }

    private boolean handleAddItem(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { sendConsoleHelp((ConsoleCommandSender) s); return true; }
        if (!p.hasPermission("randomlootchest.additem")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        if (a.length >= 2) {
            // Optional chest type parameter: /rlc additem <chestType>
            String ctId = a[1];
            if (ctId.equalsIgnoreCase("global") || me.Herzchen.RandomLootChest.model.ChestType.getChestType(ctId) != null) {
                plugin.getItemAdderGui().openGuiDirect(p, ctId.equalsIgnoreCase("global") ? null : ctId);
                return true;
            }
            MessageUtil.send(s, plugin.messages.getFormatted("general.chest_type_not_found", "{loai}", ctId));
            return true;
        }
        plugin.getItemAdderGui().openGui(p); return true;
    }

    private boolean handleRegions(CommandSender s, String[] a) {
        if (s instanceof Player p && !p.hasPermission("randomlootchest.regions")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        Collection<Region> regions = RegionConfig.getInstance().getAllRegions();
        if (regions.isEmpty()) {
            MessageUtil.send(s, plugin.messages.get("command.regions_empty"));
            return true;
        }
        MessageUtil.send(s, plugin.messages.get("command.regions_header"));
        for (Region r : regions) {
            MessageUtil.send(s, plugin.messages.getFormatted("command.regions_line",
                    "{region}", r.getId(), "{world}", r.getWorld().getName(),
                    "{x1}", String.valueOf(r.getXMin()), "{y1}", String.valueOf(r.getYMin()), "{z1}", String.valueOf(r.getZMin()),
                    "{x2}", String.valueOf(r.getXMax()), "{y2}", String.valueOf(r.getYMax()), "{z2}", String.valueOf(r.getZMax())));
            if (!r.getChestTypes().isEmpty()) {
                StringBuilder types = new StringBuilder();
                for (ChestType ct : r.getChestTypes()) types.append(ct.getId()).append(", ");
                MessageUtil.send(s, plugin.messages.getFormatted("command.regions_types", "{cac_loai}", types.substring(0, types.length() - 2)));
            }
        }
        MessageUtil.send(s, plugin.messages.get("command.regions_footer")); return true;
    }

    // ====== /rlc spawn <chestType> <x1,y1,z1> <x2,y2,z2> [world] [amount] ======
    private boolean handleSpawn(CommandSender s, String[] a) {
        if (!s.hasPermission("randomlootchest.spawn")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        if (a.length < 4) {
            MessageUtil.send(s, plugin.messages.get("command.spawn_usage"));
            return true;
        }
        ChestType ct = ChestType.getChestType(a[1]);
        if (ct == null) { MessageUtil.send(s, plugin.messages.getFormatted("general.chest_type_not_found", "{loai}", a[1])); return true; }

        Location[] corners = parseCorners(a[2], a[3]);
        if (corners == null) { MessageUtil.send(s, plugin.messages.get("command.spawn_bad_coords")); return true; }

        World world;
        if (a.length >= 5 && !isInteger(a[4])) {
            world = RLCUtils.getWorld(a[4]);
            if (world == null) { MessageUtil.send(s, plugin.messages.getFormatted("command.spawn_world_not_found", "{ten}", a[4])); return true; }
        } else {
            if (s instanceof Player p) world = p.getWorld();
            else { MessageUtil.send(s, plugin.messages.get("command.spawn_console_needs_world")); return true; }
        }

        int amount = (a.length >= 5 && isInteger(a[4])) ? parseInt(a[4])
                   : (a.length >= 6 && isInteger(a[5])) ? parseInt(a[5]) : 1;
        amount = Math.max(1, Math.min(amount, 100));

        Region spawnRegion = new Region("__spawn__", world,
                corners[0].getBlockX(), corners[1].getBlockX(),
                corners[0].getBlockY(), corners[1].getBlockY(),
                corners[0].getBlockZ(), corners[1].getBlockZ());
        spawnRegion.setChestTypeIds(Collections.singletonList(ct.getId()));
        spawnRegion.resolveChestTypes();

        int spawned = 0;
        for (int i = 0; i < amount * 10 && spawned < amount; i++) {
            Location loc = FindAvaliableLocation.FindLocation(spawnRegion);
            if (loc == null) continue;
            RandomChestInfo info = new RandomChestInfo(ct.getKillTime(),
                    ct.getMaterial().name() + ":0", ct.getId());
            plugin.RandomChests.put(loc, info);
            loc.getBlock().setType(ct.getMaterial());
            BlockState state = loc.getBlock().getState();
            if (state.getBlockData() instanceof org.bukkit.block.data.type.Chest chestData) {
                chestData.setFacing(BlockFace.values()[FindAvaliableLocation.getRandom(0, 3)]);
                state.setBlockData(chestData);
            }
            state.update();
            ct.playSpawnEffect(loc); ct.playSpawnSound(loc);
            spawned++;
        }
        MessageUtil.send(s, plugin.messages.getFormatted("command.spawn_done",
                "{da_spawn}", String.valueOf(spawned), "{yeu_cau}", String.valueOf(amount), "{loai}", ct.getId()));
        return true;
    }

    // ====== /rlc spawnregion <chestType> <regionId> [amount] ======
    private boolean handleSpawnRegion(CommandSender s, String[] a) {
        if (!s.hasPermission("randomlootchest.spawnregion")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        if (a.length < 3) {
            MessageUtil.send(s, plugin.messages.get("command.spawnregion_usage"));
            return true;
        }
        ChestType ct = ChestType.getChestType(a[1]);
        if (ct == null) { MessageUtil.send(s, plugin.messages.getFormatted("general.chest_type_not_found", "{loai}", a[1])); return true; }

        Region region = RegionConfig.getInstance().getRegion(a[2]);
        if (region == null) { MessageUtil.send(s, plugin.messages.getFormatted("command.spawnregion_not_found", "{ten}", a[2])); return true; }

        int amount = (a.length >= 4) ? parseInt(a[3]) : 1;
        amount = Math.max(1, Math.min(amount, 100));

        int spawned = 0;
        for (int i = 0; i < amount * 10 && spawned < amount; i++) {
            Location loc = FindAvaliableLocation.FindLocation(region);
            if (loc == null) continue;
            RandomChestInfo info = new RandomChestInfo(ct.getKillTime(),
                    ct.getMaterial().name() + ":0", ct.getId());
            plugin.RandomChests.put(loc, info);
            loc.getBlock().setType(ct.getMaterial());
            BlockState state = loc.getBlock().getState();
            if (state.getBlockData() instanceof org.bukkit.block.data.type.Chest chestData) {
                chestData.setFacing(BlockFace.values()[FindAvaliableLocation.getRandom(0, 3)]);
                state.setBlockData(chestData);
            }
            state.update();
            ct.playSpawnEffect(loc); ct.playSpawnSound(loc);
            spawned++;
        }
        MessageUtil.send(s, plugin.messages.getFormatted("command.spawnregion_done",
                "{da_spawn}", String.valueOf(spawned), "{yeu_cau}", String.valueOf(amount), "{loai}", ct.getId(), "{region}", a[2]));
        return true;
    }

    // ====== /rlc loottable <chestType> ======
    private boolean handleLootTable(CommandSender s, String[] a) {
        if (s instanceof Player p && !p.hasPermission("randomlootchest.loottable")) { MessageUtil.send(s, plugin.messages.get("general.no_permission", "<red>Không đủ quyền.")); return true; }
        if (a.length < 2) { MessageUtil.send(s, plugin.messages.get("command.loottable_usage")); return true; }
        ChestType ct = ChestType.getChestType(a[1]);
        if (ct == null) { MessageUtil.send(s, plugin.messages.getFormatted("general.chest_type_not_found", "{loai}", a[1])); return true; }

        MessageUtil.send(s, plugin.messages.getFormatted("command.loottable_header", "{loai}", ct.getId()));
        ConfigurationSection lootSec = ct.getLootTable();
        if (lootSec == null) {
            MessageUtil.send(s, plugin.messages.get("command.loottable_global_note", "<gray>Loại rương này dùng global items."));
            ConfigurationSection globalSec = Main.pl.db.data.getConfigurationSection("ItemDatabase");
            if (globalSec != null) {
                for (String k : globalSec.getKeys(false)) {
                    ConfigurationSection itemSec = globalSec.getConfigurationSection(k);
                    if (itemSec != null) {
                        ItemStack item = itemSec.getItemStack("item");
                        int chance = itemSec.getInt("chance", 0);
                        if (item != null) MessageUtil.send(s, plugin.messages.getFormatted("command.loottable_line", "{vat_lieu}", item.getType().name(), "{so_luong}", String.valueOf(item.getAmount()), "{ty_le}", String.valueOf(chance)));
                    }
                }
            }
        } else {
            for (String k : lootSec.getKeys(false)) {
                ConfigurationSection itemSec = lootSec.getConfigurationSection(k);
                if (itemSec != null) {
                    ItemStack item = itemSec.getItemStack("item");
                    int chance = itemSec.getInt("chance", 0);
                    if (item != null) MessageUtil.send(s, plugin.messages.getFormatted("command.loottable_line", "{vat_lieu}", item.getType().name(), "{so_luong}", String.valueOf(item.getAmount()), "{ty_le}", String.valueOf(chance)));
                }
            }
        }
        MessageUtil.send(s, plugin.messages.get("command.loottable_footer")); return true;
    }

    // ====== Utility ======
    private Location[] parseCorners(String s1, String s2) {
        try {
            String[] p1 = s1.split(",");
            String[] p2 = s2.split(",");
            if (p1.length < 3 || p2.length < 3) return null;
            return new Location[]{
                new Location(null, Double.parseDouble(p1[0]), Double.parseDouble(p1[1]), Double.parseDouble(p1[2])),
                new Location(null, Double.parseDouble(p2[0]), Double.parseDouble(p2[1]), Double.parseDouble(p2[2]))
            };
        } catch (NumberFormatException e) { return null; }
    }

    private boolean isInteger(String s) {
        try { Integer.parseInt(s); return true; } catch (NumberFormatException e) { return false; }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 1; }
    }
}