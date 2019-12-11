package org.radix.modules;

import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

	static public <T> T get(Class<T> clazz)
	{
		T value = (T) Modules.modules.get(clazz);
		if (value == null) {
			throw new IllegalStateException("Requested module " + clazz.getName() + " is not available (" + Thread.currentThread().getName() + "): " + Modules.modules.keySet());
		}
		return value;
	}

	public static <T> T getOrElse(Class<? extends T> cls, Supplier<T> elseClause) {
		Object o = Modules.modules.get(cls);
		if (o != null) {
			return (T) o;
		}
		return elseClause.get();
	}

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

	private	boolean	throwOnException = false;

	public boolean isThrowOnException() { return throwOnException; }
	public void setThrowOnException(boolean throwOnException) { this.throwOnException = throwOnException; }

	private Modules()
	{
	}
}
