package org.radix.network;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.radix.common.Syncronicity;

import com.radixdlt.common.EUID;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.events.Event.EventPriority;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.modules.exceptions.ModuleStopException;
import org.radix.network.discovery.Whitelist;
import org.radix.network.messaging.Messaging;
import org.radix.network.peers.Peer;
import org.radix.network.peers.PeerListener;
import org.radix.network.peers.PeerStore;
import org.radix.network.peers.UDPPeer;
import org.radix.network.peers.events.PeerConnectingEvent;
import org.radix.network.peers.events.PeerDisconnectedEvent;
import org.radix.network.peers.events.PeerEvent;
import org.radix.properties.RuntimeProperties;
import org.radix.state.State;
import org.radix.state.State.StateDefinition;
import org.radix.time.NtpService;
import org.radix.time.Timestamps;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.SystemMessage;
import org.radix.utils.SystemMetaData;

public class Network extends Service
{
	private static final Logger log = Logging.getLogger ();

	public static final String URI_PREFIX = "RADIX://";

	private static Network instance = null;

	public static synchronized Network getInstance()
	{
		if (instance == null)
			instance = new Network();

		return instance;
	}

	public static URI getURI(String host)
	{
		if (!host.toUpperCase().startsWith(Network.URI_PREFIX))
			host = Network.URI_PREFIX+host;

		URI uri = URI.create(host);

		if ((uri.getPath() != null && uri.getPath().length() > 0) || (uri.getQuery() != null && uri.getQuery().length() > 0))
			throw new IllegalArgumentException(uri+": Paths or queries are not permitted in peer URIs");

		if (uri.getPort() == -1)
		{
			host += ":"+Modules.get(Universe.class).getPort();
			uri = URI.create(host);
		}

		return uri;
	}

	public static URI getURI(String host, int port)
	{
		return URI.create(Network.URI_PREFIX+host+":"+(port == -1?+Modules.get(Universe.class).getPort():port));
	}

    private final Set<Peer>		peers = new HashSet<>();
	private final Whitelist 	whitelist = new Whitelist(Modules.get(RuntimeProperties.class).get("network.whitelist", ""));
    private final ReentrantLock connecting = new ReentrantLock();

    private Network()
    {
    	super();
    }

	@Override
	public void start_impl() throws ModuleException
	{
    	try
    	{
			Events.getInstance().register(PeerEvent.class, this.peerListener);

			// HOUSE KEEPING / DISCOVERY //
			scheduleAtFixedRate(new ScheduledExecutable(10, 10, TimeUnit.SECONDS)
			{
				@Override
				public void execute()
				{
					// Housekeeping //
					try
					{
						Set<Peer> toDisconnect = new HashSet<Peer>();

						synchronized(peers)
						{
							for (Peer peer : peers)
								if (Modules.get(NtpService.class).getUTCTimeMS() - peer.getTimestamp(Timestamps.ACTIVE) > TimeUnit.SECONDS.toMillis(Modules.get(RuntimeProperties.class).get("network.peer.inactivity", 60)) &&
									Modules.get(NtpService.class).getUTCTimeMS() - peer.getTimestamp(Timestamps.CONNECTED) > TimeUnit.SECONDS.toMillis(Modules.get(RuntimeProperties.class).get("network.peer.inactivity", 60)))
									toDisconnect.add(peer);
						}

						for (Peer peer : toDisconnect)
							peer.disconnect("Peer is silent for "+TimeUnit.MILLISECONDS.toSeconds(Modules.get(NtpService.class).getUTCTimeMS() - peer.getTimestamp(Timestamps.ACTIVE))+" seconds");
					}
					catch (Exception ex)
					{
						log.error(ex);
					}

					// System Heartbeat //
					synchronized(peers)
					{
						for (Peer peer : peers)
						{
							if(peer.getState().in(State.CONNECTED))
							{
								try
								{
									Messaging.getInstance().send(new SystemMessage(), peer);
								}
								catch (IOException ioex)
								{
									log.error("Could not send System heartbeat to "+peer, ioex);
								}
							}
						}
	    			}
				}
			});
    	}
    	catch (Exception ex)
    	{
    		throw new ModuleStartException(ex, this);
    	}
	}

	@Override
	public void stop_impl() throws ModuleException
	{
    	try
		{
			synchronized(peers)
			{
				for (Peer peer : peers)
					peer.disconnect("Stopping network");
			}
		}
		catch (Exception ex)
		{
			throw new ModuleStopException(ex, this);
		}
	}

	public boolean isWhitelisted(URI host)
	{
		return whitelist.accept(host);
	}

