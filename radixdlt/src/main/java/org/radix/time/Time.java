package org.radix.time;

import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;

public final class Time {
	public static final int MAXIMUM_DRIFT = 30;

	private static NtpService ntpServiceInstance;

	public static void start(RuntimeProperties properties) {
		if (properties.get("ntp", false)) {
			ntpServiceInstance = new NtpService(properties.get("ntp.pool"));
		}
	}

	public static long currentTimestamp() {
		return ntpServiceInstance != null ? ntpServiceInstance.getUTCTimeMS() : System.currentTimeMillis();
	}
}
