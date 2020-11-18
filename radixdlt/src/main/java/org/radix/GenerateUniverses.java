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

import com.radixdlt.crypto.exception.CryptoException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.keys.Keys;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import com.radixdlt.universe.Universe.UniverseType;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt256s;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.Security;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class GenerateUniverses {
	private static final BigDecimal SUB_UNITS_BIG_DECIMAL
		= new BigDecimal(UInt256s.toBigInteger(TokenDefinitionUtils.SUB_UNITS));
	private static final String DEFAULT_UNIVERSE = UniverseType.DEVELOPMENT.toString().toLowerCase();
	private static final String DEFAULT_TIMESTAMP = String.valueOf(Instant.parse("2020-01-01T00:00:00.00Z").toEpochMilli());
	private static final String DEFAULT_KEYSTORE  = "universe.ks";
	private static final String DEFAULT_STAKE = "5000000";
	private static final String VALIDATOR_TEMPLATE = "validator%s.ks";
	private static final String STAKER_TEMPLATE = "staker%s.ks";

	public static void main(String[] args) {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);

		Options options = new Options();
		options.addOption("h", "help",                   false, "Show usage information (this message)");
		options.addOption("c", "no-cbor-output",         false, "Suppress DSON output");
		options.addOption("j", "no-json-output",         false, "Suppress JSON output");
		options.addOption("k", "keystore",               true,  "Specify universe keystore (default: " + DEFAULT_KEYSTORE + ")");
		options.addOption("p", "include-private-keys",   false, "Include universe, validator and staking private keys in output");
		options.addOption("S", "stake-amounts",          true,  "Amount of stake for each staked node (default: " + DEFAULT_STAKE + ")");
		options.addOption("t", "universe-type",          true,  "Specify universe type (default: " + DEFAULT_UNIVERSE + ")");
		options.addOption("T", "universe-timestamp",     true,  "Specify universe timestamp (default: " + DEFAULT_TIMESTAMP + ")");
		options.addOption("v", "validators-count",       true,  "Specify number of validators to generate (required)");

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cmd = parser.parse(options, args);
			if (!cmd.getArgList().isEmpty()) {
				System.err.println("Extra arguments: " + cmd.getArgList().stream().collect(Collectors.joining(" ")));
				usage(options);
				return;
			}

			if (cmd.hasOption('h')) {
				usage(options);
				return;
			}

			final boolean suppressCborOutput = cmd.hasOption('c');
			final boolean suppressJsonOutput = cmd.hasOption('j');
			final String universeKeyFile = getOption(cmd, 'k').orElse(DEFAULT_KEYSTORE);
			final boolean outputPrivateKeys = cmd.hasOption('p');
			final ImmutableList<UInt256> stakes = parseStake(getOption(cmd, 'S').orElse(DEFAULT_STAKE));
			final UniverseType universeType = parseUniverseType(getOption(cmd, 't').orElse(DEFAULT_UNIVERSE));
			final long universeTimestampSeconds = Long.parseLong(getOption(cmd, 'T').orElse(DEFAULT_TIMESTAMP));
			final int validatorsCount = Integer.parseInt(
				getOption(cmd, 'v').orElseThrow(() -> new IllegalArgumentException("Must specify number of validators"))
			);

			if (stakes.isEmpty()) {
				throw new IllegalArgumentException("Must specify at least one staking amount");
			}
			if (validatorsCount <= 0) {
				throw new IllegalArgumentException("There must be at least one validator");
			}

			final ImmutableList<ECKeyPair> validatorKeys = getValidatorKeys(validatorsCount);
			final ImmutableList<StakeDelegation> stakeDelegations = getStakeDelegation(
				Lists.transform(validatorKeys, ECKeyPair::getPublicKey), stakes
			);
			final ImmutableList<TokenIssuance> tokenIssuances = getTokenIssuances(stakeDelegations);

			final long universeTimestamp = TimeUnit.SECONDS.toMillis(universeTimestampSeconds);
			final ECKeyPair universeKey = Keys.readKey(
				universeKeyFile,
				"universe",
				"RADIX_UNIVERSE_KEYSTORE_PASSWORD",
				"RADIX_UNIVERSE_KEY_PASSWORD"
			);

			final Pair<ECKeyPair, Universe> universe = RadixUniverseBuilder.forType(universeType)
				.withKey(universeKey)
				.withTimestamp(universeTimestamp)
				.withTokenIssuance(tokenIssuances)
				.withRegisteredValidators(validatorKeys)
				.withStakeDelegations(stakeDelegations)
				.build();
			if (outputPrivateKeys) {
				System.out.format("export RADIXDLT_UNIVERSE_PRIVKEY=%s%n", Bytes.toBase64String(universe.getFirst().getPrivateKey()));
				outputNumberedKeys("VALIDATOR_%s", validatorKeys);
				outputNumberedKeys("STAKER_%s", Lists.transform(stakeDelegations, StakeDelegation::staker));
			}
			outputUniverse(suppressCborOutput, suppressJsonOutput, universeType, universe);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			usage(options);
		} catch (IOException | CryptoException e) {
			System.err.println("Error while reading key: " + e.getMessage());
			usage(options);
		}
	}

	private static ImmutableList<ECKeyPair> getValidatorKeys(int validatorsCount) {
		return IntStream.range(0, validatorsCount)
			.mapToObj(n -> {
				try {
					return Keys.readKey(
						String.format(VALIDATOR_TEMPLATE, n),
						"node",
						"RADIX_VALIDATOR_KEYSTORE_PASSWORD",
						"RADIX_VALIDATOR_KEY_PASSWORD"
					);
				} catch (CryptoException | IOException e) {
					throw new IllegalStateException("While reading validator keys", e);
				}
			})
			.collect(ImmutableList.toImmutableList());
	}

	private static ImmutableList<StakeDelegation> getStakeDelegation(List<ECPublicKey> validators, List<UInt256> stakes) {
		int n = 0;
		final ImmutableList.Builder<StakeDelegation> stakeDelegations = ImmutableList.builder();
		final Iterator<UInt256> stakesCycle = Iterators.cycle(stakes);
		for (ECPublicKey validator : validators) {
			try {
				final ECKeyPair stakerKey = Keys.readKey(
					String.format(STAKER_TEMPLATE, n++),
					"wallet",
					"RADIX_STAKER_KEYSTORE_PASSWORD",
					"RADIX_STAKER_KEY_PASSWORD"
				);
				stakeDelegations.add(StakeDelegation.of(stakerKey, validator, stakesCycle.next()));
			} catch (CryptoException | IOException e) {
				throw new IllegalStateException("While reading staker keys", e);
			}
		}
		return stakeDelegations.build();
	}

	// We just generate issuances in the amounts of the stake delegations here
	private static ImmutableList<TokenIssuance> getTokenIssuances(ImmutableList<StakeDelegation> stakeDelegations) {
		return stakeDelegations.stream()
			.map(sd -> TokenIssuance.of(sd.staker().getPublicKey(), sd.amount()))
			.collect(ImmutableList.toImmutableList());
	}

	private static ImmutableList<UInt256> parseStake(String stakes) {
		return Stream.of(stakes.split(","))
			.map(String::trim)
			.map(BigDecimal::new)
			.map(GenerateUniverses::unitsToSubunits)
			.collect(ImmutableList.toImmutableList());
	}

	private static void outputNumberedKeys(String template, List<ECKeyPair> keys) {
		int n = 0;
		for (ECKeyPair k : keys) {
			String keyname = String.format(template, n++);
			System.out.format("export RADIXDLT_%s_PRIVKEY=%s%n", keyname, Bytes.toBase64String(k.getPrivateKey()));
		}
	}

	private static void outputUniverse(
		boolean suppressDson,
		boolean suppressJson,
		UniverseType type,
		Pair<ECKeyPair, Universe> p
	) {
		final Serialization serialization = DefaultSerialization.getInstance();
		final ECKeyPair k = p.getFirst();
		final Universe u = p.getSecond();
		if (!suppressDson) {
			byte[] universeBytes = serialization.toDson(u, Output.WIRE);
			RadixAddress universeAddress = new RadixAddress((byte) u.getMagic(), k.getPublicKey());
			RRI tokenRri = RRI.of(universeAddress, TokenDefinitionUtils.getNativeTokenShortCode());
			System.out.format("export RADIXDLT_UNIVERSE_TYPE=%s%n", type);
			System.out.format("export RADIXDLT_UNIVERSE_PUBKEY=%s%n", k.getPublicKey().toBase64());
			System.out.format("export RADIXDLT_UNIVERSE_ADDRESS=%s%n", universeAddress);
			System.out.format("export RADIXDLT_UNIVERSE_TOKEN=%s%n", tokenRri);
			System.out.format("export RADIXDLT_UNIVERSE=%s%n", Bytes.toBase64String(universeBytes));
		}
		if (!suppressJson) {
			JSONObject json = new JSONObject(serialization.toJson(p.getSecond(), Output.WIRE));
			System.out.println(json.toString(4));
		}
	}

	private static void usage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(GenerateUniverses.class.getSimpleName(), options, true);
	}

	private static UniverseType parseUniverseType(String type) {
		return UniverseType.valueOf(type.toUpperCase());
	}

	private static Optional<String> getOption(CommandLine cmd, char opt) {
		String value = cmd.getOptionValue(opt);
		return Optional.ofNullable(value);
	}

	private static UInt256 unitsToSubunits(BigDecimal units) {
		return UInt256s.fromBigDecimal(units.multiply(SUB_UNITS_BIG_DECIMAL));
	}
}