    public <T extends Peer> T connect(URI uri, Protocol protocol) throws IOException
    {
		if (!isWhitelisted(uri))
			throw new SocketException(uri+" is not whitelisted");

		try
		{
			connecting.lock();

			Peer peer = get(uri, protocol, State.CONNECTING, State.CONNECTED);

			if (peer == null)
			{
		    	if (protocol.toString().equalsIgnoreCase(Protocol.UDP.toString()) == true)
		    	{
					peer = Modules.get(PeerStore.class).getPeer(Network.getURI(uri.getHost(), uri.getPort()));
					peer = new UDPPeer(uri, peer);
					Modules.ifAvailable(SystemMetaData.class, a -> a.increment("udp_connects"));
		    	}
			}

			return (T) peer;
		}
		finally
		{
			connecting.unlock();
		}
    }

	public List<Peer> get(Protocol protocol, StateDefinition ... states)
	{
		return get(protocol, false, states);
	}

	public List<Peer> get(Protocol protocol, boolean shuffle, StateDefinition ... states)
	{
		synchronized(peers)
		{
			List<Peer>	peers = new ArrayList<>();

			for (Peer peer : this.peers)
			{
				if (peer.hasProtocol(protocol) &&
					(states == null || states.length == 0 || Arrays.stream(states).collect(Collectors.toSet()).contains(peer.getState().getDefinition())))
					peers.add(peer);
			}

			if (shuffle == false)
				return peers;
			else
			{
				Collections.shuffle(peers);
				return peers;
			}
		}
	}

	public <T extends Peer> T get(URI host, Protocol protocol, StateDefinition ... states)
	{
		synchronized(peers)
		{
			for (Peer peer : this.peers)
			{
				if (peer.hasProtocol(protocol) &&
					peer.getURI().getHost().equalsIgnoreCase(host.getHost()) &&
					(states == null || states.length == 0 || Arrays.stream(states).collect(Collectors.toSet()).contains(peer.getState().getDefinition())))
					return (T) peer;
			}

			return null;
		}
	}

	public boolean has(URI host, Protocol protocol)
	{
		synchronized(peers)
		{
			for (Peer peer : peers)
			{
				if (peer.hasProtocol(protocol) &&
					peer.getURI().getHost().equalsIgnoreCase(host.getHost()))
					return true;
			}

			return false;
		}
	}

	public boolean has(Collection<URI> hosts, Protocol protocol)
	{
		synchronized(peers)
		{
			for (Peer peer : peers)
			{
				if (peer.hasProtocol(protocol))
				{
					for (URI host : hosts)
						if (peer.getURI().getHost().equalsIgnoreCase(host.getHost()))
							return true;
				}
			}

			return false;
		}
	}

	public <T extends Peer> T get(EUID NID, Protocol protocol, StateDefinition ... states)
	{
		synchronized(peers)
		{
			for (Peer peer : this.peers)
			{
				if (peer.hasProtocol(protocol) &&
					peer.getSystem().getNID().equals(NID) &&
					(states == null || states.length == 0 || Arrays.stream(states).collect(Collectors.toSet()).contains(peer.getState().getDefinition())))
					return (T) peer;
			}
		}

		return null;
	}

	public boolean has(EUID NID, Protocol protocol, StateDefinition ... states)
	{
		synchronized(peers)
		{
			for (Peer peer : peers)
			{
				if (peer.hasProtocol(protocol) &&
					peer.getSystem().getNID().equals(NID) &&
					(states == null || states.length == 0 || Arrays.stream(states).collect(Collectors.toSet()).contains(peer.getState().getDefinition())))
					return true;
			}

			return false;
		}
	}

    // PEER LISTENER //
    private PeerListener peerListener = new PeerListener()
    {
    	@Override
    	public int getPriority()
    	{
    		return EventPriority.HIGH.priority();
    	}

    	@Override
		public Syncronicity getSyncronicity()
		{
			return Syncronicity.SYNCRONOUS;
		}

		@Override
		public void process(PeerEvent event)
		{
			if (event instanceof PeerConnectingEvent)
			{
				synchronized(Network.this.peers)
				{
					boolean add = true;
					// Want to check on reference not equality as we can have multiple instances
					// that may satisfy the equality check that we want to keep track of.
					for (Peer peer : Network.this.peers)
					{
						if (peer == event.getPeer())
						{
							add = false;
							break;
						}
					}

					if (add)
						Network.this.peers.add(event.getPeer());
				}
			}

			if (event instanceof PeerDisconnectedEvent)
			{
				synchronized(Network.this.peers)
				{
					// Want to check on reference not equality as we can have multiple instances
					// that may satisfy the equality check that we want to keep track of.
					Iterator<Peer> peersIterator = Network.this.peers.iterator();
					while (peersIterator.hasNext())
					{
						Peer peer = peersIterator.next();
						if (peer == event.getPeer())
						{
							peersIterator.remove();
							break;
						}
					}
				}
			}
		}
    };
}
