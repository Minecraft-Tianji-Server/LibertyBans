/* 
 * LibertyBans-core
 * Copyright © 2020 Anand Beh <https://www.arim.space>
 * 
 * LibertyBans-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * LibertyBans-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with LibertyBans-core. If not, see <https://www.gnu.org/licenses/>
 * and navigate to version 3 of the GNU Affero General Public License.
 */
package space.arim.libertybans.core.punish;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import space.arim.omnibus.util.concurrent.CentralisedFuture;
import space.arim.omnibus.util.concurrent.FactoryOfTheFuture;

import space.arim.api.chat.SendableMessage;
import space.arim.api.env.PlatformHandle;
import space.arim.api.env.annote.PlatformPlayer;
import space.arim.api.util.web.UUIDUtil;

import space.arim.libertybans.api.AddressVictim;
import space.arim.libertybans.api.NetworkAddress;
import space.arim.libertybans.api.PlayerVictim;
import space.arim.libertybans.api.PunishmentType;
import space.arim.libertybans.api.punish.Punishment;
import space.arim.libertybans.core.config.Configs;
import space.arim.libertybans.core.config.InternalFormatter;
import space.arim.libertybans.core.database.InternalDatabase;
import space.arim.libertybans.core.env.EnvEnforcer;
import space.arim.libertybans.core.env.ExactTargetMatcher;
import space.arim.libertybans.core.env.TargetMatcher;
import space.arim.libertybans.core.env.UUIDTargetMatcher;
import space.arim.libertybans.core.selector.AddressStrictness;
import space.arim.libertybans.core.selector.InternalSelector;
import space.arim.libertybans.core.selector.MuteCache;
import space.arim.libertybans.core.uuid.UUIDManager;

@Singleton
public class StandardEnforcer implements Enforcer {

	private final Configs configs;
	private final FactoryOfTheFuture futuresFactory;
	private final Provider<InternalDatabase> dbProvider;
	private final InternalSelector selector;
	private final InternalFormatter formatter;
	private final UUIDManager uuidManager;
	private final MuteCache muteCache;
	private final EnvEnforcer envEnforcer;
	private final PlatformHandle envHandle;
	
	@Inject
	public StandardEnforcer(Configs configs, FactoryOfTheFuture futuresFactory, Provider<InternalDatabase> dbProvider,
			InternalSelector selector, InternalFormatter formatter, EnvEnforcer envEnforcer,
			PlatformHandle envHandle, UUIDManager uuidManager, MuteCache muteCache) {
		this.configs = configs;
		this.futuresFactory = futuresFactory;
		this.dbProvider = dbProvider;
		this.selector = selector;
		this.formatter = formatter;
		this.envEnforcer = envEnforcer;
		this.envHandle = envHandle;
		this.uuidManager = uuidManager;
		this.muteCache = muteCache;
	}

	@Override
	public CentralisedFuture<?> enforce(Punishment punishment) {
		CentralisedFuture<SendableMessage> futureMessage = formatter.getPunishmentMessage(punishment);
		switch (punishment.getVictim().getType()) {
		case PLAYER:
			UUID uuid = ((PlayerVictim) punishment.getVictim()).getUUID();
			CentralisedFuture<?> enforceFuture = futureMessage.thenAccept((message) -> {
				envEnforcer.doForPlayerIfOnline(uuid, enforcementCallback(punishment, message));
			});
			return enforceFuture;
		case ADDRESS:
			return futureMessage.thenCompose((message) -> enforceAddressPunishment(punishment, message));
		default:
			throw MiscUtil.unknownVictimType(punishment.getVictim().getType());
		}
	}
	
	private static boolean shouldKick(PunishmentType type) {
		switch (type) {
		case BAN:
		case KICK:
			return true;
		case MUTE:
		case WARN:
			return false;
		default:
			throw MiscUtil.unknownType(type);
		}
	}
	
	private Consumer<@PlatformPlayer Object> enforcementCallback(Punishment punishment, SendableMessage message) {
		PunishmentType type = punishment.getType();
		boolean shouldKick = shouldKick(type);
		return (playerObj) -> {

			if (shouldKick) {
				envHandle.disconnectUser(playerObj, message);
			} else {
				envHandle.sendMessage(playerObj, message);

				/*
				 * Mute enforcement must additionally take into account the mute cache
				 */
				if (type == PunishmentType.MUTE) {
					UUID uuid = envEnforcer.getUniqueIdFor(playerObj);
					NetworkAddress address = NetworkAddress.of(envEnforcer.getAddressFor(playerObj));
					muteCache.setCachedMute(uuid, address, punishment);
				}
			}
		};
	}
	
