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
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.AID;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.store.SpinStateMachine;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

	private static final int MAX_ATOM_SIZE = 1024 * 1024;

	private transient CMInstruction cmInstruction;
	private transient ImmutableMap<String, String> metaData;

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
		byte[] rawAtom
	) {
		this.aid = Objects.requireNonNull(aid);
		this.witness = Objects.requireNonNull(witness);
		this.metaData = Objects.requireNonNull(metaData);
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
		} catch (DeserializeException | LedgerAtomConversionException e) {
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

	public static class LedgerAtomConversionException extends Exception {
		private final DataPointer dataPointer;
		LedgerAtomConversionException(DataPointer dataPointer, String error) {
			super(error);
			this.dataPointer = dataPointer;
		}

		public DataPointer getDataPointer() {
			return dataPointer;
		}
	}

	static ImmutableList<CMMicroInstruction> toCMMicroInstructions(List<ParticleGroup> particleGroups) throws LedgerAtomConversionException {
		final HashMap<Particle, Spin> spins = new HashMap<>();
		final ImmutableList.Builder<CMMicroInstruction> microInstructionsBuilder = new Builder<>();
		for (int i = 0; i < particleGroups.size(); i++) {
			ParticleGroup pg = particleGroups.get(i);
			if (pg.isEmpty()) {
				throw new LedgerAtomConversionException(DataPointer.ofParticleGroup(i), "Particle group must not be empty");
			}
			final HashSet<Particle> seen = new HashSet<>();
			for (int j = 0; j < pg.getParticleCount(); j++) {
				SpunParticle sp = pg.getSpunParticle(j);
				Particle particle = sp.getParticle();

				if (seen.contains(particle)) {
					throw new LedgerAtomConversionException(DataPointer.ofParticle(i, j), "Particle transition must be unique in group");
				}
				seen.add(particle);

				Spin checkSpin = SpinStateMachine.prev(sp.getSpin());
				microInstructionsBuilder.add(CMMicroInstruction.checkSpinAndPush(particle, checkSpin));
			}
			microInstructionsBuilder.add(CMMicroInstruction.particleGroup());
		}

		return microInstructionsBuilder.build();
	}

	static CMInstruction convertToCMInstruction(Atom atom) throws LedgerAtomConversionException {
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
		try {
			return DefaultSerialization.getInstance().fromDson(atom.rawAtom, Atom.class);
		} catch (DeserializeException e) {
			throw new IllegalStateException("Could not convert back to api atom " + atom, e);
		}
	}

	/**
	 * Convert an api atom (to be deprecated) into a ledger atom.
	 *
	 * @param atom the atom to convert
	 * @return an atom to be stored on ledger
	 * @throws LedgerAtomConversionException on conversion errors
	 */
	public static ClientAtom convertFromApiAtom(Atom atom) throws LedgerAtomConversionException {
		final byte[] rawAtom = DefaultSerialization.getInstance().toDson(atom, Output.PERSIST);
		final int computedSize = rawAtom.length;

		if (computedSize > MAX_ATOM_SIZE) {
			throw new LedgerAtomConversionException(DataPointer.ofAtom(), "Atom too big");
		}

		final CMInstruction cmInstruction = convertToCMInstruction(atom);

		return new ClientAtom(
			atom.getAID(),
			atom.getHash(),
			cmInstruction,
			ImmutableMap.copyOf(atom.getMetaData()),
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
