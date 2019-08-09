package com.radixdlt.mock;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Longs;
import org.bouncycastle.util.Arrays;
import org.radix.time.Time;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

@SerializerId2("mock.atom")
final class MockAtom implements Atom {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("content")
	@DsonOutput(DsonOutput.Output.ALL)
	private MockAtomContent content;

	@JsonProperty("aid")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private AID aid;

	@JsonProperty("timestamp")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private long timestamp;

	@JsonProperty("shards")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private ImmutableSet<Long> shards;

	private MockAtom() {
		// For serializer
	}

	MockAtom(MockAtomContent content) {
		this.content = Objects.requireNonNull(content);
		this.shards = Stream.concat(Stream.of(content.getKey()), content.getApplicationIndices().stream())
			.map(LedgerIndex::asKey)
			.map(Longs::fromByteArray)
			.collect(ImmutableSet.toImmutableSet());
		this.timestamp = Time.currentTimestamp();
		this.aid = doGetAID();
	}

	@Override
	public Object getContent() {
		return this.content;
	}

	@Override
	public ImmutableSet<Long> getShards() {
		return this.shards;
	}

	@Override
	public long getTimestamp() {
		return this.timestamp;
	}

	private AID doGetAID() {
		return AID.from(
			new Hash(Hash.hash256(Arrays.concatenate(content.getKey().asKey(), content.getValue()))),
			getShards()
		);
	}

	@Override
	public AID getAID() {
		return this.aid;
	}
}
