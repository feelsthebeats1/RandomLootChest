package me.Herzchen.RandomLootChest.command;

import org.bukkit.command.CommandSender;

public interface CommandHandler {
    boolean handle(CommandSender sender, String[] args);
}