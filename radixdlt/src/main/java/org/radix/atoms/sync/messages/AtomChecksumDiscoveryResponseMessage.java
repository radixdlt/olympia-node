package org.radix.atoms.sync.messages;

import java.util.Collection;
import java.util.Set;

import org.radix.collections.WireableSet;
import com.radixdlt.common.AID;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.sync.checksum.discovery.response")
public class AtomChecksumDiscoveryResponseMessage extends Message
{
	@JsonProperty("objects")
	@DsonOutput(Output.ALL)
	private WireableSet<AID> objects;

	AtomChecksumDiscoveryResponseMessage()
	{
		super();
		this.objects = new WireableSet<>();
	}

	public AtomChecksumDiscoveryResponseMessage(Collection<AID> objects)
	{
		super();

		if (objects == null)
			throw new IllegalArgumentException("Argument 'objects' is null or empty");

		this.objects = new WireableSet<>(objects);
	}

	@Override
	public String getCommand()
	{
		return "atom.sync.checksum.discovery.response";
	}

	public Set<AID> getObjects()
	{
		return this.objects;
	}
}
