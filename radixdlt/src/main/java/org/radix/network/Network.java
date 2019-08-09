package org.radix.network;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
import org.radix.network.messaging.Message;
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
import org.radix.utils.SystemProfiler;

import static org.radix.network.PublicInetAddress.isPublicUnicastInetAddress;

public class Network extends Service
{
	private static final Logger log = Logging.getLogger ();
	private static final Logger networklog = Logging.getLogger("network");

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

    private final Set<Peer>		peers = new HashSet<Peer>();
	private final Whitelist 	whitelist = new Whitelist(Modules.get(RuntimeProperties.class).get("network.whitelist", ""));
    private final ReentrantLock connecting = new ReentrantLock();
	private PublicInetAddress   localAddress = PublicInetAddress.getInstance();

	private DatagramSocket			UDPServerSocket = null;
    private DatagramSocket 			UDPClientSocket = null;
	private	final BlockingQueue<DatagramPacket> datagramQueue = new LinkedBlockingQueue<DatagramPacket>();

    private Runnable UDPListener = new Runnable()
    {
		@Override
		public void run()
		{
			byte[] buf = new byte[65536];
		    DatagramPacket datagramPacket = null;

		    try
		    {
		    	while (!UDPServerSocket.isClosed())
		    	{
		    		if (datagramPacket == null)
		    		    datagramPacket = new DatagramPacket(buf, buf.length);

		    		try
		    		{
		    			UDPServerSocket.receive(datagramPacket);
		    		}
		    		catch (SocketTimeoutException stex)
		    		{
		    			continue;
		    		}

		    		long start = SystemProfiler.getInstance().begin();

		    		try
		    		{
						byte[] data = new byte[datagramPacket.getLength()];
						System.arraycopy(datagramPacket.getData(), 0, data, 0, datagramPacket.getLength());
						DatagramPacket datagramPacketCopy = new DatagramPacket(data, data.length, datagramPacket.getSocketAddress());
			    		if (Network.this.datagramQueue.offer(datagramPacketCopy) == false)
			    			log.error("UDP datagram could not be queued");

			    		datagramPacket = null;
		    		}
		    		finally
		    		{
		    			SystemProfiler.getInstance().incrementFrom("NETWORK:UDP_PACKET_RECEIVER", start);
		    		}
		    	}
		    }
		    catch (Exception ex)
		    {
		    	log.fatal("UDP receiver thread quit on: ", ex);
		    }
		}
	};

