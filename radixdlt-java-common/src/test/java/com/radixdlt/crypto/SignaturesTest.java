/* Copyright 2021 Radix DLT Ltd incorporated in England.
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

package com.radixdlt.crypto;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Random;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SignaturesTest {

    interface SchnorrSignature extends Signature {
        // Dummy type for tests
    }

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(ECDSASignatures.class)
                .verify();
    }

    @Test
    public void verify_that_ecdsa_is_default_signature_scheme() {
        Signatures emptySignatures = DefaultSignatures.emptySignatures();
        assertEquals(SignatureScheme.ECDSA, emptySignatures.signatureScheme());
    }

    @Test
    public void verify_that_ecdsasignature_specifies_correct_scheme() {
        ECDSASignature signature = ECDSASignature.zeroSignature();
        assertEquals(SignatureScheme.ECDSA, signature.signatureScheme());
    }

    @Test
    public void well_formatted_tostring_of_signaturescheme() {
        assertThat(SignatureScheme.ECDSA).hasToString("ecdsa");
    }

    @Test
    public void verify_that_default_signature_scheme_is_ecdsa() {
        Signatures emptySignatures = DefaultSignatures.emptySignatures();

        assertEquals(SignatureScheme.ECDSA, emptySignatures.signatureScheme());
        assertTrue(emptySignatures.isEmpty());
        ECDSASignature mockSignature = mock(ECDSASignature.class);
        ECPublicKey publicKey = mock(ECPublicKey.class);

        Signatures nonEmptySignatures = emptySignatures.concatenate(publicKey, mockSignature);
        assertEquals(1, nonEmptySignatures.count());

        nonEmptySignatures.signatureScheme();
    }

    @Test
    public void verify_that_a_single_invalid_signature_does_fails_to_verify() {
        Signatures single = new ECDSASignatures(publicKey(), randomInvalidSignature());
        assertEquals(1, single.count());
        assertTrue(single.signedMessage(hashOfMessage("Fubar")).isEmpty());
    }

    @Test
    public void verify_that_multiple_invalid_signature_does_fails_to_verify() {
        Signatures multiple = new ECDSASignatures(ImmutableMap.of(publicKey(), randomInvalidSignature(), publicKey(), randomInvalidSignature()));
        assertEquals(2, multiple.count());
        assertTrue(multiple.signedMessage(hashOfMessage("Fubar")).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void verify_that_we_crash_if_we_try_to_concatenate_signatures_with_a_signature_of_incompatible_type_schnorr_to_ecdsa() {
        Signatures emptySignatures = DefaultSignatures.emptySignatures();
        assertEquals(SignatureScheme.ECDSA, emptySignatures.signatureScheme());
        assertTrue(emptySignatures.isEmpty());
        SchnorrSignature schnorr = mock(SchnorrSignature.class);
        ECPublicKey publicKey = mock(ECPublicKey.class);
        emptySignatures.concatenate(publicKey, schnorr);
        fail();
    }

    @Test(expected = IllegalArgumentException.class)
    public void verify_that_we_crash_if_we_try_to_concatenate_signatures_with_a_signature_of_incompatible_type_ecdsa_to_schnorr() {
        Signatures emptySignatures = DefaultSignatures.emptySignatures();
        assertTrue(emptySignatures.isEmpty());
        SchnorrSignature schnorrSignature = mock(SchnorrSignature.class);
        ECPublicKey publicKey = mock(ECPublicKey.class);
        emptySignatures.concatenate(publicKey, schnorrSignature);
        fail();
    }

    @Test
    public void test_that_we_can_bulk_verify_signatures_verify_all_two() {
        test_that_we_can_bulk_verify_signatures(
                2,
                true,
                2,
                0
        );
    }

    @Test
    public void test_that_we_can_bulk_verify_signatures_verify_at_least_N_of_M() {
        for (int valid = 0; valid < 5; valid++) {
            for (int invalid = 0; invalid < 5; invalid++) {
                for (int threshold = 0; threshold <= (valid + invalid); threshold++) {

                    boolean isExpectedToMeetThreshold = valid >= threshold;
                    test_that_we_can_bulk_verify_signatures(
                            threshold,
                            isExpectedToMeetThreshold,
                            valid,
                            invalid
                    );
                }

            }
        }
    }

    @Test
    public void well_formatted_tostring() {
        Signature dummySignature = randomInvalidSignature();
        Signatures signatures = DefaultSignatures.single(publicKey(), dummySignature);
        String tostring = signatures.toString();

        assertThat(tostring)
        	.contains(ECDSASignature.class.getSimpleName())
        	.contains(dummySignature.toString());
    }

    private void test_that_we_can_bulk_verify_signatures(
            int thresholdNumberOfValidSignatures,
            boolean isExpectedToMeetThreshold,
            int numberOfValidSignaturesToCreate,
            int numberOfInvalidSignaturesToCreate
    ) {
        if (thresholdNumberOfValidSignatures > (numberOfValidSignaturesToCreate + numberOfInvalidSignaturesToCreate)) {
            throw new IllegalArgumentException(
                    "The sum of #valid + #invalid signatures should >= 'thresholdNumberOfValidSignatures'"
            );
        }
        Signatures signatures = DefaultSignatures.emptySignatures();
        HashCode hashedMessage = hashOfMessage("You must do what you feel is right of course");
        for (int i = 0; i < numberOfValidSignaturesToCreate + numberOfInvalidSignaturesToCreate; i++) {
            ECKeyPair keyPair = ECKeyPair.generateNew();
            assertNotNull(keyPair);
            final Signature signature;
            boolean shouldSignatureBeValid = i >= numberOfInvalidSignaturesToCreate;
            if (shouldSignatureBeValid) {
                signature = keyPair.sign(hashedMessage);
            } else {
                signature = randomInvalidSignature();
            }
            assertNotNull(signature);

            signatures = signatures.concatenate(keyPair.getPublicKey(), signature);
        }

        assertEquals((numberOfInvalidSignaturesToCreate + numberOfValidSignaturesToCreate), signatures.count());
        boolean doesSignatureMeetValidityThreshold = signatures.signedMessage(hashedMessage).size() >= thresholdNumberOfValidSignatures;
        assertEquals(isExpectedToMeetThreshold, doesSignatureMeetValidityThreshold);
    }

    private ECPublicKey publicKey() {
        return ECKeyPair.generateNew().getPublicKey();
    }

    private ECDSASignature randomInvalidSignature() {
        Supplier<BigInteger> randomBigInt = () -> BigInteger.valueOf(new Random().nextLong());

        return ECDSASignature.create(randomBigInt.get(), randomBigInt.get(), randomBigInt.get().signum());
    }

    private HashCode hashOfMessage(String message) {
        return HashUtils.sha256(message.getBytes());
    }
}
