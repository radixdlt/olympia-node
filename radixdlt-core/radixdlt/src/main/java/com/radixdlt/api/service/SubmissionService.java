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

package com.radixdlt.api.service;

import com.radixdlt.atom.UnsignedTxnData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.radixdlt.api.data.PreparedTransaction;
import com.radixdlt.api.data.action.TransactionAction;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.constraintmachine.ConstraintMachineException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.radixdlt.api.data.ApiErrors.UNABLE_TO_PREPARE_TX;
import static com.radixdlt.atom.actions.ActionErrors.SUBMISSION_FAILURE;
import static com.radixdlt.atom.actions.ActionErrors.TRANSACTION_ADDRESS_DOES_NOT_MATCH;

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

	public Result<PreparedTransaction> prepareTransaction(
		REAddr address, List<TransactionAction> steps, Optional<String> message, boolean disableResourceAllocAndDestroy
	) {
		return Result.wrap(
			UNABLE_TO_PREPARE_TX,
			() -> radixEngine.construct(toConstructionRequest(address, steps, message, disableResourceAllocAndDestroy))
				.buildForExternalSign()
		).map(this::toPreparedTx);
	}

	private TxnConstructionRequest toConstructionRequest(
		REAddr feePayer,
		List<TransactionAction> steps,
		Optional<String> message,
		boolean disableResourceAllocAndDestroy
	) {
		var txnConstructionRequest = TxnConstructionRequest.create().feePayer(feePayer);
		if (disableResourceAllocAndDestroy) {
			txnConstructionRequest.disableResourceAllocAndDestroy();
		}
		steps.stream().flatMap(TransactionAction::toAction).forEach(txnConstructionRequest::action);
		message.map(t -> t.getBytes(RadixConstants.STANDARD_CHARSET)).ifPresent(txnConstructionRequest::msg);
		return txnConstructionRequest;
	}

	public Result<AID> calculateTxId(byte[] blob, ECDSASignature recoverable) {
		return Result.ok(buildTxn(blob, recoverable)).map(Txn::getId);
	}

	public Result<AID> submitTx(byte[] blob, ECDSASignature recoverable, AID txId) {
		var txn = buildTxn(blob, recoverable);

		if (!txn.getId().equals(txId)) {
			return TRANSACTION_ADDRESS_DOES_NOT_MATCH.result();
		}

		return submit(txn);
	}

	private Result<AID> submit(Txn txn) {
		var completableFuture = new CompletableFuture<MempoolAddSuccess>();
		var mempoolAdd = MempoolAdd.create(txn, completableFuture);

		mempoolAddEventDispatcher.dispatch(mempoolAdd);

		try {
			var success = completableFuture.get();
			return Result.ok(success.getTxn().getId());
		} catch (ExecutionException e) {
			var cause = lookupCause(e);

			logger.warn("Unable to fulfill submission request for TxID (" + txn.getId() + ")", e);
			return SUBMISSION_FAILURE.with(cause.getMessage()).result();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private Throwable lookupCause(Throwable e) {
		var reportedException = e;

		while (reportedException.getCause() instanceof MempoolRejectedException) {
			reportedException = reportedException.getCause();
		}

		while (reportedException.getCause() instanceof RadixEngineException) {
			reportedException = reportedException.getCause();
		}

		while (reportedException.getCause() instanceof ConstraintMachineException) {
			reportedException = reportedException.getCause();
		}

		if (reportedException instanceof  ConstraintMachineException && reportedException.getCause() != null) {
			reportedException = reportedException.getCause();
		}

		return reportedException;
	}

	private Txn buildTxn(byte[] blob, ECDSASignature recoverable) {
		return TxLowLevelBuilder.newBuilder(blob).sig(recoverable).build();
	}

	public Result<AID> oneStepSubmit(
		REAddr address, List<TransactionAction> steps,
		Optional<String> message, HashSigner signer, boolean disableResourceAllocAndDestroy
	) {
		return prepareTransaction(address, steps, message, disableResourceAllocAndDestroy)
			.onFailure(failure -> logger.error("Error preparing transaction {}", failure))
			.map(prepared -> buildTxn(prepared.getBlob(), signer.sign(prepared.getHashToSign())))
			.flatMap(this::submit);
	}

	private PreparedTransaction toPreparedTx(UnsignedTxnData unsignedTxnData) {
		return PreparedTransaction.create(unsignedTxnData.blob(), unsignedTxnData.hashToSign().asBytes(), unsignedTxnData.feesPaid());
	}
}
