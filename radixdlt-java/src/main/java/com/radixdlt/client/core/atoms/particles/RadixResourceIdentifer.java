package com.radixdlt.client.core.atoms.particles;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;

/**
 * A Radix resource identifier is a human readable index into the Ledger which points to a unique UP particle.
 *
 * TODO: Map this to a unique string e.g. address/index/:type/:name OR shardableType/:shardable/index/:type/:name
 */
@SerializerId2("RADIXRESOURCEIDENTIFIER")
public class RadixResourceIdentifer extends SerializableObject {
	// TODO: Will replace this with shardable at some point
	@JsonProperty("address")
	@DsonOutput(Output.ALL)
	private RadixAddress address;

	@JsonProperty("unique")
	@DsonOutput(Output.ALL)
	private String unique;

	@JsonProperty("type")
	@DsonOutput(Output.ALL)
	private String type;

	private RadixResourceIdentifer() {
	}

	public RadixResourceIdentifer(RadixAddress address, String type, String unique) {
		this.address = address;
		this.unique = unique;
		this.type = type;
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getType() {
		return type;
	}

	public String getUnique() {
		return unique;
	}

	@Override
	public String toString() {
		return address.toString() + "/" + type + "/" + unique;
	}
}
