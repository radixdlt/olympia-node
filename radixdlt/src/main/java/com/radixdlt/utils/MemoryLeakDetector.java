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

package com.radixdlt.utils;

import com.google.common.collect.Comparators;
import com.google.common.collect.EvictingQueue;
import com.sun.management.GarbageCollectionNotificationInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class MemoryLeakDetector {

    private static final Logger log = LogManager.getLogger();

    private static final String OLD_GEN_GC_NAME = "G1 Old Gen";

    private static final int CONSECUTIVE_INCREASE_ALERT_THRESHOLD = 3;

    private final EvictingQueue<Long> memTrail = EvictingQueue.create(CONSECUTIVE_INCREASE_ALERT_THRESHOLD + 1);

    private final Map<String, NotificationListener> registeredListeners = new HashMap<>();

    public MemoryLeakDetector() {
        log.info("Starting memory leak detector...");
        ManagementFactory.getGarbageCollectorMXBeans()
                .forEach(this::registerGCListener);
    }

    public void stop() {
        ManagementFactory.getGarbageCollectorMXBeans()
                .forEach(this::unregisterGCListener);
        memTrail.clear();
        registeredListeners.clear();
    }

    private void unregisterGCListener(GarbageCollectorMXBean garbageCollectorMXBean) {
        final NotificationEmitter notificationEmitter = (NotificationEmitter) garbageCollectorMXBean;
        final NotificationListener listener = registeredListeners.get(garbageCollectorMXBean.getName());
        if (listener != null) {
            try {
                notificationEmitter.removeNotificationListener(listener);
            } catch (ListenerNotFoundException e) {
                // nothing
            }
        }
    }

    private void registerGCListener(GarbageCollectorMXBean garbageCollectorMXBean) {
        final NotificationEmitter notificationEmitter = (NotificationEmitter) garbageCollectorMXBean;
        final NotificationListener listener = this::handleNotification;
        registeredListeners.put(garbageCollectorMXBean.getName(), listener);
        notificationEmitter.addNotificationListener(listener, null, null);
    }

    private void handleNotification(Notification notification, Object handback) {
        if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
            final GarbageCollectionNotificationInfo info =
                    GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());

            final long oldGenMemUsageBeforeGc = info.getGcInfo()
                    .getMemoryUsageBeforeGc().get(OLD_GEN_GC_NAME).getUsed();

            final long oldGenMemUsageAfterGc = info.getGcInfo()
                    .getMemoryUsageAfterGc().get(OLD_GEN_GC_NAME).getUsed();

            if (oldGenMemUsageAfterGc < oldGenMemUsageBeforeGc) { // we're only considering GC runs where old gen was cleaned
                memTrail.offer(oldGenMemUsageAfterGc);

                final boolean consecutivelyIncreasing = Comparators.isInStrictOrder(memTrail, Long::compare);

                if (consecutivelyIncreasing && memTrail.remainingCapacity() == 0) {
                    final String memTrailStr = memTrail.stream()
                            .map(n -> Long.toString(toMiB(n)))
                            .collect(Collectors.joining(", "));
                    log.warn("Potential memory leak detected! After {} latest GC runs the old gen heap steadily increases.",
                            CONSECUTIVE_INCREASE_ALERT_THRESHOLD);
                    log.warn("Memory (old gen) after current GC: {}MiB. Previous values (MiB): [{}]",
                            toMiB(oldGenMemUsageAfterGc), memTrailStr);

                    memTrail.clear(); // reset the state
                }
            }
        }
    }

    private long toMiB(long valueInBytes) {
        return valueInBytes / (1024L * 1024L);
    }
}
