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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.radixdlt.atommodel.AtomAlreadySignedException;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.identifiers.RRI;

import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.universe.Universe;
import com.radixdlt.universe.Universe.UniverseType;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RadixUniverseBuilder {
	private static final String RADIX_ICON_URL  = "https://assets.radixdlt.com/icons/icon-xrd-32x32.png";
	private static final String RADIX_TOKEN_URL = "https://tokens.radixdlt.com/";

	private static final ImmutableMap<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> XRD_TOKEN_PERMISSIONS =
		ImmutableMap.of(
			MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
			MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
		);

	private final UniverseType universeType;
	private long universeTimestamp = TimeUnit.SECONDS.toMillis(1577836800L); // Midnight Jan 1, 2020
	private ECKeyPair universeKey = ECKeyPair.generateNew();
	private UInt256 selfIssuance = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 + 9); // 1e9 rads
	private ImmutableList<TokenIssuance> tokenIssuances = ImmutableList.of();
	private ImmutableList<ECKeyPair> validatorKeys = ImmutableList.of();
	private ImmutableList<StakeDelegation> stakeDelegations = ImmutableList.of();

	private final Hasher hasher = Sha256Hasher.withDefaultSerialization();

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

	public RadixUniverseBuilder withSelfIssuance(UInt256 amount) {
		this.selfIssuance = Objects.requireNonNull(amount);
		return this;
	}

	public RadixUniverseBuilder withTokenIssuance(Collection<TokenIssuance> tokenIssuances) {
		this.tokenIssuances = ImmutableList.copyOf(tokenIssuances);
		return this;
	}

	public RadixUniverseBuilder withRegisteredValidators(Collection<ECKeyPair> validatorKeys) {
		this.validatorKeys = ImmutableList.copyOf(validatorKeys);
		return this;
	}

	public RadixUniverseBuilder withStakeDelegations(Collection<StakeDelegation> stakeDelegations) {
		this.stakeDelegations = ImmutableList.copyOf(stakeDelegations);
		return this;
	}

	public Pair<ECKeyPair, Universe> build() {
		final var port = portFor(this.universeType);
		final var name = nameOf(this.universeType);
		final var description = descriptionOf(this.universeType);

		final var universeMagic = (byte) Universe.computeMagic(
			this.universeKey.getPublicKey(),
			this.universeTimestamp,
			port,
			this.universeType
		);
		final var universeAtom = createGenesisAtom(
			universeMagic,
			this.selfIssuance,
			this.tokenIssuances,
			this.validatorKeys,
			this.stakeDelegations
		);

		final var universe = Universe.newBuilder()
			.port(port)
			.name(name)
			.description(description)
			.type(this.universeType)
			.timestamp(this.universeTimestamp)
			.creator(this.universeKey.getPublicKey())
			.addAtom(universeAtom)
			.build();

		Universe.sign(universe, this.universeKey, this.hasher);
		if (!Universe.verify(universe, this.universeKey.getPublicKey(), this.hasher)) {
			throw new IllegalStateException(
				String.format("Signature verification failed for %s universe with key %s", name, this.universeKey)
			);
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

	private Atom createGenesisAtom(
		byte magic,
		UInt256 selfIssuance,
		ImmutableList<TokenIssuance> tokenIssuances,
		ImmutableList<ECKeyPair> validatorKeys,
		ImmutableList<StakeDelegation> stakeDelegations
	) {
		// Check that issuances are sufficient for delegations
		final var issuances = tokenIssuances.stream()
			.collect(ImmutableMap.toImmutableMap(TokenIssuance::receiver, TokenIssuance::amount, UInt256::add));
		final var requiredDelegations = stakeDelegations.stream()
			.collect(ImmutableMap.toImmutableMap(sd -> sd.staker().getPublicKey(), StakeDelegation::amount, UInt256::add));
		requiredDelegations.forEach((pk, amount) -> {
			final var issuedAmount = issuances.getOrDefault(pk, UInt256.ZERO);
			if (amount.compareTo(issuedAmount) > 0) {
				throw new IllegalStateException(
					String.format(
						"%s wants to stake %s, but was only issued %s",
						new RadixAddress(magic, pk), amount, issuedAmount
					)
				);
			}
		});
		final var universeAddress = new RadixAddress(magic, universeKey.getPublicKey());
		final var helloUniverseMessage = createHelloMessage(universeAddress);
		final var epochParticles = createEpochUpdate();
		final var xrdParticles = createTokenDefinition(
			magic,
			TokenDefinitionUtils.getNativeTokenShortCode(),
			"Rads",
			"Radix Native Tokens",
			selfIssuance,
			tokenIssuances
		);
		final var validatorParticles = createValidators(
			magic,
			validatorKeys
		);
		final var stakingParticles = createStakes(
			magic,
			stakeDelegations,
			xrdParticles
		);

		final var genesisAtom = new Atom();
		genesisAtom.addParticleGroupWith(helloUniverseMessage, Spin.UP);
		genesisAtom.addParticleGroup(ParticleGroup.of(epochParticles));
		genesisAtom.addParticleGroup(ParticleGroup.of(xrdParticles));
		if (!validatorParticles.isEmpty()) {
			genesisAtom.addParticleGroup(ParticleGroup.of(validatorParticles));
		}
		if (!stakingParticles.isEmpty()) {
			genesisAtom.addParticleGroup(ParticleGroup.of(stakingParticles));
		}

		final var signingKeys = Streams.concat(
			Stream.of(this.universeKey),
			validatorKeys.stream(),
			stakeDelegations.stream().map(StakeDelegation::staker)
		).collect(ImmutableList.toImmutableList());


		try {
			genesisAtom.sign(signingKeys, this.hasher);
		} catch (AtomAlreadySignedException ex) {
			// This can't happen, as we have created the atom, and are sure it is not signed
			throw new IllegalStateException("Error while signing universe", ex);
		}
		signingKeys.forEach(key -> verifySignature(key, genesisAtom));

		return genesisAtom;
	}

	/*
	 * Create the 'hello' message particle at the given universes
	 */
	private static MessageParticle createHelloMessage(RadixAddress address) {
		return new MessageParticle(address, address, "Radix... just imagine!".getBytes(RadixConstants.STANDARD_CHARSET));
	}

	/*
	 * Create a token definition as a genesis token with the radix icon and granularity of 1.
	 * In addition, create an issuance for the universe key and other keys as specified.
	 */
	private ImmutableList<SpunParticle> createTokenDefinition(
		byte magic,
		String symbol,
		String name,
		String description,
		UInt256 selfIssuance,
		ImmutableList<TokenIssuance> issuances
	) {
		final var universeAddress = new RadixAddress(magic, this.universeKey.getPublicKey());
		final var tokenRRI = RRI.of(universeAddress, symbol);

		final var particles = ImmutableList.<SpunParticle>builder();

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
		var issuedTokens = UInt384.from(selfIssuance);
		if (!selfIssuance.isZero()) {
			particles.add(SpunParticle.up(new TransferrableTokensParticle(
				universeAddress,
				selfIssuance,
				UInt256.ONE,
				tokenRRI,
				XRD_TOKEN_PERMISSIONS
			)));
		}
		// Merge issuances so we only have one TTP per address
		final var issuedAmounts = issuances.stream()
			.collect(ImmutableMap.toImmutableMap(TokenIssuance::receiver, TokenIssuance::amount, UInt256::add));
		for (final var issuance : issuedAmounts.entrySet()) {
			final var amount = issuance.getValue();
			if (!amount.isZero()) {
				particles.add(SpunParticle.up(new TransferrableTokensParticle(
					new RadixAddress(magic, issuance.getKey()),
					amount,
					UInt256.ONE,
					tokenRRI,
					XRD_TOKEN_PERMISSIONS
				)));
				issuedTokens = issuedTokens.add(amount);
			}
		}
		if (!issuedTokens.getHigh().isZero()) {
			// TokenOverflowException
			throw new IllegalStateException("Too many issued tokens: " + issuedTokens);
		}
		if (!issuedTokens.getLow().equals(UInt256.MAX_VALUE)) {
			particles.add(SpunParticle.up(new UnallocatedTokensParticle(
				UInt256.MAX_VALUE.subtract(issuedTokens.getLow()),
				UInt256.ONE,
				tokenRRI,
				XRD_TOKEN_PERMISSIONS
			)));
		}
		return particles.build();
	}

	private ImmutableList<SpunParticle> createEpochUpdate() {
		ImmutableList.Builder<SpunParticle> particles = ImmutableList.builder();
		particles.add(SpunParticle.down(new SystemParticle(0, 0, 0)));
		particles.add(SpunParticle.up(new SystemParticle(1, 0, 0)));
		return particles.build();
	}

	private List<SpunParticle> createValidators(byte magic, ImmutableList<ECKeyPair> validatorKeys) {
		final List<SpunParticle> validatorParticles = Lists.newArrayList();
		validatorKeys.forEach(key -> {
			RadixAddress validatorAddress = new RadixAddress(magic, key.getPublicKey());
			UnregisteredValidatorParticle validatorDown = new UnregisteredValidatorParticle(validatorAddress, 0L);
			RegisteredValidatorParticle validatorUp = new RegisteredValidatorParticle(validatorAddress, ImmutableSet.of(), 1L);
			validatorParticles.add(SpunParticle.down(validatorDown));
			validatorParticles.add(SpunParticle.up(validatorUp));
		});
		return validatorParticles;
	}

	private List<SpunParticle> createStakes(
		byte magic,
		ImmutableList<StakeDelegation> delegations,
		List<SpunParticle> xrdParticles
	) {
		final ImmutableMap<ECPublicKey, TransferrableTokensParticle> tokensByKey = xrdParticles.stream()
			.filter(SpunParticle::isUp)
			.map(SpunParticle::getParticle)
			.filter(TransferrableTokensParticle.class::isInstance)
			.map(TransferrableTokensParticle.class::cast)
			.collect(ImmutableMap.toImmutableMap(ttp -> ttp.getAddress().getPublicKey(), Function.identity()));

		final var stakesByKey = delegations.stream()
			.collect(Collectors.groupingBy(sd -> sd.staker().getPublicKey(), ImmutableList.toImmutableList()));

		final List<SpunParticle> stakeParticles = Lists.newArrayList();
		for (final var entry : stakesByKey.entrySet()) {
			final var downParticle = tokensByKey.get(entry.getKey());
			if (downParticle == null) {
				// Has been checked previously - logic error introduced somewhere
				throw new IllegalStateException("Unexpected missing token particle");
			}
			var delegatedAmount = UInt256.ZERO;
			stakeParticles.add(SpunParticle.down(downParticle));
			for (final var delegation : entry.getValue()) {
				final var amount = delegation.amount();
				final var stp = new StakedTokensParticle(
					new RadixAddress(magic, delegation.delegate()),
					new RadixAddress(magic, delegation.staker().getPublicKey()),
					amount,
					downParticle.getGranularity(),
					downParticle.getTokDefRef(),
					downParticle.getTokenPermissions()
				);
				stakeParticles.add(SpunParticle.up(stp));
				delegatedAmount = delegatedAmount.add(amount);
			}
			if (downParticle.getAmount().compareTo(delegatedAmount) < 0) {
				// Has been previously checked to ensure no underflow - logic error
				throw new IllegalStateException("Trying to delegate more than issued");
			}
			final var balance = downParticle.getAmount().subtract(delegatedAmount);
			final var outputTtp = new TransferrableTokensParticle(
				downParticle.getAddress(),
				balance,
				downParticle.getGranularity(),
				downParticle.getTokDefRef(),
				downParticle.getTokenPermissions()
			);
			stakeParticles.add(SpunParticle.up(outputTtp));
		}
		return stakeParticles;
	}

	private void verifySignature(ECKeyPair key, Atom genesisAtom) {
		try {
			if (!genesisAtom.verify(key.getPublicKey(), this.hasher)) {
				// A bug somewhere that needs fixing
				throw new IllegalStateException(
					String.format(
						"Signature verification failed - GENESIS TRANSACTION HASH: %s",
						this.hasher.hash(genesisAtom)
					)
				);
			}
		} catch (PublicKeyException ex) {
			throw new IllegalStateException("Error while verifying universe", ex);
		}
	}
}
