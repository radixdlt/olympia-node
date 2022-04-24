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
import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.atom.*;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.constraintmachine.exceptions.InvalidHashedKeyException;
import com.radixdlt.constraintmachine.exceptions.ReservedSymbolException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.PrivateKeys;
import com.radixdlt.utils.UInt256;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Test;

public class TokenResourceTest {
  private RadixEngine<Void> engine;
  private EngineStore<Void> store;
  private REParser parser;
  private SubstateSerialization serialization;
  private Txn genesis;

  @Before
  public void setup() throws Exception {
    var cmAtomOS = new CMAtomOS();
    cmAtomOS.load(new SystemConstraintScrypt());
    cmAtomOS.load(new TokensConstraintScryptV3(Set.of("xrd"), Pattern.compile("[a-z0-9]+")));
    var cm =
        new ConstraintMachine(
            cmAtomOS.getProcedures(),
            cmAtomOS.buildSubstateDeserialization(),
            cmAtomOS.buildVirtualSubstateDeserialization());
    this.parser = new REParser(cmAtomOS.buildSubstateDeserialization());
    this.serialization = cmAtomOS.buildSubstateSerialization();
    this.store = new InMemoryEngineStore<>();
    this.engine =
        new RadixEngine<>(
            parser,
            serialization,
            REConstructor.newBuilder()
                .put(CreateSystem.class, new CreateSystemConstructorV2())
                .put(
                    CreateMutableToken.class,
                    new CreateMutableTokenConstructor(SystemConstraintScrypt.MAX_SYMBOL_LENGTH))
                .put(MintToken.class, new MintTokenConstructor())
                .build(),
            cm,
            store);
    this.genesis = this.engine.construct(new CreateSystem(0)).buildWithoutSignature();
    this.engine.execute(List.of(this.genesis), null, PermissionLevel.SYSTEM);
  }

  @Test
  public void create_new_token_with_no_errors() throws Exception {
    // Arrange
    var keyPair = ECKeyPair.generateNew();
    var addr = REAddr.ofHashedKey(keyPair.getPublicKey(), "test");
    var tokenResource = TokenResource.createFixedSupplyResource(addr);
    var holdingAddress = REAddr.ofPubKeyAccount(keyPair.getPublicKey());
    var tokensParticle = new TokensInAccount(holdingAddress, addr, UInt256.TEN);

    var builder =
        TxLowLevelBuilder.newBuilder(serialization)
            .syscall(Syscall.READDR_CLAIM, "test".getBytes(StandardCharsets.UTF_8))
            .virtualDown(SubstateId.ofSubstate(genesis.getId(), 0), addr.getBytes())
            .up(tokenResource)
            .up(tokensParticle)
            .up(TokenResourceMetadata.empty(addr, "test"))
            .end();
    var sig = keyPair.sign(builder.hashToSign().asBytes());
    var txn = builder.sig(sig).build();

    // Act
    // Assert
    this.engine.execute(List.of(txn));
  }

  @Test
  public void create_token_with_reserved_symbol_should_fail() throws Exception {
    // Arrange
    var keyPair = PrivateKeys.ofNumeric(1);
    var addr = REAddr.ofHashedKey(keyPair.getPublicKey(), "xrd");
    var tokenResource = TokenResource.createFixedSupplyResource(addr);
    var holdingAddress = REAddr.ofPubKeyAccount(keyPair.getPublicKey());
    var tokensParticle = new TokensInAccount(holdingAddress, addr, UInt256.TEN);

    var builder =
        TxLowLevelBuilder.newBuilder(serialization)
            .syscall(Syscall.READDR_CLAIM, "xrd".getBytes(StandardCharsets.UTF_8))
            .virtualDown(SubstateId.ofSubstate(genesis.getId(), 0), addr.getBytes())
            .up(tokenResource)
            .up(tokensParticle)
            .up(TokenResourceMetadata.empty(addr, "xrd"))
            .end();
    var sig = keyPair.sign(builder.hashToSign().asBytes());
    var txn = builder.sig(sig).build();

    // Act
    // Assert
    assertThatThrownBy(() -> this.engine.execute(List.of(txn)))
        .hasRootCauseInstanceOf(ReservedSymbolException.class);
  }

  @Test
  public void create_token_with_reserved_symbol_with_system_permissions_should_pass()
      throws Exception {
    // Arrange
    var keyPair = PrivateKeys.ofNumeric(1);
    var addr = REAddr.ofHashedKey(keyPair.getPublicKey(), "xrd");
    var tokenResource = TokenResource.createFixedSupplyResource(addr);
    var holdingAddress = REAddr.ofPubKeyAccount(keyPair.getPublicKey());
    var tokensParticle = new TokensInAccount(holdingAddress, addr, UInt256.TEN);

    var builder =
        TxLowLevelBuilder.newBuilder(serialization)
            .syscall(Syscall.READDR_CLAIM, "xrd".getBytes(StandardCharsets.UTF_8))
            .virtualDown(SubstateId.ofSubstate(genesis.getId(), 0), addr.getBytes())
            .up(tokenResource)
            .up(tokensParticle)
            .up(TokenResourceMetadata.empty(addr, "xrd"))
            .end();
    var txn = builder.build();

    // Act
    // Assert
    this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);
  }

  @Test
  public void create_fixed_token_with_no_tokens_should_error() throws Exception {
    // Arrange
    var keyPair = ECKeyPair.generateNew();
    var addr = REAddr.ofHashedKey(keyPair.getPublicKey(), "test");
    var tokenDefinitionParticle = TokenResource.createFixedSupplyResource(addr);
    var builder =
        TxLowLevelBuilder.newBuilder(serialization)
            .syscall(Syscall.READDR_CLAIM, "test".getBytes(StandardCharsets.UTF_8))
            .virtualDown(SubstateId.ofSubstate(genesis.getId(), 0), addr.getBytes())
            .up(tokenDefinitionParticle)
            .end();
    var sig = keyPair.sign(builder.hashToSign().asBytes());
    var txn = builder.sig(sig).build();

    // Act
    // Assert
    assertThatThrownBy(() -> this.engine.execute(List.of(txn)))
        .isInstanceOf(RadixEngineException.class);
  }

  @Test
  public void using_someone_elses_address_should_fail() throws Exception {
    var keyPair = ECKeyPair.generateNew();
    // Arrange
    var addr = REAddr.ofHashedKey(ECKeyPair.generateNew().getPublicKey(), "smthng");
    var tokenDefinitionParticle =
        TokenResource.createMutableSupplyResource(addr, keyPair.getPublicKey());
    var builder =
        TxBuilder.newBuilder(parser.getSubstateDeserialization(), serialization, 255)
            .toLowLevelBuilder()
            .syscall(Syscall.READDR_CLAIM, "smthng".getBytes(StandardCharsets.UTF_8))
            .virtualDown(SubstateId.ofSubstate(genesis.getId(), 0), addr.getBytes())
            .up(tokenDefinitionParticle)
            .end();
    var sig = keyPair.sign(builder.hashToSign());
    var txn = builder.sig(sig).build();

    // Act and Assert
    assertThatThrownBy(() -> this.engine.execute(List.of(txn)))
        .hasRootCauseInstanceOf(InvalidHashedKeyException.class);
  }
}
