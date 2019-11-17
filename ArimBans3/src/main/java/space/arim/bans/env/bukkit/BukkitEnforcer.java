package space.arim.bans.env.bukkit;

import java.util.ArrayList;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import space.arim.bans.api.Punishment;
import space.arim.bans.api.PunishmentType;
import space.arim.bans.api.events.bukkit.PostPunishEvent;
import space.arim.bans.api.events.bukkit.PostUnpunishEvent;
import space.arim.bans.api.events.bukkit.PunishEvent;
import space.arim.bans.api.events.bukkit.UnpunishEvent;
import space.arim.bans.api.exception.MissingCenterException;
import space.arim.bans.api.exception.MissingPunishmentException;
import space.arim.bans.env.Enforcer;

public class BukkitEnforcer implements Enforcer {

	private final BukkitEnv environment;
	
	public BukkitEnforcer(final BukkitEnv environment) {
		this.environment = environment;
		refreshConfig();
	}
	
	private void missingCenter(String message) {
		environment.logger().warning("MissingCenterException! Are you restarting ArimBans?");
		(new MissingCenterException(message)).printStackTrace();
	}
	
	private void cacheFailed(String subject) {
		missingCenter(subject + "'s information was not updated");
	}
	
	private void enforceFailed(String subject, PunishmentType type) {
		missingCenter(subject + " was not checked for " + type.toString());
	}
	
	void enforceBans(AsyncPlayerPreLoginEvent evt) {
		if (environment.center() == null) {
			enforceFailed(evt.getName(), PunishmentType.BAN);
			return;
		}
		if (!evt.getLoginResult().equals(AsyncPlayerPreLoginEvent.Result.ALLOWED)) {
			return;
		} else if (environment.center().punishments().isBanned(environment.center().subjects().parseSubject(evt.getUniqueId()))) {
			try {
				evt.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, environment.center().formatter()
						.format(environment.center().punishments().getPunishment(environment.center().subjects().parseSubject(evt.getUniqueId()), PunishmentType.BAN)));
			} catch (MissingPunishmentException ex) {
				environment.center().logError(ex);
			}
		} else {
			ArrayList<String> ips = environment.center().cache().getIps(evt.getUniqueId());
			ips.add(evt.getAddress().getHostAddress());
			for (String addr : ips) {
				if (environment.center().punishments().isBanned(environment.center().subjects().parseSubject(addr))) {
					try {
						evt.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
								environment.center().formatter().format(environment.center().punishments().getPunishment(environment.center().subjects().parseSubject(addr), PunishmentType.BAN)));
					} catch (MissingPunishmentException ex) {
						environment.center().logError(ex);
					}
				}
			}
		}
	}

	void enforceMutes(AsyncPlayerChatEvent evt) {
		if (environment.center() == null) {
			enforceFailed(evt.getPlayer().getName(), PunishmentType.MUTE);
			return;
		}
		if (evt.isCancelled()) {
			return;
		} else if (environment.center().punishments().isMuted(environment.center().subjects().parseSubject(evt.getPlayer().getUniqueId()))) {
			evt.setCancelled(true);
			try {
				environment.json(evt.getPlayer(), environment.center().formatter().format(environment.center().punishments().getPunishment(environment.center().subjects().parseSubject(evt.getPlayer().getUniqueId()), PunishmentType.MUTE)));
			} catch (MissingPunishmentException ex) {
				environment.center().logError(ex);
			}
		} else {
			for (String addr : environment.center().cache().getIps(evt.getPlayer().getUniqueId())) {
				if (environment.center().punishments().isBanned(environment.center().subjects().parseSubject(addr))) {
					evt.setCancelled(true);
					try {
						environment.json(evt.getPlayer(), environment.center().formatter().format(environment.center().punishments().getPunishment(environment.center().subjects().parseSubject(addr), PunishmentType.MUTE)));
					} catch (MissingPunishmentException ex) {
						environment.center().logError(ex);
					}
				}
			}
		}
	}
	
	void updateCache(AsyncPlayerPreLoginEvent evt) {
		if (environment.center() == null) {
			 cacheFailed(evt.getName());
		}
		environment.center().cache().update(evt.getUniqueId(), evt.getName(), evt.getAddress().getHostAddress());
	}

	@Override
	public void close() throws Exception {
	}

	@Override
	public void enforce(Punishment punishment) {
		Set<? extends Player> targets = environment.applicable(punishment.subject());
		String message = environment.center().formatter().format(punishment);
		if (punishment.type().equals(PunishmentType.BAN)) {
			for (Player target : targets) {
				target.kickPlayer(message);
			}
			//environment.sendMessage(punishment.subject());
		} else if (punishment.type().equals(PunishmentType.MUTE)) {
			environment.sendMessage(punishment.subject(), message);
		} else if (punishment.type().equals(PunishmentType.WARN)) {
			environment.sendMessage(punishment.subject(), message);
		}
	}
	
	@Override
	public boolean callPunishEvent(Punishment punishment, boolean retro) {
		PunishEvent  evt = new PunishEvent(punishment, retro);
		environment.plugin().getServer().getPluginManager().callEvent(evt);
		return !evt.isCancelled();
	}
	
	@Override
	public boolean callUnpunishEvent(Punishment punishment, boolean automatic) {
		UnpunishEvent evt = new UnpunishEvent(punishment, automatic);
		environment.plugin().getServer().getPluginManager().callEvent(evt);
		// Must return true if event is automatic
		// Otherwise data gets corrupted
		return automatic || !evt.isCancelled();
	}

	@Override
	public void callPostPunishEvent(Punishment punishment, boolean retro) {
		environment.plugin().getServer().getPluginManager().callEvent(new PostPunishEvent(punishment, retro));
	}

	@Override
	public void callPostUnpunishEvent(Punishment punishment, boolean automatic) {
		environment.plugin().getServer().getPluginManager().callEvent(new PostUnpunishEvent(punishment, automatic));
	}
	
	@Override
	public void refreshConfig() {
		
	}

}