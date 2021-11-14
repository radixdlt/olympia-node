/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.archive;

import com.google.common.base.Throwables;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt256;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

public final class JsonObjectReader {
	private final JSONObject jsonObject;
	private final Supplier<Addressing> addressing;

	public static JsonObjectReader empty() {
		return new JsonObjectReader(new JSONObject(), () -> {
			throw new UnsupportedOperationException();
		});
	}

	private JsonObjectReader(JSONObject jsonObject, Supplier<Addressing> addressing) {
		this.jsonObject = jsonObject;
		this.addressing = addressing;
	}

	public static JsonObjectReader create(JSONObject jsonObject, Supplier<Addressing> addressing) {
		return new JsonObjectReader(jsonObject, addressing);
	}

	public long getUnsignedLong(String key) throws InvalidParametersException {
		long l;
		try {
			l = jsonObject.getLong(key);
		} catch (JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}

		if (l < 0) {
			throw new InvalidParametersException("/" + key, "Number cannot be negative.");
		}

		return l;
	}

	public OptionalLong getOptUnsignedLong(String key) throws InvalidParametersException {
		long l;
		try {
			if (!jsonObject.has(key)) {
				return OptionalLong.empty();
			}
			l = jsonObject.getLong(key);
		} catch (JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}

		if (l < 0) {
			throw new InvalidParametersException("/" + key, "Number cannot be negative.");
		}

		return OptionalLong.of(l);
	}

