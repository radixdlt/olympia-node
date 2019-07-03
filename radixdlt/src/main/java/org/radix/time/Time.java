package org.radix.time;

import org.apache.commons.cli.CommandLine;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;
import org.radix.properties.RuntimeProperties;
import org.radix.time.RTP.RTPService;

public class Time extends Plugin
{
	public static final int MAXIMUM_DRIFT = 30;

	public static long currentTimestamp() {
		return Modules.isAvailable(RTPService.class) ? Modules.get(RTPService.class).getUTCTimeMS() :
				Modules.isAvailable(NtpService.class) ? Modules.get(NtpService.class).getUTCTimeMS() : System.currentTimeMillis();

	}

	@Override
	public void start_impl() throws ModuleException {
		if (Modules.get(RuntimeProperties.class).get("ntp", false) == true && !Modules.get(CommandLine.class).hasOption("genesis"))
			Modules.put(NtpService.class, new NtpService(Modules.get(RuntimeProperties.class).get("ntp.pool")));
		else
			Modules.put(NtpService.class, new NtpService(null));
	}

	@Override
	public void stop_impl() throws ModuleException {
		Modules.remove(NtpService.class);
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}
}
