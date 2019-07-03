package org.radix.discovery;

import org.radix.containers.BasicContainer;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("discovery.cursor")
public class DiscoveryCursor extends BasicContainer
{
	@Override
	public short VERSION()
	{
		return 100;
	}

	@JsonProperty("next")
	@DsonOutput(Output.ALL)
	private DiscoveryCursor next = null;

	@JsonProperty("position")
	@DsonOutput(Output.ALL)
	private long			position = 0;

	public DiscoveryCursor()
	{
		super();
	}

	public DiscoveryCursor(long position)
	{
		this();

		if (position < 0)
			throw new IllegalArgumentException("Cursor position "+position+" is invalid");

		this.position = position;
	}

	public final boolean hasNext()
	{
		return this.next != null;
	}

	public final DiscoveryCursor getNext()
	{
		return this.next;
	}

	public final DiscoveryCursor setNext(DiscoveryCursor next)
	{
		this.next = next;

		return this;
	}

	public final long getPosition()
	{
		return this.position;
	}

	public final DiscoveryCursor setPosition(long position)
	{
		this.position = position;

		return this;
	}

	@Override
	public String toString()
	{
		return "Position: "+position;
	}
}
