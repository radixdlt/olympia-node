package org.radix.modules;

import java.awt.Image;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.radixdlt.common.EUID;
import org.radix.common.ID.ID;
import org.radix.common.executors.Executable;
import org.radix.common.executors.Executor;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.events.ModuleResetEvent;
import org.radix.modules.events.ModuleStartedEvent;
import org.radix.modules.events.ModuleStoppedEvent;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleResetException;
import org.radix.modules.exceptions.ModuleRestartException;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.network.messaging.Message;
import org.radix.network2.messaging.MessageCentral;
import org.radix.network2.messaging.MessageListener;
import org.radix.state.SingletonState;
import org.radix.state.State;

import com.google.common.collect.ImmutableMap;

public abstract class Module implements SingletonState
{
	private static final Logger log = Logging.getLogger();

	public enum ModuleDomain 	{ PRIVATE, PROTECTED, PUBLIC }

	private EUID		uid;
	private State		state = new State(State.NONE);

	private final Semaphore dependents = new Semaphore(0);
	private final ReentrantReadWriteLock	lock = new ReentrantReadWriteLock(true);
	private final Map<Long, Executable>		executables = new WeakHashMap<Long, Executable>();
	private final Map<Class<? extends Message>, MessageListener<? extends Message>>	listeners = new HashMap<>();

	public EUID getUID()
	{
		if (uid == null || uid == EUID.ZERO)
			uid = new EUID(Modules.get(SecureRandom.class).nextLong());

		return uid;
	}

	/**
	 * Returns the current state of this module
	 *
	 * @return
	 */
	@Override
	public State getState()
	{
		return state;
	}

	/**
	 * Sets the current state of this module.
	 *
	 * @param state
	 */
	@Override
	public void setState(State state)
	{
		this.state.checkAllowed(state);
		this.state = state;
	}

	public Future<?> schedule(final ScheduledExecutable executable)
	{
		synchronized(this.executables)
		{
			Executor.getInstance().schedule(executable);
			this.executables.put(executable.getID(), executable);
			return executable.getFuture();
		}
	}

	public Future<?> scheduleWithFixedDelay(final ScheduledExecutable executable)
	{
		synchronized(this.executables)
		{
			Executor.getInstance().scheduleWithFixedDelay(executable);
			this.executables.put(executable.getID(), executable);
			return executable.getFuture();
		}
	}

	public Future<?> scheduleAtFixedRate(final ScheduledExecutable executable)
	{
		synchronized(this.executables)
		{
			Executor.getInstance().scheduleAtFixedRate(executable);
			this.executables.put(executable.getID(), executable);
			return executable.getFuture();
		}
	}

	public Future<?> submit(final Executable executable)
	{
		synchronized(this.executables)
		{
			Executor.getInstance().submit(executable);
			this.executables.put(executable.getID(), executable);
			return executable.getFuture();
		}
	}

	public Future<?> schedule(final Executable executable, int initialDelay, TimeUnit unit)
	{
		synchronized(this.executables)
		{
			Executor.getInstance().schedule(executable, initialDelay, unit);
			this.executables.put(executable.getID(), executable);
			return executable.getFuture();
		}
	}

	public Class<?> declaredClass() {
		return this.getClass();
	}

	/**
	 * Specifies if this service is a Singleton, defaults to true.
	 *
	 * If true, then only one instance of the module will be allowed by the module manager.
	 * If false, multiple instances of the module will be allowed and should be referenced by the generated module ID.
	 *
	 * NOTE:  Module IDs are generated at runtime and are NOT persisted upon a shutdown.
	 *
	 * @return
	 */
	public boolean isSingleton()
	{
		return true;
	}

	/**
	 * Specifies that this modules dependencies are to be checked on each state change.
	 *
	 * Dependencies verified as present, and that they are in an equal, or advanced state from this module.
	 *
	 * @return
	 */
	public boolean isCheckDependencies() { return false; }

	/**
	 * Specifies that this modules dependencies are to be invoked in the
	 * event of them being missing, or being at a retarded state to this module.
	 *
	 * @return
	 */
	public boolean isInvokeDependencies() { return false; }

	/**
	 * Returns a list of dependency modules required to ensure correct operation
	 *
	 * @return
	 */
	public List<Class<? extends Module>> getDependsOn()
	{
		return Collections.emptyList();
	}

	/**
	 * Returns a list of invoked modules required to ensure correct operation
	 */
	public List<Class<? extends Module>> getComponents()
	{
		return Collections.emptyList();
	}

	/**
	 * Registers a message listener to this module.
	 */
	public <T extends Message> void register(Class<T> messageClass, MessageListener<T> listener) {
		listeners.put(messageClass, listener);
		Modules.get(MessageCentral.class).addListener(messageClass, listener);
	}

