package com.radixdlt.client.core.atoms;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerDummy;
import org.radix.serialization2.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;

@SerializerId2("ACCOUNTREFERENCE")
public class AccountReference {
	@JsonProperty("version")
	@DsonOutput(Output.ALL)
	private short version = 100;

	// Placeholder for the serializer ID
	@JsonProperty("serializer")
	@DsonOutput({Output.API, Output.WIRE, Output.PERSIST})
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("key")
	@DsonOutput(Output.ALL)
	private ECKeyPair key;

	AccountReference() {
		// No-arg constructor for serializer
	}

	public AccountReference(ECPublicKey key) {
		this.key = key.toECKeyPair();
	}

	public ECPublicKey getKey() {
		return key.getPublicKey();
	}

	@Override
	public String toString() {
		return key.getPublicKey().toString();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AccountReference)) {
			return false;
		}

		return ((AccountReference) o).key.equals(this.key);
	}

	@Override
	public int hashCode() {
		return key.hashCode();
	}
}
