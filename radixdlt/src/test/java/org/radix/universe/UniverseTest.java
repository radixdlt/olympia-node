/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.universe;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.UInt256;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UniverseTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testValidUniverse() throws Exception {

        ECKeyPair creator = new ECKeyPair();

	    Atom genesisAtom = createGenesisAtom(Universe.computeMagic(creator.getPublicKey(), 0, 0, Universe.UniverseType.TEST, 0), 0);
        genesisAtom.sign(creator);

        Universe universe = Universe.newBuilder()
        	.port(0)
        	.name("test")
        	.description("test universe")
        	.type(Universe.UniverseType.TEST)
        	.timestamp(0L)
        	.planckPeriod(1L)
        	.creator(creator.getPublicKey())
        	.addAtom(genesisAtom)
        	.build();

        universe.sign(creator);

		UniverseValidator.validate(universe);
    }

    @Test
    public void testUniverseValidationInvalidSignature() throws Exception {
        ECKeyPair creator = new ECKeyPair();

	    Atom genesisAtom = createGenesisAtom(Universe.computeMagic(creator.getPublicKey(), 0, 0, Universe.UniverseType.TEST, 0), 0);
        genesisAtom.sign(creator);

        Universe universe = Universe.newBuilder()
            .port(0)
            .name("test")
            .description("test universe")
            .type(Universe.UniverseType.TEST)
            .timestamp(0L)
            .planckPeriod(1L)
            .creator(creator.getPublicKey())
            .addAtom(genesisAtom)
            .build();

		thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Invalid universe signature");

		UniverseValidator.validate(universe);
    }

    private Atom createGenesisAtom(int magic, long timestamp) throws CryptoException {
        MutableSupplyTokenDefinitionParticle pow = new MutableSupplyTokenDefinitionParticle(
			new RadixAddress((byte) (magic & 0xFF), new ECKeyPair().getPublicKey()),
			"XRD", "Proof of Launch", "Radix Tokens",
			UInt256.ONE,
			null,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.ALL,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);
	    Atom genesisAtom = new Atom(timestamp);
		genesisAtom.addParticleGroupWith(pow, Spin.UP);
		return genesisAtom;
    }
}
