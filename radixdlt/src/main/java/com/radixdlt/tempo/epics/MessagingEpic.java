package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.tempo.TempoController.ImmediateDispatcher;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoEpic;
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
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public final class MessagingEpic implements TempoEpic {
	private static final Logger logger = Logging.getLogger("Sync");

	private final Messaging messager;
	private final ImmutableMap<String, BiFunction<Message, Peer, TempoAction>> inboundMappers;
	private final ImmutableMap<Class<? extends TempoAction>, Function<TempoAction, Message>> outboundMessageMappers;
	private final ImmutableMap<Class<? extends TempoAction>, Function<TempoAction, Peer>> outboundPeerMappers;

	private MessagingEpic(Messaging messager,
	                      ImmutableMap<String, BiFunction<Message, Peer, TempoAction>> inboundMappers,
	                      ImmutableMap<Class<? extends TempoAction>, Function<TempoAction, Message>> messageMappers,
	                      ImmutableMap<Class<? extends TempoAction>, Function<TempoAction, Peer>> outboundPeerMappers) {
		this.messager = messager;
		this.inboundMappers = inboundMappers;
		this.outboundMessageMappers = messageMappers;
		this.outboundPeerMappers = outboundPeerMappers;
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of();
	}

	@Override
	public Stream<TempoAction> epic(TempoStateBundle bundle, TempoAction action) {
		if (outboundMessageMappers.containsKey(action.getClass())) {
			Message message = outboundMessageMappers.get(action.getClass()).apply(action);
			Peer peer = outboundPeerMappers.get(action.getClass()).apply(action);
			if (logger.hasLevel(Logging.TRACE)) {
				logger.trace(String.format("Forwarding outbound %s for '%s' as '%s'",
					action.getClass().getSimpleName(), peer, message.getCommand()));
			}

			sendMessage(message, peer);
		}

		return Stream.empty();
	}

	private void sendMessage(Message message, Peer peer) {
		try {
			// TODO put proper messaging here so we don't need networking
			UDPPeer udpPeer = Network.getInstance().get(peer.getURI(), Protocol.UDP, State.CONNECTED);
			messager.send(message, udpPeer);
		} catch (IOException e) {
			// TODO error handling
			throw new TempoException(String.format("Error while sending message '%s' to %s", message.getCommand(), peer), e);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private Messaging messager;
		private final Map<String, BiFunction<Message, Peer, TempoAction>> inboundMappers = new HashMap<>();
		private final Map<Class<? extends TempoAction>, Function<TempoAction, Message>> outboundMessageMappers = new HashMap<>();
		private final Map<Class<? extends TempoAction>, Function<TempoAction, Peer>> outboundPeerMappers = new HashMap<>();

		private Builder() {
		}

		public Builder messager(Messaging messager) {
			this.messager = messager;
			return this;
		}

		public <T extends Message> Builder addInbound(String command, Class<T> cls, BiFunction<T, Peer, TempoAction> mapper) {
			this.inboundMappers.put(command, (m, p) -> mapper.apply(cls.cast(m), p));

			return this;
		}

		public <T extends TempoAction> Builder addOutbound(Class<T> cls, Function<T, Message> messageMapper, Function<T, Peer> peerMapper) {
			this.outboundMessageMappers.put(cls, action -> messageMapper.apply(cls.cast(action)));
			this.outboundPeerMappers.put(cls, action -> peerMapper.apply(cls.cast(action)));

			return this;
		}

		public MessagingEpic build(ImmediateDispatcher dispatcher) {
			Objects.requireNonNull(messager, "messager is required");

			for (String command : inboundMappers.keySet()) {
				this.messager.register(command, (message, peer) -> {
					try {
						BiFunction<Message, Peer, TempoAction> messageActionMapper = inboundMappers.get(command);
						TempoAction action = messageActionMapper.apply(message, peer);
						if (logger.hasLevel(Logging.TRACE)) {
							logger.trace(String.format("Forwarding inbound '%s' from '%s' to %s",
								command, peer, action.getClass().getSimpleName()));
						}

						dispatcher.dispatch(action);
					} catch (Exception e) {
						logger.error("Error while forwarding inbound '" + message.getCommand() + "' from " + peer, e);
					}
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
