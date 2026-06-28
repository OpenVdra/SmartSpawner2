/*
 * SmartSpawner - A Minecraft plugin.
 * Copyright (C) 2026  Nighter
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.smartspawner.listener;

import com.smartspawner.SmartSpawner;
import com.smartspawner.item.SpawnerItem;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Applies the displayed item of a SmartSpawner <i>item spawner</i> at the moment it is placed.
 *
 * <p>The produced material cannot be baked into the item itself: {@link CreatureSpawner#setSpawnedItem}
 * requires a world-attached spawner, but the block state carried by an item has no world.
 * {@link SpawnerItem#itemSpawner} therefore bakes only the {@link EntityType#ITEM} type and stores the
 * concrete material in the item's PDC; here — once the block exists in a world — we read it back and
 * set the spawned item on the real block.
 *
 * <p>The write is deferred a couple of ticks rather than run inline: at {@link BlockPlaceEvent} time
 * vanilla has not finished committing the spawner block-entity, so writing immediately would be
 * overwritten. Scheduling at the block's location also keeps the work on the region thread owning it.
 */
public final class SpawnerPlaceListener implements Listener {

    /** Ticks to wait after placement before writing the spawned item, so vanilla has committed first. */
    private static final long SETUP_DELAY_TICKS = 2L;

    private final SmartSpawner plugin;

    public SpawnerPlaceListener(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack placed = event.getItemInHand();
        Material material = SpawnerItem.spawnedItemMaterial(placed);
        if (material == null) {
            return; // Not a SmartSpawner item spawner — leave it untouched.
        }

        Block block = event.getBlockPlaced();
        plugin.getFoliaLib().getScheduler().runAtLocationLater(block.getLocation(), () -> {
            if (block.getType() != Material.SPAWNER) {
                return; // Block was removed/changed before the task ran.
            }
            BlockState state = block.getState(false);
            if (!(state instanceof CreatureSpawner cs)) {
                return;
            }
            cs.setSpawnedType(EntityType.ITEM);
            cs.setSpawnedItem(new ItemStack(material, 1));
            cs.update(true, false);
        }, SETUP_DELAY_TICKS);
    }
}
