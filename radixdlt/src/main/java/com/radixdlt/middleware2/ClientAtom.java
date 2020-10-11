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

package com.radixdlt.middleware2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction.CMMicroOp;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.AID;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.ParticleGroup.ParticleGroupBuilder;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.store.SpinStateMachine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * An atom from a client which can be processed by the Radix Engine.
 */
@Immutable
@SerializerId2("consensus.client_atom")
public final class ClientAtom implements LedgerAtom {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private transient CMInstruction cmInstruction;
	private transient ImmutableMap<String, String> metaData;
	private transient ImmutableList<Map<String, String>> perGroupMetadata;

	private transient AID aid;
	private transient Hash witness;
	private byte[] rawAtom;

	private ClientAtom() {
		// Serializer only
	}

	private ClientAtom(
		AID aid,
		Hash witness,
		CMInstruction cmInstruction,
		ImmutableMap<String, String> metaData,
		ImmutableList<Map<String, String>> perGroupMetadata,
		byte[] rawAtom
	) {
		this.aid = Objects.requireNonNull(aid);
		this.witness = Objects.requireNonNull(witness);
		this.metaData = Objects.requireNonNull(metaData);
		this.perGroupMetadata = Objects.requireNonNull(perGroupMetadata);
		this.cmInstruction = Objects.requireNonNull(cmInstruction);
		this.rawAtom = Objects.requireNonNull(rawAtom);
	}

	@JsonProperty("raw")
	@DsonOutput(Output.ALL)
	private byte[] getSerializerAtom() {
		return rawAtom;
	}

	@JsonProperty("raw")
	private void setSerializerAtom(byte[] atomBytes) {
		Objects.requireNonNull(atomBytes);
		try {
			this.rawAtom = atomBytes;
			final Atom atom = DefaultSerialization.getInstance().fromDson(atomBytes, Atom.class);
			this.aid = atom.getAID();
			this.witness = atom.getHash();
			this.metaData = ImmutableMap.copyOf(atom.getMetaData());
			this.cmInstruction = convertToCMInstruction(atom);
		} catch (DeserializeException e) {
			throw new IllegalStateException("Failed to deserialize atomBytes");
		}
	}

	@Override
	public CMInstruction getCMInstruction() {
		return cmInstruction;
	}

	@Override
	public Hash getWitness() {
		return witness;
	}

	@Override
	public AID getAID() {
		return aid;
	}

	@Override
	public ImmutableMap<String, String> getMetaData() {
		return metaData;
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(rawAtom));
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ClientAtom)) {
			return false;
		}

		ClientAtom other = (ClientAtom) o;
		return Arrays.equals(other.rawAtom, this.rawAtom);
	}

	static List<ParticleGroup> toParticleGroups(
		ImmutableList<CMMicroInstruction> instructions,
		ImmutableList<Map<String, String>> perGroupMetadata
	) {
		List<ParticleGroup> pgs = new ArrayList<>();
		int groupIndex = 0;
		ParticleGroupBuilder curPg = ParticleGroup.builder();
		for (CMMicroInstruction instruction : instructions) {
			if (instruction.getMicroOp() == CMMicroOp.PARTICLE_GROUP) {
				perGroupMetadata.get(groupIndex).forEach(curPg::addMetaData);
				groupIndex++;
				ParticleGroup pg = curPg.build();
				pgs.add(pg);
				curPg = ParticleGroup.builder();
			} else {
				curPg.addParticle(instruction.getParticle(), instruction.getNextSpin());
			}
		}
		return pgs;
	}

	static ImmutableList<CMMicroInstruction> toCMMicroInstructions(List<ParticleGroup> particleGroups) {
		final ImmutableList.Builder<CMMicroInstruction> microInstructionsBuilder = new Builder<>();
		for (int i = 0; i < particleGroups.size(); i++) {
			ParticleGroup pg = particleGroups.get(i);
			for (int j = 0; j < pg.getParticleCount(); j++) {
				SpunParticle sp = pg.getSpunParticle(j);
				Particle particle = sp.getParticle();
				Spin checkSpin = SpinStateMachine.prev(sp.getSpin());
				microInstructionsBuilder.add(CMMicroInstruction.checkSpinAndPush(particle, checkSpin));
			}
			microInstructionsBuilder.add(CMMicroInstruction.particleGroup());
		}

		return microInstructionsBuilder.build();
	}

	static CMInstruction convertToCMInstruction(Atom atom) {
		final ImmutableList<CMMicroInstruction> microInstructions = toCMMicroInstructions(atom.getParticleGroups());
		return new CMInstruction(
			microInstructions,
			ImmutableMap.copyOf(atom.getSignatures())
		);
	}

	/**
	 * Converts a ledger atom back to an api atom (to be deprecated)
	 * @param atom the ledger atom to convert
	 * @return an api atom
	 */
	public static Atom convertToApiAtom(ClientAtom atom) {
		List<ParticleGroup> pgs = toParticleGroups(atom.cmInstruction.getMicroInstructions(), atom.perGroupMetadata);
		return new Atom(pgs, atom.cmInstruction.getSignatures(), atom.metaData);
	}

	/**
	 * Convert an api atom (to be deprecated) into a ledger atom.
	 *
	 * @param atom the atom to convert
	 * @return an atom to be stored on ledger
	 */
	public static ClientAtom convertFromApiAtom(Atom atom) {
		final byte[] rawAtom = DefaultSerialization.getInstance().toDson(atom, Output.PERSIST);
		final CMInstruction cmInstruction = convertToCMInstruction(atom);
		final ImmutableList<Map<String, String>> perGroupMetadata = atom.getParticleGroups().stream()
			.map(ParticleGroup::getMetaData)
			.collect(ImmutableList.toImmutableList());
		return new ClientAtom(
			atom.getAID(),
			atom.getHash(),
			cmInstruction,
			ImmutableMap.copyOf(atom.getMetaData()),
			perGroupMetadata,
			rawAtom
		);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(this.getClass().getSimpleName());
		builder.append(":\n");
		for (CMMicroInstruction microInstruction : cmInstruction.getMicroInstructions()) {
			if (microInstruction.isCheckSpin()) {
				builder.append(microInstruction.getParticle());
				builder.append(": ");
				builder.append(microInstruction.getCheckSpin());
				builder.append(" -> ");
				builder.append(microInstruction.getNextSpin());
				builder.append("\n");
			} else {
				builder.append("---\n");
			}
		}

		return builder.toString();
	}
}
