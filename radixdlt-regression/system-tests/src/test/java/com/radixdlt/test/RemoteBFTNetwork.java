/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.test;

import okhttp3.HttpUrl;

import java.util.Set;

/**
 * An abstraction over a remote, networked set of BFT nodes
 */
public interface RemoteBFTNetwork {
	/**
	 * Gets an {@link HttpUrl} to talk to a specific endpoint of a node
	 * @param nodeId The node
	 * @param endpoint The endpoint
	 * @return The {@link HttpUrl} to talk to that endpoint
	 */
	HttpUrl getEndpointUrl(String nodeId, String endpoint);

	/**
	 * Gets all node identifiers in the network.
	 * @return All node identifier strings
	 */
	Set<String> getNodeIds();
}