	/**
	 * Unregisters a message listener registered to this module.
	 */
	public <T extends Message> void unregister(Class<T> messageClass) {
		Modules.get(MessageCentral.class).removeListener(listeners.get(messageClass));
		listeners.remove(messageClass);
	}

	protected final void start() throws ModuleException
	{
		try
		{
			if (state.in(State.STOPPED) || state.in(State.NONE))
			{
				for (Class<? extends Module> clazz : getDependsOn())
				{
					final Module module = Modules.isAvailable(clazz) ? Modules.get(clazz) : invokeModule(clazz);
					module.dependents.release();
				}

				for (Class<? extends Module> clazz : getComponents())
				{
					if (Modules.isAvailable(clazz) == false)
						invokeModule(clazz);
				}

				setState(new State(State.STARTING));

				start_impl();

				setState(new State(State.STARTED));

				Events.getInstance().broadcast(new ModuleStartedEvent(Module.this));
			}
		}
		catch (ModuleException mex)
		{
			stop(true);

			throw mex;
		}
		catch (Throwable t)
		{
			stop(true);

			throw new ModuleStartException(t, this);
		}
	}

	private Module invokeModule(Class<? extends Module> cls) throws ReflectiveOperationException, ModuleException
	{
		Module module = cls.getDeclaredConstructor().newInstance();
		log.info("Starting dependency '" + module.getName() + "' (" + cls.getCanonicalName() + ")");
		module.start();
		return module;
	}

	protected abstract void start_impl() throws ModuleException;

	protected final void reset() throws ModuleException
	{
		try
		{
			if (state.in(State.STOPPED) == false)
				throw new ModuleResetException("Module "+this.getName()+" must be STOPPED to be reset", this);

			reset_impl();

			Events.getInstance().broadcast(new ModuleResetEvent(Module.this));
		}
		catch (ModuleException mex)
		{
			throw mex;
		}
		catch (Throwable t)
		{
			throw new ModuleResetException(t, this);
		}
	}

	// FIXME MPS: Made this public for RadixTestWithStores until reset() can be fixed.
	public void reset_impl() throws ModuleException
	{ }

	protected final void restart(Runnable function) throws ModuleException
	{
		if (state.in(State.STARTED) == false)
			throw new ModuleRestartException("Module "+getName()+" is not started", this);

		stop(true);
		if (function != null)
			function.run();
		start();
	}

	protected final void stop(boolean force) throws ModuleException
	{
		try
		{
			if (state.in(State.STARTED))
			{
				if (force == false && this.dependents.tryAcquire() == true)
					return;

				synchronized(this.executables)
				{
					for (Executable executable : this.executables.values())
						executable.terminate(true);
				}

				for (Class<? extends Message> messageClass : listeners.keySet()) {
					Modules.get(MessageCentral.class).removeListener(listeners.get(messageClass));
				}

				listeners.clear();

				for (Class<? extends Module> invokedClazz : getComponents())
				{
					Module invokedModule = Modules.isAvailable(invokedClazz) ? Modules.get(invokedClazz) : null;

					if (invokedModule == null)
						log.warn("Invoked module "+invokedClazz+" of "+this.getClass()+" not found");
					else
						invokedModule.stop(force);
				}

				for (Class<? extends Module> dependencyClazz : getDependsOn())
				{
					Module dependencyModule = Modules.get(dependencyClazz);
					if (!dependencyModule.dependents.tryAcquire()) {
						log.warn("Dependent module usage underflowed: " + dependencyModule.getClass().getName());
					}
				}

				setState(new State(State.STOPPING));
				stop_impl();
				setState(new State(State.STOPPED));

				Events.getInstance().broadcast(new ModuleStoppedEvent(Module.this));
			}
		}
		catch (ModuleException mex)
		{
			setState(new State(State.STARTED));

			throw mex;
		}
	}

	protected abstract void stop_impl() throws ModuleException;

	/**
	 * Returns an icon to be used for this module where appropriate (for external services).  May return null
	 *
	 * @return
	 */
	public Image getIcon() { return null; }

	/**
	 * Returns the friendly name of this service.  May return null;
	 *
	 * @return
	 */
	public String getName() { return getClass().getName(); }

	/**
	 * Returns the friendly description of this module.  May return null;
	 *
	 * @return
	 */
	public String getDescription() { return null; }

	/**
	 * Returns any meta data relating to this module.
	 * Note that the returned map is not mutable.
	 *
	 * @return
	 */
	public Map<String, Object> getMetaData() {
		return ImmutableMap.of();
	}

	/** Provides a current status overview of this module
	 *
	 * @return
	 */
	public abstract ModuleStatus getStatus();

	@Override
	public String toString()
	{
		return getUID()+": "+getClass().getName()+" '"+getName()+"'";
	}
}
