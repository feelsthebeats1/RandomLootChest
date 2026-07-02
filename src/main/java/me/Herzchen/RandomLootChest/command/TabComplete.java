package me.Herzchen.RandomLootChest.command;

import me.Herzchen.RandomLootChest.Main;
import me.Herzchen.RandomLootChest.model.ChestType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import java.util.*;
import java.util.stream.Collectors;

public class TabComplete implements TabCompleter {
    private static final Map<String, String> SUBCOMMANDS = new LinkedHashMap<>() {{
        put("additem", "randomlootchest.additem"); put("addchest", "randomlootchest.fixedchest");
        put("addchesttype", "randomlootchest.fixedchest");
        put("delchest", "randomlootchest.fixedchest"); put("delall", "randomlootchest.delall");
        put("killall", "randomlootchest.killall"); put("togglebreak", "randomlootchest.togglebreak");
        put("forcespawn", "randomlootchest.forcespawn"); put("rndtime", "randomlootchest.rndtime");
        put("reload", "randomlootchest.reload"); put("wand", "randomlootchest.command.wand");
        put("set", "randomlootchest.command.set"); put("unset", "randomlootchest.command.unset");
        put("chesttypes", "randomlootchest.chesttypes"); put("chesttypeinfo", "randomlootchest.chesttypes");
        put("delchesttype", "randomlootchest.fixedchest"); put("resettimer", "randomlootchest.resettimer");
        put("regions", "randomlootchest.regions"); put("spawn", "randomlootchest.spawn");
        put("spawnregion", "randomlootchest.spawnregion"); put("loottable", "randomlootchest.loottable");
    }};
    private static final List<String> CONSOLE_ONLY = Arrays.asList(
            "delall", "killall", "forcespawn", "rndtime", "chesttypes", "chesttypeinfo", "delchesttype",
            "resettimer", "reload", "regions", "spawn", "spawnregion", "loottable");

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (!c.getName().equalsIgnoreCase("rlc")) return null;
        if (args.length == 1) return getFilteredSubcommands(s, args[0]);
        if (args.length >= 2) return handleSubcommand(s, args[0].toLowerCase(), args);
        return Collections.emptyList();
    }

    private List<String> getFilteredSubcommands(CommandSender s, String p) {
        List<String> r = new ArrayList<>();
        for (Map.Entry<String, String> e : TabComplete.SUBCOMMANDS.entrySet())
            if (canUseCommand(s, e.getKey(), e.getValue()) && (p.isEmpty() || StringUtil.startsWithIgnoreCase(e.getKey(), p)))
                r.add(e.getKey());
        Collections.sort(r); return r;
    }

    private List<String> handleSubcommand(CommandSender s, String sub, String[] args) {
        String perm = TabComplete.SUBCOMMANDS.get(sub);
        if (perm != null && !s.hasPermission(perm)) return Collections.emptyList();
        if ("resettimer".equals(sub)) {
            if (args.length == 2) {
                // Suggest "random", "fixedchest", and chest types
                List<String> suggestions = new ArrayList<>();
                if (StringUtil.startsWithIgnoreCase("random", args[1])) suggestions.add("random");
                if (StringUtil.startsWithIgnoreCase("fixedchest", args[1])) suggestions.add("fixedchest");
                for (ChestType ct : ChestType.getAllChestTypes().values())
                    if (StringUtil.startsWithIgnoreCase(ct.getId(), args[1]))
                        suggestions.add(ct.getId());
                return suggestions;
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("random") || args[1].equalsIgnoreCase("fixedchest"))) {
                // Suggest chest types after "random" or "fixedchest"
                List<String> types = new ArrayList<>();
                for (ChestType ct : ChestType.getAllChestTypes().values())
                    if (StringUtil.startsWithIgnoreCase(ct.getId(), args[2]))
                        types.add(ct.getId());
                return types;
            }
            return Collections.emptyList();
        }
        if (("chesttypeinfo".equals(sub) || "delchesttype".equals(sub) || "additem".equals(sub) || "addchest".equals(sub) || "addchesttype".equals(sub)) && args.length == 2) {
            // Suggest chest types
            List<String> types = new ArrayList<>();
            for (ChestType ct : ChestType.getAllChestTypes().values())
                if (StringUtil.startsWithIgnoreCase(ct.getId(), args[1]))
                    types.add(ct.getId());
            return types;
        }
        if ("loottable".equals(sub) && args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("reset");
            for (ChestType ct : ChestType.getAllChestTypes().values())
                if (StringUtil.startsWithIgnoreCase(ct.getId(), args[1]))
                    suggestions.add(ct.getId());
            return suggestions;
        }
        if ("loottable".equals(sub) && args.length == 3 && args[1].equalsIgnoreCase("reset")) {
            List<String> types = new ArrayList<>();
            for (ChestType ct : ChestType.getAllChestTypes().values())
                if (StringUtil.startsWithIgnoreCase(ct.getId(), args[2]))
                    types.add(ct.getId());
            return types;
        }
        if ("set".equals(sub) && args.length == 2) {
            // Suggest chest types for /rlc set <chestType>
            List<String> types = new ArrayList<>();
            types.add("default");
            for (ChestType ct : ChestType.getAllChestTypes().values()) {
                if (StringUtil.startsWithIgnoreCase(ct.getId(), args[1]))
                    types.add(ct.getId());
            }
            return types;
        }
        if ("give".equals(sub) && args.length == 2)
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> StringUtil.startsWithIgnoreCase(n, args[1])).collect(Collectors.toList());
        if ("give".equals(sub) && args.length == 3)
            return Main.items.keySet().stream().map(String::valueOf).filter(id -> StringUtil.startsWithIgnoreCase(id, args[2])).collect(Collectors.toList());
        return Collections.emptyList();
    }

    private boolean canUseCommand(CommandSender s, String sub, String perm) {
        if (!(s instanceof Player) && !CONSOLE_ONLY.contains(sub)) return false;
        return perm == null || s.hasPermission(perm);
    }
}