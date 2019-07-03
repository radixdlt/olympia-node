package org.radix.atoms.sync;

import java.util.concurrent.atomic.AtomicLong;

import org.radix.discovery.DiscoveryCursor;
import org.radix.network.peers.Peer;
import org.radix.state.SingletonState;
import org.radix.state.State;

public final class InventorySyncState implements SingletonState
{
	private static final AtomicLong incrementer = new AtomicLong(0);

	private long 			session = InventorySyncState.incrementer.getAndIncrement();
	private final Peer  	peer;
	private DiscoveryCursor cursor;
	private long 			delay;
	private State 			state;

	InventorySyncState(Peer peer, DiscoveryCursor cursor)
	{
		super();

		this.peer = peer;
		this.cursor = cursor;
		this.state = new State(State.NONE);
	}

	public Peer getPeer()
	{
		return this.peer;
	}

	public long getSession()
	{
		return session;
	}

	@Override
	public State getState()
	{
		return this.state;
	}

	@Override
	public void setState(State state)
	{
		this.state.checkAllowed(state);
		this.state = state;
		this.session = InventorySyncState.incrementer.getAndIncrement();
	}

	public DiscoveryCursor getCursor()
	{
		return this.cursor;
	}

	void setCursor(DiscoveryCursor cursor)
	{
		this.cursor = cursor;
	}

	public long getDelay()
	{
		return this.delay;
	}

	long incrementDelay(long increment, int maximum)
	{
		this.delay += increment;
		if (delay > maximum)
			this.delay = maximum;

		return this.delay;
	}

	public void setDelay(long delay)
	{
		this.delay = delay;
	}

	@Override
	public String toString()
	{
		return "Session: "+this.session+" State: "+this.state.getName()+" Cursor: "+this.cursor;
	}
}
