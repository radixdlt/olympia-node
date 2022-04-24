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

package com.radixdlt.consensus.bft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.utils.UInt256;
import java.util.stream.IntStream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class BFTValidatorSetTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(BFTValidatorSet.class).verify();
  }

  @Test
  public void sensibleToString() {
    BFTNode node = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
    String s =
        BFTValidatorSet.from(ImmutableList.of(BFTValidator.from(node, UInt256.ONE))).toString();
    assertThat(s).contains(BFTValidatorSet.class.getSimpleName()).contains(node.getSimpleName());
  }

  @Test
  public void testStreamConstructor() {
    BFTNode node = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
    String s =
        BFTValidatorSet.from(ImmutableList.of(BFTValidator.from(node, UInt256.ONE)).stream())
            .toString();
    assertThat(s).contains(node.getSimpleName());
  }

  @Test
  public void testValidate() {
    ECKeyPair k1 = ECKeyPair.generateNew();
    ECKeyPair k2 = ECKeyPair.generateNew();
    ECKeyPair k3 = ECKeyPair.generateNew();
    ECKeyPair k4 = ECKeyPair.generateNew();
    ECKeyPair k5 = ECKeyPair.generateNew(); // Rogue signature

    BFTNode node1 = BFTNode.create(k1.getPublicKey());
    BFTNode node2 = BFTNode.create(k2.getPublicKey());
    BFTNode node3 = BFTNode.create(k3.getPublicKey());
    BFTNode node4 = BFTNode.create(k4.getPublicKey());
    BFTNode node5 = BFTNode.create(k5.getPublicKey());

    BFTValidator v1 = BFTValidator.from(node1, UInt256.ONE);
    BFTValidator v2 = BFTValidator.from(node2, UInt256.ONE);
    BFTValidator v3 = BFTValidator.from(node3, UInt256.ONE);
    BFTValidator v4 = BFTValidator.from(node4, UInt256.ONE);

    BFTValidatorSet vs = BFTValidatorSet.from(ImmutableSet.of(v1, v2, v3, v4));
    HashCode message = HashUtils.random256();

    // 2 signatures for 4 validators -> fail
    ValidationState vst1 = vs.newValidationState();
    assertTrue(vst1.addSignature(node1, 1L, k1.sign(message)));
    assertFalse(vst1.complete());
    assertTrue(vst1.addSignature(node2, 1L, k2.sign(message)));
    assertFalse(vst1.complete());
    assertEquals(2, vst1.signatures().count());

    // 3 signatures for 4 validators -> pass
    ValidationState vst2 = vs.newValidationState();
    assertTrue(vst2.addSignature(node1, 1L, k1.sign(message)));
    assertFalse(vst1.complete());
    assertTrue(vst2.addSignature(node2, 1L, k2.sign(message)));
    assertFalse(vst1.complete());
    assertTrue(vst2.addSignature(node3, 1L, k3.sign(message)));
    assertTrue(vst2.complete());
    assertEquals(3, vst2.signatures().count());

    // 2 signatures + 1 signature not from set for 4 validators -> fail
    ValidationState vst3 = vs.newValidationState();
    assertTrue(vst3.addSignature(node1, 1L, k1.sign(message)));
    assertFalse(vst3.complete());
    assertTrue(vst3.addSignature(node2, 1L, k2.sign(message)));
    assertFalse(vst3.complete());
    assertFalse(vst3.addSignature(node5, 1L, k5.sign(message)));
    assertFalse(vst3.complete());
    assertEquals(2, vst3.signatures().count());

    // 3 signatures + 1 signature not from set for 4 validators -> pass
    ValidationState vst4 = vs.newValidationState();
    assertTrue(vst4.addSignature(node1, 1L, k1.sign(message)));
    assertFalse(vst3.complete());
    assertTrue(vst4.addSignature(node2, 1L, k2.sign(message)));
    assertFalse(vst3.complete());
    assertFalse(vst4.addSignature(node5, 1L, k5.sign(message)));
    assertFalse(vst3.complete());
    assertTrue(vst4.addSignature(node3, 1L, k3.sign(message)));
    assertTrue(vst4.complete());
    assertEquals(3, vst4.signatures().count());
  }

  @Test
  public void testValidateWithUnequalPower() {
    ECKeyPair k1 = ECKeyPair.generateNew();
    ECKeyPair k2 = ECKeyPair.generateNew();

    BFTNode node1 = BFTNode.create(k1.getPublicKey());
    BFTNode node2 = BFTNode.create(k2.getPublicKey());

    BFTValidator v1 = BFTValidator.from(node1, UInt256.THREE);
    BFTValidator v2 = BFTValidator.from(node2, UInt256.ONE);

    BFTValidatorSet vs = BFTValidatorSet.from(ImmutableSet.of(v1, v2));
    HashCode message = HashUtils.random256();
    ValidationState vst1 = vs.newValidationState();
    assertTrue(vst1.addSignature(node1, 1L, k1.sign(message)));
    assertTrue(vst1.complete());
    assertEquals(1, vst1.signatures().count());
  }

  @Test
  public void testRetainsOrder() {
    for (var i = 0; i < 10; ++i) {
      final var validators =
          IntStream.range(0, 100)
              .mapToObj(n -> ECKeyPair.generateNew())
              .map(ECKeyPair::getPublicKey)
              .map(BFTNode::create)
              .map(node -> BFTValidator.from(node, UInt256.ONE))
              .toList();

      final var validatorSet = BFTValidatorSet.from(validators);
      final var setValidators = Lists.newArrayList(validatorSet.getValidators());
      checkIterableOrder(validators, setValidators);
    }
  }

  private <T> void checkIterableOrder(Iterable<T> iterable1, Iterable<T> iterable2) {
    final var i1 = iterable1.iterator();
    final var i2 = iterable2.iterator();

    while (i1.hasNext() && i2.hasNext()) {
      final var o1 = i1.next();
      final var o2 = i2.next();
      assertEquals("Objects not the same", o1, o2);
    }
    assertFalse("Iterable 1 larger than iterable 2", i1.hasNext());
    assertFalse("Iterable 2 larger than iterable 1", i2.hasNext());
  }
}
