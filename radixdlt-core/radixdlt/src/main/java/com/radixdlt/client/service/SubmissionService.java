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

package com.radixdlt.client.service;

import com.google.inject.Inject;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.client.api.PreparedTransaction;
import com.radixdlt.client.api.TransactionAction;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.radixdlt.serialization.SerializationUtils.restore;

public class SubmissionService {
	private final UInt256 fixedFee = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(50));

	private final Serialization serialization;
	private final RRI nativeToken;
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	private final RadixEngineStateComputer stateComputer;
	private final BFTNode self;

	@Inject
	public SubmissionService(
		Serialization serialization,
		@NativeToken RRI nativeToken,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher,
		RadixEngineStateComputer stateComputer,
		@Self BFTNode self
	) {
		this.serialization = serialization;
		this.nativeToken = nativeToken;
		this.mempoolAddEventDispatcher = mempoolAddEventDispatcher;
		this.stateComputer = stateComputer;
		this.self = self;
	}

	public Result<PreparedTransaction> prepareTransaction(List<TransactionAction> steps, Optional<String> message) {
		var addresses = steps.stream().map(TransactionAction::getFrom).collect(Collectors.toSet());

		if (addresses.size() != 1) {
			return Result.fail("Source addresses in all actions must be the same");
		}

		try {
			var address = addresses.iterator().next();
			var actions = steps
				.stream()
				.map(step -> step.toAction(nativeToken))
				.collect(Collectors.toList());

			return Result.ok(stateComputer.getEngine()
								 .construct(address, actions)
								 .burn(nativeToken, fixedFee)
								 .message(message)
								 .buildForExternalSign()
								 .map(this::toPreparedTx));
		} catch (Exception e) {
			return Result.fail(e.getMessage());
		}
	}

	public Result<AID> calculateTxId(byte[] blob, ECDSASignature recoverable) {
		return restore(serialization, blob, Atom.class)
			.map(atom -> atom.toSigned(recoverable))
			.map(Txn::fromAtom)
			.map(Txn::getId);
	}

	public Result<AID> submitTx(byte[] blob, ECDSASignature recoverable, AID txId) {
		return restore(serialization, blob, Atom.class)
			.map(atom -> atom.toSigned(recoverable))
			.map(Txn::fromAtom)
			.filter(txn -> txn.getId().equals(txId), "Provided txID does not match provided transaction")
			.onSuccess(txn -> stateComputer.addToMempool(txn, self)).map(Txn::getId);
	}

	private PreparedTransaction toPreparedTx(byte[] first, com.google.common.hash.HashCode second) {
		return PreparedTransaction.create(first, second.asBytes(), fixedFee);
	}
}
