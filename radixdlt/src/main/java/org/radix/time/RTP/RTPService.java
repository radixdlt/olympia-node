package org.radix.time.RTP;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.radix.common.executors.Executable;
import org.radix.common.executors.Executor;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.database.exceptions.DatabaseException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.network.Network;
import org.radix.network.Protocol;
import org.radix.network.messaging.MessageProcessor;
import org.radix.network.peers.Peer;
import org.radix.network.peers.PeerStore;
import org.radix.network.peers.UDPPeer;
import org.radix.network.peers.filters.PeerFilter;
import org.radix.properties.RuntimeProperties;
import org.radix.state.State;
import org.radix.time.LogicalClock;
import org.radix.time.NtpService;
import org.radix.time.RTP.messages.RTPMessage;
import org.radix.universe.system.LocalSystem;

import static java.lang.Math.max;
import static java.lang.Math.abs;
import static java.lang.Math.exp;

public final class RTPService extends Service
{
    private static final Logger log = Logging.getLogger();
    private static final Logger rtp = Logging.getLogger("RTP");

    private final Random rand = new Random();

    private final RTPStore store = new RTPStore();

    private long radix_genesis_time = 0L;
    private long ntp_genesis_time = 0L;

    private long new_offset = 0L;
    private long last_offset = 0L;
    private long offset = 0L;
    private long age = 0L;
    private int deviation = 0;
    private int lastGroupSize = 0;
    private boolean do_resync = false;

    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicLong sequence = new AtomicLong(rand.nextLong());

    private boolean is_synchronized = false;
    private boolean syncing = true;

    private static final int     INTERVAL = Modules.get(RuntimeProperties.class).get("rtp.interval", 4);
    private static final int     TIMEOUT = INTERVAL / 2;
    private static final float   DECAY = Modules.get(RuntimeProperties.class).get("rtp.decay", 0.3f);
    private static final long    CLOCK_OFFSET = Modules.get(RuntimeProperties.class).get("rtp.clock_offset", 0);
    private static final float   TARGET_COUPLING = Modules.get(RuntimeProperties.class).get("rtp.target_coupling", 0.1f);
    private static final int     AGE_THRESHOLD = Modules.get(RuntimeProperties.class).get("rtp.age_threshold", 5);

    private static final int     MIN_GROUP_SIZE = Modules.get(RuntimeProperties.class).get("rtp.min_group_size", 5);
    private static final int     MAX_GROUP_SIZE = Modules.get(RuntimeProperties.class).get("rtp.max_group_size", 10);
    private static final int     MAX_CORRECTION = Modules.get(RuntimeProperties.class).get("rtp.max_correction", 1000);
    private static final float   ALPHA_TRIM_FACTOR = Modules.get(RuntimeProperties.class).get("rtp.alpha_trim_factor", 0.2f);
    private static final int     RTP_MIN_OFFSET = Modules.get(RuntimeProperties.class).get("rtp.min_offset", 5);
    private static final int     RTP_MAX_DEVIATION = Modules.get(RuntimeProperties.class).get("rtp.max_deviation", 25);
    private static final int     MAX_START_DEVIATION = Modules.get(RuntimeProperties.class).get("rtp.max_start_deviation", 10000);
    private static final boolean TEST = Modules.get(RuntimeProperties.class).get("rtp.test", true);
    private static final boolean NAUGHTY = Modules.get(RuntimeProperties.class).get("rtp.naughty", false);
    private static final int     SKEW = Modules.get(RuntimeProperties.class).get("rtp.skew", 0);

    // Test parameters for Grafana
    private int maxDeviation = Integer.MIN_VALUE;
    private int minDeviation = Integer.MAX_VALUE;
    private int maxOffset = Integer.MIN_VALUE;
    private int minOffset = Integer.MAX_VALUE;
    private int maxGroupSize = Integer.MIN_VALUE;
    private int badRounds = 0;
    private int numberOfPeers = 0;

    public int getMaxDeviation() {
    	return maxDeviation == Integer.MIN_VALUE ? 0 : maxDeviation;
    }

    public int getMinDeviation() {
    	return minDeviation == Integer.MAX_VALUE ? 0 : minDeviation;
    }

    public int getMaxOffset() {
    	return maxOffset;
    }

    public int getMinOffset() {
    	return minOffset;
    }

    public int getMaxGroupSize() {
    	return maxGroupSize;
    }

    public int getBadRounds() {
    	return badRounds;
    }

    public int getNumberOfPeers() {
    	return numberOfPeers;
    }

	public int getInterval() {
		return INTERVAL;
	}

	public int getAgeThreshold() {
		return AGE_THRESHOLD;
	}

