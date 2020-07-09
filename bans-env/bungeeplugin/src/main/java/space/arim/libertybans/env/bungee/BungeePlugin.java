/* 
 * LibertyBans-env-bungeeplugin
 * Copyright © 2020 Anand Beh <https://www.arim.space>
 * 
 * LibertyBans-env-bungeeplugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * LibertyBans-env-bungeeplugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with LibertyBans-env-bungeeplugin. If not, see <https://www.gnu.org/licenses/>
 * and navigate to version 3 of the GNU General Public License.
 */
package space.arim.libertybans.env.bungee;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;

import space.arim.libertybans.bootstrap.BaseEnvironment;
import space.arim.libertybans.bootstrap.Instantiator;
import space.arim.libertybans.bootstrap.LibertyBansLauncher;

public class BungeePlugin extends Plugin {

	private BaseEnvironment base;
	
	@Override
	public void onEnable() {
		File folder = getDataFolder();
		ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		ClassLoader launchLoader;
		try {
			LibertyBansLauncher launcher = new LibertyBansLauncher(folder, executor, (clazz) -> {
				try {
					ClassLoader pluginClassLoader = clazz.getClassLoader();
					Field descField = pluginClassLoader.getClass().getDeclaredField("desc");
					Object descObj = descField.get(pluginClassLoader);
					if (descObj instanceof PluginDescription) {
						PluginDescription desc = (PluginDescription) descObj;
						return desc.getName() + " v" + desc.getVersion();
					}
				} catch (IllegalArgumentException | NoSuchFieldException | SecurityException | IllegalAccessException ignored) {}
				return null;
			});
			launchLoader = launcher.attemptLaunch().join();
		} finally {
			executor.shutdown();
			assert executor.isTerminated();
		}
		if (launchLoader == null) {
			getLogger().warning("Failed to launch LibertyBans");
			return;
		}
		try {
			base = new Instantiator("space.arim.libertybans.env.bungee.BungeeEnv", launchLoader).invoke(Plugin.class, this);
		} catch (IllegalArgumentException | SecurityException | ReflectiveOperationException ex) {
			getLogger().log(Level.WARNING, "Failed to launch LibertyBans", ex);
			return;
		}
		base.startup();
	}
	
	@Override
	public void onDisable() {
		if (base == null) {
			getLogger().warning("LibertyBans wasn't launched; check your log for a startup error");
			return;
		}
		base.shutdown();
	}
	
}
