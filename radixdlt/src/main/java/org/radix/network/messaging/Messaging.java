package org.radix.network.messaging;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.radix.Radix;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.EUID;
import org.radix.common.executors.Executable;
import com.radixdlt.crypto.CryptoException;
import org.radix.events.Events;
import org.radix.exceptions.QueueFullException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.network.Interfaces;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messages.TestMessage;
import org.radix.network.messaging.Message.Direction;
import org.radix.network.peers.Peer;
import org.radix.network.peers.PeerStore;
import org.radix.properties.RuntimeProperties;
import org.radix.state.State;
import org.radix.time.NtpService;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.SystemMessage;
import org.radix.universe.system.events.QueueFullEvent;
import org.radix.utils.SystemMetaData;
import org.radix.utils.SystemProfiler;

public class Messaging extends Service
{
	private static final Logger log = Logging.getLogger();
	private static final Logger messagingLog = Logging.getLogger("messaging");

	private static final Map<Class<?>, Integer> messagePriorities = ImmutableMap.of(PeerPingMessage.class, 0);

	private static Messaging instance = null;

	public static synchronized Messaging getInstance()
	{
		if (instance == null)
			instance = new Messaging();

		return instance;
	}

	private final long timebase = System.nanoTime();

	private final Map<String, ArrayList<MessageProcessor>> listeners = new ConcurrentHashMap<>();

	// TODO: Need to consider some mitigation strategies for queue starvation to restrict
	// attackers from preventing lower priority messages from being processed.
	private final PriorityBlockingQueue<MessageEvent> inboundQueue = new PriorityBlockingQueue<>(Modules.get(RuntimeProperties.class).get("messaging.inbound.queue_max", 8192), MessageEvent.COMPARATOR);
	private final BlockingQueue<MessageEvent>	outboundQueue = new LinkedBlockingQueue<>(Modules.get(RuntimeProperties.class).get("messaging.outbound.queue_max", 16384));

