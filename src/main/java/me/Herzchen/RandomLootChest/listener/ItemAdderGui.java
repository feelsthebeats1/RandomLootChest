package me.Herzchen.RandomLootChest.listener;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.Herzchen.RandomLootChest.Main;
import me.Herzchen.RandomLootChest.database.LoadChances;
import me.Herzchen.RandomLootChest.model.ChestType;
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

public class ItemAdderGui implements Listener {
    LoadChances lc = new LoadChances();

    public static final String GLOBAL_KEY = "__global__";
    public java.util.Map<Player, String> editingChestType = new java.util.HashMap<>();

    /** Players waiting for chat input: player -> callback */
    private final Map<Player, java.util.function.Consumer<String>> chatInputHandlers = new ConcurrentHashMap<>();

    public ConfigurationSection getItemSection(String chestTypeId) {
        ConfigurationSection root = Main.pl.db.data.getConfigurationSection("ItemDatabase");
        if (root == null) return null;
        if (chestTypeId == null || chestTypeId.equals(GLOBAL_KEY)) return root;
        ConfigurationSection ctSection = root.getConfigurationSection(chestTypeId);
        if (ctSection == null) ctSection = root.createSection(chestTypeId);
        return ctSection;
    }

    public void addOnMap(ItemStack item) {
        for (int i = 0; i < 100000; i++) { if (Main.pl.itemstoadd.get(i) == null) { Main.pl.itemstoadd.put(i, item); break; } }
    }

    public void loadItems() { loadItems(null); }

    public void loadItems(String chestTypeId) {
        Main.pl.db.loadData();
        ConfigurationSection s = getItemSection(chestTypeId);
        if (s == null) return;
        Main.pl.itemstoadd.clear();
        Main.pl.chances.clear();
        for (int i = 0; i < 10000 && s.isConfigurationSection(String.valueOf(i)); i++) {
            Main.pl.itemstoadd.put(i, Objects.requireNonNull(s.getConfigurationSection(String.valueOf(i))).getItemStack("item"));
            Main.pl.chances.put(i, Objects.requireNonNull(s.getConfigurationSection(String.valueOf(i))).getInt("chance"));
        }
    }

    public boolean isFull(Inventory inv) { for (int i = 0; i < 45; i++) if (inv.getItem(i) == null) return false; return true; }

    public void saveItems() { saveItems(null); }

    public void saveItems(String chestTypeId) {
        Main.pl.db.loadData();
        ConfigurationSection target = getItemSection(chestTypeId);
        if (target == null) return;
        // When saving global (null = root section), only clear numeric keys, NOT sub-sections (chest type IDs)
        if (chestTypeId == null || chestTypeId.equals(GLOBAL_KEY)) {
            for (String k : target.getKeys(false)) {
                if (k.matches("\\d+")) target.set(k, null);
            }
        } else {
            for (String k : target.getKeys(false)) target.set(k, null);
        }
        int toset = 0;
        for (int i = 0; i < 10000; i++) {
            if (Main.pl.itemstoadd.get(i) != null) {
                target.createSection(String.valueOf(toset));
                Objects.requireNonNull(target.getConfigurationSection(String.valueOf(toset))).set("item", Main.pl.itemstoadd.get(i));
                Objects.requireNonNull(target.getConfigurationSection(String.valueOf(toset))).set("chance", Main.pl.chances.getOrDefault(i, 50));
                toset++;
            }
        }
        Main.pl.db.saveData();
        if (chestTypeId == null) lc.loaditems();
    }

    public void addchance(int id, int v) { Main.pl.chances.compute(id, (k, c) -> (c == null ? 0 : c) + v); }
    public void remove(int id, int v) { Main.pl.chances.compute(id, (k, c) -> (c == null ? 0 : c) - v); }

    public int chancetoaddorremove(ItemStack item) {
        return Integer.parseInt(ChatColor.stripColor(item.getItemMeta().getDisplayName()).replace("Thêm", "").replace("Xóa", "").replace(" ", ""));
    }

    public void save(Inventory inv, int page) {
        int mf = page * 45 - 45, ml = page * 45;
        for (int i = mf, sc = 0; i < ml; i++, sc++) {
            ItemStack it = inv.getItem(sc);
            if (it != null) Main.pl.itemstoadd.put(i, it); else Main.pl.itemstoadd.remove(i);
        }
        saveItems();
    }

    /** Request a chat input from the player. */
    public void requestChatInput(Player player, String prompt, java.util.function.Consumer<String> callback) {
        chatInputHandlers.put(player, callback);
        MessageUtil.send(player, prompt);
    }

    public boolean hasChatInput(Player player) {
        return chatInputHandlers.containsKey(player);
    }

