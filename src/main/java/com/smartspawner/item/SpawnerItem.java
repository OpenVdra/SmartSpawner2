/*
 * SmartSpawner - A Minecraft plugin.
 * Copyright (C) 2026  Nighter
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.smartspawner.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/**
 * Builds the spawner {@link ItemStack} that {@code /smartspawner give} hands out. Two flavours are
 * produced from the same {@link Material#SPAWNER} item:
 * <ul>
 *     <li><b>Entity spawner</b> — spawns a mob ({@code pig}, {@code zombie}, …).</li>
 *     <li><b>Item spawner</b> — produces an item ({@code diamond}, {@code iron_ingot}, …).</li>
 * </ul>
 *
 * <p>The spawned type is baked into the item via {@link BlockStateMeta}, so a placed entity spawner
 * already shows the right mob. An item spawner additionally needs its displayed item applied on place
 * (see {@code SpawnerPlaceListener}), which cannot be baked into a worldless item state. The flavour
 * and concrete type are stored in the item's {@link PersistentDataContainer} so the plugin can
 * identify its own spawners later without parsing the display name.
 */
public final class SpawnerItem {

    private SpawnerItem() {}

    /** {@code "entity"} or {@code "item"} — which flavour of SmartSpawner this is. */
    public static final NamespacedKey KEY_KIND = new NamespacedKey("smartspawner", "kind");
    /** For an entity spawner: the {@link EntityType} key (e.g. {@code pig}). Absent on item spawners. */
    public static final NamespacedKey KEY_ENTITY = new NamespacedKey("smartspawner", "entity");
    /** For an item spawner: the {@link Material} key (e.g. {@code diamond}). Absent on entity spawners. */
    public static final NamespacedKey KEY_ITEM = new NamespacedKey("smartspawner", "item");

    public static final String KIND_ENTITY = "entity";
    public static final String KIND_ITEM = "item";

    /** Builds {@code amount} stacked entity spawners for the given mob type. */
    public static ItemStack entitySpawner(EntityType type, int amount) {
        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();

        // Bake the spawned mob into the item so the placed block isn't an empty cage.
        if (meta instanceof BlockStateMeta blockMeta) {
            BlockState state = blockMeta.getBlockState();
            if (state instanceof CreatureSpawner cs) {
                // Not every entity type is a valid spawner subject; ignore the ones the API rejects
                // (those simply carry no baked type) rather than failing the give.
                try {
                    cs.setSpawnedType(type);
                } catch (IllegalArgumentException ignored) {
                }
                blockMeta.setBlockState(cs);
            }
        }

        String name = prettyName(type.getKey().getKey());
        meta.displayName(title(name + " Spawner"));
        meta.lore(List.of(
                line(Component.text("Type: ", NamedTextColor.GRAY)
                        .append(Component.text("Entity", NamedTextColor.AQUA))),
                line(Component.text("Spawns: ", NamedTextColor.GRAY)
                        .append(Component.text(name, NamedTextColor.WHITE)))
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_KIND, PersistentDataType.STRING, KIND_ENTITY);
        pdc.set(KEY_ENTITY, PersistentDataType.STRING, type.getKey().getKey());

        spawner.setItemMeta(meta);
        return spawner;
    }

    /** Builds {@code amount} stacked item spawners for the given material. */
    public static ItemStack itemSpawner(Material material, int amount) {
        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();

        // Bake the ITEM entity type so the placed block is already an item spawner. The displayed
        // ItemStack can't be baked here: CreatureSpawner#setSpawnedItem needs a world-attached spawner,
        // but an item's BlockState has none — it's applied on place instead (see SpawnerPlaceListener).
        // The concrete material is stored in the PDC below.
        if (meta instanceof BlockStateMeta blockMeta) {
            BlockState state = blockMeta.getBlockState();
            if (state instanceof CreatureSpawner cs) {
                cs.setSpawnedType(EntityType.ITEM);
                blockMeta.setBlockState(cs);
            }
        }

        String name = prettyName(material.getKey().getKey());
        meta.displayName(title(name + " Spawner"));
        meta.lore(List.of(
                line(Component.text("Type: ", NamedTextColor.GRAY)
                        .append(Component.text("Item", NamedTextColor.GOLD))),
                line(Component.text("Produces: ", NamedTextColor.GRAY)
                        .append(Component.text(name, NamedTextColor.WHITE)))
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_KIND, PersistentDataType.STRING, KIND_ITEM);
        pdc.set(KEY_ITEM, PersistentDataType.STRING, material.getKey().getKey());

        spawner.setItemMeta(meta);
        return spawner;
    }

    /**
     * For an item spawner, the {@link Material} it produces, or {@code null} if {@code item} is not a
     * SmartSpawner item spawner (or its stored material key no longer resolves).
     */
    public static Material spawnedItemMaterial(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!KIND_ITEM.equals(pdc.get(KEY_KIND, PersistentDataType.STRING))) {
            return null;
        }
        String key = pdc.get(KEY_ITEM, PersistentDataType.STRING);
        return key == null ? null : Material.matchMaterial(key);
    }

    /** Turns a registry key like {@code iron_ingot} into a display label like {@code Iron Ingot}. */
    private static String prettyName(String key) {
        String[] words = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private static Component title(String text) {
        return Component.text(text, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
    }

    private static Component line(Component content) {
        return content.decoration(TextDecoration.ITALIC, false);
    }
}
