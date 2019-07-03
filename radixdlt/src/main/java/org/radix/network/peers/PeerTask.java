package org.radix.network.peers;

import java.util.concurrent.TimeUnit;

import org.radix.common.executors.ScheduledExecutable;

public abstract class PeerTask extends ScheduledExecutable
{
	private final Peer peer;

	public PeerTask(Peer peer, long delay, TimeUnit unit)
	{
		super(delay, 0, unit);

		this.peer = peer;
	}

	public Peer getPeer()
	{
		return this.peer;
	}
}
