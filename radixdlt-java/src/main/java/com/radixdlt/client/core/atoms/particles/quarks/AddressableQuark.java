package com.radixdlt.client.core.atoms.particles.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.AccountReference;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *  A quark that is stored in a shardable public key address.
 */
@SerializerId2("ADDRESSABLEQUARK")
public final class AddressableQuark extends Quark {
	@JsonProperty("addresses")
	@DsonOutput(DsonOutput.Output.ALL)
	private List<AccountReference> addresses = null;

	private AddressableQuark() {
	}

	public AddressableQuark(AccountReference accountReference) {
		this(Arrays.asList(accountReference));
	}

	public AddressableQuark(List<AccountReference> addresses) {
		this.addresses = new ArrayList<>(addresses);
	}

	public List<AccountReference> getAddresses() {
		return this.addresses;
	}
}
