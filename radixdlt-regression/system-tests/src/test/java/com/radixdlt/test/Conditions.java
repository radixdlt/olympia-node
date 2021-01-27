/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.test;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;

public class Conditions {

    private static final Logger logger = LogManager.getLogger();

    private static final Duration MAX_TIME_TO_WAIT_FOR_LIVENESS = Duration.ofMinutes(10);

    /**
     * will block and keep checking for liveness, until a fixed amount of time has passed
     */
    public static void waitUntilNetworkHasLiveness(RemoteBFTNetwork network) {
        waitUntilNetworkHasLiveness(network, Lists.newArrayList());
    }

    /**
     * will block and keep checking for liveness, until a fixed amount of time has passed. Can ignore some nodes.
     */
    public static void waitUntilNetworkHasLiveness(RemoteBFTNetwork network, List<String> nodesToIgnore) {
        logger.info("Waiting for network liveness...");
        AtomicBoolean hasLiveness = new AtomicBoolean(false);
        await().ignoreExceptionsMatching(exception -> exception.getCause() instanceof LivenessCheck.LivenessError)
                .atMost(MAX_TIME_TO_WAIT_FOR_LIVENESS).until(() -> {
            RemoteBFTTest test = RemoteBFTTest.builder()
                    .network(RemoteBFTNetworkBridge.of(network))
                    .waitUntilResponsive()
                    .startConsensusOnRun()
                    .assertLiveness(5, nodesToIgnore)
                    .build();
            test.runBlocking(20, TimeUnit.SECONDS);
            hasLiveness.set(true);
            logger.info("Network has liveness.");
            return true;
        });

        if (!hasLiveness.get()) {
            throw new AssertionError(String.format("Network was not live or responsive after %s",
                    MAX_TIME_TO_WAIT_FOR_LIVENESS.toString()));
        }
    }

}
