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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.radixdlt.application.system.construction.CreateSystemConstructorV2;
import com.radixdlt.application.system.construction.NextEpochConstructorV3;
import com.radixdlt.application.system.construction.NextViewConstructorV3;
import com.radixdlt.application.system.scrypt.EpochUpdateConstraintScrypt;
import com.radixdlt.application.system.scrypt.RoundUpdateConstraintScrypt;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.construction.StakeTokensConstructorV3;
import com.radixdlt.application.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.application.tokens.construction.UnstakeOwnershipConstructor;
import com.radixdlt.application.tokens.scrypt.StakingConstraintScryptV4;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.validators.construction.RegisterValidatorConstructor;
import com.radixdlt.application.validators.scrypt.ValidatorConstraintScryptV2;
import com.radixdlt.application.validators.scrypt.ValidatorRegisterConstraintScrypt;
import com.radixdlt.application.validators.scrypt.ValidatorUpdateOwnerConstraintScrypt;
import com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt;
import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilderException;
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
import com.radixdlt.utils.UInt256;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UnstakeTokensV2Test {

  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    return List.of(
        new Object[][] {
          {
            List.of(10),
            10,
            List.of(
                new RoundUpdateConstraintScrypt(10),
                new EpochUpdateConstraintScrypt(10, Amount.ofTokens(10).toSubunits(), 9800, 1, 10),
                new TokensConstraintScryptV3(Set.of(), Pattern.compile("[a-z0-9]+")),
                new StakingConstraintScryptV4(Amount.ofTokens(10).toSubunits()),
                new ValidatorConstraintScryptV2(),
                new ValidatorRegisterConstraintScrypt(),
                new ValidatorUpdateRakeConstraintScrypt(2),
                new ValidatorUpdateOwnerConstraintScrypt()),
            new StakeTokensConstructorV3(Amount.ofTokens(10).toSubunits()),
            new UnstakeOwnershipConstructor()
          },
          {
            List.of(5, 5),
            10,
            List.of(
                new RoundUpdateConstraintScrypt(10),
                new EpochUpdateConstraintScrypt(10, Amount.ofTokens(10).toSubunits(), 9800, 1, 10),
                new TokensConstraintScryptV3(Set.of(), Pattern.compile("[a-z0-9]+")),
                new StakingConstraintScryptV4(Amount.ofTokens(10).toSubunits()),
                new ValidatorConstraintScryptV2(),
                new ValidatorRegisterConstraintScrypt(),
                new ValidatorUpdateRakeConstraintScrypt(2),
                new ValidatorUpdateOwnerConstraintScrypt()),
            new StakeTokensConstructorV3(Amount.ofTokens(10).toSubunits()),
            new UnstakeOwnershipConstructor()
          },
          {
            List.of(10),
            6,
            List.of(
                new RoundUpdateConstraintScrypt(10),
                new EpochUpdateConstraintScrypt(10, Amount.ofTokens(10).toSubunits(), 9800, 1, 10),
                new TokensConstraintScryptV3(Set.of(), Pattern.compile("[a-z0-9]+")),
                new StakingConstraintScryptV4(Amount.ofTokens(10).toSubunits()),
                new ValidatorConstraintScryptV2(),
                new ValidatorRegisterConstraintScrypt(),
                new ValidatorUpdateRakeConstraintScrypt(2),
                new ValidatorUpdateOwnerConstraintScrypt()),
            new StakeTokensConstructorV3(Amount.ofTokens(10).toSubunits()),
            new UnstakeOwnershipConstructor()
          },
          {
            List.of(5, 5),
            6,
            List.of(
                new RoundUpdateConstraintScrypt(10),
                new EpochUpdateConstraintScrypt(10, Amount.ofTokens(10).toSubunits(), 9800, 1, 10),
                new TokensConstraintScryptV3(Set.of(), Pattern.compile("[a-z0-9]+")),
                new StakingConstraintScryptV4(Amount.ofTokens(10).toSubunits()),
                new ValidatorConstraintScryptV2(),
                new ValidatorRegisterConstraintScrypt(),
                new ValidatorUpdateRakeConstraintScrypt(2),
                new ValidatorUpdateOwnerConstraintScrypt()),
            new StakeTokensConstructorV3(Amount.ofTokens(10).toSubunits()),
            new UnstakeOwnershipConstructor()
          },
        });
  }

  private ECKeyPair key;
  private REAddr accountAddr;
  private RadixEngine<Void> sut;
  private EngineStore<Void> store;
  private final List<UInt256> stakes;
  private final UInt256 totalStakes;
  private final UInt256 unstakeAmt;
  private final List<ConstraintScrypt> scrypts;
  private final ActionConstructor<StakeTokens> stakeTokensConstructor;
  private final ActionConstructor<UnstakeOwnership> unstakeTokensConstructor;

  public UnstakeTokensV2Test(
      List<Integer> stakes,
      int unstakeAmt,
      List<ConstraintScrypt> scrypts,
      ActionConstructor<StakeTokens> stakeTokensConstructor,
      ActionConstructor<UnstakeOwnership> unstakeTokensConstructor) {
    this.stakes = stakes.stream().map(i -> Amount.ofTokens(i * 10).toSubunits()).toList();
    this.totalStakes = this.stakes.stream().reduce(UInt256::add).orElseThrow();
    this.unstakeAmt = Amount.ofTokens(unstakeAmt * 10L).toSubunits();
    this.scrypts = scrypts;
    this.stakeTokensConstructor = stakeTokensConstructor;
    this.unstakeTokensConstructor = unstakeTokensConstructor;
  }

  @Before
  public void setup() throws Exception {
    var cmAtomOS = new CMAtomOS();
    cmAtomOS.load(new SystemConstraintScrypt());
    scrypts.forEach(cmAtomOS::load);
    var cm =
        new ConstraintMachine(
            cmAtomOS.getProcedures(),
            cmAtomOS.buildSubstateDeserialization(),
            cmAtomOS.buildVirtualSubstateDeserialization());
    var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
    var serialization = cmAtomOS.buildSubstateSerialization();
    this.store = new InMemoryEngineStore<>();
    this.sut =
        new RadixEngine<>(
            parser,
            serialization,
            REConstructor.newBuilder()
                .put(CreateSystem.class, new CreateSystemConstructorV2())
                .put(NextRound.class, new NextViewConstructorV3())
                .put(
                    NextEpoch.class,
                    new NextEpochConstructorV3(Amount.ofTokens(10).toSubunits(), 9800, 1, 10))
                .put(StakeTokens.class, stakeTokensConstructor)
                .put(UnstakeOwnership.class, unstakeTokensConstructor)
                .put(
                    CreateMutableToken.class,
                    new CreateMutableTokenConstructor(SystemConstraintScrypt.MAX_SYMBOL_LENGTH))
                .put(MintToken.class, new MintTokenConstructor())
                .put(TransferToken.class, new TransferTokensConstructorV2())
                .put(RegisterValidator.class, new RegisterValidatorConstructor())
                .build(),
            cm,
            store);

    this.key = ECKeyPair.generateNew();
    this.accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
    var txn =
        this.sut
            .construct(
                TxnConstructionRequest.create()
                    .action(new CreateSystem(0))
                    .action(
                        new CreateMutableToken(
                            REAddr.ofNativeToken(), "xrd", "Name", "", "", "", null))
                    .action(new MintToken(REAddr.ofNativeToken(), accountAddr, totalStakes)))
            .buildWithoutSignature();
    this.sut.execute(List.of(txn), null, PermissionLevel.SYSTEM);
  }

  @Test
  public void unstake_tokens_after_epoch() throws Exception {
    // Arrange
    var stakeActions =
        this.stakes.stream()
            .map(amt -> new StakeTokens(accountAddr, key.getPublicKey(), amt))
            .collect(Collectors.<TxAction>toList());
    var stake =
        this.sut
            .construct(TxnConstructionRequest.create().actions(stakeActions))
            .signAndBuild(key::sign);
    this.sut.execute(List.of(stake));
    var nextEpoch = sut.construct(new NextEpoch(1)).buildWithoutSignature();
    this.sut.execute(List.of(nextEpoch), null, PermissionLevel.SUPER_USER);

    // Act
    var unstake =
        this.sut
            .construct(new UnstakeOwnership(accountAddr, key.getPublicKey(), unstakeAmt))
            .signAndBuild(key::sign);
    var parsed = this.sut.execute(List.of(unstake));
  }

  @Test
  public void cannot_unstake_others_tokens() throws Exception {
    // Arrange
    var stakeActions =
        this.stakes.stream()
            .map(amt -> new StakeTokens(accountAddr, key.getPublicKey(), amt))
            .collect(Collectors.<TxAction>toList());
    var stake =
        this.sut
            .construct(TxnConstructionRequest.create().actions(stakeActions))
            .signAndBuild(key::sign);
    this.sut.execute(List.of(stake));
    var nextEpoch = sut.construct(new NextEpoch(1)).buildWithoutSignature();
    this.sut.execute(List.of(nextEpoch), null, PermissionLevel.SUPER_USER);

    // Act
    var nextKey = ECKeyPair.generateNew();
    var unstake =
        this.sut
            .construct(new UnstakeOwnership(accountAddr, key.getPublicKey(), unstakeAmt))
            .signAndBuild(nextKey::sign);

    assertThatThrownBy(() -> this.sut.execute(List.of(unstake)))
        .hasRootCauseInstanceOf(AuthorizationException.class);
  }

  @Test
  public void cant_construct_transfer_with_unstaked_tokens_immediately() throws Exception {
    // Arrange
    var acct2 = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
    var stakeActions =
        this.stakes.stream()
            .map(amt -> new StakeTokens(accountAddr, key.getPublicKey(), amt))
            .collect(Collectors.<TxAction>toList());
    var txn =
        sut.construct(
                TxnConstructionRequest.create()
                    .actions(stakeActions)
                    .action(new RegisterValidator(key.getPublicKey())))
            .signAndBuild(key::sign);
    sut.execute(List.of(txn));
    var nextEpoch = sut.construct(new NextEpoch(1)).buildWithoutSignature();
    this.sut.execute(List.of(nextEpoch), null, PermissionLevel.SUPER_USER);
    var unstake =
        this.sut
            .construct(new UnstakeOwnership(accountAddr, key.getPublicKey(), unstakeAmt))
            .signAndBuild(key::sign);
    sut.execute(List.of(unstake));
    var request =
        TxnConstructionRequest.create()
            .action(new NextRound(10, true, 1, u -> key.getPublicKey()))
            .action(new NextEpoch(1));
    var nextEpoch2 = sut.construct(request).buildWithoutSignature();
    this.sut.execute(List.of(nextEpoch2), null, PermissionLevel.SUPER_USER);

    // Act
    // Assert
    assertThatThrownBy(
            () ->
                sut.construct(
                    new TransferToken(REAddr.ofNativeToken(), accountAddr, acct2, unstakeAmt)))
        .isInstanceOf(TxBuilderException.class);
  }
}