	private CentralisedFuture<?> enforceAddressPunishment(Punishment punishment, SendableMessage message) {
		NetworkAddress address = ((AddressVictim) punishment.getVictim()).getAddress();
		CentralisedFuture<TargetMatcher> futureMatcher;
		AddressStrictness strictness = configs.getMainConfig().enforcement().addressStrictness();
		switch (strictness) {
		case LENIENT:
			futureMatcher = completedFuture(
					new ExactTargetMatcher(address, enforcementCallback(punishment, message)));
			break;
		case NORMAL:
			futureMatcher = matchAddressPunishmentNormal(address, punishment, message);
			break;
		case STRICT:
			futureMatcher = matchAddressPunishmentStrict(address, punishment, message);
			break;
		default:
			throw MiscUtil.unknownAddressStrictness(strictness);
		}
		return futureMatcher.thenAccept(envEnforcer::enforceMatcher);
	}

	private CentralisedFuture<TargetMatcher> matchAddressPunishmentNormal(NetworkAddress address, Punishment punishment,
			SendableMessage message) {
		InternalDatabase database = dbProvider.get();
		return database.selectAsync(() -> {
			Set<UUID> uuids = database.jdbCaesar().query(
					"SELECT `uuid` FROM `libertybans_addresses` WHERE `address` = ?")
					.params(address)
					.setResult((resultSet) -> UUIDUtil.fromByteArray(resultSet.getBytes("uuid")))
					.execute();
			return new UUIDTargetMatcher(uuids, enforcementCallback(punishment, message));
		});
	}

	private CentralisedFuture<TargetMatcher> matchAddressPunishmentStrict(NetworkAddress address, Punishment punishment,
			SendableMessage message) {
		InternalDatabase database = dbProvider.get();
		return database.selectAsync(() -> {
			Set<UUID> uuids = database.jdbCaesar().query(
					"SELECT `links`.`uuid2` FROM `libertybans_strict_links` `links` WHERE `links`.`uuid1` = ?")
					.params(address)
					.setResult((resultSet) -> UUIDUtil.fromByteArray(resultSet.getBytes("uuid")))
					.execute();
			return new UUIDTargetMatcher(uuids, enforcementCallback(punishment, message));
		});
	}
	
	private <T> CentralisedFuture<T> completedFuture(T value) {
		return futuresFactory.completedFuture(value);
	}
	
	@Override
	public CentralisedFuture<SendableMessage> executeAndCheckConnection(UUID uuid, String name, NetworkAddress address) {
		uuidManager.addCache(uuid, name);
		return selector.executeAndCheckConnection(uuid, name, address).thenCompose((ban) -> {
			if (ban == null) {
				return completedFuture(null);
			}
			return formatter.getPunishmentMessage(ban);
		});
	}
	
	@Override
	public CentralisedFuture<SendableMessage> checkChat(UUID uuid, NetworkAddress address, String command) {
		if (command != null && !blockForMuted(command)) {
			return completedFuture(null);
		}
		return muteCache.getCacheableMute(uuid, address).thenCompose((optMute) -> {
			if (optMute.isEmpty()) {
				return completedFuture(null);
			}
			return formatter.getPunishmentMessage(optMute.get());
		});
	}
	
	private boolean blockForMuted(String command) {
        String[] words = command.split(" ");
        // Handle commands with colons
        if (words[0].indexOf(':') != -1) {
            words[0] = words[0].split(":", 2)[1];
        }
        for (String muteCommand : configs.getMainConfig().enforcement().muteCommands()) {
            if (muteCommandMatches(words, muteCommand)) {
                return true;
            }
        }
        return false;
	}
	
	private static boolean muteCommandMatches(String[] commandWords, String muteCommand) {
        // Basic equality check
        if (commandWords[0].equalsIgnoreCase(muteCommand)) {
            return true;
        }
        // Advanced equality check
        // Essentially a case-insensitive "startsWith" for arrays
        if (muteCommand.indexOf(' ') != -1) {
            String[] muteCommandWords = muteCommand.split(" ");
            if (muteCommandWords.length > commandWords.length) {
                return false;
            }
            for (int n = 0; n < muteCommandWords.length; n++) {
                if (!muteCommandWords[n].equalsIgnoreCase(commandWords[n])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
	
}
