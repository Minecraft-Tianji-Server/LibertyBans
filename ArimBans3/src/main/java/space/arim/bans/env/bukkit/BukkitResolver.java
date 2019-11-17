package space.arim.bans.env.bukkit;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.json.simple.parser.ParseException;

import space.arim.bans.api.Tools;
import space.arim.bans.api.exception.FetcherException;
import space.arim.bans.api.exception.MissingCacheException;
import space.arim.bans.api.exception.PlayerNotFoundException;
import space.arim.bans.env.Resolver;

public class BukkitResolver implements Resolver {
	private final BukkitEnv environment;
	private boolean internalFetcher;
	private boolean ashconFetcher;
	private boolean mojangFetcher;
	public BukkitResolver(final BukkitEnv environment) {
		this.environment = environment;
		refreshConfig();
	}
	
	private void handle(Exception ex) {
		if (ex instanceof MissingCacheException || ex instanceof FetcherException) {
			return;
		}
		// ProtocolException is a subclass of IOException
		if (ex instanceof IOException || ex instanceof ParseException) {
			environment.center().logError(ex);
			return;
		}
		ex.printStackTrace();
	}
	
	@Override
	public UUID uuidFromName(final String name) throws PlayerNotFoundException {
		Objects.requireNonNull(name, "name must not be null!");
		try {
			UUID uuid1 = environment.center().cache().getUUID(name);
			return uuid1;
		} catch (Exception ex) {
			handle(ex);
		}
		if (internalFetcher) {
			for (final OfflinePlayer player : environment.plugin().getServer().getOfflinePlayers()) {
				if (player.getName().equalsIgnoreCase(name)) {
					UUID uuid2 = player.getUniqueId();
					environment.center().cache().update(uuid2, name, null);
					return uuid2;
				}
			}
		}
		if (ashconFetcher) {
			try {
				UUID uuid3 = Tools.ashconApi(name);
				environment.center().cache().update(uuid3, name, null);
				return uuid3;
			} catch (Exception ex) {
				handle(ex);
			}
		}
		if (mojangFetcher) {
			try {
				UUID uuid4 = Tools.mojangApi(name);
				environment.center().cache().update(uuid4, name, null);
				return uuid4;
			} catch (Exception ex) {
				handle(ex);
			}
		}
		throw new PlayerNotFoundException(name);
	}
	
	@Override
	public String nameFromUUID(final UUID playeruuid) throws PlayerNotFoundException {
		Objects.requireNonNull(playeruuid, "uuid must not be null!");
		try {
			String name1 = environment.center().cache().getName(playeruuid);
			return name1;
		} catch (Exception ex) {
			handle(ex);
		}
		if (internalFetcher) {
			for (final OfflinePlayer player : environment.plugin().getServer().getOfflinePlayers()) {
				if (player.getUniqueId().equals(playeruuid)) {
					String name2 = player.getName();
					environment.center().cache().update(playeruuid, name2, null);
					return name2;
				}
			}
		}
		if (ashconFetcher) {
			try {
				String name3 = Tools.ashconApi(playeruuid);
				environment.center().cache().update(playeruuid, name3, null);
				return name3;
			} catch (Exception ex) {
				handle(ex);
			}
		}
		if (mojangFetcher) {
			try {
				String name4 = Tools.mojangApi(playeruuid);
				environment.center().cache().update(playeruuid, name4, null);
				return name4;
			} catch (Exception ex) {
				handle(ex);
			}
		}
		throw new PlayerNotFoundException(playeruuid);
	}

	@Override
	public void close() throws Exception {
		
	}

	@Override
	public void refreshConfig() {
		internalFetcher = environment.center().config().getBoolean("fetchers.internal");
		ashconFetcher = environment.center().config().getBoolean("fetchers.ashcon");
		mojangFetcher = environment.center().config().getBoolean("fetchers.mojang");
	}
}