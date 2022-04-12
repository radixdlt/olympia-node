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

package com.radixdlt.api.core.handlers;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.radixdlt.api.core.CoreJsonRpcHandler;
import com.radixdlt.api.core.model.CoreApiException;
import com.radixdlt.api.core.model.CoreModelMapper;
import com.radixdlt.api.core.openapitools.model.CommittedTransaction;
import com.radixdlt.api.core.openapitools.model.CommittedTransactionMetadata;
import com.radixdlt.api.core.openapitools.model.CommittedTransactionsRequest;
import com.radixdlt.api.core.openapitools.model.CommittedTransactionsResponse;
import com.radixdlt.api.core.openapitools.model.OperationGroup;
import com.radixdlt.api.core.openapitools.model.StateIdentifier;
import com.radixdlt.api.core.reconstruction.BerkeleyRecoverableProcessedTxnStore;
import com.radixdlt.api.core.reconstruction.RecoverableProcessedTxn;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.parser.ParsedTxn;
import com.radixdlt.engine.parser.exceptions.TxnParseException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.Bytes;

public final class TransactionsHandler
    extends CoreJsonRpcHandler<CommittedTransactionsRequest, CommittedTransactionsResponse> {
  private final Provider<RadixEngine<LedgerAndBFTProof>> radixEngineProvider;
  private final BerkeleyRecoverableProcessedTxnStore txnStore;
  private final BerkeleyLedgerEntryStore ledgerEntryStore;
  private final LedgerAccumulator ledgerAccumulator;
  private final CoreModelMapper coreModelMapper;

  @Inject
  TransactionsHandler(
      CoreModelMapper coreModelMapper,
      BerkeleyRecoverableProcessedTxnStore txnStore,
      BerkeleyLedgerEntryStore ledgerEntryStore,
      LedgerAccumulator ledgerAccumulator,
      Provider<RadixEngine<LedgerAndBFTProof>> radixEngineProvider) {
    super(CommittedTransactionsRequest.class);
    this.coreModelMapper = coreModelMapper;
    this.txnStore = txnStore;
    this.ledgerEntryStore = ledgerEntryStore;
    this.ledgerAccumulator = ledgerAccumulator;
    this.radixEngineProvider = radixEngineProvider;
  }

  private String symbol(REAddr tokenAddress) {
    var mapKey =
        SystemMapKey.ofResourceData(tokenAddress, SubstateTypeId.TOKEN_RESOURCE_METADATA.id());
    var substate = radixEngineProvider.get().read(reader -> reader.get(mapKey).orElseThrow());
    // TODO: This is a bit of a hack to require deserialization, figure out correct abstraction
    var tokenResourceMetadata = (TokenResourceMetadata) substate;
    return tokenResourceMetadata.symbol();
  }

  private CommittedTransaction construct(
      Txn txn, RecoverableProcessedTxn recoveryInfo, AccumulatorState accumulatorState) {
    var parser = radixEngineProvider.get().getParser();
    ParsedTxn parsedTxn;
    try {
      parsedTxn = parser.parse(txn);
    } catch (TxnParseException e) {
      throw new IllegalStateException("Could not parse already committed transaction", e);
    }

    var committedTransaction = new CommittedTransaction();
    recoveryInfo.recoverStateUpdates(parsedTxn).stream()
        .map(
            stateUpdateGroup -> {
              var operationGroup = new OperationGroup();
              stateUpdateGroup.stream()
                  .map(
                      stateUpdate -> {
                        var substateOperation = stateUpdate.recover(radixEngineProvider);
                        return coreModelMapper.operation(
                            substateOperation.getSubstate(),
                            substateOperation.getSubstateId(),
                            substateOperation.isBootUp(),
                            this::symbol);
                      })
                  .forEach(operationGroup::addOperationsItem);
              return operationGroup;
            })
        .forEach(committedTransaction::addOperationGroupsItem);

    var signedBy =
        parsedTxn
            .getPayloadHashAndSig()
            .map(
                hashAndSig -> {
                  var hash = hashAndSig.getFirst();
                  var sig = hashAndSig.getSecond();
                  return ECPublicKey.recoverFrom(hash, sig)
                      .orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "Invalid signature on already committed transaction"));
                });
    var transactionIdentifier = coreModelMapper.transactionIdentifier(txn.getId());

    return committedTransaction
        .committedStateIdentifier(coreModelMapper.stateIdentifier(accumulatorState))
        .metadata(
            new CommittedTransactionMetadata()
                .fee(coreModelMapper.nativeTokenAmount(parsedTxn.getFeePaid()))
                .message(parsedTxn.getMsg().map(Bytes::toHexString).orElse(null))
                .size(txn.getPayload().length)
                .hex(Bytes.toHexString(txn.getPayload()))
                .signedBy(signedBy.map(coreModelMapper::publicKey).orElse(null)))
        .transactionIdentifier(transactionIdentifier);
  }

  @Override
  public CommittedTransactionsResponse handleRequest(CommittedTransactionsRequest request)
      throws CoreApiException {
    coreModelMapper.verifyNetwork(request.getNetworkIdentifier());

    var stateIdentifier = coreModelMapper.partialStateIdentifier(request.getStateIdentifier());
    long stateVersion = stateIdentifier.getFirst();
    var accumulator = stateIdentifier.getSecond();
    var currentAccumulator =
        txnStore
            .getAccumulator(stateVersion)
            .orElseThrow(
                () ->
                    CoreApiException.notFound(
                        coreModelMapper.notFoundErrorDetails(request.getStateIdentifier())));
    if (accumulator != null) {
      var matchesInput = accumulator.equals(currentAccumulator);
      if (!matchesInput) {
        throw CoreApiException.notFound(
            coreModelMapper.notFoundErrorDetails(request.getStateIdentifier()));
      }
    }

    var limit = coreModelMapper.limit(request.getLimit());
    var recoverable = txnStore.get(stateVersion, limit);
    var accumulatorState = new AccumulatorState(stateVersion, currentAccumulator);
    var response = new CommittedTransactionsResponse();
    var txns = ledgerEntryStore.getCommittedTxns(stateVersion, recoverable.size());
    for (int i = 0; i < txns.size(); i++) {
      var txn = txns.get(i);
      var recoveryInfo = recoverable.get(i);
      var nextAccumulatorState =
          ledgerAccumulator.accumulate(accumulatorState, txn.getId().asHashCode());
      accumulatorState = nextAccumulatorState;
      response.addTransactionsItem(construct(txn, recoveryInfo, nextAccumulatorState));
    }

    return response.stateIdentifier(
        new StateIdentifier()
            .stateVersion(stateVersion)
            .transactionAccumulator(Bytes.toHexString(currentAccumulator.asBytes())));
  }
}
