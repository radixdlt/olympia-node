/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.network.p2p.transport.handshake;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.network.p2p.NodeId;

import java.util.Optional;

public interface AuthHandshakeResult {

	static AuthHandshakeSuccess success(ECPublicKey remotePubKey, Secrets secrets, HashCode remoteLatestKnownForkHash) {
		return new AuthHandshakeSuccess(NodeId.fromPublicKey(remotePubKey), secrets, remoteLatestKnownForkHash);
	}

	static AuthHandshakeError error(String msg, Optional<NodeId> maybeNodeId) {
		return new AuthHandshakeError(msg, maybeNodeId);
	}

	final class AuthHandshakeSuccess implements AuthHandshakeResult {
		private final NodeId remoteNodeId;
		private final Secrets secrets;
		private final HashCode remoteLatestKnownForkHash;

		private AuthHandshakeSuccess(NodeId remoteNodeId, Secrets secrets, HashCode remoteLatestKnownForkHash) {
			this.remoteNodeId = remoteNodeId;
			this.secrets = secrets;
			this.remoteLatestKnownForkHash = remoteLatestKnownForkHash;
		}

		public NodeId getRemoteNodeId() {
			return remoteNodeId;
		}

		public Secrets getSecrets() {
			return secrets;
		}

		public HashCode getRemoteLatestKnownForkHash() {
			return remoteLatestKnownForkHash;
		}
	}

	final class AuthHandshakeError implements AuthHandshakeResult {
		private final String msg;
		private final Optional<NodeId> maybeNodeId;

		public AuthHandshakeError(String msg, Optional<NodeId> maybeNodeId) {
			this.msg = msg;
			this.maybeNodeId = maybeNodeId;
		}

		public String getMsg() {
			return msg;
		}

		public Optional<NodeId> getMaybeNodeId() {
			return maybeNodeId;
		}
	}
}
