package org.radix.network.discovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.radixdlt.common.EUID;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.MathUtils;
import org.radix.network.peers.filters.PeerFilter;
import org.radix.network.peers.filters.UDPPeerFilter;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;

/**
 * Discovers peers which the local node will hold TCP connections with for the purpose of synchronisation.
 *
 * @author Dan
 *
 */
public class SyncDiscovery
{
	private static final Logger networkLog = Logging.getLogger("network");

	public static synchronized SyncDiscovery getInstance()
	{
		if (instance == null)
			instance = new SyncDiscovery();

		return instance;
	}

	private static SyncDiscovery instance = null;

	public static final class SyncPeerDistanceComparator implements Comparator<Peer>
	{
		private final static long seed = System.currentTimeMillis();

		private final EUID origin;

		public SyncPeerDistanceComparator(EUID origin)
		{
			this.origin = origin;
		}

		@Override
		public int compare(Peer p1, Peer p2)
		{
			long d1 = Math.abs((this.origin.getLow() * SyncPeerDistanceComparator.seed) - p1.getSystem().getNID().getLow());
			long d2 = Math.abs((this.origin.getLow() * SyncPeerDistanceComparator.seed) - p2.getSystem().getNID().getLow());

			if (d1 < d2)
				return -1;
			else if (d1 > d2)
				return 1;

			return 0;
		}
	}

	private SyncDiscovery()
	{
	}

	public Collection<Peer> discover(PeerFilter filter)
	{
		List<Peer> results = new ArrayList<>();

		try
		{
			// Handle running without PeerHandler/test conditions a little better
			final List<Peer> peers;
			if (Modules.isAvailable(AddressBook.class)) {
				UDPPeerFilter udpFilter = new UDPPeerFilter();
				SyncPeerDistanceComparator comparator = new SyncPeerDistanceComparator(LocalSystem.getInstance().getNID());
				peers = Modules.get(AddressBook.class).recentPeers()
					.filter(p -> !udpFilter.filter(p))
					.sorted(comparator)
					.collect(Collectors.toList());
			} else {
				peers = Collections.emptyList();
			}
			if (peers.isEmpty() == true)
				return results;

			List<Peer> candidatePeers = new ArrayList<Peer>();
			for (Peer peer : peers)
				if (filter == null || !filter.filter(peer))
					candidatePeers.add(peer);

			int shardRedundancy = Math.max(3, MathUtils.log2(peers.size()));
			long remainingCoverage = LocalSystem.getInstance().getShards().getRange().getSpan();
			Iterator<Peer> candidatePeersIterator = candidatePeers.iterator();

			while (shardRedundancy > 0 && candidatePeersIterator.hasNext() == true)
			{
				Peer candidatePeer = candidatePeersIterator.next();

				if (candidatePeer.getSystem().getShards().intersects(LocalSystem.getInstance().getShards()) == false)
					continue;

				results.add(candidatePeer);
				networkLog.debug("Added "+candidatePeer+" to shard space set");

				long coverage = Math.max(0, Math.min(candidatePeer.getSystem().getShards().getRange().getHigh(), LocalSystem.getInstance().getShards().getRange().getHigh()) -
											Math.max(candidatePeer.getSystem().getShards().getRange().getLow(), LocalSystem.getInstance().getShards().getRange().getLow()));
				remainingCoverage -= coverage;

				if (remainingCoverage <= 0)
				{
					shardRedundancy--;
					remainingCoverage = LocalSystem.getInstance().getShards().getRange().getSpan();
				}
			}

			if (shardRedundancy > 0)
				networkLog.debug("Shard space redundancy not met");
			else
				networkLog.debug("Shard space redundancy met");

		}
		catch (Exception ex)
		{
			networkLog.error("Could not process SyncDiscovery", ex);
		}

		return results;
	}
}
