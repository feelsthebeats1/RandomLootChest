package me.Herzchen.RandomLootChest.listener;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.Herzchen.RandomLootChest.Main;
import me.Herzchen.RandomLootChest.database.LoadChances;
import me.Herzchen.RandomLootChest.model.ChestType;
import me.Herzchen.RandomLootChest.model.ItemEditSession;
import me.Herzchen.RandomLootChest.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item editor GUI. Each player gets an isolated {@link ItemEditSession} so
 * multiple admins can edit different chest types simultaneously without
 * clobbering each other's data.
 */
public class ItemAdderGui implements Listener {
    LoadChances lc = new LoadChances();

    public static final String GLOBAL_KEY = "__global__";

    /** Players waiting for chat input: player -> callback */
    private final Map<Player, java.util.function.Consumer<String>> chatInputHandlers = new ConcurrentHashMap<>();

    // ── session helpers ──

    private ItemEditSession session(Player p) {
        return Main.pl.editSessions.computeIfAbsent(p, k -> new ItemEditSession());
    }

    private void endSession(Player p) {
        Main.pl.editSessions.remove(p);
    }

    private boolean hasSession(Player p) {
        return Main.pl.editSessions.containsKey(p);
    }

    // ── data access ──

    public ConfigurationSection getItemSection(String chestTypeId) {
        ConfigurationSection root = Main.pl.db.data.getConfigurationSection("ItemDatabase");
        if (root == null) return null;
        if (chestTypeId == null || chestTypeId.equals(GLOBAL_KEY)) return root;
        ConfigurationSection ctSection = root.getConfigurationSection(chestTypeId);
        if (ctSection == null) ctSection = root.createSection(chestTypeId);
        return ctSection;
    }

    /**
     * Load session data from YAML for the given chest type.
     */
    public void loadSession(ItemEditSession session, String chestTypeId) {
        Main.pl.db.loadData();
        ConfigurationSection s = getItemSection(chestTypeId);
        if (s == null) return;
        session.clearAll();
        String key = chestTypeId == null || chestTypeId.equals(GLOBAL_KEY) ? null : chestTypeId;
        session.setEditingChestType(key);
        for (int i = 0; i < 10000 && s.isConfigurationSection(String.valueOf(i)); i++) {
            ItemStack item = Objects.requireNonNull(s.getConfigurationSection(String.valueOf(i))).getItemStack("item");
            int chance = Objects.requireNonNull(s.getConfigurationSection(String.valueOf(i))).getInt("chance");
            session.setItem(i, item);
            session.setChance(i, chance);
        }
    }

    /**
     * Save session data to YAML for the given chest type.
     */
    public void saveSession(ItemEditSession session, String chestTypeId) {
        Main.pl.db.loadData();
        ConfigurationSection target = getItemSection(chestTypeId);
        if (target == null) return;

        // When saving global (null = root section), only clear numeric keys, NOT sub-sections
        boolean isGlobal = chestTypeId == null || chestTypeId.equals(GLOBAL_KEY);
        if (isGlobal) {
            for (String k : target.getKeys(false)) {
                if (k.matches("\\d+")) target.set(k, null);
            }
        } else {
            for (String k : target.getKeys(false)) target.set(k, null);
        }

        int toset = 0;
        for (int i = 0; i < 10000; i++) {
            ItemStack item = session.getItem(i);
            if (item != null) {
                target.createSection(String.valueOf(toset));
                Objects.requireNonNull(target.getConfigurationSection(String.valueOf(toset))).set("item", item);
                Objects.requireNonNull(target.getConfigurationSection(String.valueOf(toset))).set("chance", session.getChance(i));
                toset++;
            }
        }
        Main.pl.db.saveData();
        if (chestTypeId == null) lc.loaditems();
    }

    public boolean isFull(Inventory inv) {
        for (int i = 0; i < 45; i++) if (inv.getItem(i) == null) return false;
        return true;
    }

    // ── chest type selector ──

