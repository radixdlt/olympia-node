package org.radix.atoms.sync;

import java.util.concurrent.atomic.AtomicLong;

import org.radix.network2.addressbook.Peer;
import org.radix.shards.ShardSpace;
import org.radix.state.SingletonState;
import org.radix.state.State;
import org.radix.utils.BArray;

public final class ChecksumSyncState implements SingletonState
{
	private static final AtomicLong incrementer = new AtomicLong(0);

	private final Peer  peer;
	private long session = ChecksumSyncState.incrementer.getAndIncrement();
	private ShardSpace shards;
	private State state;

	private int 		chunk;
	private byte		chunkMask;
	private BArray		chunkBits;

	ChecksumSyncState(Peer peer, ShardSpace shards)
	{
		this(peer, shards, 0);
	}

	ChecksumSyncState(Peer peer, ShardSpace shards, int chunk)
	{
		super();

		this.chunk = chunk;
		this.shards = new ShardSpace(shards.getAnchor(), shards.getRange());
		this.state = new State(State.NONE);
		this.peer = peer;
		this.chunkBits = new BArray(ShardSpace.SHARD_CHUNKS);
		this.chunkMask = 0;
	}

	public Peer getPeer()
	{
		return this.peer;
	}

	public int getChunk()
	{
		return this.chunk;
	}

	public ShardSpace getShards()
	{
		return this.shards;
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
		this.session = ChecksumSyncState.incrementer.getAndIncrement();

		if (this.state.in(State.NONE))
		{
			this.chunk = 0;
		}
	}

	BArray getChunkBits()
	{
		return this.chunkBits;
	}

	void setChunkBits(BArray chunkBits, int from, int to)
	{
		this.chunkBits.copyBits(chunkBits, from, to - from);
	}

	byte getChunkMask()
	{
		return this.chunkMask;
	}

	void setChunkMask(byte chunkMask)
	{
		this.chunkMask = chunkMask;
		this.chunkBits.clear();
	}

	public boolean next()
	{
		return next(1);
	}

	public boolean next(int increment)
	{
		this.chunk += increment;

		if (this.chunk < ShardSpace.SHARD_CHUNKS)
			return true;

		return false;
	}

	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append("Session: "+this.session+" State: "+this.state.getName());
		builder.append(" Chunk: "+this.getChunk()+" Shards: "+this.shards);
		return builder.toString();
	}
}
