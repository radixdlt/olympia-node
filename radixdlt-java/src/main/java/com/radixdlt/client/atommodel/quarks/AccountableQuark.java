package com.radixdlt.client.atommodel.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Quark;
import java.util.stream.Collectors;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *  A quark which gives it's containing particle the property that it is something to be
 *  stored in a shardable account
 */
@SerializerId2("ACCOUNTABLEQUARK")
public final class AccountableQuark extends Quark {

	/**
	 * The addresses of the accounts the containing object will be stored in
	 */
	private List<RadixAddress> addresses = null;

	private AccountableQuark() {
	}

	public AccountableQuark(RadixAddress address) {
		this(Arrays.asList(address));
	}

	public AccountableQuark(List<RadixAddress> addresses) {
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
