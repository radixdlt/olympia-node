package org.radix.time;

import org.radix.modules.Modules;

public final class Time {
	public static final int MAXIMUM_DRIFT = 30;

	public static long currentTimestamp() {
		return Modules.isAvailable(NtpService.class) ? Modules.get(NtpService.class).getUTCTimeMS() : System.currentTimeMillis();
	}
}
