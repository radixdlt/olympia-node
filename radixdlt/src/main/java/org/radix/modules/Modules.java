package org.radix.modules;

import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Modules
{
	private static final Logger log = Logging.getLogger();

	private static final Modules instance;

	static
	{
		instance = new Modules();
	}

	public final static Modules getInstance()
	{
		return instance;
	}

	// TODO change to Map<Class<? extends Module>, Module> and refactor non-module classes that use this as a singleton store.
	private static Map<Class<?>, Object> modules = new ConcurrentHashMap<Class<?>, Object>();

	private Modules()
	{
	}
}
