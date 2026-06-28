/*
 * SmartSpawner - A Minecraft plugin.
 * Copyright (C) 2026  Nighter
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.smartspawner;

import com.smartspawner.listener.SpawnerPlaceListener;
import com.tcoded.folialib.FoliaLib;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class SmartSpawner extends JavaPlugin {

    private FoliaLib foliaLib;

    @Override
    public void onEnable() {
        this.foliaLib = new FoliaLib(this);

        getServer().getPluginManager().registerEvents(new SpawnerPlaceListener(this), this);

        getLogger().info("SmartSpawner has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SmartSpawner has been disabled.");
    }
}