	public BigInteger getBigInteger(String key) throws InvalidParametersException {
		try {
			var bigInteger = jsonObject.getString(key);
			return new BigInteger(bigInteger);
		} catch (NumberFormatException | JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}

	public int getInteger(String key, int min, int max) throws InvalidParametersException {
		int i;
		try {
			i = jsonObject.getInt(key);
		} catch (NumberFormatException | JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}

		if (i < min || i > max) {
			throw new InvalidParametersException("/" + key, "integer must be >= " + min + " and <= " + max);
		}

		return i;
	}

	public UInt256 getAmount(String key) throws InvalidParametersException {
		try {
			var amountString = jsonObject.getString(key);
			return UInt256.from(amountString);
		} catch (NumberFormatException | JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}

	public Optional<UInt256> getOptNonZeroAmount(String key) throws InvalidParametersException {
		if (!jsonObject.has(key)) {
			return Optional.empty();
		}

		var amount = getAmount(key);
		if (amount.isZero()) {
			throw new InvalidParametersException("/" + key, "Amount cannot be zero.");
		}
		return Optional.of(amount);
	}


	public UInt256 getNonZeroAmount(String key) throws InvalidParametersException {
		var amount = getAmount(key);
		if (amount.isZero()) {
			throw new InvalidParametersException("/" + key, "Amount cannot be zero.");
		}
		return amount;
	}


	public ECPublicKey getPubKey(String key) throws InvalidParametersException {
		try {
			return ECPublicKey.fromHex(jsonObject.getString(key));
		} catch (PublicKeyException | JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}

	public Optional<REAddr> tryAccountAddress(String key) throws InvalidParametersException {
		try {
			var accountAddress = jsonObject.getString(key);
			if (!accountAddress.startsWith(addressing.get().forAccounts().getHrp())) {
				return Optional.empty();
			}
			return Optional.of(addressing.get().forAccounts().parse(accountAddress));
		} catch (DeserializeException | JSONException e) {
			throw new InvalidParametersException("/" + key, Throwables.getRootCause(e));
		}
	}

	public boolean has(String key) {
		return jsonObject.has(key);
	}

	public Optional<REAddr> getOptAccountAddress(String key) throws InvalidParametersException {
		try {
			if (!jsonObject.has(key)) {
				return Optional.empty();
			}
			var addressString = jsonObject.getString(key);
			return Optional.of(addressing.get().forAccounts().parse(addressString));
		} catch (DeserializeException | JSONException e) {
			throw new InvalidParametersException("/" + key, Throwables.getRootCause(e));
		}
	}

	public REAddr getAccountAddress(String key) throws InvalidParametersException {
		try {
			var addressString = jsonObject.getString(key);
			return addressing.get().forAccounts().parse(addressString);
		} catch (DeserializeException | JSONException e) {
			throw new InvalidParametersException("/" + key, Throwables.getRootCause(e));
		}
	}

	public ECDSASignature getDERSignature(String key) throws InvalidParametersException {
		try {
			var hex = jsonObject.getString(key);
			return ECDSASignature.decodeFromHexDer(hex);
		} catch (JSONException | IllegalArgumentException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}

	public Optional<byte[]> getOptHexBytes(String key) throws InvalidParametersException {
		byte[] bytes;
		try {
			if (!jsonObject.has(key)) {
				return Optional.empty();
			}
			String hex = jsonObject.getString(key);
			bytes = Bytes.fromHexString(hex);
		} catch (JSONException | IllegalArgumentException e) {
			throw new InvalidParametersException("/" + key, e);
		}

		return Optional.of(bytes);
	}


	public Optional<byte[]> getOptHexBytes(String key, int fixedLength) throws InvalidParametersException {
		byte[] bytes;
		try {
			if (!jsonObject.has(key)) {
				return Optional.empty();
			}
			String hex = jsonObject.getString(key);
			bytes = Bytes.fromHexString(hex);
		} catch (JSONException | IllegalArgumentException e) {
			throw new InvalidParametersException("/" + key, e);
		}
		if (bytes.length != fixedLength) {
			throw new InvalidParametersException("/" + key, "Invalid bytes length");
		}

		return Optional.of(bytes);
	}

	public byte[] getHexBytes(String key, int fixedLength) throws InvalidParametersException {
		byte[] bytes;
		try {
			String hex = jsonObject.getString(key);
			bytes = Bytes.fromHexString(hex);
		} catch (JSONException | IllegalArgumentException e) {
			throw new InvalidParametersException("/" + key, e);
		}
		if (bytes.length != fixedLength) {
			throw new InvalidParametersException("/" + key, "Invalid bytes length");
		}

		return bytes;
	}

	public byte[] getHexBytes(String key) throws InvalidParametersException {
		try {
			var hex = jsonObject.getString(key);
			return Bytes.fromHexString(hex);
		} catch (JSONException | IllegalArgumentException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}

	public String getString(String key) throws InvalidParametersException {
		try {
			return jsonObject.getString(key);
		} catch (JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}

	public Optional<String> getOptString(String key) throws InvalidParametersException {
		try {
			return !jsonObject.has(key) ? Optional.empty() : Optional.of(jsonObject.getString(key));
		} catch (JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}

	public boolean getBoolean(String key) throws InvalidParametersException {
		try {
			return jsonObject.getBoolean(key);
		} catch (JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}

	public boolean getOptBoolean(String key, boolean defaultValue) throws InvalidParametersException {
		try {
			return !jsonObject.has(key) ? defaultValue : jsonObject.getBoolean(key);
		} catch (JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}

	public interface JsonObjectMapper<T> {
		T map(JsonObjectReader reader) throws InvalidParametersException;
	}

	public <T> List<T> getList(String key, JsonObjectMapper<T> mapper) throws InvalidParametersException {
		JSONArray array;
		try {
			array = jsonObject.getJSONArray(key);
		} catch (JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}

		var list = new ArrayList<T>();

		for (int i = 0; i < array.length(); i++) {
			try {
				var json = array.getJSONObject(i);
				var reader = JsonObjectReader.create(json, addressing);
				var t = mapper.map(reader);
				list.add(t);
			} catch (JSONException e) {
				throw new InvalidParametersException("/" + key + "/" + i, e);
			}
		}

		return list;
	}

	public <T> Optional<T> getOptJsonObject(String key, JsonObjectMapper<T> mapper) throws InvalidParametersException {
		try {
			if (!jsonObject.has(key)) {
				return Optional.empty();
			}
			var json = jsonObject.getJSONObject(key);
			// TODO: add parent
			var mapped = mapper.map(new JsonObjectReader(json, addressing));
			return Optional.of(mapped);
		} catch (JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}

	public <T> T getJsonObject(String key, JsonObjectMapper<T> mapper) throws InvalidParametersException {
		try {
			var json = jsonObject.getJSONObject(key);
			return mapper.map(new JsonObjectReader(json, addressing));
		} catch (JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}

	public JsonObjectReader getJsonObject(String key) throws InvalidParametersException {
		try {
			var json = jsonObject.getJSONObject(key);
			// TODO: add parent
			return new JsonObjectReader(json, addressing);
		} catch (JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}

	public REAddr getResource(String key) throws InvalidParametersException {
		try {
			var rriString = jsonObject.getString(key);
			return addressing.get().forResources().parse2(rriString).getSecond();
		} catch (DeserializeException | JSONException e) {
			throw new InvalidParametersException("/" + key, Throwables.getRootCause(e));
		}
	}

	public Optional<REAddr> getOptResource(String key) throws InvalidParametersException {
		if (!jsonObject.has(key)) {
			return Optional.empty();
		}
		return Optional.of(getResource(key));
	}

	public Optional<ECPublicKey> tryValidatorIdentifier(String key) throws InvalidParametersException {
		try {
			var validatorAddress = jsonObject.getString(key);
			if (!validatorAddress.startsWith(addressing.get().forValidators().getHrp())) {
				return Optional.empty();
			}
			return Optional.of(addressing.get().forValidators().parse(validatorAddress));
		} catch (DeserializeException | JSONException e) {
			throw new InvalidParametersException("/" + key, Throwables.getRootCause(e));
		}
	}

	public ECPublicKey getValidatorIdentifier(String key) throws InvalidParametersException {
		try {
			var validatorIdentifier = jsonObject.getString(key);
			return addressing.get().forValidators().parse(validatorIdentifier);
		} catch (DeserializeException | JSONException e) {
			throw new InvalidParametersException("/" + key, Throwables.getRootCause(e));
		}
	}

	public AID getTransactionIdentifier(String key) throws InvalidParametersException {
		try {
			var txnIdString = jsonObject.getString(key);
			return AID.from(txnIdString);
		} catch (IllegalArgumentException | JSONException e) {
			throw new InvalidParametersException("/" + key, e);
		}
	}
}
