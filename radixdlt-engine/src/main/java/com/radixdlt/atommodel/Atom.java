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

package com.radixdlt.atommodel;

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
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware.ParticleGroup;
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
 * An atom from a client which can be processed by the Radix Engine.
 */
@Immutable
@SerializerId2("radix.atom")
public final class Atom implements LedgerAtom {
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
	private final HashCode witness;

	@JsonCreator
	private Atom(
		@JsonProperty("message") String message,
		@JsonProperty("instructions") ImmutableList<byte[]> byteInstructions,
		@JsonProperty("signatures") ImmutableMap<EUID, ECDSASignature> signatures
	) {
		this(
			byteInstructions == null ? ImmutableList.of() : toInstructions(byteInstructions),
			signatures == null ? ImmutableMap.of() : signatures, message
		);
	}

	public Atom(
		ImmutableList<CMMicroInstruction> instructions,
		ImmutableMap<EUID, ECDSASignature> signatures,
		String message
	) {
		this.message = message;
		this.instructions = Objects.requireNonNull(instructions);
		this.signatures = Objects.requireNonNull(signatures);

		// FIXME: need to include message
		var outputStream = new ByteArrayOutputStream();
		serializedInstructions(instructions).forEach(outputStream::writeBytes);
		var firstHash = HashUtils.sha256(outputStream.toByteArray());
		this.witness = HashUtils.sha256(firstHash.asBytes());
	}

	public static Atom create(ImmutableList<CMMicroInstruction> instructions) {
		return new Atom(
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

	public Optional<ECDSASignature> getSignature(EUID euid) {
		return Optional.ofNullable(this.signatures.get(euid));
	}

	public static Stream<byte[]> serializedInstructions(ImmutableList<CMMicroInstruction> instructions) {
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

	public AtomBuilder toBuilder() {
		List<ParticleGroup> pgs = AtomBuilder.toParticleGroups(this.instructions);
		return new AtomBuilder(pgs, this.signatures, this.message);
	}

	@Override
	public CMInstruction getCMInstruction() {
		return new CMInstruction(instructions, signatures);
	}

	@Override
	public HashCode getWitness() {
		return witness;
	}

	public AID getAID() {
		return AID.from(getWitness().asBytes());
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
		return this.instructions.stream().map(i -> i.getMicroOp() + " " + i.getParticle() + "\n").collect(Collectors.joining());
	}
}
