package org.radix.network2.messaging;

import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.Transport;

public interface ConnectionManager {

	Transport findTransport(Peer peer, byte[] bytes);

}
