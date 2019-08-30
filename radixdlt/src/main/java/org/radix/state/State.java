package org.radix.state;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.radix.modules.Modules;
import org.radix.time.NtpService;

public class State
{
	public static class StateDefinition
	{
		private final String 	name;
		private final boolean 	reentrant;
		private final Set<StateDefinition> allowed = new HashSet<>();

		// TODO StateDefinition constructors are private for now to prevent custom 3rd party states
		private StateDefinition(String name)
		{
			this(name, false);
		}

		private StateDefinition(String name, boolean reentrant)
		{
			if (name == null || name.length() < 3)
				throw new IllegalArgumentException("Argument 'name' is null or too short");

			this.name = name;
			this.allowed.add(State.ANY);
			this.reentrant = reentrant;
		}

		// TODO StateDefinition constructors are private for now to prevent custom 3rd party states
		private StateDefinition(String name, StateDefinition ... allowed)
		{
			this(name, false, allowed);
		}

		private StateDefinition(String name, boolean reentrant, StateDefinition ... allowed)
		{
			if (name == null || name.length() < 3)
				throw new IllegalArgumentException("Argument 'name' is null or too short");

			if (allowed == null || allowed.length == 0)
				throw new IllegalArgumentException("Argument 'allowed' is null or empty");

			this.name = name;

			for (StateDefinition definition : allowed)
			{
				if (definition == null)
					throw new IllegalStateException("Allowed state definitions can not contain null");
				this.allowed.add(definition);
			}

			this.reentrant = reentrant;
		}

		public String getName()
		{
			return this.name;
		}

		public void checkAllowed(StateDefinition definition)
		{
			if (this.allowed.contains(State.ANY) && this.allowed.size() == 1)
				return;

			if (this.equals(definition) && this.reentrant)
				return;

			if (!this.allowed.contains(definition))
				throw new IllegalStateException("Transition of state "+this.getName()+" -> "+definition.getName()+" is not allowed");
		}

		@Override
		public String toString()
		{
			return this.name;
		}
	}

	// Standard state types //
	public static final StateDefinition ANY = new StateDefinition("any");
	public static final StateDefinition NONE = new StateDefinition("none");
	public static final StateDefinition COMPLETE =  new StateDefinition("complete");
	public static final StateDefinition DELETED = new StateDefinition("deleted");
	public static final StateDefinition DISCOVERED = new StateDefinition("discovered");
	public static final StateDefinition EXECUTE = new StateDefinition("execute");
	public static final StateDefinition EXECUTED = new StateDefinition("executed");
	public static final StateDefinition FAILED = new StateDefinition("failed");
	public static final StateDefinition FETCHED = new StateDefinition("fetched");
	public static final StateDefinition OBJECT = new StateDefinition("object");
	public static final StateDefinition PENDING = new StateDefinition("pending");
	public static final StateDefinition POSTPONED = new StateDefinition("postponed");
	public static final StateDefinition PROCESSED = new StateDefinition("processed");
	public static final StateDefinition REGISTERED = new StateDefinition("registered");
	public static final StateDefinition RESOLVED = new StateDefinition("resolved");
	public static final StateDefinition SHUTTINGDOWN = new StateDefinition("shuttingdown");
	public static final StateDefinition SIGNATURE = new StateDefinition("signature");
	public static final StateDefinition SHUTDOWN = new StateDefinition("shutdown");
	public static final StateDefinition STARTED = new StateDefinition("started");
	public static final StateDefinition STOPPED = new StateDefinition("stopped");
	public static final StateDefinition STORED = new StateDefinition("stored");
	public static final StateDefinition TIMEDOUT = new StateDefinition("timedout");
	public static final StateDefinition UNKNOWN = new StateDefinition("unknown");
	public static final StateDefinition UPDATED = new StateDefinition("updated");

	public static final StateDefinition DELETING = new StateDefinition("deleting", State.DELETED, State.FAILED);
	public static final StateDefinition DISCOVERING = new StateDefinition("discovering", State.DISCOVERED, State.FAILED, State.TIMEDOUT);
	public static final StateDefinition EXECUTING = new StateDefinition("executing", State.EXECUTED, State.FAILED, State.TIMEDOUT);
	public static final StateDefinition FETCHING = new StateDefinition("fetching", State.FETCHED, State.FAILED, State.TIMEDOUT);
	public static final StateDefinition PROCESSING = new StateDefinition("processing", State.PROCESSED, State.FAILED);
	public static final StateDefinition REGISTERING = new StateDefinition("registering", State.REGISTERED, State.FAILED);
	public static final StateDefinition RESOLVING = new StateDefinition("resolving", true, State.PENDING, State.RESOLVED, State.FAILED, State.TIMEDOUT);
	public static final StateDefinition STARTING = new StateDefinition("starting", State.STARTED, State.FAILED);
	public static final StateDefinition STOPPING = new StateDefinition("stopping", State.STOPPED, State.FAILED);
	public static final StateDefinition STORING = new StateDefinition("storing", State.STORED, State.FAILED);
	public static final StateDefinition UPDATING = new StateDefinition("updating", State.UPDATED, State.FAILED);

	public static State getByName(String name) throws IllegalArgumentException
	{
		for (Field field : State.class.getDeclaredFields())
		{
			try {
				if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
						field.getName().compareToIgnoreCase(name) == 0)
					return new State((StateDefinition) field.get(null));
			} catch (IllegalAccessException e) {
				// Ignore exception, try others
			}
		}

		return null;
	}

	private final StateDefinition definition;
	private final long	 	timestamp;

	public State(StateDefinition definition)
	{
		if (definition == null)
			throw new IllegalArgumentException("Argument 'definition' is null");

		this.definition = definition;

		if (Modules.isAvailable(NtpService.class) && !definition.equals(State.NONE))
			this.timestamp = Modules.get(NtpService.class).getUTCTimeMS();
		else
			this.timestamp = 0l;
	}

	@Override
	public int hashCode() {
		return this.definition.hashCode();
	}

	@Override
	public boolean equals(Object object) {
		if (object == this) {
			return true;
		}
		if (object instanceof State) {
			return (((State)object).definition.equals(this.definition));
		}
		return false;
	}

	public boolean in(StateDefinition definition) {
		return Objects.equals(this.definition, definition);
	}

	@Override
	public String toString()
	{
		return this.definition.name.toString()+":"+this.timestamp;
	}

	public StateDefinition getDefinition()
	{
		return this.definition;
	}

	public String getName()
	{
		return this.definition.getName();
	}

	public long getTimestamp()
	{
		return this.timestamp;
	}

	public void checkAllowed(State state)
	{
		this.definition.checkAllowed(state.getDefinition());
	}
}
