package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.Identifiable;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RRI;

import java.util.Objects;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;

@SerializerId2("radix.particles.fixed_supply_token_definition")
public final class FixedSupplyTokenDefinitionParticle extends Particle implements Identifiable, Ownable {

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
		String name,
		String symbol,
		String description,
		UInt256 supply,
		UInt256 granularity,
		String iconUrl
	) {
		this(RRI.of(address,  symbol), name, description, supply, granularity, iconUrl);
	}

	public FixedSupplyTokenDefinitionParticle(
		RRI rri,
		String name,
		String description,
		UInt256 supply,
		UInt256 granularity,
		String iconUrl
	) {
		super(rri.getAddress().getUID());

		this.rri = rri;
		this.name = name;
		this.description = description;
		this.supply = Objects.requireNonNull(supply);
		this.granularity = Objects.requireNonNull(granularity);
		this.iconUrl = iconUrl;
	}

	@Override
	public RRI getRRI() {
		return this.rri;
	}

	@Override
	public RadixAddress getAddress() {
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
		return String.format("%s[%s (%s:%s), (%s/%s)]", getClass().getSimpleName(),
			String.valueOf(this.rri), name, description,
			String.valueOf(supply), String.valueOf(granularity));
	}
}
