package org.radix.network2.transport;

import java.io.IOException;

public interface TransportFactory {

	Transport create(TransportMetadata metadata) throws IOException;

}
