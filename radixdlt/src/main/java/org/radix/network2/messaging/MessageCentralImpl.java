package org.radix.network2.messaging;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.radix.containers.BasicContainer;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportOutboundConnection;

import com.google.common.collect.ImmutableList;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;

class MessageCentralImpl implements MessageCentral {

	Serialization serialization;
	ConnectionManager connectionManager;

	@Override
	public CompletableFuture<SendResult> send(Peer peer, BasicContainer message) {
		byte[] bytes = serialize(message);
		return findTransportAndOpenConnection(peer, bytes).thenCompose(conn -> conn.send(bytes));
	}

	@Override
	public CompletableFuture<SendResult> send(Peer peer, Iterable<? extends BasicContainer> messages) {
		List<byte[]> bytes = StreamSupport.stream(messages.spliterator(), false)
			.map(this::serialize)
			.collect(Collectors.toList());
		return findTransportAndOpenConnection(peer, bytes).thenCompose(conn -> conn.send(bytes));
	}

	@Override
	public List<CompletableFuture<SendResult>> send(Iterable<? extends Peer> peers, BasicContainer message) {
		byte[] bytes = serialize(message);
		return StreamSupport.stream(peers.spliterator(), false)
			.map(peer -> findTransportAndOpenConnection(peer, bytes))
			.map(cfOutbound -> cfOutbound.thenCompose(conn -> conn.send(bytes)))
			.collect(ImmutableList.toImmutableList());
	}

	@Override
	public List<CompletableFuture<SendResult>> send(Iterable<? extends Peer> peers, Iterable<? extends BasicContainer> messages) {
		List<byte[]> bytes = StreamSupport.stream(messages.spliterator(), false)
			.map(this::serialize)
			.collect(Collectors.toList());
		return StreamSupport.stream(peers.spliterator(), false)
			.map(peer -> findTransportAndOpenConnection(peer, bytes))
			.map(cfOutbound -> cfOutbound.thenCompose(conn -> conn.send(bytes)))
			.collect(ImmutableList.toImmutableList());
	}

	private byte[] serialize(Object out) {
		try {
			return serialization.toDson(out, Output.WIRE);
		} catch (SerializationException e) {
			throw new UncheckedSerializationException("While serializing message", e);
		}
	}

	@Override
	public <T extends BasicContainer> boolean addListener(Class<T> messageType, MessageListener<T> listener) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public <T extends BasicContainer> boolean removeListener(Class<T> messageType, MessageListener<T> listener) {
		// TODO Auto-generated method stub
		return false;
	}

	private CompletableFuture<TransportOutboundConnection> findTransportAndOpenConnection(Peer peer, byte[] bytes) {
		Transport transport = connectionManager.findTransport(peer, bytes);
		return transport.control().open(peer);
	}

	private CompletableFuture<TransportOutboundConnection> findTransportAndOpenConnection(Peer peer, Iterable<byte[]> bytes) {
		Transport transport = connectionManager.findTransport(peer, bytes);
		return transport.control().open(peer);
	}
}

