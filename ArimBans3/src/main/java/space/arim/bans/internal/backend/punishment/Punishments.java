package space.arim.bans.internal.backend.punishment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import space.arim.bans.ArimBans;
import space.arim.bans.api.Punishment;
import space.arim.bans.api.Subject;
import space.arim.bans.api.PunishmentType;
import space.arim.bans.api.exception.ConflictingPunishmentException;
import space.arim.bans.api.exception.MissingPunishmentException;
import space.arim.bans.internal.sql.SqlQuery;

public class Punishments implements PunishmentsMaster {
	private ArimBans center;
	
	private Set<Punishment> active = ConcurrentHashMap.newKeySet();
	private Set<Punishment> history = ConcurrentHashMap.newKeySet();

	public Punishments(ArimBans center) {
		this.center = center;
	}
	
	private Set<Punishment> punishmentTally(Subject subject, PunishmentType type) {
		Set<Punishment> applicable = active();
		for (Iterator<Punishment> it = applicable.iterator(); it.hasNext();) {
			Punishment punishment = it.next();
			if (!punishment.type().equals(type) || !punishment.subject().compare(subject)) {
				it.remove();
			}
		}
		return applicable;
	}
	
	private int punishmentCount(Subject subject, PunishmentType type) {
		int c = 0;
		Set<Punishment> active = active();
		for (Punishment punishment : active) {
			if (punishment.subject().compare(subject) && punishment.type().equals(type)) {
				
				// you have to do it
				c++;
				
			}
		}
		return c;
	}

	private void addPunishment(Punishment punishment) throws ConflictingPunishmentException {
		// Throw error if the call would produce duplicate bans/mutes
		if ((punishment.type().equals(PunishmentType.BAN) || punishment.type().equals(PunishmentType.MUTE)) && punishmentCount(punishment.subject(), punishment.type()) > 0) {
			throw new ConflictingPunishmentException(punishment.subject(), punishment.type());
		}
		// Do all SQL and Events stuff in a separate thread
		center.async().execute(() -> {
			
			// Check whether punishment is retrogade
			boolean retro = (punishment.expiration() > 0 && punishment.expiration() <= System.currentTimeMillis());
			
			// Call event before proceeding
			if (center.environment().enforcer().callPunishEvent(punishment, retro)) {
				
				// If it's retro we only need to add it to history
				// Otherwise we also need to add it to active
				SqlQuery queryHistory = new SqlQuery(SqlQuery.Query.INSERT_HISTORY.eval(center.sql().mode()), punishment.type().toString(), punishment.subject().deserialise(), punishment.operator().deserialise(), punishment.expiration(), punishment.date());
				history.add(punishment);
				if (retro) {
					center.sql().executeQuery(queryHistory);
				} else if (!retro) {
					active.add(punishment);
					center.sql().executeQuery(queryHistory, new SqlQuery(SqlQuery.Query.INSERT_ACTIVE.eval(center.sql().mode()), punishment.type().toString(), punishment.subject().deserialise(), punishment.operator().deserialise(), punishment.expiration(), punishment.date()));
				}
				
				// Call PostPunishEvent once finished
				center.environment().enforcer().callPostPunishEvent(punishment, retro);
			}
		});
	}
	
	@Override
	public void addPunishments(Punishment... punishments) throws ConflictingPunishmentException {
		if (punishments.length == 1) {
			addPunishment(punishments[0]);
			return;
		}
		// Before proceeding, determine whether adding the specified punishments
		// would produce duplicate bans or mutes
		// If it would, throw an error terminating everything
		for (Punishment punishment : punishments) {
			if ((punishment.type().equals(PunishmentType.BAN) || punishment.type().equals(PunishmentType.MUTE)) && punishmentCount(punishment.subject(), punishment.type()) > 0) {
				throw new ConflictingPunishmentException(punishment.subject(), punishment.type());
			}
		}
		// We're just doing the same things we did for #addPunishment but inside a loop instead
		center.async().execute(() -> {
			
			// A set of queries we'll execute all together for increased efficiency
			Set<SqlQuery> exec = new HashSet<SqlQuery>();
			// A map of punishments for which PunishEvents were successfully called
			// Key = the punishment, Value = whether it's retro
			// At the end we'll call PostPunishEvent for each punishment in the map
			HashMap<Punishment, Boolean> passedEvents = new HashMap<Punishment, Boolean>();
			
			for (Punishment punishment : punishments) {
				
				// Check whether punishment is retrogade
				boolean retro = (punishment.expiration() > 0 && punishment.expiration() <= System.currentTimeMillis());
				
				// Call event before proceeding
				if (center.environment().enforcer().callPunishEvent(punishment, retro)) {
					
					// If it's retro we only need to add it to history
					// Otherwise we also need to add it to active
					exec.add(new SqlQuery(SqlQuery.Query.INSERT_HISTORY.eval(center.sql().mode()), punishment.type().deserialise(), punishment.subject().deserialise(), punishment.operator().deserialise(), punishment.expiration(), punishment.date()));
					history.add(punishment);
					
					if (!retro) {
						active.add(punishment);
						exec.add(new SqlQuery(SqlQuery.Query.INSERT_ACTIVE.eval(center.sql().mode()), punishment.type().toString(), punishment.subject().deserialise(), punishment.operator().deserialise(), punishment.expiration(), punishment.date()));
					}
					
					// Add punishment to passedEvents so we can remember to call PostPunishEvents
					passedEvents.put(punishment, retro);
				}
			}
			// Execute queries
			center.sql().executeQuery((SqlQuery[]) exec.toArray());
			
			// Call PostPunishEvents once done
			passedEvents.forEach((punishment, retro) -> {
				center.environment().enforcer().callPostPunishEvent(punishment, retro);
			});
		});
	}
	
