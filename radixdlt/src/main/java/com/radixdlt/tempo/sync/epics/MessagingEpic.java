package com.radixdlt.tempo.sync.epics;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.SyncEpic;
import com.radixdlt.tempo.sync.TempoAtomSynchroniser.ImmediateDispatcher;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.Network;
import org.radix.network.Protocol;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.Messaging;
import org.radix.network.peers.Peer;
import org.radix.network.peers.UDPPeer;
import org.radix.state.State;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class MessagingEpic implements SyncEpic {
	private static final Logger logger = Logging.getLogger("Sync");

	private final Messaging messager;
	private final ImmutableMap<String, BiFunction<Message, Peer, SyncAction>> inboundMappers;
	private final ImmutableMap<Class<? extends SyncAction>, Function<SyncAction, Message>> outboundMessageMappers;
	private final ImmutableMap<Class<? extends SyncAction>, Function<SyncAction, Peer>> outboundPeerMappers;

	private MessagingEpic(Messaging messager,
	                      ImmutableMap<String, BiFunction<Message, Peer, SyncAction>> inboundMappers,
	                      ImmutableMap<Class<? extends SyncAction>, Function<SyncAction, Message>> messageMappers,
	                      ImmutableMap<Class<? extends SyncAction>, Function<SyncAction, Peer>> outboundPeerMappers) {
		this.messager = messager;
		this.inboundMappers = inboundMappers;
		this.outboundMessageMappers = messageMappers;
		this.outboundPeerMappers = outboundPeerMappers;
	}

	@Override
	public Stream<SyncAction> epic(SyncAction action) {
		if (outboundMessageMappers.containsKey(action.getClass())) {
			Message message = outboundMessageMappers.get(action.getClass()).apply(action);
			Peer peer = outboundPeerMappers.get(action.getClass()).apply(action);
			if (logger.hasLevel(Logging.DEBUG)) {
				logger.debug(String.format("Forwarding outbound %s for '%s' as '%s'",
					action.getClass().getSimpleName(), peer, message.getCommand()));
			}

			sendMessage(message, peer);
		}

		return Stream.empty();
	}

	private void sendMessage(Message message, Peer peer) {
		try {
			UDPPeer udpPeer = Network.getInstance().get(peer.getURI(), Protocol.UDP, State.CONNECTED);
			messager.send(message, udpPeer);
		} catch (IOException e) {
			// TODO error handling
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private Messaging messager;
		private final Map<String, BiFunction<Message, Peer, SyncAction>> inboundMappers = new HashMap<>();
		private final Map<Class<? extends SyncAction>, Function<SyncAction, Message>> outboundMessageMappers = new HashMap<>();
		private final Map<Class<? extends SyncAction>, Function<SyncAction, Peer>> outboundPeerMappers = new HashMap<>();

		private Builder() {
		}

		public Builder messager(Messaging messager) {
			this.messager = messager;
			return this;
		}

		public <T extends Message> Builder addInbound(String command, Class<T> cls, BiFunction<T, Peer, SyncAction> mapper) {
			this.inboundMappers.put(command, (m, p) -> mapper.apply(cls.cast(m), p));

			return this;
		}

		public <T extends SyncAction> Builder addOutbound(Class<T> cls, Function<T, Message> messageMapper, Function<T, Peer> peerMapper) {
			this.outboundMessageMappers.put(cls, action -> messageMapper.apply(cls.cast(action)));
			this.outboundPeerMappers.put(cls, action -> peerMapper.apply(cls.cast(action)));

			return this;
		}

		public MessagingEpic build(ImmediateDispatcher dispatcher) {
			Objects.requireNonNull(messager, "messager is required");

			for (String command : inboundMappers.keySet()) {
				this.messager.register(command, (message, peer) -> {
					BiFunction<Message, Peer, SyncAction> messageActionMapper = inboundMappers.get(command);
					SyncAction action = messageActionMapper.apply(message, peer);
					if (logger.hasLevel(Logging.DEBUG)) {
						logger.debug(String.format("Forwarding inbound '%s' from '%s' to %s",
							command, peer, action));
					}

					dispatcher.dispatch(action);
				});
			}

			return new MessagingEpic(
				messager,
				ImmutableMap.copyOf(inboundMappers),
				ImmutableMap.copyOf(outboundMessageMappers),
				ImmutableMap.copyOf(outboundPeerMappers)
			);
		}
	}
}