	public float getDecay() {
		return DECAY;
	}

	public int getMinGroupSize() {
		return MIN_GROUP_SIZE;
	}

	public int getGroupSize() {
		return MAX_GROUP_SIZE;
	}

	public int getMaxCorrection() {
		return MAX_CORRECTION;
	}

	public float getAlphaTrimFactor() {
		return ALPHA_TRIM_FACTOR;
	}

	public float getTargetCoupling() {
		return TARGET_COUPLING;
	}

	public int getRtpMinOffset() {
		return RTP_MIN_OFFSET;
	}

	public int getRtpMaxDeviation() {
		return RTP_MAX_DEVIATION;
	}

	public int getMaxStartDeviation() {
		return MAX_START_DEVIATION;
	}

	public boolean getTest() {
		return TEST;
	}

	public boolean getNaughty() {
		return NAUGHTY;
	}

	public int getSkew() {
		return SKEW;
	}

	public int getClockOffset() {
		return (int) CLOCK_OFFSET;
	}

    private float compute_coupling()
    {
        double alpha = max(age - AGE_THRESHOLD, 0L);
        float c = max((float) exp(-DECAY * alpha), TARGET_COUPLING);
        if (c <= TARGET_COUPLING) {
        	syncing = false;
        }
        return c;
    }

    private long sys_time_ms()
    {
        return System.currentTimeMillis() + CLOCK_OFFSET;
    }

    private long radix_time()
    {
        // Grafana data
        if (offset > maxOffset) maxOffset = (int) offset;
        if (offset < minOffset) minOffset = (int) offset;

        if ((long) abs(new_offset * compute_coupling()) > 0L) {
        	rtp.debug("[" + age + "]++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++>> offset correction = " + (long) (offset * compute_coupling()) + "ms");
        }

        if (new_offset != last_offset)
        {
            //offset += (long) (new_offset * compute_coupling());
            offset = (long) (new_offset * compute_coupling());
            last_offset = new_offset;
        }

        if (NAUGHTY) {
            return sys_time_ms() + SKEW + offset;
        }

        if (do_resync)
        {
            if (Modules.get(NtpService.class).isSynchronized()) {
                rtp.warn("##### forcing sync to NTP as RTP_MAX_DEVIATION of " + RTP_MAX_DEVIATION + "ms has been exceeded");
                do_resync = false;
                return Modules.get(NtpService.class).getUTCTimeMS();
            } else {
            	rtp.warn("##### cannot force sync to NTP - RTP_MAX_DEVIATION of " + RTP_MAX_DEVIATION + "ms has been exceeded - No NTP service available");
            }
        }

        return sys_time_ms() + offset;
    }

    private class RTPStore
    {
        class RTPStoreEntry
        {
            boolean completed = false;

            long t1 = 0; // sent request
            long t2 = 0; // received request
            long t3 = 0; // sent response
            long t4 = 0; // received response

            RTPStoreEntry() {
            	// Nothing to do here
            }
        }

        final Map<Long, RTPStoreEntry> store = new HashMap<>();

        RTPStore() {}

        void putRequest(long seq, long sent_r)
        {
            RTPStoreEntry entry = new RTPStoreEntry();
            entry.t1 = sent_r;

            synchronized (store) {
                store.put(seq, entry);
            }
        }

        void setResponse(RTPMessage response, long received_r)
        {
            synchronized (store) {
                RTPStoreEntry entry = this.store.get(response.getSeq());
                if (entry != null) {
                    entry.t2 = response.getReceivedR();
                    entry.t3 = response.getSentR();
                    entry.t4 = received_r;
                    entry.completed = true;

                    store.replace(response.getSeq(), entry);
                } else {
                    rtp.info("cant find request for seq = " + response.getSeq());
                }
            }
        }

        void clear() {
            synchronized (store) {
                store.clear();
            }
        }

        List<RTPStoreEntry> getCompleteResponses() {
        	synchronized (store) {
        		return this.store.values().stream()
       				.filter(entry -> entry.completed)
       				.collect(Collectors.toList());
        	}
        }
    }

    private class RTPTimeout extends Executable
    {
        RTPTimeout() { }

        List<RTPStore.RTPStoreEntry> list = new ArrayList<>();
        long offset = 0L;

