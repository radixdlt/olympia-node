package org.radix.atoms;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import org.radix.discovery.DiscoveryRequest;
import org.radix.modules.Modules;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializerId2;
import org.radix.shards.ShardSpace;

@SerializerId2("discovery.atom_discovery_request")
public class AtomDiscoveryRequest extends DiscoveryRequest {
	@Override
	public short VERSION() { return 100; }

	private Class<? extends Particle> particle = null;

	private AID     aid = AID.ZERO;

	private EUID	destination = EUID.ZERO;

	@JsonProperty("shards")
	@DsonOutput(Output.ALL)
	private ShardSpace			shards;

	public AtomDiscoveryRequest()
	{
		// Serializer only
	}

	public AtomDiscoveryRequest(Action action)
	{
		super(action);
	}

	// FIXME: Spin isn't used anywhere right now
	public AtomDiscoveryRequest(Class<? extends Particle> particle, Action action) {
		super(action);

		this.particle = particle;
	}

	public Class<? extends Particle> getParticle()
	{
		return this.particle;
	}

	public ShardSpace getShards()
	{
		return this.shards;
	}

	public AtomDiscoveryRequest setShards(ShardSpace shards)
	{
		this.shards = shards;

		return this;
	}

	public AID getAID() {
		return this.aid;
	}

	public AtomDiscoveryRequest setAID(AID aid) {
		this.aid = aid;
		return this;
	}

	public EUID getDestination()
	{
		return this.destination;
	}

	public AtomDiscoveryRequest setDestination(EUID destination)
	{
		this.destination = destination;
		return this;
	}

	@JsonProperty("particle")
	@DsonOutput(Output.ALL)
	private String getJsonParticle() {
		return this.particle == null ? null : Modules.get(Serialization.class).getIdForClass(this.particle);
	}

	@JsonProperty("particle")
	private void setJsonClassId(String classId) {
		if (classId == null) {
			this.particle = null;
		} else {
			this.particle = (Class<? extends Particle>) Modules.get(Serialization.class).getClassForId(classId);
		}
	}

	@JsonProperty("aid")
	@DsonOutput(Output.ALL)
	private AID getJsonAID() {
		return (aid == null || aid.isZero()) ? null : aid;
	}

	@JsonProperty("aid")
	private void setJsonAID(AID aid) {
		this.aid = aid;
	}

	@JsonProperty("destination")
	@DsonOutput(Output.ALL)
	private EUID getJsonDestination() {
		return (destination == null || EUID.ZERO.equals(destination)) ? null : destination;
	}

	@JsonProperty("destination")
	private void setJsonDestination(EUID destination) {
		this.destination = destination;
	}
}
