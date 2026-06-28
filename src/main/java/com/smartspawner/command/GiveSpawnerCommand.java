/*
 * SmartSpawner - A Minecraft plugin.
 * Copyright (C) 2026  Nighter
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.smartspawner.command;

import com.smartspawner.item.SpawnerItem;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Backing logic for {@code /smartspawner give smartspawner <type> <amount> [player]}.
 *
 * <p>The {@code <type>} argument is unified: it accepts either an {@link EntityType} key (e.g.
 * {@code pig}) — producing an entity spawner — or a {@link Material} key (e.g. {@code diamond}) —
 * producing an item spawner. Entity types are resolved first; a value that is not a spawnable living
 * entity falls through to the material lookup. The optional {@code [player]} names the recipient;
 * when omitted the spawner is given to the command sender (which must then be a player).
 */
public final class GiveSpawnerCommand {

    private GiveSpawnerCommand() {}

    /** Largest stack the recipient can receive in one command (a vanilla spawner stacks to 64). */
    public static final int MAX_AMOUNT = 64;

    public static int give(CommandSourceStack source, String typeArg, int amount, String targetName) {
        CommandSender sender = source.getSender();

        // Resolve the recipient: an explicit name, or the sender themselves.
        Player recipient;
        if (targetName == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text(
                        "Console must specify a player: /smartspawner give smartspawner <type> <amount> <player>",
                        NamedTextColor.RED));
                return 0;
            }
            recipient = player;
        } else {
            recipient = Bukkit.getPlayerExact(targetName);
            if (recipient == null) {
                sender.sendMessage(Component.text("Player '" + targetName + "' is not online.", NamedTextColor.RED));
                return 0;
            }
        }

        // Build the spawner: entity first, then fall back to an item material.
        ItemStack spawner = build(typeArg, amount);
        if (spawner == null) {
            sender.sendMessage(Component.text(
                    "'" + typeArg + "' is not a valid mob or item type.", NamedTextColor.RED));
            return 0;
        }

        // addItem returns whatever didn't fit; drop the remainder at the recipient's feet so nothing is lost.
        var overflow = recipient.getInventory().addItem(spawner);
        overflow.values().forEach(leftover ->
                recipient.getWorld().dropItemNaturally(recipient.getLocation(), leftover));

        sender.sendMessage(Component.text("Gave ", NamedTextColor.GREEN)
                .append(Component.text(amount + "x ", NamedTextColor.YELLOW))
                .append(Component.text("SmartSpawner (" + typeArg.toLowerCase(Locale.ROOT) + ") ", NamedTextColor.YELLOW))
                .append(Component.text("to " + recipient.getName() + ".", NamedTextColor.GREEN)));
        return 1;
    }

    /**
     * Builds the spawner item for {@code typeArg}, or {@code null} if it names neither a spawnable
     * living entity nor an obtainable item. Shared with the suggestion provider's validity check.
     */
    public static ItemStack build(String typeArg, int amount) {
        String key = normalize(typeArg);

        EntityType entity = resolveEntity(key);
        if (entity != null) {
            return SpawnerItem.entitySpawner(entity, amount);
        }

        Material material = Material.matchMaterial(key);
        if (material != null && material.isItem()) {
            return SpawnerItem.itemSpawner(material, amount);
        }
        return null;
    }

    /**
     * Resolves an {@link EntityType} from a registry key, or {@code null} if none matches. Every
     * registered entity type is accepted — there is no spawnable/living filter — except PLAYER and
     * UNKNOWN, which are not real spawner subjects.
     */
    public static EntityType resolveEntity(String key) {
        NamespacedKey nk = NamespacedKey.minecraft(key);
        EntityType type = Registry.ENTITY_TYPE.get(nk);
        if (type == null || type == EntityType.PLAYER || type == EntityType.UNKNOWN) {
            return null;
        }
        return type;
    }

    /** Lowercases and strips a leading {@code minecraft:} namespace so the key is registry-ready. */
    public static String normalize(String typeArg) {
        String key = typeArg.toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        return key;
    }
}
