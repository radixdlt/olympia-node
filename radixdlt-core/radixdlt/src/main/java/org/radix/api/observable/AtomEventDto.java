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

package org.radix.api.observable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atom.Atom;
import com.radixdlt.identifiers.AID;
import org.radix.containers.BasicContainer;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

/**
 * An event description concerning an atom and whether it has been stored or deleted.
 */
@SerializerId2("api.atom_event")
public final class AtomEventDto extends BasicContainer {

	public enum AtomEventType {
		STORE, DELETE
	}

	@Override
	public short version() {
		return 100;
	}

	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private final Atom atom;

	@JsonProperty("atomId")
	@DsonOutput(Output.ALL)
	private final AID atomId;

	private AtomEventType type;

	public AtomEventDto(AtomEventType type, Atom atom, AID atomId) {
		this.type = type;
		this.atom = atom;
		this.atomId = atomId;
	}

	public AID getAtomId() {
		return atomId;
	}

	public Atom getAtom() {
		return atom;
	}

	public AtomEventType getType() {
		return type;
	}

	@JsonProperty("type")
	@DsonOutput(Output.ALL)
	private String getTypeString() {
		return this.type.name().toLowerCase();
	}

	@JsonProperty("type")
	private void setTypeString(String type) {
		this.type = AtomEventType.valueOf(type.toUpperCase());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		AtomEventDto that = (AtomEventDto) o;
		return Objects.equals(atom, that.atom)
			&& Objects.equals(atomId, that.atomId)
			&& type == that.type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(atom, atomId, type);
	}
}
