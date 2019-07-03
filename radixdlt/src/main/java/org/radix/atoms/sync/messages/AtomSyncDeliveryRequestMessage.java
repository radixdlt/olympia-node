package org.radix.atoms.sync.messages;

import java.util.Collection;

import org.radix.collections.WireableSet;
import com.radixdlt.common.AID;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.sync.delivery.request")
public class AtomSyncDeliveryRequestMessage extends Message
{
	@JsonProperty("inventory")
	@DsonOutput(Output.ALL)
	private WireableSet<AID> 	inventory;

	AtomSyncDeliveryRequestMessage()
	{
		// Serializer only
		this.inventory = new WireableSet<>();
	}

	public AtomSyncDeliveryRequestMessage(Collection<AID> inventory)
	{
		super();

		this.inventory = new WireableSet<>(inventory);
	}

	@Override
	public String getCommand()
	{
		return "atom.sync.delivery.request";
	}

	public Collection<AID> getInventory()
	{
		return this.inventory;
	}
}
