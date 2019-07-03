package org.radix.atoms.sync.messages;

import java.util.Collection;
import java.util.Set;

import org.radix.collections.WireableSet;
import com.radixdlt.common.AID;
import org.radix.discovery.DiscoveryCursor;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.sync.inventory.response")
public class AtomSyncInventoryResponseMessage extends Message
{
	@JsonProperty("inventory")
	@DsonOutput(Output.ALL)
	private WireableSet<AID> 	inventory;

	@JsonProperty("cursor")
	@DsonOutput(Output.ALL)
	private DiscoveryCursor 	cursor;

	AtomSyncInventoryResponseMessage()
	{
		// Serializer only
		this.inventory = new WireableSet<>();
	}

	public AtomSyncInventoryResponseMessage(Collection<AID> inventory, DiscoveryCursor cursor)
	{
		this.inventory = new WireableSet<>(inventory);
		this.cursor = cursor;
	}

	@Override
	public String getCommand()
	{
		return "atom.sync.inventory.response";
	}

	public Set<AID> getInventory()
	{
		return this.inventory;
	}

	public DiscoveryCursor getCursor()
	{
		return cursor;
	}
}
