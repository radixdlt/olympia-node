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

package com.radix.test.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.client.core.ledger.InMemoryAtomStore;
import com.radixdlt.identifiers.AID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.identifiers.RadixAddress;
import org.json.JSONObject;

public final class TokenUtilities {
	private static final Logger log = LogManager.getLogger();

	// Number of times to retry if faucet fails
	private static final int MAX_RETRY_COUNT = 10;

	private TokenUtilities() {
		throw new IllegalStateException("Can't construct");
	}

	public static void requestTokensFor(RadixIdentity identity) {
		requestTokensFor(RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), identity));
	}

	public static synchronized void requestTokensFor(RadixApplicationAPI api) {
		AID atomId = requestTokens(api.getAddress());

		for (int i = 0; i < MAX_RETRY_COUNT; i++) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
			}
			Atom atom = getAtom(atomId);
			if (atom != null) {
				var inMemoryAtomStore = (InMemoryAtomStore) api.getAtomStore();
				inMemoryAtomStore.store(api.getAddress(), AtomObservation.stored(atom, System.currentTimeMillis()));
				return;
			}
		}
	}

	private static Atom getAtom(AID aid) {
		try {
			String faucetHost = Optional.ofNullable(System.getenv("FAUCET_HOST")).orElse("http://localhost:8080");
			URL url = new URL(faucetHost + "/rpc");
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setConnectTimeout(5000);
			con.setReadTimeout(5000);
			con.setDoOutput(true);
			var jsonRequest = new JSONObject()
				.put("method", "Ledger.getAtom")
				.put("params", new JSONObject().put("aid", aid))
				.put("id", 0);
			try (var os = con.getOutputStream()) {
				byte[] input = jsonRequest.toString().getBytes("utf-8");
				os.write(input, 0, input.length);
			}

			int status = con.getResponseCode();
			final String result;
			try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
				StringBuilder response = new StringBuilder();
				String responseLine;
				while ((responseLine = in.readLine()) != null) {
					response.append(responseLine.trim());
				}
				result = response.toString();
			}

			if (status == 200) {
				var res = new JSONObject(result);
				if (res.has("error")) {
					return null;
				}

				return DefaultSerialization.getInstance().fromJson(result, Atom.class);
			}
			throw new IllegalStateException(String.format("Could not request tokens (%s): %s", status, result));
		} catch (IOException e) {
			// Just going to ignore these and timeout
			log.info("Ignoring IOException while requesting tokens: {}", e.getMessage());
			throw new RuntimeException(e);
		}
	}

	private static AID requestTokens(RadixAddress address) {
		try {
			String faucetHost = Optional.ofNullable(System.getenv("FAUCET_HOST")).orElse("http://localhost:8080");
			URL url = new URL(faucetHost + "/faucet/request");
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setConnectTimeout(5000);
			con.setReadTimeout(5000);
			con.setDoOutput(true);
			var jsonRequest = new JSONObject().put("params", new JSONObject().put("address", address));
			try (var os = con.getOutputStream()) {
				byte[] input = jsonRequest.toString().getBytes("utf-8");
				os.write(input, 0, input.length);
			}

			int status = con.getResponseCode();
			final String result;
			try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
				StringBuilder response = new StringBuilder();
				String responseLine;
				while ((responseLine = in.readLine()) != null) {
					response.append(responseLine.trim());
				}
				result = response.toString();
			}

			var res = new JSONObject(result);

			if (status == 200) {
				return AID.from(res.getString("result"));
			}
			throw new IllegalStateException(String.format("Could not request tokens (%s): %s", status, result));
		} catch (IOException e) {
			// Just going to ignore these and timeout
			log.info("Ignoring IOException while requesting tokens: {}", e.getMessage());
			throw new RuntimeException(e);
		}
	}
}
