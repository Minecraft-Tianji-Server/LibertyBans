/* 
 * LibertyBans-integrationtest
 * Copyright © 2020 Anand Beh <https://www.arim.space>
 * 
 * LibertyBans-integrationtest is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * LibertyBans-integrationtest is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with LibertyBans-integrationtest. If not, see <https://www.gnu.org/licenses/>
 * and navigate to version 3 of the GNU Affero General Public License.
 */
package space.arim.libertybans.it.env;

import java.util.Set;

import space.arim.libertybans.core.env.Environment;
import space.arim.libertybans.core.env.PlatformListener;

public class QuackEnv implements Environment {

	@Override
	public Set<PlatformListener> createListeners() {
		return Set.of();
	}

	@Override
	public PlatformListener createAliasCommand(String command) {
		return new PlatformListener() {

			@Override
			public void register() {
			}

			@Override
			public void unregister() {
			}

		};
	}

}
