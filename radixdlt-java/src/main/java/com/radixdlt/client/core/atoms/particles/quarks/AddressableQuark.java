package com.radixdlt.client.core.atoms.particles.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.address.RadixAddress;
import java.util.stream.Collectors;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *  A quark that is stored in a shardable public key address.
 */
@SerializerId2("ADDRESSABLEQUARK")
public final class AddressableQuark extends Quark {
	private List<RadixAddress> addresses = null;

	private AddressableQuark() {
	}

	public AddressableQuark(RadixAddress address) {
		this(Arrays.asList(address));
	}

	public AddressableQuark(List<RadixAddress> addresses) {
		this.addresses = new ArrayList<>(addresses);
	}


	@JsonProperty("addresses")
	@DsonOutput(Output.ALL)
	private List<String> getJsonAddresses() {
		return this.addresses == null ? null : this.addresses.stream().map(RadixAddress::toString).collect(Collectors.toList());
	}

	@JsonProperty("addresses")
	private void setJsonAddresses(List<String> addresses) {
		this.addresses = addresses.stream().map(RadixAddress::fromString).collect(Collectors.toList());
	}

	public List<RadixAddress> getAddresses() {
		return this.addresses;
	}
}
