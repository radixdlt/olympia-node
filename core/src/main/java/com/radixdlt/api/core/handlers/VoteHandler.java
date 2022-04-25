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

import static java.util.Objects.requireNonNull;

import com.google.inject.Inject;
import com.radixdlt.api.core.CoreJsonRpcHandler;
import com.radixdlt.api.core.model.CoreApiException;
import com.radixdlt.api.core.model.CoreModelMapper;
import com.radixdlt.api.core.model.NotEnoughNativeTokensForFeesException;
import com.radixdlt.api.core.openapitools.model.UpdateVoteRequest;
import com.radixdlt.api.core.openapitools.model.UpdateVoteResponse;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.keys.LocalSigner;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.forks.CandidateForkVote;
import com.radixdlt.statecomputer.forks.Forks;

public final class VoteHandler extends CoreJsonRpcHandler<UpdateVoteRequest, UpdateVoteResponse> {
  private final ECPublicKey validatorKey;
  private final CoreModelMapper coreModelMapper;
  private final Forks forks;
  private final RadixEngine<LedgerAndBFTProof> radixEngine;
  private final RadixEngineStateComputer radixEngineStateComputer;
  private final HashSigner hashSigner;

  @Inject
  VoteHandler(
      @Self ECPublicKey validatorKey,
      CoreModelMapper coreModelMapper,
      Forks forks,
      RadixEngine<LedgerAndBFTProof> radixEngine,
      RadixEngineStateComputer radixEngineStateComputer,
      @LocalSigner HashSigner hashSigner) {
    super(UpdateVoteRequest.class);

    this.validatorKey = requireNonNull(validatorKey);
    this.coreModelMapper = requireNonNull(coreModelMapper);
    this.forks = requireNonNull(forks);
    this.radixEngine = requireNonNull(radixEngine);
    this.radixEngineStateComputer = requireNonNull(radixEngineStateComputer);
    this.hashSigner = requireNonNull(hashSigner);
  }

  @Override
  public UpdateVoteResponse handleRequest(UpdateVoteRequest request) throws CoreApiException {
    coreModelMapper.verifyNetwork(request.getNetworkIdentifier());

    final Txn signedTx;
    try {
      signedTx =
          radixEngine
              .constructWithFees(
                  this::buildVote,
                  false,
                  REAddr.ofPubKeyAccount(validatorKey),
                  NotEnoughNativeTokensForFeesException::new)
              .signAndBuild(hashSigner::sign);
    } catch (TxBuilderException e) {
      throw CoreApiException.badRequest(coreModelMapper.builderErrorDetails(e));
    }

    try {
      radixEngineStateComputer.addToMempool(signedTx);
      return new UpdateVoteResponse()
          .transactionIdentifier(coreModelMapper.transactionIdentifier(signedTx.getId()))
          .duplicate(false);
    } catch (MempoolDuplicateException e) {
      return new UpdateVoteResponse()
          .transactionIdentifier(coreModelMapper.transactionIdentifier(signedTx.getId()))
          .duplicate(true);
    } catch (MempoolFullException e) {
      throw coreModelMapper.mempoolFullException(e);
    } catch (MempoolRejectedException e) {
      throw coreModelMapper.radixEngineException((RadixEngineException) e.getCause());
    }
  }

  private void buildVote(TxBuilder builder) {
    builder.down(ValidatorSystemMetadata.class, validatorKey);
    builder.up(
        new ValidatorSystemMetadata(
            validatorKey,
            forks
                .getCandidateFork()
                .map(
                    candidateFork ->
                        CandidateForkVote.create(validatorKey, candidateFork).payload())
                .orElseGet(HashUtils::zero256)
                .asBytes()));
    builder.end();
  }
}
