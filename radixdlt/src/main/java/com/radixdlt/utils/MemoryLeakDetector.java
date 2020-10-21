package com.radixdlt.utils;

import com.sun.management.GarbageCollectionNotificationInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MemoryLeakDetector {

    private static final Logger log = LogManager.getLogger();

    private static final String OldGenGcName = "G1 Old Gen";

    private static final int CONSECUTIVE_INCREASE_ALERT_THRESHOLD = 3;

    private final List<Long> memTrail = new ArrayList<>();

    public void init() {
        log.info("Starting memory leak detector...");
        final List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean garbageCollectorMXBean : garbageCollectorMXBeans) {
            listenForGarbageCollectionOn(garbageCollectorMXBean);
        }
    }

    private void listenForGarbageCollectionOn(GarbageCollectorMXBean garbageCollectorMXBean) {
        final NotificationEmitter notificationEmitter = (NotificationEmitter) garbageCollectorMXBean;
        final GCListener listener = new GCListener();
        notificationEmitter.addNotificationListener(listener, null, null);
    }

    private class GCListener implements NotificationListener {
        @Override
        public void handleNotification(Notification notification, Object handback) {
            if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                final GarbageCollectionNotificationInfo info =
                        GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());

                final long oldGenMemUsageBeforeGc = info.getGcInfo()
                        .getMemoryUsageBeforeGc().get(OldGenGcName).getUsed();

                final long oldGenMemUsageAfterGc = info.getGcInfo()
                        .getMemoryUsageAfterGc().get(OldGenGcName).getUsed();

                if (oldGenMemUsageAfterGc < oldGenMemUsageBeforeGc) { // we're only considering GC runs where old gen was cleaned
                    memTrail.add(oldGenMemUsageAfterGc);
                    if (memTrail.size() > CONSECUTIVE_INCREASE_ALERT_THRESHOLD + 1) {
                        memTrail.remove(0); // to keep the list bounded
                    }

                    boolean consecutivelyIncreasing = true;
                    for (int i = 1; i < memTrail.size(); i++) {
                        if (memTrail.get(i) <= memTrail.get(i - 1)) {
                            consecutivelyIncreasing = false;
                            break;
                        }
                    }

                    if (consecutivelyIncreasing && memTrail.size() > CONSECUTIVE_INCREASE_ALERT_THRESHOLD) {
                        final String memTrailStr = memTrail.stream()
                                .map(n -> Long.toString(toMb(n)))
                                .collect(Collectors.joining(", "));
                        log.info("Potential memory leak detected! After {} latest GC runs the old gen heap steadily increases.",
                                CONSECUTIVE_INCREASE_ALERT_THRESHOLD);
                        log.info("Memory (old gen) after current GC: {}Mb. Previous values (Mb): [{}]",
                                toMb(oldGenMemUsageAfterGc), memTrailStr);

                        memTrail.clear(); // reset the state
                    }
                }
            }
        }

        private long toMb(long valueInBytes) {
            return valueInBytes / 1000000;
        }
    }
}
