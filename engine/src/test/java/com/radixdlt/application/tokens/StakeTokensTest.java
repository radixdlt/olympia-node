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

package com.radixdlt.application.tokens;

import static com.radixdlt.atom.TxAction.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.application.system.construction.CreateSystemConstructorV2;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.construction.StakeTokensConstructorV3;
import com.radixdlt.application.tokens.scrypt.StakingConstraintScryptV4;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.tokens.state.AccountBucket;
import com.radixdlt.application.tokens.state.PreparedStakeBucket;
import com.radixdlt.application.validators.scrypt.ValidatorConstraintScryptV2;
import com.radixdlt.application.validators.scrypt.ValidatorUpdateOwnerConstraintScrypt;
import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StakeTokensTest {

  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    var startAmounts = List.of(10);
    var stakeAmounts = List.of(10, 6);
    var scrypts =
        List.of(
            Pair.of(
                List.of(
                    new SystemConstraintScrypt(),
                    new TokensConstraintScryptV3(Set.of(), Pattern.compile("[a-z0-9]+")),
                    new StakingConstraintScryptV4(Amount.ofTokens(10).toSubunits()),
                    new ValidatorConstraintScryptV2(),
                    new ValidatorUpdateOwnerConstraintScrypt()),
                new StakeTokensConstructorV3(Amount.ofTokens(10).toSubunits())));

    var parameters = new ArrayList<Object[]>();
    for (var scrypt : scrypts) {
      for (var startAmount : startAmounts) {
        for (var stakeAmount : stakeAmounts) {
          var param =
              new Object[] {startAmount, stakeAmount, scrypt.getFirst(), scrypt.getSecond()};
          parameters.add(param);
        }
      }
    }
    return parameters;
  }

  private RadixEngine<Void> engine;
  private EngineStore<Void> store;
  private final UInt256 startAmt;
  private final UInt256 stakeAmt;
  private final List<ConstraintScrypt> scrypts;
  private final ActionConstructor<StakeTokens> stakeTokensConstructor;

  public StakeTokensTest(
      long startAmt,
      long stakeAmt,
      List<ConstraintScrypt> scrypts,
      ActionConstructor<StakeTokens> stakeTokensConstructor) {
    this.startAmt = Amount.ofTokens(startAmt * 10).toSubunits();
    this.stakeAmt = Amount.ofTokens(stakeAmt * 10).toSubunits();
    this.scrypts = scrypts;
    this.stakeTokensConstructor = stakeTokensConstructor;
  }

  @Before
  public void setup() throws Exception {
    var cmAtomOS = new CMAtomOS();
    scrypts.forEach(cmAtomOS::load);
    var cm =
        new ConstraintMachine(
            cmAtomOS.getProcedures(),
            cmAtomOS.buildSubstateDeserialization(),
            cmAtomOS.buildVirtualSubstateDeserialization());
    var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
    var serialization = cmAtomOS.buildSubstateSerialization();
    this.store = new InMemoryEngineStore<>();
    this.engine =
        new RadixEngine<>(
            parser,
            serialization,
            REConstructor.newBuilder()
                .put(CreateSystem.class, new CreateSystemConstructorV2())
                .put(StakeTokens.class, stakeTokensConstructor)
                .put(
                    CreateMutableToken.class,
                    new CreateMutableTokenConstructor(SystemConstraintScrypt.MAX_SYMBOL_LENGTH))
                .put(MintToken.class, new MintTokenConstructor())
                .build(),
            cm,
            store);
    var genesis = this.engine.construct(new CreateSystem(0)).buildWithoutSignature();
    this.engine.execute(List.of(genesis), null, PermissionLevel.SYSTEM);
  }

  @Test
  public void stake_tokens() throws Exception {
    // Arrange
    var key = ECKeyPair.generateNew();
    var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
    var txn =
        this.engine
            .construct(
                TxnConstructionRequest.create()
                    .action(
                        new CreateMutableToken(
                            REAddr.ofNativeToken(), "xrd", "Name", "", "", "", null))
                    .action(new MintToken(REAddr.ofNativeToken(), accountAddr, startAmt)))
            .buildWithoutSignature();
    this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);

    // Act
    var transfer =
        this.engine
            .construct(new StakeTokens(accountAddr, key.getPublicKey(), stakeAmt))
            .signAndBuild(key::sign);
    var processed = this.engine.execute(List.of(transfer));
    var accounting =
        REResourceAccounting.compute(
            processed.getProcessedTxn().getGroupedStateUpdates().get(0).stream());
    assertThat(accounting.bucketAccounting())
        .hasSize(2)
        .containsEntry(
            AccountBucket.from(REAddr.ofNativeToken(), accountAddr),
            new BigInteger(-1, stakeAmt.toByteArray(), 0, UInt256.BYTES))
        .containsEntry(
            new PreparedStakeBucket(accountAddr, key.getPublicKey()),
            new BigInteger(1, stakeAmt.toByteArray(), 0, UInt256.BYTES));
  }

  @Test
  public void cannot_stake_others_tokens() throws Exception {
    // Arrange
    var key = ECKeyPair.generateNew();
    var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
    var tokenAddr = REAddr.ofHashedKey(key.getPublicKey(), "test");
    var txn =
        this.engine
            .construct(
                TxnConstructionRequest.create()
                    .action(
                        new CreateMutableToken(
                            REAddr.ofNativeToken(), "xrd", "Name", "", "", "", null))
                    .action(new MintToken(REAddr.ofNativeToken(), accountAddr, startAmt)))
            .buildWithoutSignature();
    this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);

    // Act
    var nextKey = ECKeyPair.generateNew();
    var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
    var transfer =
        this.engine
            .construct(new StakeTokens(accountAddr, key.getPublicKey(), stakeAmt))
            .signAndBuild(nextKey::sign);
    assertThatThrownBy(() -> this.engine.execute(List.of(transfer)))
        .hasRootCauseInstanceOf(AuthorizationException.class);
  }
}
