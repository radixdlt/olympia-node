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

package com.radixdlt.api.core;

import com.google.inject.Inject;
import com.radixdlt.api.ApiTest;
import com.radixdlt.api.core.handlers.ConstructionBuildHandler;
import com.radixdlt.api.core.handlers.ConstructionFinalizeHandler;
import com.radixdlt.api.core.handlers.ConstructionSubmitHandler;
import com.radixdlt.api.core.model.CoreModelMapper;
import com.radixdlt.api.core.openapitools.model.*;
import com.radixdlt.atom.Txn;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.hotstuff.HashSigner;
import com.radixdlt.hotstuff.bft.Self;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.qualifier.LocalSigner;
import com.radixdlt.statecomputer.RadixEngineMempool;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt256;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public final class ConstructionBuildResourceLargeSymbolTest extends ApiTest {
  @Inject private ConstructionBuildHandler buildHandler;
  @Inject private ConstructionFinalizeHandler finalizeHandler;
  @Inject private ConstructionSubmitHandler submitHandler;
  @Inject private CoreModelMapper coreModelMapper;
  @Inject @LocalSigner private HashSigner hashSigner;
  @Inject @Self private ECPublicKey self;
  @Inject private RadixEngineMempool mempool;

  private ConstructionBuildRequest buildTokenDefinition(
      EntityIdentifier tokenEntityIdentifier,
      String symbol,
      REAddr owner,
      int granularity,
      boolean isMutable) {
    var accountAddress = REAddr.ofPubKeyAccount(self);
    return new ConstructionBuildRequest()
        .networkIdentifier(new NetworkIdentifier().network("localnet"))
        .feePayer(coreModelMapper.entityIdentifier(accountAddress))
        .addOperationGroupsItem(
            new OperationGroup()
                .addOperationsItem(
                    new Operation()
                        .entityIdentifier(tokenEntityIdentifier)
                        .data(
                            new Data()
                                .action(Data.ActionEnum.CREATE)
                                .dataObject(
                                    new TokenData()
                                        .owner(
                                            owner == null
                                                ? null
                                                : coreModelMapper.entityIdentifier(owner))
                                        .granularity(Integer.toString(granularity))
                                        .isMutable(isMutable)
                                        .type("TokenData"))))
                .addOperationsItem(
                    new Operation()
                        .entityIdentifier(tokenEntityIdentifier)
                        .data(
                            new Data()
                                .action(Data.ActionEnum.CREATE)
                                .dataObject(
                                    new TokenMetadata()
                                        .symbol(symbol)
                                        .name("")
                                        .url("")
                                        .iconUrl("")
                                        .description("")
                                        .type("TokenMetadata")))));
  }

  private ConstructionBuildRequest buildMintOrBurn(
          ResourceIdentifier resourceIdentifier, UInt256 amount, boolean isMint, EntityIdentifier to) {
    var accountAddress = REAddr.ofPubKeyAccount(self);
    return new ConstructionBuildRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .feePayer(coreModelMapper.entityIdentifier(accountAddress))
            .addOperationGroupsItem(
                    new OperationGroup()
                            .addOperationsItem(
                                    new Operation()
                                            .entityIdentifier(to)
                                            .amount(
                                                    new ResourceAmount()
                                                            .resourceIdentifier(resourceIdentifier)
                                                            .value(isMint ? amount.toString() : "-" + amount.toString()))));
  }

  public Txn buildSignAndSubmit(ConstructionBuildRequest request) throws Exception {
    var buildResponse = handleRequestWithExpectedResponse(buildHandler, request, ConstructionBuildResponse.class);

    var payloadToSign = Bytes.fromHexString(buildResponse.getPayloadToSign());
    var unsignedTxnPayload = Bytes.fromHexString(buildResponse.getUnsignedTransaction());

    assertThat(payloadToSign).isNotNull();
    assertThat(unsignedTxnPayload).isNotNull();

    var sig = hashSigner.sign(payloadToSign);
    var derSignature = sig.toUnrecoverableDERBytes();

    var finalizeRequest =
            new ConstructionFinalizeRequest()
                    .networkIdentifier(new NetworkIdentifier().network("localnet"))
                    .unsignedTransaction(Bytes.toHexString(unsignedTxnPayload))
                    .signature(
                            new Signature()
                                    .bytes(Bytes.toHexString(derSignature))
                                    .publicKey(coreModelMapper.publicKey(self)));

    var finalizeResponse =
            handleRequestWithExpectedResponse(finalizeHandler, finalizeRequest, ConstructionFinalizeResponse.class);

    var signedTransactionBytes = Bytes.fromHexString(finalizeResponse.getSignedTransaction());
    var signedTransaction = Txn.create(signedTransactionBytes);

    assertThat(signedTransactionBytes).isNotNull();

    var submitRequest =
            new ConstructionSubmitRequest()
                    .networkIdentifier(networkIdentifier())
                    .signedTransaction(Bytes.toHexString(signedTransactionBytes));
    var submitResponse =
            handleRequestWithExpectedResponse(submitHandler, submitRequest, ConstructionSubmitResponse.class);

    // Assert
    assertThat(submitResponse.getTransactionIdentifier().getHash())
            .isEqualTo(signedTransaction.getId().toString());
    assertThat(mempool.getData(m -> m.containsKey(signedTransaction.getId())).booleanValue()).isTrue();

    return signedTransaction;
  }

  public ConstructionBuildRequest tokenCreateRequest(ECPublicKey publicKey, String symbol) {
    return buildTokenDefinition(
      coreModelMapper.entityIdentifier(REAddr.ofHashedKey(publicKey, symbol), symbol),
            symbol,
      REAddr.ofPubKeyAccount(publicKey),
      1,
      true);
  }

  @Test
  public void creating_a_new_token_definition_should_work() throws Exception {
    // Arrange
    start();

    var publicKey = self;
    var symbol = "01234567890123456789012345678901a";
    var accountAddress = REAddr.ofPubKeyAccount(publicKey);

    // Act
    var submittedTransaction = buildSignAndSubmit(tokenCreateRequest(publicKey, symbol));
    runUntilCommitted(submittedTransaction.getId());

    var resourceAddress = REAddr.ofHashedKey(publicKey, symbol);

    var txn2 = buildSignAndSubmit(buildMintOrBurn(
            coreModelMapper.create(resourceAddress, symbol),
            UInt256.ONE,
            true,
            coreModelMapper.entityIdentifier(accountAddress)
    ));
    runUntilCommitted(txn2.getId());
  }
}
