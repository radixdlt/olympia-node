package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.tempo.TempoController.ImmediateDispatcher;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.reactive.TempoEpic;
import com.radixdlt.tempo.reactive.TempoFlow;
import com.radixdlt.tempo.reactive.TempoFlowSource;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;
import org.radix.network2.messaging.MessageCentral;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class MessagingEpic implements TempoEpic {
	private static final Logger logger = Logging.getLogger("Sync");

	private final MessageCentral messager;
	private final ImmutableMap<Class<? extends Message>, BiFunction<Message, Peer, TempoAction>> inboundMappers;
	private final ImmutableMap<Class<? extends TempoAction>, Function<TempoAction, Message>> outboundMessageMappers;
	private final ImmutableMap<Class<? extends TempoAction>, Function<TempoAction, Peer>> outboundPeerMappers;

	private MessagingEpic(MessageCentral messager,
	                      ImmutableMap<Class<? extends Message>, BiFunction<Message, Peer, TempoAction>> inboundMappers,
	                      ImmutableMap<Class<? extends TempoAction>, Function<TempoAction, Message>> messageMappers,
	                      ImmutableMap<Class<? extends TempoAction>, Function<TempoAction, Peer>> outboundPeerMappers) {
		this.messager = messager;
		this.inboundMappers = inboundMappers;
		this.outboundMessageMappers = messageMappers;
		this.outboundPeerMappers = outboundPeerMappers;
	}

	public TempoFlow<TempoAction> epic(TempoFlowSource flow) {
		outboundMessageMappers.forEach((actionCls, messageMapper) -> flow.of(actionCls)
			.forEach(send -> {
				Message message = messageMapper.apply(send);
				Peer peer = outboundPeerMappers.get(send.getClass()).apply(send);
				sendMessage(message, peer);
			}));

		return TempoFlow.empty();
	}

	private void sendMessage(Message message, Peer peer) {
		messager.send(peer, message);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private MessageCentral messager;
		private final Map<Class<? extends Message>, BiFunction<Message, Peer, TempoAction>> inboundMappers = new HashMap<>();
		private final Map<Class<? extends TempoAction>, Function<TempoAction, Message>> outboundMessageMappers = new HashMap<>();
		private final Map<Class<? extends TempoAction>, Function<TempoAction, Peer>> outboundPeerMappers = new HashMap<>();

		private Builder() {
		}

		public Builder messager(MessageCentral messager) {
			this.messager = messager;
			return this;
		}

		public <T extends Message> Builder addInbound(Class<T> messageClass, BiFunction<T, Peer, TempoAction> mapper) {
			this.inboundMappers.put(messageClass, (message, peer) -> mapper.apply(messageClass.cast(message), peer));

			return this;
		}

		public <T extends TempoAction> Builder addOutbound(Class<T> cls, Function<T, Message> messageMapper, Function<T, Peer> peerMapper) {
			this.outboundMessageMappers.put(cls, action -> messageMapper.apply(cls.cast(action)));
			this.outboundPeerMappers.put(cls, action -> peerMapper.apply(cls.cast(action)));

			return this;
		}

		public MessagingEpic build(ImmediateDispatcher dispatcher) {
			Objects.requireNonNull(messager, "messager is required");

			for (Class<? extends Message> command : inboundMappers.keySet()) {
				this.messager.addListener(command, (peer, message) -> {
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
