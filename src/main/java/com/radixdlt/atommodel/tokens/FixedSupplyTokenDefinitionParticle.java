package com.radixdlt.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atomos.RRI;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

@SerializerId2("radix.particles.fixed_supply_token_definition")
public final class FixedSupplyTokenDefinitionParticle extends Particle {
	@JsonProperty("rri")
	@DsonOutput(Output.ALL)
	private RRI rri;

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String	name;

	@JsonProperty("description")
	@DsonOutput(Output.ALL)
	private String	description;

	@JsonProperty("supply")
	@DsonOutput(Output.ALL)
	private UInt256 supply;

	@JsonProperty("granularity")
	@DsonOutput(Output.ALL)
	private UInt256 granularity;

	@JsonProperty("iconUrl")
	@DsonOutput(Output.ALL)
	private String iconUrl;

	FixedSupplyTokenDefinitionParticle() {
		// For serializer only
		super();
	}

	public FixedSupplyTokenDefinitionParticle(
		RadixAddress address,
		String symbol,
		String name,
		String description,
		UInt256 supply,
		UInt256 granularity,
		String iconUrl
	) {
		super(address.getUID());

		this.rri = RRI.of(address, symbol);
		this.name = name;
		this.description = description;
		this.supply = Objects.requireNonNull(supply);
		this.granularity = Objects.requireNonNull(granularity);
		this.iconUrl = iconUrl;
	}

	public RRI getRRI() {
		return RRI.of(getOwner(), getSymbol());
	}

	public RadixAddress getOwner() {
		return this.rri.getAddress();
	}

	public String getSymbol() {
		return this.rri.getName();
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public UInt256 getSupply() {
		return this.supply;
	}

	public UInt256 getGranularity() {
		return this.granularity;
	}

	public String getIconUrl() {
		return this.iconUrl;
	}

	@Override
	public String toString() {
		return String.format("%s[(%s:%s:%s:%s), (%s)]", getClass().getSimpleName(),
			String.valueOf(this.rri), String.valueOf(name),
			String.valueOf(supply), String.valueOf(granularity),
			String.valueOf(description));
	}
}
