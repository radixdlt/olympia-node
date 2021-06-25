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

package com.radixdlt.universe;

import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Bytes;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public final class Universe {
	private final int networkId;
	private final VerifiedTxnsAndProof genesis;

	public Universe(int networkId, VerifiedTxnsAndProof genesis) {
		this.networkId = networkId;
		this.genesis = genesis;
	}

	public int getNetworkId() {
		return networkId;
	}

	public VerifiedTxnsAndProof getGenesis() {
		return genesis;
	}

	public JSONObject asJSON() {
		var txns = new JSONArray();
		genesis.getTxns().forEach(txn -> txns.put(Bytes.toHexString(txn.getPayload())));
		return new JSONObject()
			.put("networkId", networkId)
			.put("proof", genesis.getProof().asJSON())
			.put("txns", txns);
	}

	public static Universe fromJSON(JSONObject jsonObject) throws DeserializeException {
		var networkId = jsonObject.getInt("networkId");
		var txnArray = jsonObject.getJSONArray("txns");
		var txns = new ArrayList<Txn>();
		for (int i = 0; i < txnArray.length(); i++) {
			var txn = Txn.create(Bytes.fromHexString(txnArray.getString(i)));
			txns.add(txn);
		}
		var proof = LedgerProof.fromJSON(jsonObject.getJSONObject("proof"));
		return new Universe(networkId, VerifiedTxnsAndProof.create(txns, proof));
	}
}