    private Runnable UDPworker = new Runnable()
    {
		@Override
		public void run()
		{
		    try
		    {
		    	while (!UDPServerSocket.isClosed())
		    	{
		    		DatagramPacket datagramPacket = null;

					try {
						datagramPacket = datagramQueue.poll(1, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						// Give up and exit if interrupted
						Thread.currentThread().interrupt();
						break;
					}

					if (datagramPacket == null)
						continue;

					// part of the local address validation process
					if (localAddress.endValidation(datagramPacket.getData(), datagramPacket.getOffset(), datagramPacket.getLength()))
						continue;

		    		long start = SystemProfiler.getInstance().begin();

					try
					{
						Network.this.connecting.lock();

						byte[] data = datagramPacket.getData();
						InetAddress peerAddress = datagramPacket.getAddress();

						int dataOffset = 0;
						if (data.length > 0 && (data[0] & 0x80) != 0)
						{
							// NAT: decode the source and dest addresses (see UDPPeer for how this is encoded)
							byte[] rawLocalAddress = new byte[(data[0] & 0x01) != 0 ? 16 : 4];
							byte[] rawPeerAddress = new byte[(data[0] & 0x02) != 0 ? 16 : 4];

							if ((rawPeerAddress.length + rawLocalAddress.length) < data.length)
							{
								System.arraycopy(data, 1, rawPeerAddress, 0, rawPeerAddress.length);
								InetAddress addr = InetAddress.getByAddress(rawPeerAddress);
								// TODO: if addr is previously unknown we need to challenge it to prevent peer table poisoning:
								// See "Proposed solution for Routing Table Poisoning" in https://pdfs.semanticscholar.org/3990/e316c8ecedf8398bd6dc167d92f094525920.pdf
								if (!isPublicUnicastInetAddress(peerAddress) && isPublicUnicastInetAddress(addr)) {
									peerAddress = addr;
								}
								System.arraycopy(data, 1 + rawPeerAddress.length, rawLocalAddress, 0, rawLocalAddress.length);
								addr = InetAddress.getByAddress(rawLocalAddress);
								if (isPublicUnicastInetAddress(addr)) {
									localAddress.startValidation(addr);
								}
								dataOffset = 1 + rawPeerAddress.length + rawLocalAddress.length;
//								networklog.debug("Docker: " + localAddress.get().getHostAddress() + "<" + peerAddress.getHostAddress());
							}
						}

						URI uri = URI.create(Network.URI_PREFIX+peerAddress.getHostAddress()+":"+Modules.get(Universe.class).getPort());
						UDPPeer peer = Network.this.connect(uri, Protocol.UDP);

						Message message = Message.parse(new ByteArrayInputStream(data, dataOffset, data.length - dataOffset));
	    				Messaging.getInstance().received(message, peer);
					}
					catch (Exception ex)
					{
						log.error("UDP "+datagramPacket.getAddress().toString()+" error", ex);
						continue;
		    		}
		    		finally
		    		{
		    			Network.this.connecting.unlock();
		    			SystemProfiler.getInstance().incrementFrom("NETWORK:UDP_PACKET_WORKER", start);
		    		}
		    	}
		    }
		    catch (Exception ex)
		    {
		    	log.fatal("UDP worker thread quit on: ", ex);
		    }
		}
	};

    private Network()
    {
    	super();
    }

	@Override
	public void start_impl() throws ModuleException
	{
    	try
    	{
	    	if (Modules.get(RuntimeProperties.class).has("network.address"))
	    	{
	    		UDPServerSocket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName(Modules.get(RuntimeProperties.class).get("network.address", "127.0.0.1")),
	    											 Modules.get(RuntimeProperties.class).get("network.udp", Modules.get(Universe.class).getPort())));
    		}
	    	else
	    		UDPServerSocket = new DatagramSocket(Modules.get(RuntimeProperties.class).get("network.port", Modules.get(Universe.class).getPort()));

	    	this.UDPServerSocket.setSoTimeout(1000);
	    	this.UDPServerSocket.setReceiveBufferSize(Modules.get(RuntimeProperties.class).get("network.udp.buffer", 1<<18));
	    	this.UDPServerSocket.setSendBufferSize(Modules.get(RuntimeProperties.class).get("network.udp.buffer", 1<<18));
	    	networklog.debug("UDP server socket "+this.UDPServerSocket.getLocalSocketAddress()+" RCV_BUF: "+this.UDPServerSocket.getReceiveBufferSize());
	    	networklog.debug("UDP server socket "+this.UDPServerSocket.getLocalSocketAddress()+" SEND_BUF: "+this.UDPServerSocket.getSendBufferSize());

    		this.UDPClientSocket = new DatagramSocket();
    		this.UDPClientSocket.setSendBufferSize(Modules.get(RuntimeProperties.class).get("network.udp.buffer", 1<<18));
    		this.UDPClientSocket.setReceiveBufferSize(Modules.get(RuntimeProperties.class).get("network.udp.buffer", 1<<18));
			networklog.debug("UDP client socket "+this.UDPClientSocket.getLocalSocketAddress()+" RCV_BUF: "+this.UDPClientSocket.getReceiveBufferSize());
			networklog.debug("UDP client socket "+this.UDPClientSocket.getLocalSocketAddress()+" SEND_BUF: "+this.UDPClientSocket.getSendBufferSize());

	        Thread UDPServerThread = new Thread(UDPListener);
	        UDPServerThread.setDaemon(false);
	        UDPServerThread.setName("UDP Server");
	        UDPServerThread.start();

	        Thread UDPworkerThread = new Thread(UDPworker);
	        UDPworkerThread.setDaemon(false);
	        UDPworkerThread.setName("UDP Worker");
	        UDPworkerThread.start();

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

			this.UDPClientSocket.close();
			this.UDPServerSocket.close();
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
					peer = new UDPPeer(this.UDPServerSocket, uri, peer, this.localAddress);
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
			List<Peer>	peers = new ArrayList<Peer>();

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
