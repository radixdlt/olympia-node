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
import com.google.common.hash.HashCode;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
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
@SerializerId2("atom")
public final class Atom {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("s")
	@DsonOutput({Output.ALL})
	private final ECDSASignature signature;

	private final List<REInstruction> instructions;
	private final HashCode witness;

	@JsonCreator
	private Atom(
		@JsonProperty("i") ImmutableList<byte[]> byteInstructions,
		@JsonProperty("s") ECDSASignature signature
	) {
		this(
			byteInstructions == null ? ImmutableList.of() : toInstructions(byteInstructions),
			signature,
			computeHashToSignFromBytes(byteInstructions == null ? Stream.empty() : byteInstructions.stream())
		);
	}

	private Atom(
		List<REInstruction> instructions,
		ECDSASignature signature,
		HashCode witness
	) {
		this.instructions = Objects.requireNonNull(instructions);
		this.signature = signature;
		this.witness = witness;
	}

	static Atom create(
		List<REInstruction> instructions,
		ECDSASignature signature
	) {
		return new Atom(instructions, signature, computeHashToSign(instructions));
	}

	// FIXME: need to include message
	public static HashCode computeHashToSignFromBytes(Stream<byte[]> instructions) {
		var outputStream = new ByteArrayOutputStream();
		instructions.forEach(outputStream::writeBytes);
		var firstHash = HashUtils.sha256(outputStream.toByteArray());
		return HashUtils.sha256(firstHash.asBytes());
	}

	public static HashCode computeHashToSign(List<REInstruction> instructions) {
		return computeHashToSignFromBytes(serializedInstructions(instructions));
	}

	@JsonProperty("i")
	@DsonOutput(Output.ALL)
	private ImmutableList<byte[]> getSerializerInstructions() {
		return serializedInstructions(this.instructions).collect(ImmutableList.toImmutableList());
	}

	public Optional<ECDSASignature> getSignature() {
		return Optional.ofNullable(this.signature);
	}

	private static Stream<byte[]> serializedInstructions(List<REInstruction> instructions) {
		return instructions.stream()
			.flatMap(i -> Stream.of(new byte[] {i.getMicroOp().opCode()}, i.getData()));
	}

	private static ImmutableList<REInstruction> toInstructions(ImmutableList<byte[]> bytesList) {
		Objects.requireNonNull(bytesList);
		Builder<REInstruction> instructionsBuilder = ImmutableList.builder();

		Iterator<byte[]> bytesIterator = bytesList.iterator();
		while (bytesIterator.hasNext()) {
			byte[] bytes = bytesIterator.next();
			byte[] dataBytes = bytesIterator.next();
			var instruction = REInstruction.create(bytes[0], dataBytes);
			instructionsBuilder.add(instruction);
		}

		return instructionsBuilder.build();
	}

	public List<REInstruction> getInstructions() {
		return instructions;
	}

	public Stream<REInstruction> bootUpInstructions() {
		return instructions.stream().filter(i -> i.getMicroOp() == REInstruction.REOp.UP);
	}

	public HashCode getWitness() {
		return witness;
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