        @Override
        public void execute()
        {
            synchronized (RTPService.this.store.store)
            {
            	completed.set(true);

                if (RTPService.this.store.store.isEmpty())
                {
                    rtp.info("store is empty");
                    is_synchronized = false;
                    badRounds += 1;
                    return;
                }
                list = RTPService.this.store.getCompleteResponses();
            }

            if (list.isEmpty())
            {
                rtp.info("no completed replies size = " + list.size());
                is_synchronized = false;
                badRounds += 1;
                return;
            }

            rtp.info("RTP round completed (completed samples = " + list.size() + ")");
            list.removeIf(entry -> {
                long rtt = (entry.t4 - entry.t1) - (entry.t3 - entry.t2);
                long offset = entry.t2 - entry.t1 - rtt / 2;

                return age > AGE_THRESHOLD && abs(offset) > MAX_CORRECTION;
            });

            List<Long> offsets = new ArrayList<>();

            for (RTPStore.RTPStoreEntry entry : list)
            {
                long rtt = (entry.t4 - entry.t1) - (entry.t3 - entry.t2);
                offsets.add(entry.t2 - entry.t1 - rtt / 2);
            }

            // do alpha-trim
            if (offsets.size() > MIN_GROUP_SIZE) {
                Collections.sort(offsets);
                int maxToTrim = offsets.size() - MIN_GROUP_SIZE;
                int factorToTrim = Math.round(offsets.size() * ALPHA_TRIM_FACTOR) * 2; // Alpha trim is for each side
                int actualTrim = Math.min(maxToTrim, factorToTrim);
                int leftInclusive = (actualTrim + rand.nextInt(2)) / 2;
                int rightExclusive = offsets.size() - (actualTrim - leftInclusive);

                for (int i = leftInclusive; i < rightExclusive; i++) {
                	offset += offsets.get(i);
                }
                rtp.info("alpha trim removed " + actualTrim + " entries from " + offsets.size());
            } else {
                rtp.info("no alpha trim, got " + offsets.size() + " entries");
                for (Long os : offsets) {
                    offset += os;
                }
            }

            is_synchronized = true;
            rtp.info("new_offset = " + offset / offsets.size());
            new_offset = offset / offsets.size();
        }
    }

    public RTPService() { super(); }

    private List<Peer> getPeersGroup()
    {
        List<Peer> raw_peers = new ArrayList<>();
        if (Modules.isAvailable(PeerStore.class)) {
        	try {
        		PeerFilter filter = PeerFilter.getInstance();
        		Modules.get(PeerStore.class).getPeers(filter).stream()
        			.filter(p -> !p.getSystem().getNID().equals(LocalSystem.getInstance().getNID()))
            		.forEachOrdered(raw_peers::add);
        	} catch (DatabaseException ex) {
        		rtp.error("Unable to find any peers", ex);
        	}
        } else {
    		rtp.error("PeerStore not yet available");
        }

		numberOfPeers = raw_peers.size();
        List<Peer> group = new ArrayList<>();
        if (raw_peers.size() <= MIN_GROUP_SIZE) {
        	group.addAll(raw_peers);
        } else {
        	int groupSize = Math.min(MAX_GROUP_SIZE, raw_peers.size());
        	Collections.shuffle(raw_peers, rand);
        	group.addAll(raw_peers.subList(0, groupSize));
        }

        // Grafana data
        lastGroupSize = group.size();
        if (lastGroupSize > maxGroupSize) {
        	maxGroupSize = lastGroupSize;
        }
        return group;
    }

