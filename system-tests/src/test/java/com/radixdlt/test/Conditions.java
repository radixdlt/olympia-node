package com.radixdlt.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;

public class Conditions {

    private static final Logger logger = LogManager.getLogger();

    private static final Duration MAX_TIME_TO_WAIT_FOR_LIVENESS = Duration.ofMinutes(15);

    /**
     * will block and keep checking for liveness, until a fixed amount of time has passed
     */
    public static void waitUntilNetworkHasLiveness(RemoteBFTNetwork network) {
        logger.info("Waiting for network liveness...");
        AtomicBoolean hasLiveness = new AtomicBoolean(false);
        await().ignoreExceptionsMatching(exception -> exception.getCause() instanceof LivenessCheck.LivenessError)
                .atMost(MAX_TIME_TO_WAIT_FOR_LIVENESS).until(() -> {
            RemoteBFTTest test = RemoteBFTTest.builder()
                    .network(RemoteBFTNetworkBridge.of(network))
                    .waitUntilResponsive()
                    .startConsensusOnRun()
                    .assertLiveness(5)
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
