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
package space.arim.libertybans.it.util;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import space.arim.libertybans.api.punish.Punishment;
import space.arim.libertybans.api.punish.PunishmentBase;

public final class TestingUtil {

	private TestingUtil() {}
	
	/**
	 * Asserts the qualities of the punishment objects are equal
	 * 
	 * @param expected expected value
	 * @param actual actualv alue
	 */
	public static void assertEqualDetails(PunishmentBase expected, PunishmentBase actual) {
		assertEquals(expected.getType(), actual.getType());
		assertEquals(expected.getVictim(), actual.getVictim());
		assertEquals(expected.getOperator(), actual.getOperator());
		assertEquals(expected.getReason(), actual.getReason());
		assertEquals(expected.getScope(), actual.getScope());
		if (expected instanceof Punishment && actual instanceof Punishment) {
			assertEqualDetailsFinish((Punishment) expected, (Punishment) actual);
		}
	}
	
	private static void assertEqualDetailsFinish(Punishment expected, Punishment actual) {
		assertEquals(expected.getID(), actual.getID());
		assertEquals(expected.getStartDate(), actual.getStartDate());
		assertEquals(expected.getEndDate(), actual.getEndDate());
	}

	/**
	 * Invokes {@code Thread.sleep} for the specified duration
	 * 
	 * @param duration the duration to sleep
	 */
	public static void sleepUnchecked(Duration duration) {
		try {
			TimeUnit.MILLISECONDS.sleep(duration.toMillis());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			fail("Failed to sleep", ex);
		}
	}

}
