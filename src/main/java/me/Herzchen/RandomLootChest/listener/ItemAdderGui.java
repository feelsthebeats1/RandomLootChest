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
            int aMin = s.getConfigurationSection(String.valueOf(i)).getInt("amount-min", item.getAmount());
            int aMax = s.getConfigurationSection(String.valueOf(i)).getInt("amount-max", aMin);
            session.setItem(i, item);
            session.setChance(i, chance);
            session.setAmountRange(i, aMin, aMax);
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
                ConfigurationSection sec = target.getConfigurationSection(String.valueOf(toset));
                assert sec != null;
                sec.set("item", item);
                sec.set("chance", session.getChance(i));
                int aMin = session.getAmountMin(i);
                int aMax = session.getAmountMax(i);
                if (aMin != 1 || aMax != item.getAmount()) {
                    sec.set("amount-min", aMin);
                    sec.set("amount-max", aMax);
                }
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
        ItemEditSession sess = session(player);
        String filter = sess.getSearchFilter();

        // Collect matching chest types
        List<ChestType> allTypes = new ArrayList<>(ChestType.getAllChestTypes().values());
        List<ChestType> filteredTypes;
        if (filter.isEmpty()) {
            filteredTypes = allTypes;
        } else {
            String lower = filter.toLowerCase();
            filteredTypes = new ArrayList<>();
            // Always include "global" as a match when filter matches "global"
            for (ChestType ct : allTypes) {
                if (ct.getId().toLowerCase().contains(lower) || ct.getDisplayName().toLowerCase().contains(lower))
                    filteredTypes.add(ct);
            }
        }

        // +1 slot for global, +1 slot for search bar
        int extraSlots = 2; // global + search
        if (!filter.isEmpty()) extraSlots++; // clear button
        int count = filteredTypes.size() + extraSlots;
        int rows = Math.max(1, (int) Math.ceil(count / 9.0));
        int size = Math.min(54, rows * 9);

        Component title = filter.isEmpty()
                ? MessageUtil.parse("<dark_gray>Chọn loại rương để chỉnh sửa")
                : MessageUtil.parse("<dark_gray>Tìm: <white>" + filter);

        Gui gui = Gui.gui()
                .title(title)
                .rows(size / 9)
                .create();

        // ── Search bar ──
        GuiItem searchItem = ItemBuilder.from(Material.COMPASS)
                .name(MessageUtil.parse("<aqua><bold>Tìm kiếm</bold></aqua>"))
                .lore(
                        MessageUtil.parse("<gray>Click để nhập từ khoá tìm kiếm"),
                        filter.isEmpty()
                                ? MessageUtil.parse("<gray>(đang hiện tất cả)")
                                : MessageUtil.parse("<gray>Đang lọc: <white>" + filter)
                )
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    player.closeInventory();
                    // Clear previous chat handlers
                    chatInputHandlers.remove(player);
                    requestChatInput(player,
                            "<yellow>Nhập từ khoá tìm kiếm (hoặc nhấn ESC để hủy):",
                            input -> {
                                String trimmed = input.trim();
                                ItemEditSession s = session(player);
                                if (trimmed.equalsIgnoreCase("clear")) {
                                    s.setSearchFilter("");
                                } else {
                                    s.setSearchFilter(trimmed);
                                }
                                openChestTypeSelector(player);
                            });
                });
        gui.addItem(searchItem);

        // ── Global Items ──
        GuiItem globalItem = ItemBuilder.from(Material.CHEST)
                .name(MessageUtil.parse("<gold><bold>Global Items</bold>"))
                .lore(
                        MessageUtil.parse("<gray>Items dùng chung cho tất cả"),
                        MessageUtil.parse("<gray>loại rương không có loot table riêng")
                )
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    ItemEditSession s = session(player);
                    s.setEditingChestType(null);
                    MessageUtil.send(player, Main.pl.messages.get("gui.selected_global", "<green>Đã chọn: <white>Global Items"));
                    player.closeInventory();
                    loadSession(s, GLOBAL_KEY);
                    s.setCurrentPage(1);
                    openPage(player);
                });
        gui.addItem(globalItem);

        // ── Filtered chest types ──
        for (ChestType ct : filteredTypes) {
            String ctId = ct.getId();
            GuiItem ctItem = ItemBuilder.from(ct.getMaterial())
                    .name(MessageUtil.parse("<gold><bold>" + ct.getDisplayName() + "</bold>"))
                    .lore(
                            MessageUtil.parse("<gray>ID: <white>" + ct.getId()),
                            MessageUtil.parse("<gray>Click để chỉnh sửa items cho loại rương này")
                    )
                    .asGuiItem(event -> {
                        event.setCancelled(true);
                        ItemEditSession s = session(player);
                        s.setEditingChestType(ctId);
                        MessageUtil.send(player, Main.pl.messages.getFormatted("gui.selected_type", "{ten}", ct.getDisplayName()));
                        player.closeInventory();
                        loadSession(s, ctId);
                        s.setCurrentPage(1);
                        openPage(player);
                    });
            gui.addItem(ctItem);
        }

        // ── Clear filter button (only visible when filter is active) ──
        if (!filter.isEmpty()) {
            GuiItem clearItem = ItemBuilder.from(Material.BARRIER)
                    .name(MessageUtil.parse("<red><bold>Xoá lọc</bold></red>"))
                    .lore(MessageUtil.parse("<gray>Hiện tất cả loại rương"))
                    .asGuiItem(event -> {
                        event.setCancelled(true);
                        ItemEditSession s = session(player);
                        s.setSearchFilter("");
                        player.closeInventory();
                        openChestTypeSelector(player);
                    });
            gui.addItem(clearItem);
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
                        session.setKeepOnClose(true);    // don't let closeGuiAction end the session
                        player.closeInventory();
                        session.setKeepOnClose(false);
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
                        session.setKeepOnClose(true);
                        player.closeInventory();
                        session.setKeepOnClose(false);
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
                        session.setKeepOnClose(true);
                        player.closeInventory();
                        session.setKeepOnClose(false);
                        session.setCurrentPage(page + 1);
                        openPage(player);
                    } else {
                        MessageUtil.send(player, Main.pl.messages.get("gui.page_full", "<red>Vui lòng điền đầy trang này trước khi chuyển sang trang tiếp theo."));
                    }
                });
        gui.setItem(53, next);

        // Close action: save items (unless navigating to chance editor / another page)
        gui.setCloseGuiAction(event -> {
            Player p = (Player) event.getPlayer();
            ItemEditSession sess = Main.pl.editSessions.get(p);
            if (sess == null) return;
            if (sess.isKeepOnClose()) return; // navigation — don't end session
            saveCurrentPage(p, gui.getInventory(), page);
            saveSession(sess, sess.getStorageKey());
            MessageUtil.send(p, Main.pl.messages.get("gui.items_updated", "<green>Danh sách vật phẩm đã được cập nhật!"));
            endSession(p);
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

        // Decorations (NOT including slot 13 and 22; those are chance/amount displays)
        GuiItem pane = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).asGuiItem(event -> event.setCancelled(true));
        List<Integer> deco = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 19, 20, 21, 23, 24, 25, 26);
        gui.setItem(deco, pane);

        // Current chance display (slot 13)
        GuiItem currentDisplay = buildChanceDisplay(session, id, item, chanceValue, player);
        gui.setItem(13, currentDisplay);

        // Amount display (slot 22) — clickable to edit via chat
        GuiItem amountDisplay = buildAmountDisplay(session, id, item, player);
        gui.setItem(22, amountDisplay);

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

    /** Build the amount display (slot 9). Click to edit via chat: type "5" or "2-5". */
    private GuiItem buildAmountDisplay(ItemEditSession session, int id, ItemStack item, Player player) {
        String display = session.getAmountDisplay(id);
        return ItemBuilder.from(Material.REPEATER)
                .name(MessageUtil.parse("<gold>Số lượng: <white>" + display))
                .lore(
                        MessageUtil.parse("<gray>Click để nhập số lượng"),
                        MessageUtil.parse("<gray>qua chat."),
                        Component.empty(),
                        MessageUtil.parse("<gray>Nhập <white>5<gray> → số lượng cố định là 5"),
                        MessageUtil.parse("<gray>Nhập <white>2-5<gray> → số lượng ngẫu nhiên 2-5"),
                        MessageUtil.parse("<gray>(tự động đảo nếu nhập 5-2)")
                )
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    player.closeInventory();
                    requestChatInput(player,
                            "<yellow>Nhập số lượng (vd: 5) hoặc khoảng (vd: 2-5): <white>(ESC để hủy)",
                            input -> {
                                String trimmed = input.trim();
                                if (trimmed.contains("-")) {
                                    // Range
                                    String[] parts = trimmed.split("-", 2);
                                    try {
                                        int a = Integer.parseInt(parts[0].trim());
                                        int b = Integer.parseInt(parts[1].trim());
                                        if (a < 1 || b < 1 || a > 99 || b > 99) {
                                            MessageUtil.send(player, Main.pl.messages.get("gui.amount_out_of_range", "<red>Số lượng phải từ 1 đến 99!"));
                                        } else {
                                            session.setAmountRange(id, a, b);
                                            MessageUtil.send(player, "<green>Đã đặt số lượng: <white>" + session.getAmountDisplay(id));
                                        }
                                    } catch (NumberFormatException e) {
                                        MessageUtil.send(player, "<red>Định dạng không hợp lệ! Vui lòng nhập '5' hoặc '2-5'.");
                                    }
                                } else {
                                    // Fixed amount
                                    try {
                                        int val = Integer.parseInt(trimmed);
                                        if (val < 1 || val > 99) {
                                            MessageUtil.send(player, Main.pl.messages.get("gui.amount_out_of_range", "<red>Số lượng phải từ 1 đến 99!"));
                                        } else {
                                            session.setFixedAmount(id, val);
                                            MessageUtil.send(player, Main.pl.messages.getFormatted("gui.amount_set", "{so_luong}", session.getAmountDisplay(id)));
                                        }
                                    } catch (NumberFormatException e) {
                                        MessageUtil.send(player, "<red>Định dạng không hợp lệ! Vui lòng nhập '5' hoặc '2-5'.");
                                    }
                                }
                                openChanceEditor(player, id);
                            });
                });
    }

    /** Build the chance-display icon (slot 13). Click to edit chance via chat. */
    private GuiItem buildChanceDisplay(ItemEditSession session, int id, ItemStack item, int chance, Player player) {
        String bar = progressBar(chance);
        return ItemBuilder.from(item)
                .name(MessageUtil.parse("<gold>Tỷ lệ: <red>" + chance))
                .lore(
                        MessageUtil.parse("<gray>Click để nhập tỷ lệ qua chat."),
                        Component.empty(),
                        MessageUtil.parse(bar)
                )
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    player.closeInventory();
                    requestChatInput(player,
                            Main.pl.messages.get("gui.chance_prompt", "<yellow>Nhập tỷ lệ mới (1-100) cho vật phẩm này: <white>(ESC để hủy)"),
                            input -> {
                                try {
                                    int val = Integer.parseInt(input.trim());
                                    if (val < 1 || val > 100) {
                                        MessageUtil.send(player, Main.pl.messages.get("gui.chance_out_of_range", "<red>Tỷ lệ phải từ 1 đến 100!"));
                                    } else {
                                        session.setChance(id, val);
                                        MessageUtil.send(player, Main.pl.messages.getFormatted("gui.chance_set", "{ty_le}", String.valueOf(val)));
                                    }
                                } catch (NumberFormatException e) {
                                    MessageUtil.send(player, Main.pl.messages.get("gui.chance_invalid_number", "<red>Số không hợp lệ! Vui lòng nhập lại."));
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
