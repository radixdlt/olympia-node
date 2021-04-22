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

package org.radix.api.http;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.application.Balances;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.application.StakeReceived;
import com.radixdlt.application.StakedBalance;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.application.ValidatorInfo;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.MoveStake;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnregisterValidator;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.client.Rri;
import com.radixdlt.client.ValidatorAddress;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.RadixAddress;

import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.ImmutableIndex;
import com.radixdlt.utils.UInt256;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.radix.api.http.RestUtils.*;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public final class NodeController implements Controller {
	private static final UInt256 FEE = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(1000));
	private final REAddr nativeToken;
	private final RadixAddress selfAddress;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final ImmutableIndex immutableIndex;
	private final EventDispatcher<NodeApplicationRequest> nodeApplicationRequestEventDispatcher;
	private final ECPublicKey bftKey;

	@Inject
	public NodeController(
		@NativeToken REAddr nativeToken,
		@Self RadixAddress selfAddress,
		@Self ECPublicKey bftKey,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		ImmutableIndex immutableIndex,
		EventDispatcher<NodeApplicationRequest> nodeApplicationRequestEventDispatcher
	) {
		this.nativeToken = nativeToken;
		this.selfAddress = selfAddress;
		this.bftKey = bftKey;
		this.radixEngine = radixEngine;
		this.immutableIndex = immutableIndex;
		this.nodeApplicationRequestEventDispatcher = nodeApplicationRequestEventDispatcher;
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.post("/node/execute", this::handleExecute);
		handler.get("/node", this::respondWithNode);
		handler.post("/node/validator", this::respondWithValidator);
	}

	private JSONObject getValidator() {
		var stakeReceived = radixEngine.getComputedState(StakeReceived.class);
		var validatorInfo = radixEngine.getComputedState(ValidatorInfo.class);
		var stakeFrom = new JSONArray();
		stakeReceived.forEach((addr, amt) -> {
			stakeFrom.put(
				new JSONObject()
					.put("delegator", addr)
					.put("amount", TokenUnitConversions.subunitsToUnits(amt))
			);
		});
		return new JSONObject()
			.put("address", ValidatorAddress.of(bftKey))
			.put("registered", validatorInfo.isRegistered())
			.put("totalStake", TokenUnitConversions.subunitsToUnits(stakeReceived.getTotal()))
			.put("stakes", stakeFrom);
	}

	private JSONObject getBalance() {
		var balances = radixEngine.getComputedState(Balances.class);
		var stakedBalance = radixEngine.getComputedState(StakedBalance.class);
		var stakeTo = new JSONArray();
		stakedBalance.forEach((addr, amt) ->
			stakeTo.put(
				new JSONObject()
					.put("delegate", addr)
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
			.put("address", selfAddress)
			.put("balance", getBalance()));
	}

	@VisibleForTesting
	void respondWithValidator(HttpServerExchange exchange) {
		respond(exchange, jsonObject()
			.put("validator", getValidator()));
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
				return new CreateFixedToken(symbol, name, description, iconUrl, url, supply);
			}
			case "TransferTokens": {
				var rri = Rri.parse(paramsObject.getString("rri"));
				var addressString = paramsObject.getString("to");
				var to = RadixAddress.from(addressString);
				var amountBigInt = paramsObject.getBigInteger("amount");
				var subunits = TokenUnitConversions.unitsToSubunits(new BigDecimal(amountBigInt));
				return new TransferToken(rri.getSecond(), to, subunits);
			}
			case "MintTokens": {
				var rri = Rri.parse(paramsObject.getString("rri"));
				var addressString = paramsObject.getString("to");
				var to = RadixAddress.from(addressString);
				var amountBigInt = paramsObject.getBigInteger("amount");
				var subunits = TokenUnitConversions.unitsToSubunits(new BigDecimal(amountBigInt));
				return new MintToken(rri.getSecond(), to, subunits);
			}
			case "BurnTokens": {
				var rri = Rri.parse(paramsObject.getString("rri"));
				var amountBigInt = paramsObject.getBigInteger("amount");
				var subunits = TokenUnitConversions.unitsToSubunits(new BigDecimal(amountBigInt));
				return new BurnToken(rri.getSecond(), subunits);
			}
			case "StakeTokens": {
				var validatorString = paramsObject.getString("to");
				var key = ValidatorAddress.parse(validatorString);
				var amountBigInt = paramsObject.getBigInteger("amount");
				var subunits = TokenUnitConversions.unitsToSubunits(new BigDecimal(amountBigInt));
				return new StakeTokens(key, subunits);
			}
			case "UnstakeTokens": {
				var addressString = paramsObject.getString("from");
				var delegate = ValidatorAddress.parse(addressString);
				var amountBigInt = paramsObject.getBigInteger("amount");
				var subunits = TokenUnitConversions.unitsToSubunits(new BigDecimal(amountBigInt));
				return new UnstakeTokens(delegate, subunits);
			}
			case "MoveStake": {
				var fromString = paramsObject.getString("from");
				var fromDelegate = ValidatorAddress.parse(fromString);
				var toString = paramsObject.getString("to");
				var toDelegate = ValidatorAddress.parse(toString);
				var amountBigInt = paramsObject.getBigInteger("amount");
				var subunits = TokenUnitConversions.unitsToSubunits(new BigDecimal(amountBigInt));
				return new MoveStake(fromDelegate, toDelegate, subunits);
			}
			case "RegisterAsValidator":
				return new RegisterValidator();
			case "UnregisterAsValidator":
				return new UnregisterValidator();
			default:
				throw new IllegalArgumentException("Bad action object: " + actionObject);
		}
	}

	void handleExecute(HttpServerExchange exchange) {
		// TODO: implement JSON-RPC 2.0 specification
		withBody(exchange, values -> {
			var actionsArray = values.getJSONArray("actions");
			var actions = new ArrayList<TxAction>();
			for (int i = 0; i < actionsArray.length(); i++) {
				var actionObject = actionsArray.getJSONObject(i);
				var txAction = parseAction(actionObject);
				actions.add(txAction);
			}
			actions.add(new BurnToken(nativeToken, FEE));
			var completableFuture = new CompletableFuture<MempoolAddSuccess>();
			var request = NodeApplicationRequest.create(actions, completableFuture);
			nodeApplicationRequestEventDispatcher.dispatch(request);

			try {
				var success = completableFuture.get();
				respond(exchange, jsonObject()
					.put("result", jsonObject()
						.put("transaction", Hex.toHexString(success.getTxn().getPayload()))
						.put("transaction_identifier", success.getTxn().getId().toString())
					)
				);
			} catch (ExecutionException e) {
				respond(exchange, jsonObject()
					.put("error", jsonObject()
					.put("message", e.getCause().getMessage()))
				);
			}
		});
	}
}
