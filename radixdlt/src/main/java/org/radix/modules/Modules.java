package org.radix.modules;

import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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

	public static <T> void ifAvailable(Class<T> cls, Consumer<T> action) {
		@SuppressWarnings("unchecked")
		T value = (T) Modules.modules.get(cls);
		if (value != null) {
			action.accept(value);
		}
	}

	static public boolean isAvailable(Class<?> cls) {
		return Modules.modules.containsKey(cls);
	}

	static public void put(Class<?> clazz, Object module)
	{
		if (Modules.modules.containsKey(clazz))
		{
			log.error("Already have an instance of module "+module, new Throwable());
			return;
		}

		Modules.modules.put(clazz, module);
	}

	public static void replace(Class<?> clazz, Object module)
	{
		Modules.modules.put(clazz, module);
	}

	public static <T> T remove(Class<?> clazz)
	{
		return (T) Modules.modules.remove(clazz);
	}

	private Modules()
	{
	}
}
