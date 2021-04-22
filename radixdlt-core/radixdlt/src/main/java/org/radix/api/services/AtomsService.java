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

package org.radix.api.services;

import org.bouncycastle.util.encoders.Hex;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.identifiers.AID;
import com.radixdlt.store.TxnIndex;
import com.radixdlt.utils.functional.Result;

import java.util.Objects;

import static com.radixdlt.utils.functional.Result.fromOptional;

public class AtomsService {
	private final TxnIndex store;

	@Inject
	public AtomsService(TxnIndex store) {
		this.store = Objects.requireNonNull(store);
	}

	public Result<JSONObject> getAtomByAtomId(AID txnId) throws JSONException {
		return fromOptional(store.get(txnId), "Atom with AID {0} not found", txnId)
			.map(txn -> new JSONObject().put("tx", Hex.toHexString(txn.getPayload())));
	}
}
