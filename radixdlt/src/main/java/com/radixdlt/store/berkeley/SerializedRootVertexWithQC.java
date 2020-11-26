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
 */

package com.radixdlt.store.berkeley;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;

@SerializerId2("store.proposal")
public class SerializedRootVertexWithQC {

	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("root")
	@DsonOutput(Output.ALL)
	private final UnverifiedVertex root;

	@JsonProperty("child")
	@DsonOutput(Output.ALL)
	private final UnverifiedVertex child;

	@JsonProperty("grand_child")
	@DsonOutput(Output.ALL)
	private final UnverifiedVertex grandChild;

	@JsonProperty("commit_qc")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate commitQC;

	@JsonCreator
	public SerializedRootVertexWithQC(
		@JsonProperty("root") UnverifiedVertex root,
		@JsonProperty("child") UnverifiedVertex child,
		@JsonProperty("grand_child") UnverifiedVertex grandChild,
		@JsonProperty("commit_qc") QuorumCertificate commitQC
	) {
		this.root = Objects.requireNonNull(root);
		this.child = Objects.requireNonNull(child);
		this.grandChild = Objects.requireNonNull(grandChild);
		this.commitQC = Objects.requireNonNull(commitQC);
	}

	public UnverifiedVertex getRoot() {
		return root;
	}

	public UnverifiedVertex getChild() {
		return child;
	}

	public UnverifiedVertex getGrandChild() {
		return grandChild;
	}

	public QuorumCertificate getCommitQC() {
		return commitQC;
	}
}
