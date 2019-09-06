package org.radix.network;

import org.radix.modules.Modules;
import org.radix.network.discovery.Whitelist;
import org.radix.properties.RuntimeProperties;

import com.google.common.annotations.VisibleForTesting;

// FIXME: remove this and put the remaining whitelist stuff in a better spot.
public class Network {
	private static Network instance = null;

	@VisibleForTesting
	public static synchronized void reset() {
		Network.instance = null;
	}

	public static synchronized Network getInstance() {
		if (Network.instance == null) {
			Network.instance = new Network();
		}
		return Network.instance;
	}

	private final Whitelist whitelist;

	private Network() {
		this.whitelist = new Whitelist(Modules.get(RuntimeProperties.class).get("network.whitelist", ""));
	}

	public boolean isWhitelisted(String hostname) {
		return whitelist.accept(hostname);
	}
}
