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

import okhttp3.Request;

import java.util.Set;

/**
 * An abstraction over network of a set of BFT nodes
 */
public interface BFTNetwork {
	/**
	 * Make a request to a certain node's endpoint
	 * @param nodeId The node id as given by getNodeIds
	 * @param endpoint
	 * @return
	 */
	Request makeRequest(String nodeId, String endpoint);

	Set<String> getNodeIds();
}
