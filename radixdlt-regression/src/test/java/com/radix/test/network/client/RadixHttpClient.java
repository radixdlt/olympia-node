package com.radix.test.network.client;

import com.radix.test.network.RadixNetworkConfiguration;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.utils.functional.Failure;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;

import static com.radixdlt.errors.RadixErrors.IO_ERROR;

/**
 * A small HTTP client that consumes the non-JSON-RPC methods e.g. /health.
 * <p>
 * Also consumes the JSON-RPC methods that are not part of the RadixApi client e.g. /faucet
 */
public class RadixHttpClient {
	private static final String FAUCET_PATH = "/faucet";

	public static RadixHttpClient fromRadixNetworkConfiguration(RadixNetworkConfiguration configuration) {
		return new RadixHttpClient(configuration.getBasicAuth());
	}

	public RadixHttpClient(String basicAuth) {
		if (basicAuth != null && !basicAuth.isBlank()) {
			var encodedCredentials = Base64.getEncoder().encodeToString(basicAuth.getBytes(StandardCharsets.UTF_8));
			Unirest.config().setDefaultHeader("Authorization", "Basic " + encodedCredentials);
		}
	}

	public String callFaucet(String rootUrl, int port, String address) {
		var faucetBody = new JSONObject()
			.put("jsonrpc", "2.0")
			.put("id", "1")
			.put("method", "faucet.request_tokens"); //TODO: may no longer work!!!

		var params = new JSONObject().put("address", address);

		faucetBody.put("params", params);

		var response = Unirest.post(rootUrl + ":" + port + FAUCET_PATH)
			.body(faucetBody.toString(5))
			.asJson();

		if (!response.isSuccess()) {
			throw new RadixApiException(IO_ERROR.with(response.getBody().toPrettyString()));
		}

		var responseObject = response.getBody().getObject();

		if (responseObject.has("error")) {
			var errorObject = responseObject.getJSONObject("error");
			var errorMessage = errorObject.getString("message");
			int errorCode = errorObject.getInt("code");
			throw new RadixApiException(Failure.failure(errorCode, errorMessage));
		}
		return responseObject.getJSONObject("result").getString("txID");
	}

}
