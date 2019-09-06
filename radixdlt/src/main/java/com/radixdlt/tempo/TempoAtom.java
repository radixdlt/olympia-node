package com.radixdlt.tempo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;
import com.radixdlt.AtomContent;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import org.radix.exceptions.ValidationException;
import org.radix.time.TemporalProof;

import java.util.Objects;
import java.util.Set;

@SerializerId2("tempo.atom")
public final class TempoAtom implements Atom {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	/**
	 * Arbitrary, opaque content
	 */
	@JsonProperty("content")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private AtomContent content;

	@JsonProperty("aid")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private AID aid;

	@JsonProperty("shards")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private ImmutableSet<Long> shards;

	private TempoAtom() {
		// For serializer
	}

	public TempoAtom(AtomContent content, AID aid, Set<Long> shards) {
		this.content = Objects.requireNonNull(content, "content is required");
		this.aid = Objects.requireNonNull(aid, "aid is required");
		this.shards = ImmutableSet.copyOf(shards);
	}

	@Override
	public AtomContent getContent() {
		return this.content;
	}

	@Override
	public AID getAID() {
		return this.aid;
	}

	@Override
	public ImmutableSet<Long> getShards() {
		return this.shards;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		TempoAtom tempoAtom = (TempoAtom) o;
		return aid.equals(tempoAtom.aid);
	}

	@Override
	public int hashCode() {
		return aid.hashCode();
	}

	@Override
	public String toString() {
		return "TempoAtom{" +
			"aid=" + aid +
			", shards=" + shards +
			'}';
	}
}
