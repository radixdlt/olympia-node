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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.radixdlt.api.ApiTest;
import com.radixdlt.api.core.handlers.ConstructionParseHandler;
import com.radixdlt.api.core.model.CoreModelMapper;
import com.radixdlt.api.core.model.EntityOperation;
import com.radixdlt.api.core.model.NotEnoughNativeTokensForFeesException;
import com.radixdlt.api.core.model.OperationTxBuilder;
import com.radixdlt.api.core.model.ResourceOperation;
import com.radixdlt.api.core.model.TokenResource;
import com.radixdlt.api.core.model.entities.AccountVaultEntity;
import com.radixdlt.api.core.openapitools.model.ConstructionParseRequest;
import com.radixdlt.api.core.openapitools.model.ConstructionParseResponse;
import com.radixdlt.api.core.openapitools.model.NetworkIdentifier;
import com.radixdlt.api.core.openapitools.model.Operation;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.CurrentForkView;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.PrivateKeys;
import com.radixdlt.utils.UInt256;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

public class ConstructionParseTransferTest extends ApiTest {
  @Inject private ConstructionParseHandler sut;
  @Inject private CoreModelMapper coreModelMapper;
  @Inject @Self private ECPublicKey self;
  @Inject private RadixEngine<LedgerAndBFTProof> radixEngine;
  @Inject private CurrentForkView currentForkView;

  private UInt256 transferAmount() {
    return getLiquidAmount().toSubunits().subtract(Amount.ofTokens(1).toSubunits());
  }

  private byte[] buildUnsignedTransferTxn(REAddr from, REAddr to) throws Exception {
    final UInt256 toTransfer = transferAmount();
    var entityOperationGroups =
        List.of(
            List.of(
                EntityOperation.from(
                    new AccountVaultEntity(from),
                    ResourceOperation.withdraw(
                        new TokenResource("xrd", REAddr.ofNativeToken()), toTransfer)),
                EntityOperation.from(
                    new AccountVaultEntity(to),
                    ResourceOperation.deposit(
                        new TokenResource("xrd", REAddr.ofNativeToken()), toTransfer))));
    var operationTxBuilder = new OperationTxBuilder(null, entityOperationGroups, currentForkView);
    var builder =
        radixEngine.constructWithFees(
            operationTxBuilder, false, from, NotEnoughNativeTokensForFeesException::new);
    var unsignedTransaction = builder.buildForExternalSign();
    return unsignedTransaction.blob();
  }

  @Test
  public void parsing_transaction_with_transfer_should_have_proper_substates() throws Exception {
    // Arrange
    start();

    // Act
    var accountAddress = REAddr.ofPubKeyAccount(self);
    var otherAddress = REAddr.ofPubKeyAccount(PrivateKeys.ofNumeric(2).getPublicKey());
    var unsignedTxn = buildUnsignedTransferTxn(accountAddress, otherAddress);
    var request =
        new ConstructionParseRequest()
            .signed(false)
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .transaction(Bytes.toHexString(unsignedTxn));
    var response = handleRequestWithExpectedResponse(sut, request, ConstructionParseResponse.class);

    // Assert
    assertThat(response.getMetadata()).isNotNull();
    assertThat(response.getMetadata().getMessage()).isNull();
    var feeValue = new BigInteger(response.getMetadata().getFee().getValue());
    assertThat(feeValue).isGreaterThan(BigInteger.ZERO);
    var entityHoldings =
        response.getOperationGroups().stream()
            .flatMap(g -> g.getOperations().stream())
            .peek(
                op -> {
                  assertThat(op.getSubstate()).isNotNull();
                  assertThat(op.getSubstate().getSubstateIdentifier()).isNotNull();
                  assertThat(op.getAmount()).isNotNull();
                  assertThat(op.getAmount().getResourceIdentifier())
                      .isEqualTo(coreModelMapper.nativeToken());
                })
            .collect(
                Collectors.groupingBy(
                    Operation::getEntityIdentifier,
                    Collectors.mapping(
                        op -> new BigInteger(op.getAmount().getValue()),
                        Collectors.reducing(BigInteger.ZERO, BigInteger::add))));

    var accountEntityIdentifier = coreModelMapper.entityIdentifier(accountAddress);
    var otherEntityIdentifier = coreModelMapper.entityIdentifier(otherAddress);
    var transferAmount = new BigInteger(transferAmount().toString());
    var expectedChange = transferAmount.negate().subtract(feeValue);
    assertThat(entityHoldings)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(accountEntityIdentifier, expectedChange, otherEntityIdentifier, transferAmount));
  }
}
