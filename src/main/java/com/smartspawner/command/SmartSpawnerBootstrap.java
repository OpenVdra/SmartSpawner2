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

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registers the {@code /smartspawner} command tree (aliases {@code /spawner}, {@code /ss}) using
 * Paper's Brigadier command API. Registering from a {@link PluginBootstrap} — rather than at
 * {@code onEnable} — lets the commands exist before the first {@code COMMANDS} lifecycle event, so
 * tab-completion works for the very first join.
 *
 * <p>Command shape:
 * <pre>/smartspawner give smartspawner &lt;type&gt; &lt;amount&gt; [player]</pre>
 * where {@code <type>} is a unified argument accepting both mob types (entity spawner) and item
 * materials (item spawner) — see {@link GiveSpawnerCommand}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SmartSpawnerBootstrap implements PluginBootstrap {

    /** Permission gating {@code /smartspawner give}. */
    private static final String GIVE_PERMISSION = "smartspawner.command.give";

    /** A purely-informational header pinned to the top of the {@code <type>} suggestion list. */
    private static final Message HEADER_TOOLTIP = new LiteralMessage("Info only — not a value");
    private static final Message ENTITY_TOOLTIP = new LiteralMessage("Entity spawner");
    private static final Message ITEM_TOOLTIP = new LiteralMessage("Item spawner");
    private static final Message PLAYER_TOOLTIP = new LiteralMessage("Player");

    // Candidate type keys (mob types, then item materials) are gathered once and cached. They must be
    // built LAZILY — on first suggestion, not at class-load — because this class loads during the
    // bootstrap phase, when org.bukkit.Registry (which Material.isItem()/EntityType.isSpawnable()
    // touch) is not yet initialised. Touching it that early crashes the whole server boot.
    private static volatile List<String> entityKeys;
    private static volatile List<String> itemKeys;

    private static final int[] AMOUNT_VALUES = {1, 8, 16, 32, 64};

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    Commands.literal("smartspawner")
                            .then(Commands.literal("give")
                                    .requires(src -> src.getSender().hasPermission(GIVE_PERMISSION))
                                    // The literal 'smartspawner' marks the item being given (vs. any
                                    // future give targets), matching the requested command shape.
                                    .then(Commands.literal("smartspawner")
                                            .then(Commands.argument("type", StringArgumentType.word())
                                                    .suggests(TYPE_SUGGESTIONS)
                                                    // Amount omitted → default to 1, given to the sender.
                                                    .executes(ctx -> GiveSpawnerCommand.give(
                                                            ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "type"),
                                                            1,
                                                            null))
                                                    .then(Commands.argument("amount",
                                                                    IntegerArgumentType.integer(1, GiveSpawnerCommand.MAX_AMOUNT))
                                                            .suggests(AMOUNT_SUGGESTIONS)
                                                            .executes(ctx -> GiveSpawnerCommand.give(
                                                                    ctx.getSource(),
                                                                    StringArgumentType.getString(ctx, "type"),
                                                                    IntegerArgumentType.getInteger(ctx, "amount"),
                                                                    null))
                                                            // Optional [player] → give to someone else (required from console).
                                                            .then(Commands.argument("player", StringArgumentType.word())
                                                                    .suggests(ONLINE_PLAYERS)
                                                                    .executes(ctx -> GiveSpawnerCommand.give(
                                                                            ctx.getSource(),
                                                                            StringArgumentType.getString(ctx, "type"),
                                                                            IntegerArgumentType.getInteger(ctx, "amount"),
                                                                            StringArgumentType.getString(ctx, "player"))))))))
                            .build(),
                    "SmartSpawner commands",
                    List.of("spawner", "ss")
            );
        });
    }

    /**
     * Suggests valid {@code <type>} values: mob types (entity spawners) first, then item materials
     * (item spawners), each filtered by the typed prefix and tagged with a flavour tooltip. Capped so
     * a prefix matching hundreds of materials can't flood the client.
     */
    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGESTIONS = (ctx, builder) -> {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        if (prefix.isEmpty()) {
            builder.suggest("(mob or item)", HEADER_TOOLTIP);
        }
        int added = 0;
        for (String key : entityKeys()) {
            if (key.startsWith(prefix)) {
                builder.suggest(key, ENTITY_TOOLTIP);
                if (++added >= 50) return builder.buildFuture();
            }
        }
        for (String key : itemKeys()) {
            if (key.startsWith(prefix)) {
                builder.suggest(key, ITEM_TOOLTIP);
                if (++added >= 50) return builder.buildFuture();
            }
        }
        return builder.buildFuture();
    };

    /** Suggests a few common stack sizes for the {@code <amount>} argument. */
    private static final SuggestionProvider<CommandSourceStack> AMOUNT_SUGGESTIONS = (ctx, builder) -> {
        for (int value : AMOUNT_VALUES) {
            builder.suggest(value);
        }
        return builder.buildFuture();
    };

    /** Suggests names of currently online players for the optional {@code [player]} argument. */
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (ctx, builder) -> {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(p.getName(), PLAYER_TOOLTIP);
            }
        }
        return builder.buildFuture();
    };

    /** Lazily builds and caches the entity-spawner keys on first use (see {@link #entityKeys} for why). */
    private static List<String> entityKeys() {
        List<String> local = entityKeys;
        if (local == null) {
            synchronized (SmartSpawnerBootstrap.class) {
                local = entityKeys;
                if (local == null) {
                    entityKeys = local = collectEntityKeys();
                }
            }
        }
        return local;
    }

    /** Lazily builds and caches the item-spawner keys on first use (see {@link #itemKeys} for why). */
    private static List<String> itemKeys() {
        List<String> local = itemKeys;
        if (local == null) {
            synchronized (SmartSpawnerBootstrap.class) {
                local = itemKeys;
                if (local == null) {
                    itemKeys = local = collectItemKeys();
                }
            }
        }
        return local;
    }

    /** Every entity-type key (e.g. {@code pig}) except PLAYER/UNKNOWN — the valid entity-spawner types. */
    private static List<String> collectEntityKeys() {
        List<String> keys = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            if (type == EntityType.PLAYER || type == EntityType.UNKNOWN) continue;
            keys.add(type.getKey().getKey());
        }
        keys.sort(String::compareTo);
        return List.copyOf(keys);
    }

    /** All obtainable item material keys (e.g. {@code diamond}) — the valid item-spawner types. */
    private static List<String> collectItemKeys() {
        List<String> keys = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isLegacy() || !material.isItem()) continue;
            keys.add(material.getKey().getKey());
        }
        keys.sort(String::compareTo);
        return List.copyOf(keys);
    }
}
