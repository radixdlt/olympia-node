package org.radix.network.peers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.radixdlt.common.EUID;
import org.radix.common.executors.Executor;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.database.exceptions.DatabaseException;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.network.Network;
import org.radix.network.Protocol;
import org.radix.network.discovery.BootstrapDiscovery;
import org.radix.network.messages.GetPeersMessage;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messages.PeerPongMessage;
import org.radix.network.messages.PeersMessage;
import org.radix.network.peers.events.PeerAvailableEvent;
import org.radix.network.peers.events.PeerDisconnectedEvent;
import org.radix.network.peers.events.PeerEvent;
import org.radix.network.peers.filters.PeerBroadcastFilter;
import org.radix.network.peers.filters.PeerFilter;
import org.radix.network2.messaging.MessageCentral;
import org.radix.properties.RuntimeProperties;
import org.radix.state.State;
import org.radix.time.NtpService;
import org.radix.time.Timestamps;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.LocalSystem;

public class PeerHandler extends Service
{
//	private static final Logger log = Logging.getLogger();
	private static final Logger networklog = Logging.getLogger("network");

	private static final long PROBE_TIMEOUT_SECONDS = 20;

	public static final Comparator<Peer> SHUFFLER = new Comparator<Peer>()
	{
		Random random = new Random(System.currentTimeMillis());

		@Override
		public int compare(Peer p1, Peer p2)
		{
			int rn = random.nextInt();

			if (rn < 0)
				return -1;

			if (rn > 1)
				return 1;

			return 0;
		}
	};

	public static final class PeerDistanceComparator implements Comparator<Peer>
	{
		private final EUID origin;

		public PeerDistanceComparator(EUID origin)
		{
			this.origin = origin;
		}

		@Override
		public int compare(Peer p1, Peer p2)
		{
			return origin.compareXorDistances(p2.getSystem().getNID(), p1.getSystem().getNID());
		}
	}

	private class ProbeTimeout extends PeerTask
	{
		private final long nonce;

		ProbeTimeout(final Peer peer, final long nonce, long delay, TimeUnit unit)
		{
			super(peer, delay, unit);

			this.nonce = nonce;
		}

		@Override
		public void execute()
		{
			synchronized(PeerHandler.this.probes)
			{
				if (PeerHandler.this.probes.containsKey(getPeer()) && PeerHandler.this.probes.get(getPeer()).longValue() == this.nonce)
				{
					PeerHandler.this.probes.remove(getPeer());

					synchronized(PeerHandler.this.network)
					{
						PeerHandler.this.network.remove(getPeer().getSystem().getNID());
						networklog.debug("Probe of "+getPeer()+" with nonce '"+this.nonce+"' timed out");
					}
				}
			}
		}
	}

	public enum PeerDomain
	{
		NETWORK, PERSISTED
	}

	private Map<Peer, Long>	probes = new HashMap<Peer, Long>(); // TODO can convert to simpler Map<EUID, Long>?
	private Map<EUID, Peer>	network = new HashMap<EUID, Peer>();  // TODO can convert this to a simple Set<EUID>?

	public PeerHandler()
	{
		super();
	}