    public void openChestTypeSelector(Player player) {
        int count = ChestType.getAllChestTypes().size() + 1;
        int rows = Math.max(1, (int) Math.ceil(count / 9.0));
        int size = Math.min(54, rows * 9);

        Gui gui = Gui.gui()
                .title(MessageUtil.parse("<dark_gray>Chọn loại rương để chỉnh sửa"))
                .rows(size / 9)
                .create();

        GuiItem globalItem = ItemBuilder.from(Material.CHEST)
                .name(MessageUtil.parse("<gold><bold>Global Items</bold>"))
                .lore(
                        MessageUtil.parse("<gray>Items dùng chung cho tất cả"),
                        MessageUtil.parse("<gray>loại rương không có loot table riêng")
                )
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    ItemEditSession sess = session(player);
                    sess.setEditingChestType(null);
                    MessageUtil.send(player, Main.pl.messages.get("gui.selected_global", "<green>Đã chọn: <white>Global Items"));
                    player.closeInventory();
                    loadSession(sess, GLOBAL_KEY);
                    sess.setCurrentPage(1);
                    openPage(player);
                });
        gui.addItem(globalItem);

        for (ChestType ct : ChestType.getAllChestTypes().values()) {
            String ctId = ct.getId();
            GuiItem ctItem = ItemBuilder.from(ct.getMaterial())
                    .name(MessageUtil.parse("<gold><bold>" + ct.getDisplayName() + "</bold>"))
                    .lore(
                            MessageUtil.parse("<gray>ID: <white>" + ct.getId()),
                            MessageUtil.parse("<gray>Click để chỉnh sửa items cho loại rương này")
                    )
                    .asGuiItem(event -> {
                        event.setCancelled(true);
                        ItemEditSession sess = session(player);
                        sess.setEditingChestType(ctId);
                        MessageUtil.send(player, Main.pl.messages.getFormatted("gui.selected_type", "{ten}", ct.getDisplayName()));
                        player.closeInventory();
                        loadSession(sess, ctId);
                        sess.setCurrentPage(1);
                        openPage(player);
                    });
            gui.addItem(ctItem);
        }

        gui.open(player);
    }

    // ── item editor page ──

    public void openPage(Player player) {
        ItemEditSession session = session(player);
        int page = session.getCurrentPage();
        String ctId = session.getEditingChestType();

        Component title;
        if (ctId == null || ctId.equals(GLOBAL_KEY)) {
            title = MessageUtil.parse("<dark_gray>Global - Trang: <white>" + page + "/5");
        } else {
            ChestType ct = ChestType.getChestType(ctId);
            String displayName = (ct != null) ? ct.getDisplayName() : ctId;
            title = MessageUtil.parse("<dark_gray>" + displayName + " - Trang: <white>" + page + "/5");
        }

        Gui gui = Gui.gui()
                .title(title)
                .rows(6)
                .create();

        // Fill items (slots 0-44)
        int fn = page * 45 - 45, mn = page * 45, sc = 0;
        for (int g = fn; g < mn; g++) {
            final int slot = sc;
            final int itemId = g;
            if (session.hasItem(itemId)) {
                ItemStack rawItem = session.getItem(itemId).clone();

                GuiItem gi = ItemBuilder.from(rawItem).asGuiItem(event -> {
                    if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                        // Right click → edit chance
                        event.setCancelled(true);
                        saveCurrentPage(player, gui.getInventory(), page);
                        player.closeInventory();
                        openChanceEditor(player, itemId);
                    } else if (event.isShiftClick() && event.getClick().isLeftClick()) {
                        // Shift + Left click → delete item
                        event.setCancelled(true);
                        session.removeItem(itemId);
                        gui.getInventory().setItem(slot, null);
                        MessageUtil.send(player, "<gray>Đã xoá item khỏi danh sách.");
                    }
                    // Plain left click: allow free movement (not cancelled)
                });
                gui.setItem(slot, gi);
            }
            sc++;
        }

        // Decorative panes in bottom row
        GuiItem pane = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .asGuiItem(event -> event.setCancelled(true));
        for (int s : new int[]{46, 47, 48, 50, 51, 52}) gui.setItem(s, pane);

        // Info paper (slot 49)
        GuiItem info = ItemBuilder.from(Material.PAPER)
                .name(MessageUtil.parse("<gold>Thông tin:"))
                .lore(
                        MessageUtil.parse("<green>Thả item vào đây để thêm vào rương."),
                        MessageUtil.parse("<green>Kéo thả item tự do giữa các ô."),
                        MessageUtil.parse("<green>Nhấp chuột phải vào item để"),
                        MessageUtil.parse("<green>chỉnh sửa tỷ lệ."),
                        MessageUtil.parse("<gold>Shift+Click trái: xoá item"),
                        MessageUtil.parse("<red>Nếu không chỉnh tỷ lệ, mặc định là 50.")
                )
                .asGuiItem(event -> event.setCancelled(true));
        gui.setItem(49, info);

        // Back button (slot 45)
        GuiItem back = ItemBuilder.from(Material.ARROW)
                .name(MessageUtil.parse("<red>Quay lại"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    saveCurrentPage(player, gui.getInventory(), page);
                    if (page > 1) {
                        player.closeInventory();
                        session.setCurrentPage(page - 1);
                        openPage(player);
                    } else {
                        // First page → go back to chest type selector
                        String key = session.getStorageKey();
                        saveSession(session, key);
                        endSession(player);
                        player.closeInventory();
                        openChestTypeSelector(player);
                    }
                });
        gui.setItem(45, back);

        // Next page button (slot 53)
        GuiItem next = ItemBuilder.from(Material.ARROW)
                .name(MessageUtil.parse("<red>Trang sau"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    if (page >= 5) return;
                    if (isFull(gui.getInventory())) {
                        saveCurrentPage(player, gui.getInventory(), page);
                        player.closeInventory();
                        session.setCurrentPage(page + 1);
                        openPage(player);
                    } else {
                        MessageUtil.send(player, Main.pl.messages.get("gui.page_full", "<red>Vui lòng điền đầy trang này trước khi chuyển sang trang tiếp theo."));
                    }
                });
        gui.setItem(53, next);

        // Close action: save items
        gui.setCloseGuiAction(event -> {
            Player p = (Player) event.getPlayer();
            saveCurrentPage(p, gui.getInventory(), page);
            ItemEditSession sess = Main.pl.editSessions.get(p);
            if (sess != null) {
                saveSession(sess, sess.getStorageKey());
                MessageUtil.send(p, Main.pl.messages.get("gui.items_updated", "<green>Danh sách vật phẩm đã được cập nhật!"));
                endSession(p);
            }
        });

        gui.open(player);
    }

    private void saveCurrentPage(Player player, Inventory inv, int page) {
        ItemEditSession session = session(player);
        int mf = page * 45 - 45, ml = page * 45;
        for (int i = mf; i < ml; i++) session.removeItem(i);
        for (int i = mf, sc = 0; i < ml; i++, sc++) {
            ItemStack it = inv.getItem(sc);
            if (it != null && it.getType() != Material.AIR) {
                session.setItem(i, it);
            }
        }
    }

    // ── chance editor ──

    public void openChanceEditor(Player player, int id) {
        ItemEditSession session = session(player);
        session.setEditingId(id);
        session.setLastPageNo(session.getCurrentPage());

        ItemStack item = session.getItem(id);
        if (item == null) return;
        item = item.clone();
        int chanceValue = session.getChance(id);

        Gui gui = Gui.gui()
                .title(MessageUtil.parse("<dark_gray>Chỉnh sửa tỷ lệ"))
                .rows(3)
                .disableAllInteractions()
                .create();

        // Decorations
        GuiItem pane = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).asGuiItem(event -> event.setCancelled(true));
        List<Integer> deco = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 19, 20, 21, 22, 23, 24, 25, 26);
        gui.setItem(deco, pane);

        // Current chance display (slot 13) — clickable to type custom value
        GuiItem currentDisplay = buildChanceDisplay(session, id, item, chanceValue, player);
        gui.setItem(13, currentDisplay);

        // Add buttons
        gui.setItem(10, addChanceButton(1, id, gui, player));
        gui.setItem(11, addChanceButton(10, id, gui, player));
        gui.setItem(12, addChanceButton(50, id, gui, player));

        // Remove buttons
        gui.setItem(14, removeChanceButton(1, id, gui, player));
        gui.setItem(15, removeChanceButton(10, id, gui, player));
        gui.setItem(16, removeChanceButton(50, id, gui, player));

        // Back button
        GuiItem back = ItemBuilder.from(Material.ARROW)
                .name(MessageUtil.parse("<red>Quay lại"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    player.closeInventory();
                    session.setCurrentPage(session.getLastPageNo());
                    openPage(player);
                });
        gui.setItem(18, back);

        // Close action
        String chestTypeId = session.getStorageKey();
        gui.setCloseGuiAction(event -> {
            Player p = (Player) event.getPlayer();
            session.setEditingId(-1);
            if (chestTypeId != null) {
                saveSession(session, chestTypeId);
            }
        });

        gui.open(player);
    }

    /** Build the chance-display icon with a clickable name, progress-bar and amount lore. */
    private GuiItem buildChanceDisplay(ItemEditSession session, int id, ItemStack item, int chance, Player player) {
        String bar = progressBar(chance);
        int amount = session.getItemAmount(id);
        return ItemBuilder.from(item)
                .name(MessageUtil.parse("<gold>Tỷ lệ hiện tại: <red>" + chance + " <gold>| Số lượng: <red>" + amount))
                .lore(
                        MessageUtil.parse("<gray>Click để nhập tỷ lệ hoặc số lượng"),
                        MessageUtil.parse("<gray>qua chat."),
                        Component.empty(),
                        MessageUtil.parse("<gray>Nhập số → đặt tỷ lệ"),
                        MessageUtil.parse("<gray>Nhập <white>a &lt;số&gt;<gray> → đặt số lượng"),
                        Component.empty(),
                        MessageUtil.parse(bar)
                )
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    player.closeInventory();
                    requestChatInput(player,
                            "<yellow>Nhập tỷ lệ (1-100) hoặc 'a &lt;số&gt;' (1-99) để đặt số lượng: <white>(ESC để hủy)",
                            input -> {
                                String trimmed = input.trim();
                                // "a <number>" → set amount
                                if (trimmed.toLowerCase().startsWith("a ")) {
                                    try {
                                        int val = Integer.parseInt(trimmed.substring(2).trim());
                                        if (val < 1 || val > 99) {
                                            MessageUtil.send(player, Main.pl.messages.get("gui.amount_out_of_range", "<red>Số lượng phải từ 1 đến 99!"));
                                        } else {
                                            session.setItemAmount(id, val);
                                            MessageUtil.send(player, Main.pl.messages.getFormatted("gui.amount_set", "{so_luong}", String.valueOf(val)));
                                        }
                                    } catch (NumberFormatException e) {
                                        MessageUtil.send(player, "<red>Số không hợp lệ! Vui lòng nhập lại.");
                                    }
                                } else {
                                    // plain number → set chance
                                    try {
                                        int val = Integer.parseInt(trimmed);
                                        if (val < 1 || val > 100) {
                                            MessageUtil.send(player, Main.pl.messages.get("gui.chance_out_of_range", "<red>Tỷ lệ phải từ 1 đến 100!"));
                                        } else {
                                            session.setChance(id, val);
                                            MessageUtil.send(player, Main.pl.messages.getFormatted("gui.chance_set", "{ty_le}", String.valueOf(val)));
                                        }
                                    } catch (NumberFormatException e) {
                                        MessageUtil.send(player, Main.pl.messages.get("gui.chance_invalid_number", "<red>Số không hợp lệ! Vui lòng nhập lại."));
                                    }
                                }
                                openChanceEditor(player, id);
                            });
                });
    }

    /** Format a progress-bar string: {@code <dark_green>[██████░░░░] 60%}. */
    private static String progressBar(int chance) {
        int filled = Math.max(0, Math.min(10, chance / 10));
        int empty = 10 - filled;
        return "<dark_green>[<green>" + "█".repeat(filled) + "<gray>" + "░".repeat(empty) + "<dark_green>] <white>" + chance + "%";
    }

    private GuiItem addChanceButton(int amount, int id, Gui gui, Player player) {
        ItemEditSession session = session(player);
        return ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
                .name(MessageUtil.parse("<green>Thêm " + amount))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    int cur = session.getChance(id);
                    if (cur + amount > 100) {
                        MessageUtil.send(player, Main.pl.messages.get("gui.chance_too_high", "<red>Tỷ lệ không thể lớn hơn 100!"));
                    } else {
                        session.addChance(id, amount);
                        gui.updateItem(13, buildChanceDisplay(session, id, session.getItem(id).clone(), session.getChance(id), player));
                    }
                });
    }

    private GuiItem removeChanceButton(int amount, int id, Gui gui, Player player) {
        ItemEditSession session = session(player);
        return ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                .name(MessageUtil.parse("<red>Xoá " + amount))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    int cur = session.getChance(id);
                    if (cur - amount < 1) {
                        MessageUtil.send(player, Main.pl.messages.get("gui.chance_too_low", "<red>Tỷ lệ không thể nhỏ hơn 1!"));
                    } else {
                        session.addChance(id, -amount);
                        gui.updateItem(13, buildChanceDisplay(session, id, session.getItem(id).clone(), session.getChance(id), player));
                    }
                });
    }

    // ── public entry points ──

    public void openGuiDirect(Player player, String chestTypeId) {
        ItemEditSession session = session(player);
        String ctId = (chestTypeId == null) ? GLOBAL_KEY : chestTypeId;
        session.setEditingChestType(chestTypeId);
        loadSession(session, ctId);
        session.setCurrentPage(1);
        openPage(player);
    }

    public void openGui(Player player) {
        openChestTypeSelector(player);
    }

    // ── chat input ──

    public void requestChatInput(Player player, String prompt, java.util.function.Consumer<String> callback) {
        chatInputHandlers.put(player, callback);
        MessageUtil.send(player, prompt);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        if (chatInputHandlers.containsKey(player)) {
            e.setCancelled(true);
            String msg = e.getMessage();
            if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("hủy")) {
                chatInputHandlers.remove(player);
                MessageUtil.send(player, Main.pl.messages.get("gui.cancelled", "<yellow>Đã hủy."));
                return;
            }
            java.util.function.Consumer<String> handler = chatInputHandlers.remove(player);
            if (handler != null) {
                org.bukkit.Bukkit.getScheduler().runTask(Main.pl, () -> handler.accept(msg));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        chatInputHandlers.remove(p);
        endSession(p);
    }

    // ── legacy helpers (kept for binary compat; delegate to session) ──

    @Deprecated
    public void addOnMap(ItemStack item) {
        // No-op; items are now managed per-session.
    }

    public void loadItems() { loadItems(null); }

    @Deprecated
    public void loadItems(String chestTypeId) {
        // Legacy — called from LoadChances / old code.
        // Ignored; real loading happens via loadSession().
    }

    public void saveItems() { saveItems(null); }

    @Deprecated
    public void saveItems(String chestTypeId) {
        // Legacy — ignored; real saving happens via saveSession().
    }

    public void addchance(int id, int v) {
        // Legacy — ignored.
    }

    public void remove(int id, int v) {
        // Legacy — ignored.
    }

    @Deprecated
    public int chancetoaddorremove(ItemStack item) {
        return Integer.parseInt(ChatColor.stripColor(item.getItemMeta().getDisplayName()).replace("Thêm", "").replace("Xóa", "").replace(" ", ""));
    }

    @Deprecated
    public void save(Inventory inv, int page) {
        // Legacy — ignored.
    }

    @Deprecated
    public boolean hasChatInput(Player player) {
        return chatInputHandlers.containsKey(player);
    }

    @Deprecated
    public void handleChatInput(Player player, String message) {
        java.util.function.Consumer<String> handler = chatInputHandlers.remove(player);
        if (handler != null) handler.accept(message);
    }
}
