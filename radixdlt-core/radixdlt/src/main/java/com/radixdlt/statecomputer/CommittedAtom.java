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

package com.radixdlt.statecomputer;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.identifiers.AID;
import com.radixdlt.atom.ClientAtom;
import com.radixdlt.atom.LedgerAtom;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;

/**
 * An atom which has been committed by the BFT
 *
 * TODO: add commit signature proof
 */
@Immutable
public final class CommittedAtom implements LedgerAtom {
	private final ClientAtom clientAtom;
	private final long stateVersion;
	private final VerifiedLedgerHeaderAndProof proof;

	private CommittedAtom(ClientAtom clientAtom, long stateVersion, VerifiedLedgerHeaderAndProof proof) {
		this.clientAtom = clientAtom;
		this.stateVersion = stateVersion;
		this.proof = proof;
	}

	public static CommittedAtom create(ClientAtom clientAtom, VerifiedLedgerHeaderAndProof proof) {
		return new CommittedAtom(clientAtom, proof.getStateVersion(), proof);
	}

	public static CommittedAtom create(ClientAtom clientAtom, long stateVersion) {
		return new CommittedAtom(clientAtom, stateVersion, null);
	}

	public long getStateVersion() {
		return stateVersion;
	}

	public ClientAtom getClientAtom() {
		return clientAtom;
	}

	public Optional<VerifiedLedgerHeaderAndProof> getHeaderAndProof() {
		return Optional.ofNullable(proof);
	}

	@Override
	public CMInstruction getCMInstruction() {
		return clientAtom.getCMInstruction();
	}

	@Override
	public HashCode getWitness() {
		return clientAtom.getWitness();
	}

	@Override
	public String getMessage() {
		return clientAtom.getMessage();
	}

	@Override
	public AID getAID() {
		return clientAtom.getAID();
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.clientAtom, this.stateVersion, this.proof);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CommittedAtom)) {
			return false;
		}

		CommittedAtom other = (CommittedAtom) o;
		return Objects.equals(other.clientAtom, this.clientAtom)
			&& other.stateVersion == this.stateVersion
			&& Objects.equals(other.proof, this.proof);
	}

	@Override
	public String toString() {
		return String.format("%s{atom=%s, stateVersion=%s}",
			getClass().getSimpleName(), clientAtom != null ? clientAtom.getAID() : null, stateVersion);
	}
}

