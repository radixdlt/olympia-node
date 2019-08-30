package org.radix.network;

import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.network.discovery.Whitelist;
import org.radix.properties.RuntimeProperties;

public class Network extends Service {
	private static Network instance = null;

	public static synchronized Network getInstance()
	{
		if (instance == null)
			instance = new Network();

		return instance;
	}

	private final Whitelist whitelist = new Whitelist(Modules.get(RuntimeProperties.class).get("network.whitelist", ""));

    private Network()
    {
    	super();
    }

	@Override
	public void start_impl() throws ModuleException
	{
		// Empty
	}

	@Override
	public void stop_impl() throws ModuleException
	{
		// Empty
	}

	public boolean isWhitelisted(String hostname)
	{
		return whitelist.accept(hostname);
	}
}
