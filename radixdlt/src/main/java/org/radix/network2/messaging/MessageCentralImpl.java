package org.radix.network2.messaging;

import java.util.concurrent.CompletableFuture;
import org.radix.network.messaging.Message;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.TransportOutboundConnection;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;

class MessageCentralImpl implements MessageCentral {

	private final Serialization serialization;
	private final ConnectionManager connectionManager;

	MessageCentralImpl(Serialization serialization, ConnectionManager connectionManager) {
		this.serialization = serialization;
		this.connectionManager = connectionManager;
	}

	@Override
	public CompletableFuture<SendResult> send(Peer peer, Message message) {
		byte[] bytes = serialize(message);
		return findTransportAndOpenConnection(peer, bytes).thenCompose(conn -> conn.send(bytes));
	}

	@Override
	public <T extends Message> boolean addListener(Class<T> messageType, MessageListener<T> listener) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public <T extends Message> boolean removeListener(Class<T> messageType, MessageListener<T> listener) {
		// TODO Auto-generated method stub
		return false;
	}

	private byte[] serialize(Object out) {
		try {
			return serialization.toDson(out, Output.WIRE);
		} catch (SerializationException e) {
			throw new UncheckedSerializationException("While serializing message", e);
		}
	}

	private CompletableFuture<TransportOutboundConnection> findTransportAndOpenConnection(Peer peer, byte[] bytes) {
		return connectionManager.findTransport(peer, bytes).control().open(peer);
	}

}