    public void handleChatInput(Player player, String message) {
        java.util.function.Consumer<String> handler = chatInputHandlers.remove(player);
        if (handler != null) handler.accept(message);
    }

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
                    editingChestType.put(player, GLOBAL_KEY);
                        MessageUtil.send(player, Main.pl.messages.get("gui.selected_global", "<green>Đã chọn: <white>Global Items"));
                        player.closeInventory();
                    loadItems(GLOBAL_KEY);
                    openPage(player, 1);
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
                        editingChestType.put(player, ctId);
                        MessageUtil.send(player, Main.pl.messages.getFormatted("gui.selected_type", "{ten}", ct.getDisplayName()));
                        player.closeInventory();
                        loadItems(ctId);
                        openPage(player, 1);
                    });
            gui.addItem(ctItem);
        }

        gui.open(player);
    }

    public void openPage(Player player, int page) {
        String ctId = editingChestType.get(player);
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

        Main.pl.additem.add(player);
        Main.pl.currentpage.put(player, page);

        // Fill items (slots 0-44)
        int fn = page * 45 - 45, mn = page * 45, sc = 0;
        for (int g = fn; g < mn; g++) {
            final int slot = sc;
            final int itemId = g;
            if (Main.pl.itemstoadd.get(g) != null) {
                ItemStack rawItem = Main.pl.itemstoadd.get(g).clone();
                final int finalItemId = itemId;
                
                GuiItem gi = ItemBuilder.from(rawItem).asGuiItem(event -> {
                    if (event.getClick().equals(ClickType.RIGHT) || event.getClick().equals(ClickType.SHIFT_RIGHT)) {
                        event.setCancelled(true);
                        // Right click → save current page, close old GUI, then open chance editor
                        saveCurrentPage(player, gui.getInventory(), page);
                        String cid = editingChestType.get(player);
                        player.closeInventory();
                        openChanceEditor(player, finalItemId, page, cid);
                    }
                    // Left click: allow free movement (not cancelled)
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
                        // Go to previous page (save already done)
                        player.closeInventory();
                        openPage(player, page - 1);
                    } else {
                        // First page → go back to chest type selector
                        String id = editingChestType.get(player);
                        if (id != null) {
                            saveItems(id.equals(GLOBAL_KEY) ? null : id);
                            editingChestType.remove(player);
                            Main.pl.additem.remove(player);
                            Main.pl.currentpage.remove(player);
                        }
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
                        openPage(player, page + 1);
                    } else {
                        MessageUtil.send(player, Main.pl.messages.get("gui.page_full", "<red>Vui lòng điền đầy trang này trước khi chuyển sang trang tiếp theo."));
                    }
                });
        gui.setItem(53, next);

        // Close action: save items (genuine close by player)
        gui.setCloseGuiAction(event -> {
            Player p = (Player) event.getPlayer();
            saveCurrentPage(p, gui.getInventory(), page);
            String cid = editingChestType.get(p);
            // Only save if cid exists (genuine close). Skip if null (navigation via back/next/right-click already saved)
            if (cid != null) {
                saveItems(cid.equals(GLOBAL_KEY) ? null : cid);
            }
            if (Main.pl.additem.contains(p)) {
                MessageUtil.send(p, Main.pl.messages.get("gui.items_updated", "<green>Danh sách vật phẩm đã được cập nhật!"));
                Main.pl.additem.remove(p);
                Main.pl.currentpage.remove(p);
                editingChestType.remove(p);
            }
        });

        gui.open(player);
    }

    private void saveCurrentPage(Player player, Inventory inv, int page) {
        int mf = page * 45 - 45, ml = page * 45;
        // Clear items for this page range first, then re-sync from inventory
        for (int i = mf; i < ml; i++) {
            Main.pl.itemstoadd.remove(i);
        }
        for (int i = mf, sc = 0; i < ml; i++, sc++) {
            ItemStack it = inv.getItem(sc);
            if (it != null && it.getType() != Material.AIR) {
                Main.pl.itemstoadd.put(i, it);
            }
        }
    }

    public void openChanceEditor(Player player, int id, int page, String chestTypeId) {
        Main.pl.idediting.put(player, id);
        Main.pl.lastpageno.put(player, page);

        ItemStack item = Main.pl.itemstoadd.get(id);
        if (item == null) return;
        item = item.clone();
        int finalId = id;

        Gui gui = Gui.gui()
                .title(MessageUtil.parse("<dark_gray>Chỉnh sửa tỷ lệ"))
                .rows(3)
                .disableAllInteractions()
                .create();

        // Decorations
        GuiItem pane = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).asGuiItem(event -> event.setCancelled(true));
        List<Integer> deco = Arrays.asList(0,1,2,3,4,5,6,7,8,9,17,19,20,21,22,23,24,25,26);
        gui.setItem(deco, pane);

        // Current chance display (slot 13) — clickable to type custom value
        GuiItem currentDisplay = ItemBuilder.from(item)
                .name(MessageUtil.parse("<gold>Tỷ lệ hiện tại: <red>" + Main.pl.chances.get(id)))
                .lore(
                        MessageUtil.parse("<gray>Click để nhập tỷ lệ tùy chỉnh"),
                        MessageUtil.parse("<gray>qua chat.")
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
                                        Main.pl.chances.put(finalId, val);
                                        MessageUtil.send(player, Main.pl.messages.getFormatted("gui.chance_set", "{ty_le}", String.valueOf(val)));
                                    }
                                } catch (NumberFormatException e) {
                                    MessageUtil.send(player, Main.pl.messages.get("gui.chance_invalid_number", "<red>Số không hợp lệ! Vui lòng nhập lại."));
                                }
                                // Reopen chance editor
                                openChanceEditor(player, finalId, page, chestTypeId);
                            });
                });
        gui.setItem(13, currentDisplay);

        // Add buttons
        gui.setItem(10, addChanceButton("<green>Thêm 1", 1, finalId, gui, player, page));
        gui.setItem(11, addChanceButton("<green>Thêm 10", 10, finalId, gui, player, page));
        gui.setItem(12, addChanceButton("<green>Thêm 50", 50, finalId, gui, player, page));

        // Remove buttons
        gui.setItem(14, removeChanceButton("<red>Xoá 1", 1, finalId, gui, player, page));
        gui.setItem(15, removeChanceButton("<red>Xoá 10", 10, finalId, gui, player, page));
        gui.setItem(16, removeChanceButton("<red>Xoá 50", 50, finalId, gui, player, page));

        // Back button - returns to item editor page
        GuiItem back = ItemBuilder.from(Material.ARROW)
                .name(MessageUtil.parse("<red>Quay lại"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    player.closeInventory();
                    openPage(player, page);
                });
        gui.setItem(18, back);

        // Close action: save with the correct chestTypeId (passed as parameter, not read from editable map)
        gui.setCloseGuiAction(event -> {
            Player p = (Player) event.getPlayer();
            Main.pl.idediting.remove(p);
            Main.pl.lastpageno.remove(p);
            if (chestTypeId != null) {
                saveItems(chestTypeId.equals(GLOBAL_KEY) ? null : chestTypeId);
            }
        });

        gui.open(player);
    }

    private GuiItem addChanceButton(String name, int amount, int id, Gui gui, Player player, int page) {
        return ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
                .name(MessageUtil.parse(name))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    int cur = Main.pl.chances.getOrDefault(id, 50);
                    if (cur + amount > 100) {
                        MessageUtil.send(player, Main.pl.messages.get("gui.chance_too_high", "<red>Tỷ lệ không thể lớn hơn 100!"));
                    } else {
                        addchance(id, amount);
                        // Update display
                        GuiItem display = ItemBuilder.from(Main.pl.itemstoadd.get(id).clone())
                                .name(MessageUtil.parse("<gold>Tỷ lệ hiện tại: <red>" + Main.pl.chances.get(id)))
                                .lore(
                                        MessageUtil.parse("<gray>Click để nhập tỷ lệ tùy chỉnh"),
                                        MessageUtil.parse("<gray>qua chat.")
                                )
                                .asGuiItem(e -> e.setCancelled(true));
                        gui.updateItem(13, display);
                    }
                });
    }

    private GuiItem removeChanceButton(String name, int amount, int id, Gui gui, Player player, int page) {
        return ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                .name(MessageUtil.parse(name))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    int cur = Main.pl.chances.getOrDefault(id, 50);
                    if (cur - amount < 1) {
                        MessageUtil.send(player, Main.pl.messages.get("gui.chance_too_low", "<red>Tỷ lệ không thể nhỏ hơn 1!"));
                    } else {
                        remove(id, amount);
                        // Update display
                        GuiItem display = ItemBuilder.from(Main.pl.itemstoadd.get(id).clone())
                                .name(MessageUtil.parse("<gold>Tỷ lệ hiện tại: <red>" + Main.pl.chances.get(id)))
                                .lore(
                                        MessageUtil.parse("<gray>Click để nhập tỷ lệ tùy chỉnh"),
                                        MessageUtil.parse("<gray>qua chat.")
                                )
                                .asGuiItem(e -> e.setCancelled(true));
                        gui.updateItem(13, display);
                    }
                });
    }

    /**
     * Open directly to a specific chest type's item editor (skip selector).
     * @param chestTypeId the chest type id, or null/null/"__global__" for global items
     */
    public void openGuiDirect(Player player, String chestTypeId) {
        String ctId = (chestTypeId == null) ? GLOBAL_KEY : chestTypeId;
        editingChestType.put(player, ctId);
        loadItems(ctId);
        openPage(player, 1);
    }

    public void openGui(Player player) {
        openChestTypeSelector(player);
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
            // Run handler on main thread
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
        if (Main.pl.additem.contains(p)) {
            Main.pl.additem.remove(p);
            Main.pl.currentpage.remove(p);
            editingChestType.remove(p);
        }
        Main.pl.addChestTypeSelector.remove(p);
        Main.pl.idediting.remove(p);
        Main.pl.lastpageno.remove(p);
    }
}