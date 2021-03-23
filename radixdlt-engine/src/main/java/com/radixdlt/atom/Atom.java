/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.atom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction.CMMicroOp;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngineAtom;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DeserializeException;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.concurrent.Immutable;

/**
 * An atom to be processed by radix engine
 */
@Immutable
@SerializerId2("radix.atom")
public final class Atom implements RadixEngineAtom {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("m")
	@DsonOutput({Output.ALL})
	private final String message;

	@JsonProperty("s")
	@DsonOutput({Output.ALL})
	private final ImmutableMap<EUID, ECDSASignature> signatures;

	private final ImmutableList<CMMicroInstruction> instructions;
	private final HashCode witness;

	public static AtomBuilder newBuilder() {
		return new AtomBuilder();
	}

	@JsonCreator
	private Atom(
		@JsonProperty("m") String message,
		@JsonProperty("i") ImmutableList<byte[]> byteInstructions,
		@JsonProperty("s") ImmutableMap<EUID, ECDSASignature> signatures
	) {
		this(
			byteInstructions == null ? ImmutableList.of() : toInstructions(byteInstructions),
			signatures == null ? ImmutableMap.of() : signatures,
			message,
			computeHashToSignFromBytes(byteInstructions == null ? Stream.empty() : byteInstructions.stream())
		);
	}

	private Atom(
		ImmutableList<CMMicroInstruction> instructions,
		ImmutableMap<EUID, ECDSASignature> signatures,
		String message,
		HashCode witness
	) {
		this.message = message;
		this.instructions = Objects.requireNonNull(instructions);
		this.signatures = Objects.requireNonNull(signatures);
		this.witness = witness;
	}

	static Atom create(
		ImmutableList<CMMicroInstruction> instructions,
		ImmutableMap<EUID, ECDSASignature> signatures,
		String message
	) {
		return new Atom(instructions, signatures, message, computeHashToSign(instructions));
	}

	public static Atom create(ImmutableList<CMMicroInstruction> instructions) {
		return new Atom(
			instructions,
			ImmutableMap.of(),
			null,
			computeHashToSign(instructions)
		);
	}

	// FIXME: need to include message
	public static HashCode computeHashToSignFromBytes(Stream<byte[]> instructions) {
		var outputStream = new ByteArrayOutputStream();
		instructions.forEach(outputStream::writeBytes);
		var firstHash = HashUtils.sha256(outputStream.toByteArray());
		return HashUtils.sha256(firstHash.asBytes());
	}

	public static HashCode computeHashToSign(List<CMMicroInstruction> instructions) {
		return computeHashToSignFromBytes(serializedInstructions(instructions));
	}

	@JsonProperty("i")
	@DsonOutput(Output.ALL)
	private ImmutableList<byte[]> getSerializerInstructions() {
		return serializedInstructions(this.instructions).collect(ImmutableList.toImmutableList());
	}

	public Optional<ECDSASignature> getSignature(EUID euid) {
		return Optional.ofNullable(this.signatures.get(euid));
	}

	private static Stream<byte[]> serializedInstructions(List<CMMicroInstruction> instructions) {
		return instructions.stream().flatMap(i -> {

			final Stream<byte[]> additional;
			if (i.getMicroOp() == CMMicroOp.PARTICLE_GROUP) {
				additional = Stream.empty();
			} else {
				if (i.getMicroOp() == CMMicroOp.SPIN_UP) {
					byte[] particleDson = DefaultSerialization.getInstance().toDson(i.getParticle(), Output.ALL);
					additional = Stream.of(particleDson);
				} else if (i.getMicroOp() == CMMicroOp.VIRTUAL_SPIN_DOWN) {
					byte[] particleDson = DefaultSerialization.getInstance().toDson(i.getParticle(), Output.ALL);
					additional = Stream.of(particleDson);
				} else if (i.getMicroOp() == CMMicroOp.SPIN_DOWN) {
					byte[] particleHash = i.getParticleHash().asBytes();
					additional = Stream.of(particleHash);
				} else {
					throw new IllegalStateException();
				}
			}

			return Stream.concat(Stream.of(new byte[] {i.getMicroOp().opCode()}), additional);
		});
	}

	private static ImmutableList<CMMicroInstruction> toInstructions(ImmutableList<byte[]> bytesList) {
		Objects.requireNonNull(bytesList);
		Builder<CMMicroInstruction> instructionsBuilder = ImmutableList.builder();

		Iterator<byte[]> bytesIterator = bytesList.iterator();
		while (bytesIterator.hasNext()) {
			byte[] bytes = bytesIterator.next();
			if (bytes[0] == CMMicroOp.PARTICLE_GROUP.opCode()) {
				instructionsBuilder.add(CMMicroInstruction.particleGroup());
			} else if (bytes[0] == CMMicroOp.SPIN_UP.opCode()) {
				byte[] particleBytes = bytesIterator.next();
				final Particle particle;
				try {
					particle = DefaultSerialization.getInstance().fromDson(particleBytes, Particle.class);
				} catch (DeserializeException e) {
					throw new IllegalStateException("Could not deserialize particle: " + e);
				}

				instructionsBuilder.add(CMMicroInstruction.spinUp(particle));
			} else if (bytes[0] == CMMicroOp.VIRTUAL_SPIN_DOWN.opCode()) {
				byte[] particleBytes = bytesIterator.next();
				final Particle particle;
				try {
					particle = DefaultSerialization.getInstance().fromDson(particleBytes, Particle.class);
				} catch (DeserializeException e) {
					throw new IllegalStateException("Could not deserialize particle: " + e);
				}

				instructionsBuilder.add(CMMicroInstruction.virtualSpinDown(particle));
			} else if (bytes[0] == CMMicroOp.SPIN_DOWN.opCode()) {
				var particleHash = HashCode.fromBytes(bytesIterator.next());
				instructionsBuilder.add(CMMicroInstruction.spinDown(particleHash));
			} else {
				throw new IllegalStateException();
			}
		}

		return instructionsBuilder.build();
	}

	@Override
	public CMInstruction getCMInstruction() {
		return new CMInstruction(instructions, signatures);
	}

	public Stream<CMMicroInstruction> uniqueInstructions() {
		return instructions.stream().filter(CMMicroInstruction::isPush);
	}

	public Stream<Particle> upParticles() {
		return uniqueInstructions()
			.filter(i -> i.getNextSpin() == Spin.UP)
			.map(CMMicroInstruction::getParticle);
	}

	@Override
	public HashCode getWitness() {
		return witness;
	}

	public AID getAID() {
		return AID.from(witness.asBytes());
	}

	public String getMessage() {
		return this.message;
	}

	@Override
	public int hashCode() {
		return Objects.hash(witness);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Atom)) {
			return false;
		}

		Atom other = (Atom) o;
		return Objects.equals(this.witness, other.witness);
	}

	@Override
	public String toString() {
		return String.format("%s {witness=%s}", this.getClass().getSimpleName(), this.witness);
	}

	public String toInstructionsString() {
		return this.instructions.stream().map(i -> i + "\n").collect(Collectors.joining());
	}
}
