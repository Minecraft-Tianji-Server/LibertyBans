/* 
 * LibertyBans-env-bungee
 * Copyright © 2020 Anand Beh <https://www.arim.space>
 * 
 * LibertyBans-env-bungee is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * LibertyBans-env-bungee is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with LibertyBans-env-bungee. If not, see <https://www.gnu.org/licenses/>
 * and navigate to version 3 of the GNU Affero General Public License.
 */
package space.arim.libertybans.env.bungee;

import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import space.arim.libertybans.core.commands.Commands;
import space.arim.libertybans.core.env.Environment;
import space.arim.libertybans.core.env.PlatformListener;

@Singleton
public class BungeeEnv implements Environment {

	private final Provider<ConnectionListener> connectionListenerProvider;
	private final Provider<ChatListener> chatListenerProvider;
	private final CommandHandler.DependencyPackage commandDependencies;

	@Inject
	public BungeeEnv(Provider<ConnectionListener> connectionListenerProvider,
			Provider<ChatListener> chatListenerProvider, CommandHandler.DependencyPackage commandDependencies) {
		this.connectionListenerProvider = connectionListenerProvider;
		this.chatListenerProvider = chatListenerProvider;
		this.commandDependencies = commandDependencies;
	}

	@Override
	public Set<PlatformListener> createListeners() {
		return Set.of(
				connectionListenerProvider.get(),
				chatListenerProvider.get(),
				new CommandHandler(commandDependencies, Commands.BASE_COMMAND_NAME, false));
	}

	@Override
	public PlatformListener createAliasCommand(String command) {
		return new CommandHandler(commandDependencies, command, true);
	}

}
