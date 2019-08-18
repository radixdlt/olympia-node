package org.radix.network2.messaging;

import java.io.Closeable;
import java.util.Collection;
import org.radix.network.peers.Peer;
import org.radix.network2.transport.Transport;

/**
 * Interface for management of transports.
 * <p>
 * Keeps track of transports, and finds suitable transports for peers and
 * messages.
 */
public interface TransportManager extends Closeable {

	/**
	 * Returns a collection of all the {@link Transport}.
	 *
	 * @return A collection of all the transports this manager is managing.
	 */
	Collection<Transport> transports();

	/**
	 * Finds a suitable transport for the specified {@link Peer} and
	 * message to be sent.
	 * <p>
	 * At least in theory, this should take connected transports and the
	 * size of the message into account and select a suitable transport.
	 * <p>
	 * Note that this method doesn't actually connect to the remote.  You
	 * will need to call methods on the returned transport to make that
	 * happen, even for connectionless protocols.
	 *
	 * @param peer The peer we wish to connect to.
	 * @param bytes The message to be sent, or {@code null}.
	 * @return A found transport, or {@code null} if no suitable transport
	 *    can be found.
	 */
	Transport findTransport(Peer peer, byte[] bytes);

}
