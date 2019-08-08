package com.radixdlt.ledger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import org.bouncycastle.util.Arrays;

import java.util.Objects;

@SerializerId2("ledger.index")
// TODO change to interface
public final class LedgerIndex {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	// TODO change to int (byte for compatibility with legacy AtomStore IDType)
	@JsonProperty("prefix")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private byte prefix;

	@JsonProperty("identifier")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private byte[] identifier;

	public static byte[] from(byte prefix, byte[] identifier) {
		return Arrays.concatenate(new byte[]{prefix}, identifier);
	}

	public LedgerIndex(byte prefix, byte[] identifier) {
		this.prefix = prefix;
		this.identifier = Objects.requireNonNull(identifier, "identifier is required");
	}

	public LedgerIndex(byte[] key) {
		Objects.requireNonNull(key, "key is required");

		this.prefix = key[0];
		this.identifier = Arrays.copyOfRange(key, 1, key.length);
	}

	public int getPrefix() {
		return this.prefix;
	}

	public byte[] getIdentifier() {
		return this.identifier;
	}

	public byte[] getKey() {
		return from(this.prefix, this.identifier);
	}
}
