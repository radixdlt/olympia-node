/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.transport;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Interface providing control over a transport's outbound connections.
 */
public interface TransportControl extends Closeable {

	/**
	 * Open an outbound connection to a peer.
	 *
	 * @param endpointMetadata the metadata for the endpoint we are connecting to
	 * @return A {@link CompletableFuture} returning an outbound transport connection once the connection is open
	 */
	CompletableFuture<TransportOutboundConnection> open(TransportMetadata endpointMetadata);

	/**
	 * Closes this {@code TransportControl} and releases any system resources associated
	 * with it. If the {@code TransportControl} is already closed then invoking this
	 * method has no effect.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	void close() throws IOException;

}
