package org.radix.discovery.messages;

import org.radix.discovery.DiscoveryRequest;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class DiscoveryMessage<T extends DiscoveryRequest> extends Message
{
	@JsonProperty("request")
	@DsonOutput(Output.ALL)
	private T request;

	protected DiscoveryMessage()
	{
		super();
	}

	protected DiscoveryMessage(T request)
	{
		this();

		this.request = request;
	}

	public T getRequest() { return request; }

	@Override
	public String toString()
	{
		return super.toString()+" "+this.request;
	}
}
