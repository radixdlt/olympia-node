package org.radix.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.radix.common.Syncronicity;
import org.radix.events.Event.EventPriority;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.events.ModuleEvent;
import org.radix.modules.events.ModuleListener;
import org.radix.modules.events.ModuleResetEvent;
import org.radix.modules.events.ModuleStartedEvent;
import org.radix.modules.events.ModuleStoppedEvent;
import org.radix.modules.exceptions.ModuleException;
import org.radix.state.State;

public class Modules implements ModuleListener
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

	static public Collection<Module> getAll()
	{
		List<Module> modules = new ArrayList<Module>();

		for (Object module : Modules.modules.values())
			if (module instanceof Module)
				modules.add((Module)module);

		return modules;
	}

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
		Events.getInstance().register(ModuleEvent.class, this);

		Runtime.getRuntime().addShutdownHook(new Thread()
		{
			@Override
			public void run()
			{
				List<Object> reverseModuleOrder = new ArrayList<Object>(Modules.modules.values());
				Collections.reverse(reverseModuleOrder);

				for (Object module : reverseModuleOrder)
				{
					if (module instanceof Module)
					{
						try
						{
							Modules.this.stop((Module)module);
						}
						catch (ModuleException mex)
						{
							log.fatal("Shutdown of service module "+module+" in shutdown hook failed", mex);
						}
					}
				}
			}
		});
	}

	public void start() throws ModuleException
	{
		// Make a copy, as modules can now add other modules, potentially resulting
		// in a ConcurrentModificationException if we iterate over modules.values
		List<Module> modules = Modules.modules.values().stream()
			.filter(o -> Module.class.isAssignableFrom(o.getClass()))
			.map(Module.class::cast)
			.collect(Collectors.toList());
		for (Module module : modules)
		{
			try
			{
				start(module);
			}
			catch (ModuleException e)
			{
				if (isThrowOnException())
					throw e;
				else
					log.error("Start of module "+module+" failed:", e);
			}
		}
	}

	public void stop() throws ModuleException
	{
		List<Object> reverseModuleOrder = new ArrayList<Object>(Modules.modules.values());
		Collections.reverse(reverseModuleOrder);

		for (Object module : reverseModuleOrder)
		{
			if (module instanceof Module)
			{
				try
				{
					stop((Module)module);
				}
				catch (ModuleException e)
				{
					if (isThrowOnException())
						throw e;
					else
						log.error("Stop of module "+module+" failed:", e);
				}
			}
		}
	}

	public void reset() throws ModuleException
	{
		List<Object> modules = new ArrayList<Object>(Modules.modules.values());
		Collections.reverse(modules);

		for (Object module : modules)
		{
			if (module instanceof Module)
			{
				try
				{
					((Module)module).stop(true);
				}
				catch (ModuleException e)
				{
					if (isThrowOnException())
						throw e;
					else
						log.error("Stop of module "+module+" for reset failed:", e);
				}
			}
		}

		Collections.reverse(modules);
		for (Object module : modules)
		{
			if (module instanceof Module)
			{
				try
				{
					((Module)module).reset();
					((Module)module).start();
				}
				catch (ModuleException e)
				{
					if (isThrowOnException())
						throw e;
					else
						log.error("Reset of module "+module+" failed:", e);
				}
			}
		}
	}

	public void start(Module module) throws ModuleException
	{
		log.info("Starting module '" + module.getName() + "' (" + module.getClass().getCanonicalName() + ")");
		module.start();
	}

	public void startIfNeeded(Class<? extends Module> cls) throws ModuleException {
		if (!Modules.isAvailable(cls)) {
			try {
				start(cls.getDeclaredConstructor().newInstance());
			} catch (IllegalArgumentException | ReflectiveOperationException | SecurityException e) {
				throw new ModuleException("Could not start module " + cls.getName(), e);
			}
		}
	}

	public void stop(Module module) throws ModuleException
	{
		if (module.getState().in(State.STARTED))
			module.stop(false);
	}

	public void restart(Module module) throws ModuleException
	{
		stop(module);
		start(module);
	}

	public void reset(Module module) throws ModuleException
	{
		stop(module);
		module.reset();
		start(module);
	}

	// MODULEEVENT LISTENER //
	@Override
	public int getPriority()
	{
		return EventPriority.HIGH.priority();
	}

	@Override
	public Syncronicity getSyncronicity()
	{
		return Syncronicity.SYNCRONOUS;
	}

	@Override
	public void process(ModuleEvent event)
	{
		if (event instanceof ModuleStartedEvent)
		{
			put(event.getModule().getClass(), event.getModule());
			log.debug("Started module "+event.getModule());
		}

		if (event instanceof ModuleResetEvent)
		{
			remove(event.getModule().getClass());
			log.debug("Reset module "+event.getModule());
		}

		if (event instanceof ModuleStoppedEvent)
		{
			remove(event.getModule().getClass());
			log.debug("Stopped module "+event.getModule());
		}
	}
}
