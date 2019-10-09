package com.radixdlt.ledger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

import java.util.Objects;

@SerializerId2("ledger.index")
// TODO Comparable impl is unfortunately required for Jackson as this is used as a map keyA
public final class LedgerIndex implements Comparable<LedgerIndex> {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	// TODO change to int (byte for compatibility with legacy AtomStore IDType)
	@JsonProperty("prefix")
	@DsonOutput(DsonOutput.Output.ALL)
	private byte prefix;

	@JsonProperty("identifier")
	@DsonOutput(DsonOutput.Output.ALL)
	private byte[] identifier;

	private LedgerIndex() {
		// For serializer
	}

	public LedgerIndex(byte prefix, byte[] identifier) {
		this.prefix = prefix;
		this.identifier = Objects.requireNonNull(identifier, "identifier is required");
	}

	public LedgerIndex(byte[] key) {
		Objects.requireNonNull(key, "key is required");
		if (key.length < 2) {
			throw new IllegalArgumentException("Key must be at least 2 bytes but was " + key.length);
		}

		this.prefix = key[0];
		this.identifier = Arrays.copyOfRange(key, 1, key.length);
	}

	public int getPrefix() {
		return this.prefix;
	}

	public byte[] getIdentifier() {
		return this.identifier;
	}

	public byte[] asKey() {
		return from(this.prefix, this.identifier);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		LedgerIndex that = (LedgerIndex) o;
		return prefix == that.prefix && java.util.Arrays.equals(identifier, that.identifier);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(prefix);
		result = 31 * result + Arrays.hashCode(identifier);
		return result;
	}

	public String toHexString() {
		return Hex.toHexString(asKey());
	}

	@Override
	public String toString() {
		return toHexString();
	}

	@JsonCreator
	public static LedgerIndex from(String hexKey) {
		Objects.requireNonNull(hexKey);
		return new LedgerIndex(Hex.decode(hexKey));
	}

	public static LedgerIndex from(byte[] key) {
		return new LedgerIndex(key);
	}

	public static byte[] from(byte prefix, byte[] identifier) {
		return Arrays.concatenate(new byte[]{prefix}, identifier);
	}

	@Override
	public int compareTo(LedgerIndex other) {
		int compare = other.prefix - this.prefix;
		if (compare != 0) {
			return compare;
		}

		int minLen = Math.min(this.identifier.length, other.identifier.length);
		for (int i = 0; i < minLen; i++) {
			compare = other.identifier[i] - this.identifier[i];
			if (compare != 0) {
				return compare;
			}
		}
		return 0;
	}

	public enum LedgerIndexType {
		UNIQUE, DUPLICATE
	}
}
