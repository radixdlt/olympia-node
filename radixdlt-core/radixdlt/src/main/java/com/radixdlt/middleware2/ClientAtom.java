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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction.CMMicroOp;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.ParticleGroup.ParticleGroupBuilder;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.store.SpinStateMachine;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

	@JsonProperty("message")
	@DsonOutput({Output.ALL})
	private final String message;

	@JsonProperty("signatures")
	@DsonOutput({Output.ALL})
	private final ImmutableMap<EUID, ECDSASignature> signatures;

	private final ImmutableList<CMMicroInstruction> instructions;

	@JsonProperty("aid")
	@DsonOutput({Output.ALL})
	private final AID aid;

	@JsonProperty("witness")
	@DsonOutput({Output.ALL})
	private final HashCode witness;

	@JsonCreator
	private ClientAtom(
		@JsonProperty("aid") AID aid,
		@JsonProperty("instructions") ImmutableList<byte[]> byteInstructions,
		@JsonProperty("witness") HashCode witness,
		@JsonProperty("signatures") ImmutableMap<EUID, ECDSASignature> signatures,
		@JsonProperty("message") String message
	) {
		this.aid = aid;
		this.witness = witness;
		this.instructions = toInstructions(byteInstructions);
		this.signatures = signatures == null ? ImmutableMap.of() : signatures;
		this.message = message;
	}

	private ClientAtom() {
		// Serializer only
		this.message = null;
		this.signatures = null;
		this.instructions = null;
		this.aid = null;
		this.witness = null;
	}

	private ClientAtom(
		AID aid,
		HashCode witness,
		ImmutableList<CMMicroInstruction> instructions,
		ImmutableMap<EUID, ECDSASignature> signatures,
		String message
	) {
		this.aid = Objects.requireNonNull(aid);
		this.witness = Objects.requireNonNull(witness);
		this.message = message;
		this.instructions = Objects.requireNonNull(instructions);
		this.signatures = Objects.requireNonNull(signatures);
	}

	public static ClientAtom create(
		ImmutableList<CMMicroInstruction> instructions,
		Hasher hasher
	) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		serializedInstructions(instructions).forEach(outputStream::writeBytes);
		HashCode witness = hasher.hashBytes(outputStream.toByteArray());
		AID aid = AID.from(witness.asBytes());
		return new ClientAtom(
			aid,
			witness,
			instructions,
			ImmutableMap.of(),
			null
		);
	}

	@JsonProperty("instructions")
	@DsonOutput(Output.ALL)
	private ImmutableList<byte[]> getSerializerInstructions() {
		return serializedInstructions(this.instructions).collect(ImmutableList.toImmutableList());
	}

	private static Stream<byte[]> serializedInstructions(ImmutableList<CMMicroInstruction> instructions) {
		final byte[] particleGroupByteCode = new byte[] {0};
		final byte[] checkNeutralThenUpByteCode = new byte[] {1};
		final byte[] checkUpThenDownByteCode = new byte[] {2};

		return instructions.stream().flatMap(i -> {
			if (i.getMicroOp() == CMMicroOp.PARTICLE_GROUP) {
				return Stream.of(particleGroupByteCode);
			} else {
				final byte[] instByte;
				if (i.getMicroOp() == CMMicroOp.CHECK_NEUTRAL_THEN_UP) {
					instByte = checkNeutralThenUpByteCode;
				} else if (i.getMicroOp() == CMMicroOp.CHECK_UP_THEN_DOWN) {
					instByte = checkUpThenDownByteCode;
				} else {
					throw new IllegalStateException();
				}

				byte[] particleDson = DefaultSerialization.getInstance().toDson(i.getParticle(), Output.ALL);
				return Stream.of(instByte, particleDson);
			}
		});
	}

	private static ImmutableList<CMMicroInstruction> toInstructions(ImmutableList<byte[]> bytesList) {
		Objects.requireNonNull(bytesList);
		Builder<CMMicroInstruction> instructionsBuilder = ImmutableList.builder();

		Iterator<byte[]> bytesIterator = bytesList.iterator();
		while (bytesIterator.hasNext()) {
			byte[] bytes = bytesIterator.next();
			if (bytes[0] == 0) {
				instructionsBuilder.add(CMMicroInstruction.particleGroup());
			} else {
				final Spin checkSpin;
				if (bytes[0] == 1) {
					checkSpin = Spin.NEUTRAL;
				} else if (bytes[0] == 2) {
					checkSpin = Spin.UP;
				} else {
					throw new IllegalStateException();
				}

				byte[] particleBytes = bytesIterator.next();
				final Particle particle;
				try {
					particle = DefaultSerialization.getInstance().fromDson(particleBytes, Particle.class);
				} catch (DeserializeException e) {
					throw new IllegalStateException("Could not deserialize particle: " + e);
				}
				instructionsBuilder.add(CMMicroInstruction.checkSpinAndPush(particle, checkSpin));
			}
		}

		return instructionsBuilder.build();
	}

	@Override
	public CMInstruction getCMInstruction() {
		return new CMInstruction(instructions, signatures);
	}

	@Override
	public HashCode getWitness() {
		return witness;
	}

	@Override
	public AID getAID() {
		return aid;
	}

	@Override
	public String getMessage() {
		return this.message;
	}

	@Override
	public int hashCode() {
		return Objects.hash(aid);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ClientAtom)) {
			return false;
		}

		ClientAtom other = (ClientAtom) o;
		return Objects.equals(this.aid, other.aid);
	}

	static List<ParticleGroup> toParticleGroups(
		ImmutableList<CMMicroInstruction> instructions
	) {
		List<ParticleGroup> pgs = new ArrayList<>();
		ParticleGroupBuilder curPg = ParticleGroup.builder();
		for (CMMicroInstruction instruction : instructions) {
			if (instruction.getMicroOp() == CMMicroOp.PARTICLE_GROUP) {
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

	/**
	 * Converts a ledger atom back to an api atom (to be deprecated)
	 * @param atom the ledger atom to convert
	 * @return an api atom
	 */
	public static Atom convertToApiAtom(ClientAtom atom) {
		List<ParticleGroup> pgs = toParticleGroups(atom.instructions);
		return new Atom(pgs, atom.signatures, atom.message);
	}

	/**
	 * Convert an api atom (to be deprecated) into a ledger atom.
	 *
	 * @param atom the atom to convert
	 * @return an atom to be stored on ledger
	 */
	public static ClientAtom convertFromApiAtom(Atom atom, Hasher hasher) {
		final ImmutableList<CMMicroInstruction> instructions = toCMMicroInstructions(atom.getParticleGroups());
		return new ClientAtom(
			Atom.aidOf(atom, hasher),
			hasher.hash(atom),
			instructions,
			ImmutableMap.copyOf(atom.getSignatures()),
			atom.getMessage()
		);
	}

	@Override
	public String toString() {
		return String.format("%s {aid=%s}", this.getClass().getSimpleName(), this.aid);
	}

	public String toInstructionsString() {
		return this.instructions.stream().map(i -> i.getMicroOp() + " " + i.getParticle() + "\n").collect(Collectors.joining());
	}
}
