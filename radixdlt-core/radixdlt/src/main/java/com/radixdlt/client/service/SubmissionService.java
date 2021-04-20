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

import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.client.api.PreparedTransaction;
import com.radixdlt.client.api.TransactionAction;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.Rri;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public final class SubmissionService {
	private final UInt256 fixedFee = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(50));
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;

	@Inject
	public SubmissionService(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher
	) {
		this.radixEngine = radixEngine;
		this.mempoolAddEventDispatcher = mempoolAddEventDispatcher;
	}

	public Result<PreparedTransaction> prepareTransaction(List<TransactionAction> steps) {
		var addresses = steps.stream().map(TransactionAction::getFrom)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());

		if (addresses.size() != 1) {
			return Result.fail("Source addresses for all actions must be the same");
		}

		var address = addresses.iterator().next();

		try {
			var transaction = radixEngine
				.construct(address, toActions(steps))
				.burn(Rri.NATIVE_TOKEN, fixedFee)
				.buildForExternalSign()
				.map(this::toPreparedTx);

			return Result.ok(transaction);
		} catch (Exception e) {
			return Result.fail(e.getMessage());
		}
	}

	private List<TxAction> toActions(List<TransactionAction> steps) {
		return steps.stream()
			.map(TransactionAction::toAction)
			.collect(Collectors.toList());
	}

	public Result<AID> calculateTxId(byte[] blob, ECDSASignature recoverable) {
		return Result.ok(TxLowLevelBuilder.newBuilder(blob).sig(recoverable).build())
			.map(Txn::getId);
	}

	public Result<AID> submitTx(byte[] blob, ECDSASignature recoverable, AID txId) {
		var txn = TxLowLevelBuilder.newBuilder(blob).sig(recoverable).build();
		if (!txn.getId().equals(txId)) {
			return Result.fail("Provided txID does not match provided transaction");
		}

		var completableFuture = new CompletableFuture<MempoolAddSuccess>();
		var mempoolAdd = MempoolAdd.create(txn, completableFuture);
		this.mempoolAddEventDispatcher.dispatch(mempoolAdd);

		try {
			var success = completableFuture.get();
			return Result.ok(success.getTxn().getId());
		} catch (ExecutionException | InterruptedException e) {
			return Result.fail(e);
		}
	}

	private PreparedTransaction toPreparedTx(byte[] first, HashCode second) {
		return PreparedTransaction.create(first, second.asBytes(), fixedFee);
	}
}
