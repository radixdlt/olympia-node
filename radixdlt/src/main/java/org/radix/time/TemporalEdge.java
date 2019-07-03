package org.radix.time;

import com.radixdlt.common.EUID;
import org.radix.containers.BasicContainer;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("tempo.temporal_edge")
public class TemporalEdge extends BasicContainer
{
	@Override
	public short VERSION()
	{
		return 100;
	}

	@JsonProperty("receiving_nid")
	@DsonOutput(Output.ALL)
	private EUID	receivingNID;

	@JsonProperty("transmitting_nid")
	@DsonOutput(Output.ALL)
	private EUID	transmittingNID;

	@JsonProperty("receiving_clock")
	@DsonOutput(Output.ALL)
	private long receivingClock;

	@JsonProperty("transmitting_clock")
	@DsonOutput(Output.ALL)
	private long transmittingClock;

	public TemporalEdge()
	{
		super();
	}

	public TemporalEdge(EUID receivingNID, long receivingClock, EUID transmittingNID, long transmittingClock)
	{
		this();

		this.receivingNID = receivingNID;
		this.transmittingNID = transmittingNID;
		this.receivingClock = receivingClock;
		this.transmittingClock = transmittingClock;
	}

	public EUID getReceivingNID()
	{
		return receivingNID;
	}

	public EUID getTransmittingNID()
	{
		return transmittingNID;
	}

	public long getReceivingClock()
	{
		return receivingClock;
	}

	public long getTransmittingClock()
	{
		return transmittingClock;
	}

	@Override
	public String toString()
	{
		return receivingNID+":"+receivingClock+" "+transmittingNID+":"+transmittingClock;
	}
}
