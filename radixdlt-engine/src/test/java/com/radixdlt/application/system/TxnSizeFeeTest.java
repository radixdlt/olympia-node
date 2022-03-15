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

package com.radixdlt.application.system;

import static com.radixdlt.atom.TxAction.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.application.system.construction.CreateSystemConstructorV2;
import com.radixdlt.application.system.construction.FeeReserveCompleteConstructor;
import com.radixdlt.application.system.construction.FeeReservePutConstructor;
import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.tokens.state.AccountBucket;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.atom.*;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.exceptions.DefaultedSystemLoanException;
import com.radixdlt.constraintmachine.exceptions.DepletedFeeReserveException;
import com.radixdlt.constraintmachine.exceptions.ExecutionContextDestroyException;
import com.radixdlt.constraintmachine.meter.TxnSizeFeeMeter;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bouncycastle.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TxnSizeFeeTest {
  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    return List.of(
        new Object[] {Amount.ofSubunits(UInt256.ONE)},
        new Object[] {Amount.ofSubunits(UInt256.TWO)},
        new Object[] {Amount.ofMicroTokens(2)});
  }

  private RadixEngine<Void> engine;
  private EngineStore<Void> store;
  private final ECKeyPair key = ECKeyPair.generateNew();
  private final REAddr accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
  private final Amount costPerByte;
  private static final long MAX_SIZE = 507;

  public TxnSizeFeeTest(Amount costPerByte) {
    this.costPerByte = costPerByte;
  }

  @Before
  public void setup() throws Exception {
    var cmAtomOS = new CMAtomOS();
    cmAtomOS.load(new TokensConstraintScryptV3(Set.of(), Pattern.compile("[a-z0-9]+")));
    cmAtomOS.load(new SystemConstraintScrypt());
    var cm =
        new ConstraintMachine(
            cmAtomOS.getProcedures(),
            cmAtomOS.buildSubstateDeserialization(),
            cmAtomOS.buildVirtualSubstateDeserialization(),
            TxnSizeFeeMeter.create(costPerByte.toSubunits(), MAX_SIZE));
    var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
    var serialization = cmAtomOS.buildSubstateSerialization();
    this.store = new InMemoryEngineStore<>();
    this.engine =
        new RadixEngine<>(
            parser,
            serialization,
            REConstructor.newBuilder()
                .put(CreateSystem.class, new CreateSystemConstructorV2())
                .put(TransferToken.class, new TransferTokensConstructorV2())
                .put(
                    CreateMutableToken.class,
                    new CreateMutableTokenConstructor(SystemConstraintScrypt.MAX_SYMBOL_LENGTH))
                .put(MintToken.class, new MintTokenConstructor())
                .put(FeeReservePut.class, new FeeReservePutConstructor())
                .put(
                    FeeReserveComplete.class,
                    new FeeReserveCompleteConstructor(FeeTable.create(costPerByte, Map.of())))
                .build(),
            cm,
            store);
    var txn =
        this.engine
            .construct(
                TxnConstructionRequest.create()
                    .action(new CreateSystem(0))
                    .action(
                        new CreateMutableToken(
                            REAddr.ofNativeToken(), "xrd", "xrd", "", "", "", null))
                    .action(
                        new MintToken(
                            REAddr.ofNativeToken(), accountAddr, Amount.ofTokens(2).toSubunits())))
            .buildWithoutSignature();
    this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);
  }

  @Test
  public void transaction_thats_over_allowed_size_should_fail() throws Exception {
    // Act
    var nextKey = ECKeyPair.generateNew();
    var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
    var transfer =
        this.engine
            .construct(
                TxnConstructionRequest.create()
                    .action(
                        new FeeReservePut(
                            accountAddr,
                            costPerByte.toSubunits().multiply(UInt256.from(MAX_SIZE + 1))))
                    .action(new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.ONE))
                    .action(
                        new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.ONE)))
            .signAndBuild(key::sign);

    assertThat(transfer.getPayload().length).isEqualTo(MAX_SIZE + 1);

    // Act
    assertThatThrownBy(() -> this.engine.execute(List.of(transfer)))
        .hasRootCauseInstanceOf(DepletedFeeReserveException.class);
  }

  @Test
  public void paying_for_fees_should_work() throws Exception {
    var expectedTxnSize = 360;
    // Act
    var nextKey = ECKeyPair.generateNew();
    var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
    var transfer =
        this.engine
            .construct(
                TxnConstructionRequest.create()
                    .action(
                        new FeeReservePut(
                            accountAddr,
                            costPerByte.toSubunits().multiply(UInt256.from(expectedTxnSize))))
                    .action(
                        new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.FIVE)))
            .signAndBuild(key::sign);

    assertThat(transfer.getPayload().length).isEqualTo(expectedTxnSize);

    // Act
    var result = this.engine.execute(List.of(transfer));
  }

  @Test
  public void pay_just_fees_should_not_fail() throws Exception {
    var expectedTxnSize = 212;
    // Act
    var fee = costPerByte.toSubunits().multiply(UInt256.from(expectedTxnSize));
    var txn =
        this.engine
            .construct(
                txBuilder -> {
                  var buf = ByteBuffer.allocate(2 + 1 + ECPublicKey.COMPRESSED_BYTES);
                  buf.put(SubstateTypeId.TOKENS.id());
                  buf.put((byte) 0);
                  buf.put(accountAddr.getBytes());
                  var index = SubstateIndex.create(buf.array(), TokensInAccount.class);
                  // Take
                  var remainder =
                      txBuilder.downFungible(
                          index,
                          p ->
                              p.resourceAddr().isNativeToken()
                                  && p.holdingAddress().equals(accountAddr),
                          fee,
                          available -> {
                            var from = AccountBucket.from(REAddr.ofNativeToken(), accountAddr);
                            return new NotEnoughResourcesException(from, fee, available);
                          });
                  txBuilder.toLowLevelBuilder().syscall(Syscall.FEE_RESERVE_PUT, fee.toByteArray());
                  if (!remainder.isZero()) {
                    txBuilder.up(
                        new TokensInAccount(accountAddr, REAddr.ofNativeToken(), remainder));
                  }
                  txBuilder.end();
                })
            .signAndBuild(key::sign);
    assertThat(txn.getPayload().length).isEqualTo(expectedTxnSize);

    // Act
    var result = this.engine.execute(List.of(txn));
    assertThat(result.getProcessedTxn().getFeePaid()).isEqualTo(fee);
  }

  @Test
  public void adding_extra_bytes_to_call_data_should_fail() throws Exception {
    var expectedTxnSize = 213;
    // Act
    var fee = costPerByte.toSubunits().multiply(UInt256.from(expectedTxnSize));
    var txn =
        this.engine
            .construct(
                txBuilder -> {
                  var buf = ByteBuffer.allocate(2 + 1 + ECPublicKey.COMPRESSED_BYTES);
                  buf.put(SubstateTypeId.TOKENS.id());
                  buf.put((byte) 0);
                  buf.put(accountAddr.getBytes());
                  var index = SubstateIndex.create(buf.array(), TokensInAccount.class);
                  // Take
                  var remainder =
                      txBuilder.downFungible(
                          index,
                          p ->
                              p.resourceAddr().isNativeToken()
                                  && p.holdingAddress().equals(accountAddr),
                          fee,
                          available -> {
                            var from = AccountBucket.from(REAddr.ofNativeToken(), accountAddr);
                            return new NotEnoughResourcesException(from, fee, available);
                          });

                  var data = new byte[Short.BYTES + 1 + UInt256.BYTES + 1];
                  data[0] = 0;
                  data[1] = (byte) (1 + UInt256.BYTES + 1);
                  data[2] = Syscall.FEE_RESERVE_PUT.id();
                  System.arraycopy(
                      Arrays.concatenate(fee.toByteArray(), new byte[1]),
                      0,
                      data,
                      3,
                      UInt256.BYTES + 1);
                  txBuilder.toLowLevelBuilder().instruction(REInstruction.REMicroOp.SYSCALL, data);
                  if (!remainder.isZero()) {
                    txBuilder.up(
                        new TokensInAccount(accountAddr, REAddr.ofNativeToken(), remainder));
                  }
                  txBuilder.end();
                })
            .signAndBuild(key::sign);
    assertThat(txn.getPayload().length).isEqualTo(expectedTxnSize);

    // Act
    assertThatThrownBy(() -> this.engine.execute(List.of(txn)))
        .isInstanceOf(RadixEngineException.class);
  }

  @Test
  public void paying_for_fees_should_work_2() throws Exception {
    var expectedTxnSize = 360;
    // Act
    var nextKey = ECKeyPair.generateNew();
    var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
    var transfer =
        this.engine
            .construct(
                TxnConstructionRequest.create()
                    .action(
                        new FeeReservePut(
                            accountAddr,
                            costPerByte.toSubunits().multiply(UInt256.from(expectedTxnSize))))
                    .action(
                        new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.FIVE))
                    .action(new FeeReserveComplete(accountAddr)))
            .signAndBuild(key::sign);

    assertThat(transfer.getPayload().length).isEqualTo(expectedTxnSize);

    // Act
    var result = this.engine.execute(List.of(transfer));
    REResourceAccounting.compute(result.getProcessedTxn().getGroupedStateUpdates().get(0).stream());
  }

  @Test
  public void paying_too_little_fees_should_fail() throws Exception {
    // Arrange
    var nextKey = ECKeyPair.generateNew();
    var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
    var transfer =
        this.engine
            .construct(
                TxnConstructionRequest.create()
                    .action(new FeeReservePut(accountAddr, UInt256.THREE))
                    .action(
                        new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.FIVE)))
            .signAndBuild(key::sign);

    // Act
    assertThatThrownBy(() -> this.engine.execute(List.of(transfer)))
        .hasRootCauseInstanceOf(DefaultedSystemLoanException.class);
  }

  @Test
  public void paying_too_much_in_fees_should_fail() throws Exception {
    // Arrange
    var nextKey = ECKeyPair.generateNew();
    var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
    var transfer =
        this.engine
            .construct(
                TxnConstructionRequest.create()
                    .action(new FeeReservePut(accountAddr, Amount.ofTokens(1).toSubunits()))
                    .action(
                        new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.TWO)))
            .signAndBuild(key::sign);

    // Act
    assertThatThrownBy(() -> this.engine.execute(List.of(transfer)))
        .hasRootCauseInstanceOf(ExecutionContextDestroyException.class);
  }

  @Test
  public void put_then_take_reserve_should_work() throws Exception {
    // Arrange
    var nextKey = ECKeyPair.generateNew();
    var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
    var transfer =
        this.engine
            .construct(
                TxnConstructionRequest.create()
                    .action(new FeeReservePut(accountAddr, Amount.ofTokens(1).toSubunits()))
                    .action(new TransferToken(REAddr.ofNativeToken(), accountAddr, to, UInt256.TWO))
                    .action(new FeeReserveComplete(accountAddr)))
            .signAndBuild(key::sign);

    var expectedFee = costPerByte.toSubunits().multiply(UInt256.from(transfer.getPayload().length));
    var expectedRefund =
        new BigInteger(1, Amount.ofTokens(1).toSubunits().subtract(expectedFee).toByteArray());

    // Act
    var result = this.engine.execute(List.of(transfer));
    var refund =
        REResourceAccounting.compute(
                result.getProcessedTxn().getGroupedStateUpdates().get(2).stream())
            .bucketAccounting()
            .get(AccountBucket.from(REAddr.ofNativeToken(), accountAddr));
    assertThat(refund).isEqualTo(expectedRefund);
  }
}
