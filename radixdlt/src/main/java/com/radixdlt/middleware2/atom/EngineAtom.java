package com.radixdlt.middleware2.atom;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;
import com.radixdlt.AtomContent;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;

import java.util.Objects;
import java.util.Set;

public class EngineAtom implements Atom {

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

	public EngineAtom(AtomContent content, AID aid, Set<Long> shards) {
		this.content = Objects.requireNonNull(content, "content is required");
		this.aid = Objects.requireNonNull(aid, "aid is required");
		this.shards = ImmutableSet.copyOf(shards);
	}

	@Override
	public AtomContent getContent() {
		return content;
	}

	@Override
	public ImmutableSet<Long> getShards() {
		return shards;
	}

	@Override
	public AID getAID() {
		return aid;
	}
}
