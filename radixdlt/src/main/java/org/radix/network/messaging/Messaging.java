package org.radix.network.messaging;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Objects;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.utils.Pair;

import com.radixdlt.serialization.SerializerConstants;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.network.messages.TestMessage;
import org.radix.network.peers.Peer;
import org.radix.network2.messaging.MessageCentral;
import org.radix.network2.messaging.MessageListener;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

public class Messaging extends Service
{
	private static final Logger log = Logging.getLogger();

	private static Messaging instance = null;

	public static synchronized Messaging getInstance() {
		if (instance == null) {
			throw new IllegalStateException("Messaging not yet configured");
		}
		return instance;
	}

	public static synchronized Messaging configure(MessageCentral messageCentral) {
		if (instance != null) {
			throw new IllegalStateException("Messaging already configured");
		}
		instance = new Messaging(messageCentral);
		return instance;
	}


	// FIXME: Remove when network2 complete
	// In fact this whole class should go, so that should be easy :)
	private final MessageCentral messageCentral;

	// Map from message command names to classes
	private final ImmutableMap<String, Class<? extends Message>> commandMap;

	private Messaging(MessageCentral messageCentral) {
		this.messageCentral = Objects.requireNonNull(messageCentral);

		// FIXME: Note this is temporary.  It provides a bridge from old, name based
		// callbacks (via Message#getCommand) to new Class based callbacks.
		ConfigurationBuilder config = new ConfigurationBuilder()
			.setUrls(ClasspathHelper.forJavaClassPath())
			.filterInputsBy(new FilterBuilder().includePackage("org.radix", "com.radixdlt"));
		Reflections reflections = new Reflections(config);
		this.commandMap = reflections.getTypesAnnotatedWith(SerializerConstants.SERIALIZER_ID_ANNOTATION).stream()
			.filter(Message.class::isAssignableFrom)
			.map(this::commandForClass)
			.collect(ImmutableMap.toImmutableMap(Pair::getFirst, Pair::getSecond));
	}

	@Override
	public void start_impl() throws ModuleException
	{
		register("test", new MessageProcessor<TestMessage>() {
			long testMessagesReceived = 0;

			@Override
			public void process(TestMessage m, Peer peer)
			{
				testMessagesReceived++;

				if (testMessagesReceived % 1000 == 0)
					log.debug("Received " + testMessagesReceived + " TestMessage");
			}
		});
	}

	@Override
	public void stop_impl() throws ModuleException
	{
		// Nothing to do here.
	}

	private static class MessageListenerAdapter<T extends Message> implements MessageListener<T> {

		private final MessageProcessor<T> listener;

		MessageListenerAdapter(MessageProcessor<T> listener) {
			this.listener = listener;
		}

		@Override
		public void handleMessage(Peer peer, T message) {
			this.listener.process(message, peer);
		}

		@Override
		public int hashCode() {
			return listener.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj instanceof MessageListenerAdapter) {
				MessageListenerAdapter<?> other = (MessageListenerAdapter<?>) obj;
				return Objects.equals(this.listener, other.listener);
			}
			return false;
		}
	}

	@Override
	public void register(String command, MessageProcessor listener)
	{
		Class<? extends Message> messageClass = findMessageClass(command);
		MessageListener messageListener = new MessageListenerAdapter(listener);
		this.messageCentral.addListener(messageClass, messageListener);
	}

	public void deregister(MessageProcessor<? extends Message> listener)
	{
		this.messageCentral.removeListener(new MessageListenerAdapter<>(listener));
	}

	public void received(Message message, Peer peer) {
		this.messageCentral.inject(peer, message);
	}

	public void send(Message message, Peer peer) throws IOException {
		this.messageCentral.send(peer, message);
	}

	private Pair<String, Class<? extends Message>> commandForClass(Class cls) {
		if (!Message.class.isAssignableFrom(cls)) {
			throw new IllegalStateException("Class is somehow not a message: " + cls.getName());
		}
		try {
			Constructor c = cls.getDeclaredConstructor();
			c.setAccessible(true);
			Message msg = Message.class.cast(c.newInstance());
			return Pair.of(msg.getCommand(), cls);
		} catch (ReflectiveOperationException | SecurityException e) {
			throw new IllegalStateException("Can't determine command for class: " + cls.getName(), e);
		}
	}

	private Class<? extends Message> findMessageClass(String command) {
		Class<? extends Message> foundClass = commandMap.get(command);
		if (foundClass == null) {
			throw new IllegalStateException("No message known with command: " + command);
		}
		return foundClass;
	}
}