	@Override
	public void start_impl() throws ModuleException {
		register(PeersMessage.class, (peer, peersMessage) ->
		{
			for (Peer p : peersMessage.getPeers()) {
				if (p.getSystem() == null)
					continue;

				if (p.getSystem().getNID().equals(LocalSystem.getInstance().getNID()) == true)
					continue;

				try {
					if (Modules.get(PeerStore.class).hasPeer(p.getSystem().getNID()) == false)
						Modules.get(PeerStore.class).storePeer(p);
				} catch (DatabaseException dbex) {
					networklog.error("Failed to store '" + p + "'", dbex);
				}
			}
		});

		register(GetPeersMessage.class, (peer, getPeersMessage) -> {
			try {
				// Deliver known Peers in its entirety, filtered on whitelist and activity
				// Chunk the sending of Peers so that UDP can handle it
				PeersMessage peersMessage = new PeersMessage();
				List<Peer> peers = Modules.get(PeerStore.class).getPeers(new PeerBroadcastFilter());

				for (Peer p : peers) {
					if (p.getSystem().getNID().equals(peer.getSystem().getNID()))
						continue;

					peersMessage.getPeers().add(p);

					if (peersMessage.getPeers().size() == 64) {
						Modules.get(MessageCentral.class).send(peer, peersMessage);
						peersMessage = new PeersMessage();
					}
				}

				if (!peersMessage.getPeers().isEmpty()){
					Modules.get(MessageCentral.class).send(peer, peersMessage);
				}
			} catch (Exception ex) {
				networklog.error("peers.get " + peer, ex);
			}
		});

		register(PeerPingMessage.class, (peer, message) -> {
			try {
				networklog.debug("peer.ping from " + peer + " with nonce '" + message.getNonce() + "'");
				synchronized (PeerHandler.this.network) {
					PeerHandler.this.network.put(peer.getSystem().getNID(), new Peer(peer.getURI(), peer));
				}
				Modules.get(MessageCentral.class).send(peer, new PeerPongMessage(message.getNonce()));
				Events.getInstance().broadcast(new PeerAvailableEvent(peer));
			} catch (Exception ex) {
				networklog.error("peer.ping " + peer, ex);
			}
		});

		register(PeerPongMessage.class, (peer, message) -> {
			try {
				synchronized (PeerHandler.this.probes) {
					synchronized (PeerHandler.this.network) {
						PeerHandler.this.network.put(peer.getSystem().getNID(), new Peer(peer.getURI(), peer));
					}

					if (PeerHandler.this.probes.containsKey(peer) &&
							PeerHandler.this.probes.get(peer).longValue() == message.getNonce()) {
						PeerHandler.this.probes.remove(peer);
						networklog.debug("Got peer.pong from " + peer + " with nonce '" + message.getNonce() + "'");
						Events.getInstance().broadcast(new PeerAvailableEvent(peer));
					} else
						networklog.debug("Got peer.pong without matching probe from " + peer + " with nonce '" + message.getNonce() + "'");
				}
			} catch (Exception ex) {
				networklog.error("peer.pong " + peer, ex);
			}
		});

        // PEERS HOUSEKEEPING //
		scheduleWithFixedDelay(new ScheduledExecutable(60, Modules.get(RuntimeProperties.class).get("network.peers.broadcast.interval", 30), TimeUnit.SECONDS)
		{
			@Override
			public void execute()
			{
				try
				{
					// Clean out aged peers with no activity
					for (Peer peer : Modules.get(PeerStore.class).getPeers(null)) {
						if ((peer.getSystem().getNID().equals(EUID.ZERO) == true || Network.getInstance().has(peer.getSystem().getNID(), Protocol.UDP) == false) &&
							Modules.get(NtpService.class).getUTCTimeMS() - peer.getTimestamp(Timestamps.ACTIVE) >= Modules.get(Universe.class).getPlanck()) {
							Modules.get(PeerStore.class).deletePeer(peer.getSystem().getNID());

							synchronized (PeerHandler.this.network) {
								PeerHandler.this.network.remove(peer.getSystem().getNID());
							}
						}
					}

					// Request peers information from connected nodes
					List<Peer> peers = Network.getInstance().get(Protocol.UDP, State.CONNECTED);

					if (peers.isEmpty() == false) {
						Collections.shuffle(peers);
						Modules.get(MessageCentral.class).send(peers.get(0), new GetPeersMessage());
					}
				}
				catch (Throwable t)
				{
					networklog.error("Peers update failed", t);
				}
			}
		});

		// PROBING //
		scheduleWithFixedDelay(new ScheduledExecutable(0, Modules.get(RuntimeProperties.class).get("network.peers.probe.interval", 1), TimeUnit.SECONDS)
		{
			private int index = 0;
			private int numPeers = 0;

			@Override
			public void execute()
			{
				try
				{
					int numProbes = (int) (this.numPeers/TimeUnit.MILLISECONDS.toSeconds(Modules.get(Universe.class).getPlanck()));

					if (numProbes == 0)
						numProbes = 16;

					List<Peer> toProbe = Modules.get(PeerStore.class).getPeers(this.index, numProbes<<1, new PeerFilter());

					if (this.index + toProbe.size() > this.numPeers)
						this.numPeers = this.index + toProbe.size();

					this.index += numProbes;

					if (this.index >= this.numPeers)
						this.index = 0;

					if (toProbe.isEmpty() == false)
					{
						for (Peer peer : toProbe.subList(0, Math.min(toProbe.size(), numProbes)))
							probe(peer.getURI());
					}
				}
				catch (Exception ex)
				{
					networklog.error("Peer probing failed", ex);
				}
			}
		});

		Events.getInstance().register(PeerEvent.class, this.peerListener);

		// Clean out any existing non-whitelisted peers from the store (whitelist may have changed since last execution) //
		try
		{
			for (Peer peer : Modules.get(PeerStore.class).getPeers(null))
			{
				if (!Network.getInstance().isWhitelisted(peer.getURI()))
				{
					networklog.debug("Deleting "+peer.getURI()+" as not whitelisted");
					Modules.get(PeerStore.class).deletePeer(peer.getURI());
				}
			}
		}
		catch (DatabaseException dbex)
		{
			throw new ModuleStartException(dbex, this);
		}

		Executor.getInstance().schedule(new ScheduledExecutable(1, 60, TimeUnit.SECONDS)
		{
			@Override
			public void execute()
			{
				discoverPeers();
			}
		});
	}

