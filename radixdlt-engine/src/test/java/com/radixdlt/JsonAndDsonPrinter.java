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

package com.radixdlt;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.tokens.*;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.junit.Test;

public class JsonAndDsonPrinter {

    private static final Logger logger = LogManager.getLogger();
    public static final RadixAddress ADDRESS = RadixAddress.from("23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x");
    public static final RadixAddress ADDRESS2 = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
    public static final RadixAddress ADDRESS3 = RadixAddress.from("JHJTc1vUVA3JbjKCDtNu3x7tPaeB8fFcfnYp26s3hzos3s351To");
    public static final RRI TOKEN_RRI = RRI.of(ADDRESS, "COOKIE");
    public static final ImmutableMap<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> TOKEN_PERMISSIONS = ImmutableMap.of(
            MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
            MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
    );

    private Serialization instance = DefaultSerialization.getInstance();

    @Test
    public void logAllParticles() {
        logFixedSupplyTokenDefinitionParticle();
        logMutableSupplyTokenDefinitionParticle();
        logStakedTokensParticle();
        logTransferableTokensParticle();
        logUnallocatedTokensParticle();
        logRRIParticle();
        logRegisteredValidatorParticle();
        logUnregisteredValidatorParticle();
        logSystemParticle();
    }

    private void logSystemParticle() {
        final var particle = new SystemParticle(1, 1000, System.currentTimeMillis());
        logParticle(particle);
    }

    private void logUnregisteredValidatorParticle() {
        final var particle = new UnregisteredValidatorParticle(
                ADDRESS,
                1L);
        logParticle(particle);
    }

    private void logRegisteredValidatorParticle() {
        final var particle = new RegisteredValidatorParticle(
                ADDRESS,
                ImmutableSet.of(ADDRESS2, ADDRESS3),
                1L);
        logParticle(particle);
    }

    private void logRRIParticle() {
        final var particle = new RRIParticle(TOKEN_RRI);
        logParticle(particle);
    }

    private void logUnallocatedTokensParticle() {
        final var particle = new UnallocatedTokensParticle(
                UInt256.TEN,
                UInt256.ONE,
                TOKEN_RRI,
                TOKEN_PERMISSIONS
        );
        logParticle(particle);
    }

    private void logTransferableTokensParticle() {
        final var particle = new TransferrableTokensParticle(
                ADDRESS,
                UInt256.EIGHT,
                UInt256.ONE,
                TOKEN_RRI,
                TOKEN_PERMISSIONS
        );
        logParticle(particle);
    }

    private void logStakedTokensParticle() {
        final var particle = new StakedTokensParticle(
                ADDRESS,
                ADDRESS,
                UInt256.EIGHT,
                UInt256.ONE,
                TOKEN_RRI,
                TOKEN_PERMISSIONS
        );
        logParticle(particle);
    }

    private void logMutableSupplyTokenDefinitionParticle() {
        final var particle = new MutableSupplyTokenDefinitionParticle(
                TOKEN_RRI,
                "TEST",
                "description",
                UInt256.ONE,
                "http://somewhere.com/icon.jpg",
                "http://somewhere.com",
                TOKEN_PERMISSIONS
        );
        logParticle(particle);
    }

    private void logFixedSupplyTokenDefinitionParticle() {
        final var particle = new FixedSupplyTokenDefinitionParticle(
                TOKEN_RRI,
                "TEST",
                "description",
                UInt256.TEN,
                UInt256.ONE,
                "http://somewhere.com/icon.jpg",
                "http://somewhere.com"
        );
        logParticle(particle);
    }

    private void logParticle(Particle particle) {
        System.out.println();
        logger.info("=".repeat(20) + particle.getClass().getName() + "=".repeat(20));
        String json = instance.toJson(particle, DsonOutput.Output.ALL);
        logger.info("JSON:\n {}", new JSONObject(json).toString(5));
        byte[] bytesAll = instance.toDson(particle, DsonOutput.Output.ALL);
        byte[] bytesHash = instance.toDson(particle, DsonOutput.Output.HASH);
        logger.info("Hex of Output.ALL: {}", Bytes.toHexString(bytesAll));
        logger.info("Hex of Output.HASH: {}", Bytes.toHexString(bytesHash));
        logger.info("=".repeat(50));
    }

}
