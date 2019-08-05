package com.radixdlt.tempo.sync;

import com.google.common.collect.ImmutableMap;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.Messaging;
import org.radix.network.peers.Peer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class MessagingEpic implements SyncEpic {
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

			sendMessage(message, peer);
		}

		return Stream.empty();
	}

	private void sendMessage(Message message, Peer peer) {
		try {
			messager.send(message, peer);
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

		public Builder messager(Messaging messager) {
			this.messager = messager;
			return this;
		}

		public <T extends Message> Builder addInbound(String command, Class<T> cls, BiFunction<T, Peer, SyncAction> mapper) {
			this.inboundMappers.put(command, (m, p) -> mapper.apply(cls.cast(m), p));

			return this;
		}

		public <T extends SyncAction> Builder addOutound(Class<T> cls, Function<T, Message> messageMapper, Function<T, Peer> peerMapper) {
			this.outboundMessageMappers.put(cls, action -> messageMapper.apply(cls.cast(action)));
			this.outboundPeerMappers.put(cls, action -> peerMapper.apply(cls.cast(action)));

			return this;
		}

		public MessagingEpic build(Consumer<SyncAction> dispatcher) {
			Objects.requireNonNull(messager, "messager is required");

			for (String command : inboundMappers.keySet()) {
				this.messager.register(command, (message, peer) -> {
					BiFunction<Message, Peer, SyncAction> messageActionMapper = inboundMappers.get(command);
					dispatcher.accept(messageActionMapper.apply(message, peer));
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