	// Needs to return something, as it is used as a Callable
	private void discoverPeers() {
		// Probe all the bootstrap hosts so that they know about us //
		Collection<URI> bootstrap = BootstrapDiscovery.getInstance().discover(new PeerFilter());

		for (URI host : bootstrap)
			probe(host);

		// Ask them for known peers too //
		for (URI host : bootstrap)
		{
			UDPPeer bootstrapPeer = Network.getInstance().get(host, Protocol.UDP, State.CONNECTED);

			if (bootstrapPeer == null){
				continue;
			}
			Modules.get(MessageCentral.class).send(bootstrapPeer, new GetPeersMessage());
		}
	}

	@Override
	public void stop_impl()
	{
		Events.getInstance().deregister(PeerEvent.class, this.peerListener);
	}

	@Override
	public String getName() { return "Peers Handler"; }

	private boolean probe(URI host)
	{
		try
		{
			synchronized(this.probes)
			{
				Peer peer = Network.getInstance().get(host, Protocol.UDP, State.CONNECTED);

				if (peer == null)
					peer = Modules.get(PeerStore.class).getPeer(host);

				if (peer != null &&
					(Modules.get(NtpService.class).getUTCTimeMS() - peer.getTimestamp(Timestamps.PROBED) < TimeUnit.SECONDS.toMillis(Modules.get(RuntimeProperties.class).get("network.peer.probe.delay", 30)) ||
					 Modules.get(NtpService.class).getUTCTimeMS() - peer.getTimestamp(Timestamps.DISCONNECTED) < TimeUnit.SECONDS.toMillis(Modules.get(RuntimeProperties.class).get("network.peer.probe.delay", 30))))
					return false;

				if ((peer instanceof UDPPeer) == false)
					peer = Network.getInstance().connect(host, Protocol.UDP);

				if (peer != null && !this.probes.containsKey(peer))
				{
					PeerPingMessage ping = new PeerPingMessage();

					this.probes.put(peer, ping.getNonce());
					networklog.debug("Probing "+peer+" with nonce '"+ping.getNonce()+"'");
					Executor.getInstance().schedule(new ProbeTimeout(peer, ping.getNonce(), PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS));

					Modules.get(MessageCentral.class).send(peer, ping);
					peer.setTimestamp(Timestamps.PROBED, Modules.get(NtpService.class).getUTCTimeMS());
					return true;
				}
			}
		}
		catch (Exception ex)
		{
			networklog.error("Probe of peer "+host+" failed", ex);
		}

		return false;
	}

