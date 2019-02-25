package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Identifiable;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.crypto.ECPublicKey;

/**
 * A logical action on the ledger, composed of distinct {@link Quark} properties
 */
@SerializerId2("PARTICLE")
public abstract class Particle extends SerializableObject {
	protected Particle() {
	}

	public final Set<ECPublicKey> getKeyDestinations() {
		Set<RadixAddress> addresses = new HashSet<>();

		if (this instanceof Accountable) {
			Accountable a = (Accountable) this;
			addresses.addAll(a.getAddresses());
		}

		if (this instanceof Identifiable) {
			Identifiable i = (Identifiable) this;
			addresses.add(i.getRRI().getAddress());
		}

		return addresses.stream().map(RadixAddress::getPublicKey).collect(Collectors.toSet());
	}
}
