/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.radixdlt.common.EUID;

import java.util.Objects;

/**
 * Represents a vote on a vertex
 */
public final class VoteMessage {
	private final EUID author;
	private final VertexMetadata vertexMetadata;
	// TODO add signature

	public VoteMessage(EUID author, VertexMetadata vertexMetadata) {
		this.author = Objects.requireNonNull(author);
		this.vertexMetadata = Objects.requireNonNull(vertexMetadata);
	}

	public EUID getAuthor() {
		return author;
	}

	public VertexMetadata getVertexMetadata() {
		return vertexMetadata;
	}
}
