package com.radixdlt.client.atommodel.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Quark;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 *  A quark which gives it's containing particle the property that it is something to be
 *  stored in a shardable account
 */
@SerializerId2("ACCOUNTABLEQUARK")
public final class AccountableQuark extends Quark {

	/**
	 * The addresses of the accounts the containing object will be stored in
	 */
	@JsonProperty("addresses")
	@DsonOutput(Output.ALL)
	private List<RadixAddress> addresses = null;

	private AccountableQuark() {
	}

	public AccountableQuark(RadixAddress address) {
		this(Arrays.asList(Objects.requireNonNull(address, "address is required")));
	}

	public AccountableQuark(List<RadixAddress> addresses) {
		this.addresses = new ArrayList<>(Objects.requireNonNull(addresses, "addresses is required"));
	}

	public List<RadixAddress> getAddresses() {
		return this.addresses;
	}
}
