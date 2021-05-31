/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.api.node;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.application.Balances;
import com.radixdlt.application.MyValidator;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.application.MyStakedBalance;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.application.ValidatorInfo;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnregisterValidator;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atom.actions.UpdateValidator;
import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.client.Rri;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.REAddr;

import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.transaction.TokenFeeChecker;
import com.radixdlt.utils.UInt256;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;
import com.radixdlt.api.Controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.radixdlt.api.RestUtils.*;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public final class NodeController implements Controller {
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final EventDispatcher<NodeApplicationRequest> nodeApplicationRequestEventDispatcher;
	private final ECPublicKey bftKey;
	private final REAddr account;

	@Inject
	public NodeController(
		@Self REAddr account,
		@Self ECPublicKey bftKey,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		EventDispatcher<NodeApplicationRequest> nodeApplicationRequestEventDispatcher
	) {
		this.account = account;
		this.bftKey = bftKey;
		this.radixEngine = radixEngine;
		this.nodeApplicationRequestEventDispatcher = nodeApplicationRequestEventDispatcher;
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.post("/node/execute", this::handleExecute);
		handler.get("/node", this::respondWithNode);
		handler.post("/node/validator", this::respondWithValidator);
	}

	private JSONObject getValidator() {
		var myStakes = radixEngine.getComputedState(MyValidator.class);
		var validatorInfo = radixEngine.getComputedState(ValidatorInfo.class);
		var stakeFrom = new JSONArray();
		myStakes.forEach((addr, amt) -> {
			stakeFrom.put(
				new JSONObject()
					.put("delegator", AccountAddress.of(addr))
					.put("amount", TokenUnitConversions.subunitsToUnits(amt))
			);
		});
		return new JSONObject()
			.put("address", ValidatorAddress.of(bftKey))
			.put("name", validatorInfo.getName())
			.put("url", validatorInfo.getUrl())
			.put("registered", validatorInfo.isRegistered())
			.put("totalStake", TokenUnitConversions.subunitsToUnits(myStakes.getTotalStake()))
			.put("stakes", stakeFrom);
	}

	private JSONObject getBalance() {
		var balances = radixEngine.getComputedState(Balances.class);
		var stakedBalance = radixEngine.getComputedState(MyStakedBalance.class);
		var stakeTo = new JSONArray();
		stakedBalance.forEach((addr, amt) ->
			stakeTo.put(
				new JSONObject()
					.put("delegate", ValidatorAddress.of(addr))
					.put("amount", TokenUnitConversions.subunitsToUnits(amt))
			)
		);
		var balancesJson = new JSONObject();
		balances.forEach((rri, balance) -> {
			balancesJson.put(rri.toString(), TokenUnitConversions.subunitsToUnits(balance));
		});

		return new JSONObject()
			.put("balances", balancesJson)
			.put("staked", stakeTo);
	}

	@VisibleForTesting
	void respondWithNode(HttpServerExchange exchange) {
		respond(exchange, jsonObject()
			.put("address", AccountAddress.of(REAddr.ofPubKeyAccount(bftKey)))
			.put("balance", getBalance()));
	}

	@VisibleForTesting
	void respondWithValidator(HttpServerExchange exchange) {
		respond(exchange, jsonObject()
			.put("validator", getValidator()));
	}

	private UInt256 parseAmount(JSONObject o, String paramName) {
		final UInt256 subunits;
		var amt = o.get(paramName);
		if (amt instanceof Number) {
			var amountBigInt = o.getBigInteger(paramName);
			subunits = TokenUnitConversions.unitsToSubunits(new BigDecimal(amountBigInt));
		} else {
			var amtString = o.getString(paramName);
			subunits = UInt256.from(amtString);
		}
		return subunits;
	}

	private TxAction parseAction(JSONObject actionObject) throws IllegalArgumentException, DeserializeException {
		var actionString = actionObject.getString("action");
		var paramsObject = actionObject.getJSONObject("params");
		switch (actionString) {
			case "CreateMutableToken": {
				var symbol = paramsObject.getString("symbol");
				var name = paramsObject.getString("name");
				var description = paramsObject.getString("description");
				var iconUrl = paramsObject.getString("iconUrl");
				var url = paramsObject.getString("url");
				return new CreateMutableToken(symbol, name, description, iconUrl, url);
			}
			case "CreateFixedToken": {
				var symbol = paramsObject.getString("symbol");
				var name = paramsObject.getString("name");
				var description = paramsObject.getString("description");
				var iconUrl = paramsObject.getString("iconUrl");
				var url = paramsObject.getString("url");
				var supplyInteger = paramsObject.getBigInteger("supply");
				var supply = TokenUnitConversions.unitsToSubunits(new BigDecimal(supplyInteger));
				var reAddr = REAddr.ofHashedKey(bftKey, symbol);
				return new CreateFixedToken(
					reAddr,
					account,
					symbol,
					name,
					description,
					iconUrl,
					url,
					supply
				);
			}
			case "TransferTokens": {
				var rri = Rri.parse(paramsObject.getString("rri"));
				var addressString = paramsObject.getString("to");
				var to = AccountAddress.parse(addressString);
				var amt = parseAmount(paramsObject, "amount");
				return new TransferToken(rri.getSecond(), account, to, amt);
			}
			case "MintTokens": {
				var rri = Rri.parse(paramsObject.getString("rri"));
				var addressString = paramsObject.getString("to");
				var to = AccountAddress.parse(addressString);
				var amt = parseAmount(paramsObject, "amount");
				return new MintToken(rri.getSecond(), to, amt);
			}
			case "BurnTokens": {
				var rri = Rri.parse(paramsObject.getString("rri"));
				var amt = parseAmount(paramsObject, "amount");
				return new BurnToken(rri.getSecond(), account, amt);
			}
			case "StakeTokens": {
				var validatorString = paramsObject.getString("to");
				var key = ValidatorAddress.parse(validatorString);
				var amt = parseAmount(paramsObject, "amount");
				return new StakeTokens(account, key, amt);
			}
			case "UnstakeTokens": {
				var addressString = paramsObject.getString("from");
				var delegate = ValidatorAddress.parse(addressString);
				var amt = parseAmount(paramsObject, "amount");
				return new UnstakeTokens(account, delegate, amt);
			}
			case "RegisterValidator": {
				var name = paramsObject.has("name") ? paramsObject.getString("name") : null;
				var url = paramsObject.has("url") ? paramsObject.getString("url") : null;
				return new RegisterValidator(bftKey, name, url);
			}
			case "UnregisterValidator": {
				var name = paramsObject.has("name") ? paramsObject.getString("name") : null;
				var url = paramsObject.has("url") ? paramsObject.getString("url") : null;
				return new UnregisterValidator(bftKey, name, url);
			}
			case "UpdateValidator": {
				var name = paramsObject.has("name") ? paramsObject.getString("name") : null;
				var url = paramsObject.has("url") ? paramsObject.getString("url") : null;
				return new UpdateValidator(bftKey, name, url);
			}
			default:
				throw new IllegalArgumentException("Bad action object: " + actionObject);
		}
	}

	void handleExecute(HttpServerExchange exchange) {
		// TODO: implement JSON-RPC 2.0 specification
		withBody(exchange, values -> {
			try {
				var actionsArray = values.getJSONArray("actions");
				var actions = new ArrayList<TxAction>();
				for (int i = 0; i < actionsArray.length(); i++) {
					var actionObject = actionsArray.getJSONObject(i);
					var txAction = parseAction(actionObject);
					actions.add(txAction);
				}
				actions.add(new BurnToken(REAddr.ofNativeToken(), account, TokenFeeChecker.FIXED_FEE));
				var completableFuture = new CompletableFuture<MempoolAddSuccess>();
				var request = NodeApplicationRequest.create(actions, completableFuture);
				nodeApplicationRequestEventDispatcher.dispatch(request);

				var success = completableFuture.get();
				respond(exchange, jsonObject()
					.put("result", jsonObject()
						.put("transaction", Hex.toHexString(success.getTxn().getPayload()))
						.put("transaction_identifier", success.getTxn().getId().toString())
					)
				);
			} catch (ExecutionException | RuntimeException e) {
				respond(exchange, jsonObject()
					.put("error", jsonObject()
					.put("message", e.getCause().getMessage()))
				);
			}
		});
	}
}