	public Peer getPeer(PeerDomain domain, EUID NID) throws DatabaseException
	{
		if (domain.equals(PeerDomain.PERSISTED))
			return  Modules.get(PeerStore.class).getPeer(NID);
		else
		{
			synchronized(this.network)
			{
				return this.network.get(NID);
			}
		}
	}

	public List<Peer> getPeers(PeerDomain domain) throws DatabaseException
	{
		return getPeers(domain, (PeerFilter) null, (Comparator<Peer>) null);
	}

	public List<Peer> getPeers(PeerDomain domain, PeerFilter filter) throws DatabaseException
	{
		return getPeers(domain, filter, null);
	}

	public List<Peer> getPeers(PeerDomain domain, PeerFilter filter, Comparator<Peer> sorter) throws DatabaseException
	{
		List<Peer> peers = new ArrayList<Peer>();

		if (domain.equals(PeerDomain.PERSISTED))
			peers = Modules.get(PeerStore.class).getPeers(filter);
		else
		{
			synchronized(this.network)
			{
				for (EUID NID : this.network.keySet())
				{
					Peer peer = this.network.get(NID);

					if (filter == null || !filter.filter(peer))
						peers.add(peer);
				}
			}
		}

		if (sorter != null)
			peers.sort(sorter);

		return Collections.unmodifiableList(peers);
	}

	public List<Peer> getPeers(PeerDomain domain, Collection<EUID> NIDS) throws DatabaseException
	{
		return getPeers(domain, NIDS, null, null);
	}

	public List<Peer> getPeers(PeerDomain domain, Collection<EUID> NIDS, PeerFilter filter) throws DatabaseException
	{
		return getPeers(domain, NIDS, filter, null);
	}

	public List<Peer> getPeers(PeerDomain domain, Collection<EUID> NIDS, PeerFilter filter, Comparator<Peer> sorter) throws DatabaseException
	{
		List<Peer> peers = new ArrayList<Peer>();

		if (domain.equals(PeerDomain.PERSISTED))
		{
			for (EUID NID : NIDS)
			{
				Peer peer = Modules.get(PeerStore.class).getPeer(NID);

				if (peer == null)
					continue;

				peers.add(peer);
			}
		}
		else
		{
			synchronized(this.network)
			{
				for (EUID NID : NIDS)
				{
					Peer peer = this.network.get(NID);

					if (peer == null)
						continue;

					if (filter == null || !filter.filter(peer))
						peers.add(peer);
				}
			}
		}

		if (sorter != null)
			peers.sort(sorter);

		return Collections.unmodifiableList(peers);
	}

	// PEER LISTENER //
	PeerListener peerListener = new PeerListener()
	{
		@Override
		public void process(PeerEvent event)
		{
			try
			{
				if (event instanceof PeerAvailableEvent)
					Modules.get(PeerStore.class).storePeer(event.getPeer());

				if (event instanceof PeerDisconnectedEvent)
				{
					if (!Network.getInstance().isWhitelisted(event.getPeer().getURI()))
					{
						networklog.debug("Store aborted, "+event.getPeer().getURI()+" is not whitelisted");
						return;
					}

					if (event.getPeer().getSystem() != null && event.getPeer().getSystem().getNID().equals(EUID.ZERO) == false)
					{
						if (Modules.get(Network.class).has(event.getPeer().getSystem().getNID(), Protocol.UDP))
							return;

						Modules.get(PeerStore.class).storePeer(event.getPeer());
					}
				}
			}
			catch (Exception ex)
			{
				networklog.error("Peer event "+event+" processing failed", ex);
			}
		}
	};
}
