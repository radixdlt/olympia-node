/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.lib;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Ints;

import java.security.Security;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public class BalanceVerifier {
	private static final String DEFAULT_HOST = "http://localhost:8080/";
	private static final MediaType MEDIA_TYPE = MediaType.parse("application/json");

	public static void main(String[] args) {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);

		Options options = new Options();
		options.addOption("h", "help", false, "Show usage information (this message)");
		options.addOption("n", "node-url", true, "Node URL (default to " + DEFAULT_HOST + ")");

		try {
			var cmd = new DefaultParser().parse(options, args);

			if (!cmd.getArgList().isEmpty()) {
				usage(options);
				return;
			}

			if (cmd.hasOption('h')) {
				usage(options);
				return;
			}

			var baseUrl = getOption(cmd, 'h').orElse(DEFAULT_HOST);
			var client = NodeClient.create(baseUrl);

			//String json = "{\"params\":{\"address\":\"" + addressToSendTokensTo + "\"}}";

		} catch (ParseException e) {
			System.err.println(e.getMessage());
			usage(options);
		}
	}

	private static void printResponse(ResponseBody responseBody) {

	}

	private static OkHttpClient assembleClient() {
		var client = new OkHttpClient.Builder()
			.connectTimeout(30, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.pingInterval(30, TimeUnit.SECONDS)
			.build();
		return client;
	}

	private static ECPublicKey pubkeyOf(int pk) {
		final byte[] privateKey = new byte[ECKeyPair.BYTES];
		Ints.copyTo(pk, privateKey, ECKeyPair.BYTES - Integer.BYTES);
		ECKeyPair kp;
		try {
			kp = ECKeyPair.fromPrivateKey(privateKey);
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalArgumentException("Error while generating public key", e);
		}
		return kp.getPublicKey();
	}

	private static void usage(Options options) {
		new HelpFormatter().printHelp(BalanceVerifier.class.getSimpleName(), options, true);
	}

	private static Optional<String> getOption(CommandLine cmd, char opt) {
		return Optional.ofNullable(cmd.getOptionValue(opt));
	}
}
