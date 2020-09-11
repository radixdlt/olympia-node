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

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.keys.Keys;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import com.radixdlt.universe.Universe.UniverseType;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Pair;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.security.Security;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class GenerateUniverses {
	private static final String DEFAULT_UNIVERSES = Arrays.stream(UniverseType.values())
		.map(UniverseType::toString)
		.map(String::toLowerCase)
		.collect(Collectors.joining(","));
	private static final String DEFAULT_TIMESTAMP = String.valueOf(Instant.parse("2020-01-01T00:00:00.00Z").toEpochMilli());
	private static final String DEFAULT_KEYSTORE  = "universe.ks";

	public static void main(String[] args) {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);

		Options options = new Options();
		options.addOption("h", false, "Show usage information (this message)");
		options.addOption("d", false, "Suppress DSON output");
		options.addOption("j", false, "Suppress JSON output");
		options.addOption("k", true,  "Specify universe keystore (default: " + DEFAULT_KEYSTORE + ")");
		options.addOption("p", false, "Include universe private key in output");
		options.addOption("t", true,  "Specify universe types (default: " + DEFAULT_UNIVERSES + ")");
		options.addOption("T", true,  "Specify universe timestamp (default: " + DEFAULT_TIMESTAMP + ")");

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

			final boolean suppressDson = cmd.hasOption('d');
			final boolean suppressJson = cmd.hasOption('j');
			final String universeKeyFile = getDefaultOption(cmd, 'k', DEFAULT_KEYSTORE);
			final boolean outputPrivateKey = cmd.hasOption('p');
			final EnumSet<UniverseType> universeTypes = parseUniverseTypes(getDefaultOption(cmd, 't', DEFAULT_UNIVERSES));
			final long universeTimestampSeconds = Long.parseLong(getDefaultOption(cmd, 'T', DEFAULT_TIMESTAMP));

			final long universeTimestamp = TimeUnit.SECONDS.toMillis(universeTimestampSeconds);
			final ECKeyPair universeKey = Keys.readKey(
				universeKeyFile,
				"universe",
				"RADIX_UNIVERSE_KEYSTORE_PASSWORD",
				"RADIX_UNIVERSE_KEY_PASSWORD"
			);

			universeTypes.stream()
				.map(type -> Pair.of(type, RadixUniverseBuilder.forType(type).withKey(universeKey).withTimestamp(universeTimestamp).build()))
				.forEach(p -> outputUniverse(suppressDson, suppressJson, outputPrivateKey, p.getFirst(), p.getSecond()));
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			usage(options);
		} catch (IOException | CryptoException e) {
			System.err.println("Error while reading key: " + e.getMessage());
			usage(options);
		}
	}

	private static void outputUniverse(
		boolean suppressDson,
		boolean suppressJson,
		boolean outputPrivateKey,
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
			if (outputPrivateKey) {
				System.out.format("export RADIXDLT_UNIVERSE_PRIVKEY=%s%n", Bytes.toBase64String(k.getPrivateKey()));
			}
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

	private static EnumSet<UniverseType> parseUniverseTypes(String types) {
		return Arrays.stream(types.split(","))
			.map(String::toUpperCase)
			.map(UniverseType::valueOf)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(UniverseType.class)));
	}

	private static String getDefaultOption(CommandLine cmd, char opt, String defaultValue) {
		String value = cmd.getOptionValue(opt);
		return value == null ? defaultValue : value;
	}
}