    @Override
    public void start_impl()
    {
    	log.info("Starting RTP ...");
        rtp.info("setup ...");
        radix_genesis_time = System.currentTimeMillis();

        if (Modules.get(NtpService.class).isSynchronized())
        {
            ntp_genesis_time = Modules.get(NtpService.class).getUTCTimeMS();
            rtp.info("setting NTP radix_genesis_time to " + radix_genesis_time);
        } else {
            rtp.warn("NTP service is not available for reference");
        }

        if (Modules.get(NtpService.class).isSynchronized()) {
            if (abs(radix_genesis_time - ntp_genesis_time) > Modules.get(RuntimeProperties.class).get("rtp.max_start_deviation", 30000)) // 30 seconds
            {
                // FIXME what should we do in this case? Raise some sort of exception? Refuse to start until this is corrected ?
                rtp.error("System time deviation from NTP is too high. FIX THIS ");
            }
        }

        rtp.info("rtp broadcast interval = " + Modules.get(RuntimeProperties.class).get("rtp.interval", 4));

        if (Modules.get(RuntimeProperties.class).get("rtp.naughty", false))
            rtp.info("Clock is artificially skewed by " + Modules.get(RuntimeProperties.class).get("rtp.skew", 10));

        store.clear();

        //Messages
        register("rtp.message", new MessageProcessor<RTPMessage>()
        {
            @Override
            public void process(RTPMessage rtpMessage, Peer peer)
            {
                if (rtpMessage.getMessageType() == 0)
                {
                    rtp.info("got a request message from " + peer.getURI());
                    RTPMessage response = new RTPMessage(
                    		1,
                    		rtpMessage.getSeq(),
                    		LogicalClock.getInstance().get(),
                    		radix_time(),
                    		LogicalClock.getInstance().get(),
                    		radix_time());


                    UDPPeer udp = Network.getInstance().get(peer.getURI(), Protocol.UDP, State.CONNECTED);
                    if (udp == null) {
                    	rtp.info("no connected peer to request from");
                    } else {

                    	try {
                    		udp.send(response);
                    	} catch (IOException ex) {
                    		rtp.error("unable to send RTP response", ex);
                    		badRounds += 1;
                    	}
                    }
                } else {
					if (!completed.get()) {
						rtp.info("got a response message from " + peer.getURI());
						store.setResponse(rtpMessage, radix_time());
					} else {
						rtp.info("got a response after timeout");
					}
                }
            }
        });

        // Scheduled main loop
        int rtpInterval = Modules.get(RuntimeProperties.class).get("rtp.interval", 4);
        scheduleAtFixedRate(new ScheduledExecutable(rtpInterval, rtpInterval, TimeUnit.SECONDS)
        {
            @Override
            public void execute()
            {
            	rtp.info("Begin RTP round " + age);
                try
                {
                    store.clear();
                    completed.set(false);

                    // set timeout
                    Executor.getInstance().schedule(new RTPTimeout(), TIMEOUT, TimeUnit.SECONDS);

                    List<Peer> peers = getPeersGroup();

                    if (!peers.isEmpty())
                    {
                        for (Peer peer : peers)
                        {
                        	UDPPeer udp = Network.getInstance().get(peer.getURI(), Protocol.UDP, State.CONNECTED);
                        	if (udp != null) {
                        		long seq = sequence.incrementAndGet();
                                RTPMessage request = new RTPMessage(0, seq);

                                udp.send(request);
                                rtp.info("request sent to " + peer.getURI());
                                store.putRequest(seq, radix_time());
                            } else {
                                rtp.info("no connected UDPPeer found for Peer " + peer.getURI().getHost() + " ... skipping");
                            }
                        }
                    } else {
                        rtp.error("No peers available");
                    }
                    long t = radix_time();
                    if (Modules.get(NtpService.class).isSynchronized())
                    {
                        deviation = (int) (Modules.get(NtpService.class).getUTCTimeMS() - t + (radix_genesis_time - ntp_genesis_time));

                        // Grafana data
                        maxDeviation = Math.max(deviation, maxDeviation);
                        minDeviation = Math.min(deviation, minDeviation);

                        if (deviation > RTP_MAX_DEVIATION) {
                            is_synchronized = false;
                            // try to force a resync with NTP
                            forceResync();
                        }

                        if (! syncing)
                            rtp.info("[" + age + "}:==============================>> radix time = " + t + "  (deviation from NTP = " + deviation + "ms)");
                        else
                        	rtp.info("[" + age + "}:======{syncing}===============>> radix time = " + t + "  (deviation from NTP = " + deviation + "ms)");
                    } else
                        if (! syncing)
                        	rtp.info("[" + age + "}:==============================>> radix time = " + t);
                        else
                        	rtp.info("[" + age + "}:======{syncing}===============>> radix time = " + t);
                }
                catch (Exception ex)
                {
                	rtp.error("RTP round failed", ex);
                    is_synchronized = false;
                }
                age += 1;
            }
        });
    }

    private void forceResync()
    {
        do_resync= true;
    }

    @Override
    public void stop_impl() { }

    @Override
    public String getName() { return "RTPService"; }

    public int getLastGroupSize() {
    	return lastGroupSize;
    }

    public boolean isSynchronized() {
    	return is_synchronized && ! syncing;
    }

    /**
     * Returns the current offset in milliseconds
     *
     * @return
     */
	public synchronized int getOffset() {
		return (int) offset;
	}

    /**
     * Returns the current deviation from NTP in milliseconds (if NTP is enabled)
     *
     * @return
     */
	public synchronized int getDeviation() {
		return deviation;
	}

    /**
     * Returns a corrected UTC time in milliseconds
     *
     * @return
     */
	public synchronized long getUTCTimeMS() {
		return radix_time();
	}

    /**
     * Returns an RTPTimestamp
     *
     * @return
     */
    public synchronized RTPTimestamp getRTPTimestamp()
    {
        return new RTPTimestamp(
        	LocalSystem.getInstance().getNID(),
                radix_time(),
                LocalSystem.getInstance().getClock().get(),
                is_synchronized && ! syncing,
        	deviation);
    }
}