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
import com.radixdlt.tempo.exceptions.TempoException;
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

	@JsonProperty("timestamp")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private long timestamp;

	@JsonProperty("shards")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private ImmutableSet<Long> shards;

	@JsonProperty("temporalProof")
	@DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	private TemporalProof temporalProof;

	private TempoAtom() {
		// For serializer
	}

	public TempoAtom(AtomContent content, AID aid, long timestamp, Set<Long> shards) {
		this(content, aid, timestamp, shards, null);
	}

	public TempoAtom(AtomContent content, AID aid, long timestamp, Set<Long> shards, TemporalProof temporalProof) {
		this.content = Objects.requireNonNull(content, "content is required");
		this.aid = Objects.requireNonNull(aid, "aid is required");
		this.timestamp = timestamp;
		this.shards = ImmutableSet.copyOf(shards);
		this.temporalProof = temporalProof;
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
	public long getTimestamp() {
		return this.timestamp;
	}

	public TemporalProof getTemporalProof() {
		if (this.temporalProof == null) {
			this.temporalProof = new TemporalProof(this.getAID());
		}

		return this.temporalProof;
	}

	TempoAtom aggregate(TemporalProof temporalProof) {
		TemporalProof aggregated = new TemporalProof(this.aid, getTemporalProof().getVertices());
		try {
			// TODO make TP.merge operate on two immutable tps
			aggregated.merge(temporalProof);
			return with(aggregated);
		} catch (ValidationException e) {
			throw new TempoException("Error while aggregating temporal proof", e);
		}
	}

	TempoAtom with(TemporalProof temporalProof) {
		return new TempoAtom(
			this.content,
			this.aid,
			this.timestamp,
			this.shards,
			temporalProof
		);
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
}