	private final Executable inboundExecutable = new Executable()
	{
		@Override
		public void execute()
		{
			while (!isTerminated())
			{
				MessageEvent inboundMessage = null;

				try {
					inboundMessage = inboundQueue.poll(1, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					// Exit if we are interrupted
					Thread.currentThread().interrupt();
					break;
				}

				if (inboundMessage == null)
					continue;

				try
				{
					Modules.ifAvailable(SystemMetaData.class, a -> a.put("messages.inbound.pending", inboundQueue.size()));

					if (inboundMessage.getPeer().getState().in(State.DISCONNECTING) == true ||
						inboundMessage.getPeer().getState().in(State.DISCONNECTED) == true)
						continue;

					if (Modules.get(NtpService.class).getUTCTimeMS() - inboundMessage.getMessage().getTimestamp() > (Modules.get(RuntimeProperties.class).get("messaging.time_to_live", 30)*1000l))
					{
						Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.inbound.discarded"));
						continue;
					}

					if (inboundMessage.getMessage() instanceof SystemMessage)
					{
						if (((SystemMessage)inboundMessage.getMessage()).getSystem().getNID() == null ||
							((SystemMessage)inboundMessage.getMessage()).getSystem().getNID() == EUID.ZERO)
						{
							inboundMessage.getPeer().disconnect(inboundMessage.getMessage()+": Gave null NID");
							continue;
						}

						if (((SystemMessage)inboundMessage.getMessage()).getSystem().getAgentVersion() <= Radix.REFUSE_AGENT_VERSION)
						{
							inboundMessage.getPeer().disconnect("Old peer "+inboundMessage.getPeer().getURI()+" "+((SystemMessage)inboundMessage.getMessage()).getSystem().getAgent()+":"+((SystemMessage)inboundMessage.getMessage()).getSystem().getProtocolVersion());
							continue;
						}

						if (((SystemMessage)inboundMessage.getMessage()).getSystem().getNID().equals(LocalSystem.getInstance().getNID()))
						{
							inboundMessage.getPeer().ban("Message from self");
							Modules.get(Interfaces.class).addInterfaceAddress(InetAddress.getByName(inboundMessage.getPeer().getURI().getHost())); // TODO what about DNS lookups?
							continue;
						}

						if (inboundMessage.getPeer().getState().in(State.CONNECTED) == false)
						{
							Peer knownPeer = Modules.get(PeerStore.class).getPeer(((SystemMessage)inboundMessage.getMessage()).getSystem().getNID());

							if (knownPeer != null && knownPeer.getTimestamp(Timestamps.BANNED) > System.currentTimeMillis())
							{
								inboundMessage.getPeer().setTimestamp(Timestamps.BANNED, knownPeer.getTimestamp(Timestamps.BANNED));
								inboundMessage.getPeer().setBanReason(knownPeer.getBanReason());
								inboundMessage.getPeer().ban("Banned peer "+((SystemMessage)inboundMessage.getMessage()).getSystem().getNID()+" at "+inboundMessage.getPeer().toString());
								continue;
							}
						}
					}
				}
				catch (Exception ex)
				{
					messagingLog.error(inboundMessage.getMessage()+": Pre-processing from "+inboundMessage.getPeer().getURI()+" failed", ex);
					continue;
				}

				long start = SystemProfiler.getInstance().begin();

				// MESSAGING PROCESSING //
				try
				{
					SystemProfiler.getInstance().increment("MESSAGING:IN:LATENCY", (System.nanoTime()-inboundMessage.getMessage().getTimestamp(Timestamps.LATENCY)));

					final MessageEvent msg = inboundMessage; // FIXME: inboundMessage could be final with different code structure
					Modules.ifAvailable(MessageProfiler.class, mp -> mp.process(msg.getMessage(), msg.getPeer()));

					synchronized ( listeners )
					{
						ArrayList<MessageProcessor> listeners = Messaging.this.listeners.get(inboundMessage.getMessage().getCommand());

						if (listeners != null)
						{
							for (MessageProcessor listener : listeners)
							{
								try
								{
									listener.process(inboundMessage.getMessage(), inboundMessage.getPeer());
								}
								catch (Exception ex)
								{
									messagingLog.error(inboundMessage.getMessage()+" from "+inboundMessage.getPeer().getURI()+" failed", ex);
								}
							}
						}
					}

					Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.inbound.processed"));
					Events.getInstance().broadcast(inboundMessage);
				}
				finally
				{
					SystemProfiler.getInstance().incrementFrom("MESSAGING:IN:"+inboundMessage.getMessage().getCommand(), start);
					SystemProfiler.getInstance().incrementFrom("MESSAGING:IN", start);
				}
			}
		}
	};

	private final Executable outboundExecutable = new Executable()
	{
		@Override
		public void execute()
		{
			while (!isTerminated())
			{
				MessageEvent outboundMessage = null;

				try {
					outboundMessage = outboundQueue.poll(1, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					// Exit if we are interrupted
					Thread.currentThread().interrupt();
					break;
				}

				if (outboundMessage == null)
					continue;

				Modules.ifAvailable(SystemMetaData.class, a -> a.put("messages.outbound.pending", outboundQueue.size()));

				if (Modules.get(NtpService.class).getUTCTimeMS() - outboundMessage.getMessage().getTimestamp() > (Modules.get(RuntimeProperties.class).get("messaging.time_to_live", 30)*1000l))
				{
					messagingLog.warn(outboundMessage.getMessage()+": TTL to "+outboundMessage.getPeer()+" has expired");
					Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.outbound.aborted"));
					continue;
				}

				long start = SystemProfiler.getInstance().begin();

				try
				{
					final MessageEvent outMsg = outboundMessage; // FIXME: outboundMessage could be made final with a bit of restructure
					Modules.ifAvailable(MessageProfiler.class, mp -> mp.process(outMsg.getMessage(), outMsg.getPeer()));

					outboundMessage.getPeer().send(outboundMessage.getMessage());
					Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.outbound.processed"));

					Events.getInstance().broadcast(outboundMessage);
				}
				catch (Exception ex)
				{
					Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.outbound.aborted"));
					messagingLog.error(outboundMessage.getMessage()+" to "+outboundMessage.getPeer()+" failed", ex);
				}
				finally
				{
					SystemProfiler.getInstance().incrementFrom("MESSAGING:OUT:"+outboundMessage.getMessage().getCommand(), start);
					SystemProfiler.getInstance().incrementFrom("MESSAGING:OUT", start);
				}
			}
		}
	};

	private Thread inboundThread = null;
	private Thread outboundThread = null;

	private Messaging() { }

	@Override
	public void start_impl() throws ModuleException
	{
		register("test", new MessageProcessor<TestMessage>()
		{
			long testMessagesReceived = 0;

			@Override
			public void process(TestMessage m, Peer peer)
			{
				testMessagesReceived++;

				if (testMessagesReceived % 1000 == 0)
					messagingLog.debug("Received "+testMessagesReceived+" TestMessage");
			}
		});

		inboundThread = new Thread(inboundExecutable);
		inboundThread.setDaemon (true);
		inboundThread.setName ("Messaging-Inbound");
		inboundThread.start();

		outboundThread = new Thread(outboundExecutable);
		outboundThread.setDaemon (true);
		outboundThread.setName ("Messaging-Outbound");
		outboundThread.start();
	}

	@Override
	public void stop_impl() throws ModuleException
	{
		inboundExecutable.terminate(true);
		outboundExecutable.terminate(true);
	}

	@Override
	public void register(String command, MessageProcessor listener)
	{
		synchronized ( listeners )
		{
			if (!listeners.containsKey(command))
				listeners.put(command, new ArrayList<MessageProcessor>());

			if (!listeners.get(command).contains(listener))
				listeners.get(command).add(listener);
		}
	}

	public void deregister(MessageProcessor<? extends Message> listener)
	{
		synchronized ( listeners )
		{
			for (String command : listeners.keySet())
				listeners.get(command).remove(listener);
		}
	}

	public void received(Message message, Peer peer) throws IOException
	{
		long start = SystemProfiler.getInstance().begin();

		try
		{
			// TODO this feels dirty here
			if (message instanceof SystemMessage)
			{
				if (((SystemMessage)message).getSystem().getClock().get() < peer.getSystem().getClock().get())
					log.error("IMPLEMENT CLOCK MANIPULATION CHECK!");

				peer.setSystem(((SystemMessage)message).getSystem());
			}

			peer.setTimestamp(Timestamps.ACTIVE, Modules.get(NtpService.class).getUTCTimeMS());

			if (this.inboundQueue.offer(new MessageEvent(messagePriority(message), messageTime(), message, peer)) == false)
			{
				messagingLog.error(message+": Inbound queue is full "+peer);
				Events.getInstance().broadcast(new QueueFullEvent());
				throw new QueueFullException(message+": Inbound queue is full "+peer);
			}

			Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.inbound.received"));
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("MESSAGING:RECEIVED:"+message.getCommand(), start);
		}
	}

	public void send(Message message, Peer peer) throws IOException
	{
		long start = SystemProfiler.getInstance().begin();

		try
		{
			if ((peer.getState().in(State.DISCONNECTED) || peer.getState().in(State.DISCONNECTING)))
				throw new SocketException(peer+" is "+peer.getState());

			message.setDirection(Direction.OUTBOUND);

			if (message instanceof SignedMessage && ((SignedMessage)message).getSignature() == null)
				((SignedMessage)message).sign(LocalSystem.getInstance().getKeyPair());

			if (outboundQueue.offer(new MessageEvent(messagePriority(message), messageTime(), message, peer), 1, TimeUnit.SECONDS) == false)
			{
				messagingLog.error(message+": Outbound queue is full "+peer);
				Events.getInstance().broadcast(new QueueFullEvent());
				throw new QueueFullException(message+": Outbound queue is full "+peer);
			}

			Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.outbound.sent"));
		} catch (InterruptedException ex) {
			messagingLog.error(message + ": Sending to " + peer + " failed", ex);
			// Not going to handle it here.
			Thread.currentThread().interrupt();
			throw new IOException("While sending message", ex);
		} catch (IOException | CryptoException ex) {
			messagingLog.error(message + ": Sending to " + peer + " failed", ex);

			if (ex instanceof IOException)
				throw (IOException) ex;
			else
				throw new IOException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("MESSAGING:SEND:"+message.getCommand(), start);
		}
	}

	private long messageTime() {
		return System.nanoTime() - timebase;
	}

	private int messagePriority(Message message) {
		return messagePriorities.getOrDefault(message, Integer.MAX_VALUE);
	}
}
