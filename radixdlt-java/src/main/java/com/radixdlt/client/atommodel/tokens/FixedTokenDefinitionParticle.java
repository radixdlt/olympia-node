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

@SerializerId2("radix.particles.fixed_token_definition")
public final class FixedTokenDefinitionParticle extends Particle implements Identifiable, Ownable {

	@JsonProperty("address")
	@DsonOutput(Output.ALL)
	private RadixAddress address;

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String	name;

	@JsonProperty("symbol")
	@DsonOutput(Output.ALL)
	private String symbol;

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

	FixedTokenDefinitionParticle() {
		// For serializer only
		super();
	}

	public FixedTokenDefinitionParticle(
		RadixAddress address,
		String name,
		String symbol,
		String description,
		UInt256 supply,
		UInt256 granularity,
		String iconUrl
	) {
		super(address.getUID());

		this.address = address;
		this.name = name;
		this.symbol = symbol;
		this.description = description;
		this.supply = Objects.requireNonNull(supply);
		this.granularity = Objects.requireNonNull(granularity);
		this.iconUrl = iconUrl;
	}

	@Override
	public RRI getRRI() {
		return RRI.of(this.address, this.symbol);
	}

	@Override
	public RadixAddress getAddress() {
		return this.address;
	}

	public String getSymbol() {
		return this.symbol;
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
		return String.format("%s[(%s:%s:%s:%s), (%s), %s]", getClass().getSimpleName(),
			String.valueOf(name), String.valueOf(symbol),
			String.valueOf(supply), String.valueOf(granularity),
			String.valueOf(description), String.valueOf(address));
	}
}
