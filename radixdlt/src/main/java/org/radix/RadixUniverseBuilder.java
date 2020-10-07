/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.AtomAlreadySignedException;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.identifiers.RRI;

import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.universe.Universe;
import com.radixdlt.universe.Universe.UniverseType;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class RadixUniverseBuilder {
	private static final String RADIX_ICON_URL  = "https://assets.radixdlt.com/icons/icon-xrd-32x32.png";
	private static final String RADIX_TOKEN_URL = "https://www.radixdlt.com/";

	private static final ImmutableMap<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> XRD_TOKEN_PERMISSIONS =
		ImmutableMap.of(
			MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
			MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
		);


	private final UniverseType universeType;
	private long universeTimestamp = TimeUnit.SECONDS.toMillis(1577836800L); // Midnight Jan 1, 2020
	private ECKeyPair universeKey = ECKeyPair.generateNew();

	private RadixUniverseBuilder(UniverseType type) {
		this.universeType = type;
	}

	public static RadixUniverseBuilder production() {
		return new RadixUniverseBuilder(UniverseType.PRODUCTION);
	}

	public static RadixUniverseBuilder development() {
		return new RadixUniverseBuilder(UniverseType.DEVELOPMENT);
	}

	public static RadixUniverseBuilder test() {
		return new RadixUniverseBuilder(UniverseType.TEST);
	}

	public static RadixUniverseBuilder forType(UniverseType type) {
		return new RadixUniverseBuilder(Objects.requireNonNull(type));
	}

	public RadixUniverseBuilder withTimestamp(long timestamp) {
		this.universeTimestamp = timestamp;
		return this;
	}

	public RadixUniverseBuilder withKey(ECKeyPair key) {
		this.universeKey = Objects.requireNonNull(key);
		return this;
	}

	public RadixUniverseBuilder withNewKey() {
		return withKey(ECKeyPair.generateNew());
	}

	public Pair<ECKeyPair, Universe> build() {
		final int port = portFor(this.universeType);
		final String name = nameOf(this.universeType);
		final String description = descriptionOf(this.universeType);

		byte universeMagic = (byte) Universe.computeMagic(this.universeKey.getPublicKey(), this.universeTimestamp, port, this.universeType);
		Atom universeAtom = createGenesisAtom(universeMagic);

		Universe universe = Universe.newBuilder()
			.port(port)
			.name(name)
			.description(description)
			.type(this.universeType)
			.timestamp(this.universeTimestamp)
			.creator(universeKey.getPublicKey())
			.addAtom(universeAtom)
			.build();
		universe.sign(this.universeKey);

		if (!universe.verify(this.universeKey.getPublicKey())) {
			throw new IllegalStateException("Signature verification failed for " + name + " universe");
		}
		return Pair.of(this.universeKey, universe);
	}

	private int portFor(UniverseType type) {
		switch (type) {
		case PRODUCTION:
			return 10000;
		case TEST:
			return 20000;
		case DEVELOPMENT:
			return 30000;
		}
		return unknownUniverseType(type);
	}

	private String nameOf(UniverseType type) {
		switch (type) {
		case PRODUCTION:
			return "Radix Mainnet";
		case TEST:
			return "Radix Testnet";
		case DEVELOPMENT:
			return "Radix Devnet";
		}
		return unknownUniverseType(type);
	}

	private String descriptionOf(UniverseType type) {
		switch (type) {
		case PRODUCTION:
			return "The Radix public Universe";
		case TEST:
			return "The Radix test Universe";
		case DEVELOPMENT:
			return "The Radix development Universe";
		}
		return unknownUniverseType(type);
	}

	private <T> T unknownUniverseType(UniverseType type) {
		throw new IllegalArgumentException("Unknown universe type: " + type);
	}

	private Atom createGenesisAtom(byte magic) {
		RadixAddress universeAddress = new RadixAddress(magic, universeKey.getPublicKey());
		UInt256 genesisAmount = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 + 9); // 10^9 = 1,000,000,000 pieces of eight, please
		List<SpunParticle> xrdParticles = createTokenDefinition(
			magic,
			TokenDefinitionUtils.getNativeTokenShortCode(),
			"Rads",
			"Radix Native Tokens",
			genesisAmount
		);
		MessageParticle helloUniverseMessage = createHelloMessage(universeAddress);

		Atom genesisAtom = new Atom();
		genesisAtom.addParticleGroupWith(helloUniverseMessage, Spin.UP);
		genesisAtom.addParticleGroup(ParticleGroup.of(xrdParticles));
		try {
			genesisAtom.sign(this.universeKey);
			if (!genesisAtom.verify(this.universeKey.getPublicKey())) {
				throw new IllegalStateException("Signature verification failed - GENESIS TRANSACTION HASH: " + genesisAtom.getHash());
			}
		} catch (PublicKeyException | AtomAlreadySignedException ex) {
			throw new IllegalStateException("Error while signing universe", ex);
		}

		return genesisAtom;
	}

	/*
	 * Create the 'hello' message particle at the given universes
	 */
	private static MessageParticle createHelloMessage(RadixAddress address) {
		return new MessageParticle(address, address, "Radix... just imagine!".getBytes(RadixConstants.STANDARD_CHARSET));
	}

	/*
	 * Create a token definition as a genesis token with the radix icon and granularity of 1
	 */
	private ImmutableList<SpunParticle> createTokenDefinition(
		byte magic,
		String symbol,
		String name,
		String description,
		UInt256 initialSupply
	) {
		RadixAddress universeAddress = new RadixAddress(magic, this.universeKey.getPublicKey());
		RRI tokenRRI = RRI.of(universeAddress, symbol);

		ImmutableList.Builder<SpunParticle> particles = ImmutableList.builder();

		particles.add(SpunParticle.down(new RRIParticle(tokenRRI)));
		particles.add(SpunParticle.up(new MutableSupplyTokenDefinitionParticle(
			tokenRRI,
			name,
			description,
			UInt256.ONE,
			RADIX_ICON_URL,
			RADIX_TOKEN_URL,
			XRD_TOKEN_PERMISSIONS
		)));
		particles.add(SpunParticle.up(new TransferrableTokensParticle(
			universeAddress,
			initialSupply,
			UInt256.ONE,
			tokenRRI,
			XRD_TOKEN_PERMISSIONS
		)));
		if (!initialSupply.equals(UInt256.MAX_VALUE)) {
			particles.add(SpunParticle.up(new UnallocatedTokensParticle(
				UInt256.MAX_VALUE.subtract(initialSupply),
				UInt256.ONE,
				tokenRRI,
				XRD_TOKEN_PERMISSIONS
			)));
		}
		return particles.build();
	}
}
