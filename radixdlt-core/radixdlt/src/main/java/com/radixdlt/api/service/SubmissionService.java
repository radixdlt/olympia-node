/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
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

package com.radixdlt.api.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.api.data.ApiErrors;
import com.radixdlt.api.data.PreparedTransaction;
import com.radixdlt.api.data.action.ResourceAction;
import com.radixdlt.api.data.action.TransactionAction;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.UnsignedTxnData;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.constraintmachine.exceptions.ConstraintMachineException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.data.ApiErrors.MUST_MATCH_TX_ID;
import static com.radixdlt.api.data.ApiErrors.UNABLE_TO_SUBMIT_TX;
import static com.radixdlt.identifiers.CommonErrors.OPERATION_INTERRUPTED;

public final class SubmissionService {
	private final Logger logger = LogManager.getLogger();
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	private final Addressing addressing;

	@Inject
	public SubmissionService(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher,
		Addressing addressing
	) {
		this.radixEngine = radixEngine;
		this.mempoolAddEventDispatcher = mempoolAddEventDispatcher;
		this.addressing = addressing;
	}

	public Result<PreparedTransaction> prepareTransaction(
		REAddr address, List<TransactionAction> steps, Optional<String> message, boolean disableResourceAllocAndDestroy
	) {
		return Result.wrap(
			ApiErrors.UNABLE_TO_PREPARE_TX,
			() -> radixEngine.construct(toConstructionRequest(address, steps, message, disableResourceAllocAndDestroy))
				.buildForExternalSign()
		).map(unsignedTxnData -> toPreparedTx(unsignedTxnData, steps));
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

	public Result<Txn> finalizeTxn(byte[] blob, ECDSASignature recoverable, boolean immediateSubmit) {
		return Result.ok(buildTxn(blob, recoverable)).flatMap(txn -> submitNow(txn, immediateSubmit));
	}

	private Result<Txn> submitNow(Txn txn, boolean immediateSubmit) {
		return immediateSubmit ? submit(txn) : Result.ok(txn);
	}

	public Result<Txn> submitTx(byte[] blob, Optional<AID> txId) {
		var txn = TxLowLevelBuilder.newBuilder(blob).build();

		if (!sameTxId(txId, txn.getId())) {
			return MUST_MATCH_TX_ID.result();
		}

		return submit(txn);
	}

	private boolean sameTxId(Optional<AID> txId, AID newId) {
		return txId.map(newId::equals).orElse(true);
	}

	private Result<Txn> submit(Txn txn) {
		var completableFuture = new CompletableFuture<MempoolAddSuccess>();
		var mempoolAdd = MempoolAdd.create(txn, completableFuture);

		mempoolAddEventDispatcher.dispatch(mempoolAdd);

		try {
			var success = completableFuture.get();
			return Result.ok(success.getTxn());
		} catch (ExecutionException e) {
			logger.warn("Unable to fulfill submission request for TxID (" + txn.getId() + ")", e);
			return lookupCause(e).result();
		} catch (InterruptedException e) {
			logger.warn("Unexpected InterruptedException", e);
			Thread.currentThread().interrupt();
			return OPERATION_INTERRUPTED.with("transaction Id", txn.getId()).result();
		}
	}

	private Failure lookupCause(Throwable e) {
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

		if (reportedException instanceof ConstraintMachineException && reportedException.getCause() != null) {
			reportedException = reportedException.getCause();
		}

		return UNABLE_TO_SUBMIT_TX.with(reportedException.getMessage());
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
			.flatMap(this::submit)
			.map(Txn::getId);
	}

	@SuppressWarnings("UnstableApiUsage")
	private PreparedTransaction toPreparedTx(UnsignedTxnData unsignedTxnData, List<TransactionAction> steps) {
		return PreparedTransaction.create(
			unsignedTxnData.blob(),
			unsignedTxnData.hashToSign().asBytes(),
			unsignedTxnData.feesPaid(),
			extractNotifications(steps)
		);
	}

	private List<JSONObject> extractNotifications(List<TransactionAction> steps) {
		var list = new ArrayList<JSONObject>();

		steps.forEach(step -> processStep(step).ifPresent(list::add));

		return list;
	}

	private Optional<JSONObject> processStep(TransactionAction step) {
		if (step instanceof ResourceAction) {
			var resourceAction = (ResourceAction) step;
			var rri = addressing.forResources().of(resourceAction.getSymbol(), resourceAction.getAddress());
			return Optional.of(jsonObject()
								   .put("type", "TokenCreate")
								   .put("symbol", resourceAction.getSymbol())
								   .put("rri", rri));
		}

		return Optional.empty();
	}
}
