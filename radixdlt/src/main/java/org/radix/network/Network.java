package org.radix.network;

import org.radix.modules.Modules;
import org.radix.network.discovery.Whitelist;
import org.radix.properties.RuntimeProperties;

import com.google.common.annotations.VisibleForTesting;

// FIXME: remove this and put the remaining whitelist stuff in a better spot.
public class Network {
	private static class Holder {
		static Network instance = new Network();
	}

	@VisibleForTesting
	// Not thread safe.  Be careful.
	public static void reset() {
		Holder.instance = new Network();
	}

	public static Network getInstance() {
		return Holder.instance;
	}

	private final Whitelist whitelist;

	private Network() {
		this.whitelist = new Whitelist(Modules.get(RuntimeProperties.class).get("network.whitelist", ""));
	}

	public boolean isWhitelisted(String hostname) {
		return whitelist.accept(hostname);
	}
}
