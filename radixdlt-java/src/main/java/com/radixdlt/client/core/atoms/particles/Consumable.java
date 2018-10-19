package com.radixdlt.client.core.atoms.particles;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.Serialize;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;

@SerializerId2("TRANSFERPARTICLE")
public class Consumable extends Particle {

	public enum ConsumableType {
		MINTED, AMOUNT
	}

	@JsonProperty("addresses")
	@DsonOutput(Output.ALL)
	private List<AccountReference> addresses;

	@JsonProperty("amount")
	@DsonOutput(Output.ALL)
	private long amount;

	@JsonProperty("nonce")
	@DsonOutput(Output.ALL)
	private long nonce;

	private Spin spin;

	@JsonProperty("planck")
	@DsonOutput(Output.ALL)
	private long planck;

	@JsonProperty("token_reference")
	@DsonOutput(Output.ALL)
	private TokenRef tokenRef;

	private ConsumableType type;

	Consumable() {
		// No-arg constructor for serializer
	}

	public Consumable(long amount, ConsumableType type, AccountReference address, long nonce, TokenRef tokenRef, long planck, Spin spin) {
		this.type = type;
		this.spin = spin;
		this.addresses = Collections.singletonList(address);
		this.amount = amount;
		this.nonce = nonce;
		this.tokenRef = tokenRef;
		this.planck = planck;
	}

	public Consumable spinDown() {
		return new Consumable(getAmount(), getType(), getAddress(), getNonce(), getTokenRef(), getPlanck(), Spin.DOWN);
	}

	public AccountReference getAddress() {
		return addresses.get(0);
	}

	@Override
	public Spin getSpin() {
		return spin;
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return addresses.stream().map(AccountReference::getKey).collect(Collectors.toSet());
	}

	public void addConsumerQuantities(long amount, ECKeyPair newOwner, Map<ECKeyPair, Long> consumerQuantities) {
		if (amount > getAmount()) {
			throw new IllegalArgumentException(
				"Unable to create consumable with amount " + amount + " (available: " + getAmount() + ")"
			);
		}

		if (amount == getAmount()) {
			consumerQuantities.merge(newOwner, amount, Long::sum);
			return;
		}

		consumerQuantities.merge(newOwner, amount, Long::sum);
		consumerQuantities.merge(getAddress().getKey().toECKeyPair(), getAmount() - amount, Long::sum);
	}

	public ConsumableType getType() {
		return type;
	}

	public long getPlanck() {
		return planck;
	}

	public long getNonce() {
		return nonce;
	}

	public TokenRef getTokenRef() {
		return tokenRef;
	}

	public long getSignedAmount() {
		return amount * (getSpin() == Spin.UP ? 1 : -1);
	}

	public long getAmount() {
		return amount;
	}

	public Set<ECPublicKey> getOwnersPublicKeys() {
		return addresses == null ? Collections.emptySet() : addresses.stream().map(AccountReference::getKey).collect(Collectors.toSet());
	}

	public ECPublicKey getOwner() {
		return addresses.get(0).getKey();
	}

	public RadixHash getHash() {
		return RadixHash.of(getDson());
	}

	public byte[] getDson() {
		return Serialize.getInstance().toDson(this, Output.HASH);
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " owners(" + addresses + ") amount(" + amount + ") spin(" + spin + ")";
	}

	@JsonProperty("spin")
	@DsonOutput(value = {Output.WIRE, Output.API, Output.PERSIST})
	private int getJsonSpin() {
		return this.spin.ordinalValue();
	}

	@JsonProperty("spin")
	private void setJsonSpin(int spin) {
		this.spin = Spin.valueOf(spin);
	}

	@JsonProperty("type")
	@DsonOutput(Output.ALL)
	private String getJsonType() {
		return this.type == null ? null : this.type.name().toLowerCase();
	}

	@JsonProperty("type")
	private void setJsonType(String type) {
		this.type = type == null ? null : ConsumableType.valueOf(type.toUpperCase());
	}
}
