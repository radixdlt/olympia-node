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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.client.api.PreparedTransaction;
import com.radixdlt.client.api.TransactionAction;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.transaction.TokenFeeChecker;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.radixdlt.atom.actions.ActionErrors.DIFFERENT_SOURCE_ADDRESSES;
import static com.radixdlt.atom.actions.ActionErrors.EMPTY_TRANSACTIONS_NOT_SUPPORTED;
import static com.radixdlt.atom.actions.ActionErrors.SUBMISSION_FAILURE;
import static com.radixdlt.atom.actions.ActionErrors.TRANSACTION_ADDRESS_DOES_NOT_MATCH;
import static com.radixdlt.client.api.ApiErrors.UNABLE_TO_PREPARE_TX;

public final class SubmissionService {
	private final Logger logger = LogManager.getLogger();
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
			return addresses.size() == 0
				   ? EMPTY_TRANSACTIONS_NOT_SUPPORTED.result()
				   : DIFFERENT_SOURCE_ADDRESSES.result();
		}

		var addr = addresses.iterator().next();

		return Result.wrap(
			UNABLE_TO_PREPARE_TX,
			() -> radixEngine
				.construct(toActionsAndFee(addr, steps))
				.buildForExternalSign()
				.map(this::toPreparedTx)
		);
	}

	private List<TxAction> toActionsAndFee(REAddr addr, List<TransactionAction> steps) {
		return Stream.concat(
			steps.stream().map(TransactionAction::toAction),
			Stream.of(new BurnToken(REAddr.ofNativeToken(), addr, TokenFeeChecker.FIXED_FEE))
		).collect(Collectors.toList());
	}

	public Result<AID> calculateTxId(byte[] blob, ECDSASignature recoverable) {
		return Result.ok(TxLowLevelBuilder.newBuilder(blob).sig(recoverable).build())
			.map(Txn::getId);
	}

	public Result<AID> submitTx(byte[] blob, ECDSASignature recoverable, AID txId) {
		var txn = TxLowLevelBuilder.newBuilder(blob).sig(recoverable).build();

		if (!txn.getId().equals(txId)) {
			return TRANSACTION_ADDRESS_DOES_NOT_MATCH.result();
		}

		var completableFuture = new CompletableFuture<MempoolAddSuccess>();
		var mempoolAdd = MempoolAdd.create(txn, completableFuture);

		this.mempoolAddEventDispatcher.dispatch(mempoolAdd);

		try {
			var success = completableFuture.get();
			return Result.ok(success.getTxn().getId());
		} catch (ExecutionException e) {
			logger.warn("Unable to fulfill submission request: " + txId.toJson() + ": ", e);
			return SUBMISSION_FAILURE.with(e.getMessage()).result();
		} catch (InterruptedException e) {
			// unrecoverable error, propagate
			throw new IllegalStateException(e);
		}
	}

	private PreparedTransaction toPreparedTx(byte[] first, HashCode second) {
		return PreparedTransaction.create(first, second.asBytes(), TokenFeeChecker.FIXED_FEE);
	}
}