	@Override
	public Punishment getPunishment(Subject subject, PunishmentType type) throws MissingPunishmentException {
		Set<Punishment> active = active();
		for (Punishment punishment : active) {
			if (punishment.subject().compare(subject) && punishment.type().equals(type)) {
				return punishment;
			}
		}
		throw new MissingPunishmentException(subject, type);
	}
	
	@Override
	public void removePunishments(Punishment...punishments) throws MissingPunishmentException {
		for (Punishment punishment : punishments) {
			if (!active.contains(punishment)) {
				throw new MissingPunishmentException(punishment);
			}
		}
		// Do all SQL and Events stuff in a separate thread
		center.async().execute(() -> {
			
			// A set of queries we'll execute all together for increased efficiency
			Set<SqlQuery> exec = new HashSet<SqlQuery>();
			// A set of punishments for which UnpunishEvents were successfully called
			// At the end we'll call PostUnpunishEvent for each punishment in the set
			Set<Punishment> passedEvents = new HashSet<Punishment>();
			
			for (Punishment punishment : punishments) {
				
				// Call event before proceeding
				if (center.environment().enforcer().callUnpunishEvent(punishment, false)) {
					
					passedEvents.add(punishment);
					exec.add(new SqlQuery(SqlQuery.Query.DELETE_ACTIVE_FROM_DATE.eval(center.sql().mode()), punishment.date()));
				}
			}
			// Execute queries
			center.sql().executeQuery((SqlQuery[]) exec.toArray());
			
			// Remove the punishments
			synchronized (active) {
				active.removeAll(passedEvents);
			}
			
			// Call PostUnpunishEvents once done
			passedEvents.forEach((punishment) -> {
				center.environment().enforcer().callPostUnpunishEvent(punishment, false);
			});
		});
	}
	
	@Override
	public boolean isBanned(Subject subject) {
		return punishmentCount(subject, PunishmentType.BAN) > 0;
	}

	@Override
	public boolean isMuted(Subject subject) {
		return punishmentCount(subject, PunishmentType.MUTE) > 0;
	}

	@Override
	public Set<Punishment> getWarns(Subject subject) {
		return punishmentTally(subject, PunishmentType.WARN);
	}
	
	@Override
	public Set<Punishment> getKicks(Subject subject) {
		return punishmentTally(subject, PunishmentType.KICK);
	}
	
	@Override
	public void loadActive(ResultSet data) {
		try {
			while (data.next()) {
				active.add(new Punishment(PunishmentType.serialise(data.getString("type")), Subject.serialise(data.getString("subject")), Subject.serialise(data.getString("operator")), data.getString("reason"), data.getLong("expiration"), data.getLong("date")));
			}
		} catch (SQLException ex) {
			center.logError(ex);
		}
	}
	
	@Override
	public void loadHistory(ResultSet data) {
		try {
			while (data.next()) {
				history.add(new Punishment(PunishmentType.serialise(data.getString("type")), Subject.serialise(data.getString("subject")), Subject.serialise(data.getString("operator")), data.getString("reason"), data.getLong("expiration"), data.getLong("date")));
			}
		} catch (SQLException ex) {
			center.logError(ex);
		}
	}
	
	/**
	 * Returns a copy of the Set of active punishments,
	 * purging expired members.
	 * 
	 * <br><br>Changes are <b>NOT</b> backed by the set
	 * 
	 * @return Set of active punishments
	 */
	private Set<Punishment> active() {
		Set<Punishment> validated = new HashSet<Punishment>();
		Set<Punishment> invalidated = new HashSet<Punishment>();
		// We need to synchronise because iterators are not thread-safe
		synchronized (active) {
			for (Iterator<Punishment> it = active.iterator(); it.hasNext();) {
				Punishment punishment = it.next();
				if (punishment.expiration() != -1L && punishment.expiration() < System.currentTimeMillis()) {
					// call UnpunishEvent with parameter true because the removal is automatic
					if (center.environment().enforcer().callUnpunishEvent(punishment, true)) {
						invalidated.add(punishment);
						it.remove();
					} else {
						validated.add(punishment);
					}
				} else {
					validated.add(punishment); // Seems a little redundant. Isn't there something I can use to avoid writing this twice?
				}
			}
		}
		// Call PostUnpunishEvents in a separate thread
		center.async().execute(() -> {
			invalidated.forEach((punishment) -> {
				center.environment().enforcer().callPostUnpunishEvent(punishment, true);
			});
		});
		return validated;
	}
	
	public void refreshActive() {
		center.sql().executeQuery(SqlQuery.Query.REFRESH_ACTIVE.eval(center.sql().mode()));
	}
	
	@Override
	public void close() {
		active.clear();
		history.clear();
	}

	@Override
	public void refreshConfig() {
		
	}

}