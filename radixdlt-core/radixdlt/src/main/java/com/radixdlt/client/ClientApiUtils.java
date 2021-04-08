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

package com.radixdlt.client;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateSerializer;
import com.radixdlt.atom.Txn;
import com.radixdlt.client.store.ParsedTx;
import com.radixdlt.client.store.ParticleWithSpin;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.functional.Result;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.radixdlt.atom.SubstateSerializer.deserializeFromBytes;
import static com.radixdlt.serialization.SerializationUtils.restore;
import static com.radixdlt.utils.functional.Optionals.allOf;

public final class ClientApiUtils {
	private ClientApiUtils() { }

	private static final Serialization serialization = DefaultSerialization.getInstance();

	public static Result<ParsedTx> toParsedTx(
		Txn txn,
		byte universeMagic,
		Function<AID, Optional<Txn>> loader
	) {
		var atomResult = restore(serialization, txn.getPayload(), Atom.class);
		var insnsResult = atomResult.map(Atom::getInstructions).map(ConstraintMachine::toInstructions);
		var particlesResult = insnsResult.map(instructions1 -> toParticles(instructions1, loader));

		var message = insnsResult.toOptional().flatMap(ClientApiUtils::extractMessage);
		var creator = atomResult.toOptional().flatMap(atom -> extractCreator(atom, universeMagic));

		return atomResult.flatMap(
			atom -> insnsResult.flatMap(
				instructions -> particlesResult.flatMap(
					particleList -> Result.ok(ParsedTx.create(txn, particleList, message, creator)))));
	}

	private static List<ParticleWithSpin> toParticles(
		List<REInstruction> instructions,
		Function<AID, Optional<Txn>> loader
	) {
		var reInstructions = instructions.stream()
			.filter(REInstruction::isPush)
			.collect(Collectors.toList());

		return toParticleWithSpin(reInstructions, loader);
	}

	public static Optional<RadixAddress> extractCreator(Atom tx, byte universeMagic) {
		return tx.getSignature()
			.flatMap(signature -> ECPublicKey.recoverFrom(tx.computeHashToSign(), signature))
			.map(publicKey -> new RadixAddress(universeMagic, publicKey));
	}

	private static Optional<String> extractMessage(List<REInstruction> instructions) {
		return instructions.stream()
			.filter(instruction -> instruction.getMicroOp() == REInstruction.REOp.MSG)
			.map(instruction -> new String(instruction.getData(), StandardCharsets.UTF_8))
			.findFirst();
	}

	// This method assumes that incoming instructions already passed RE and validated,
	// so it don't have to re-validate everything from scratch.
	private static List<ParticleWithSpin> toParticleWithSpin(
		List<REInstruction> rawInstructions, Function<AID, Optional<Txn>> loader
	) {
		var instructionIndex = new AtomicInteger();
		var localUpParticles = new HashMap<Integer, Particle>();
		var parsedInstructions = new ArrayList<ParticleWithSpin>();

		for (var inst : rawInstructions) {
			if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.UP) {
				deserializeFromBytes(inst.getData())
					.onSuccess(particle -> localUpParticles.put(instructionIndex.get(), particle))
					.map(ParticleWithSpin::up)
					.onSuccess(parsedInstructions::add);
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.VDOWN) {
				deserializeFromBytes(inst.getData())
					.map(ParticleWithSpin::down)
					.onSuccess(parsedInstructions::add);
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.DOWN) {
				var substateId = SubstateId.fromBytes(inst.getData());

				allOf(substateId.getIndex(), loader.apply(substateId.getTxnId()))
					.map((index, txn) -> restore(serialization, txn.getPayload(), Atom.class)
						.map(Atom::getInstructions)
						.map(ConstraintMachine::toInstructions)
						.map(list -> list.get(index).getData())
						.flatMap(SubstateSerializer::deserializeFromBytes)
						.map(ParticleWithSpin::down)
						.onSuccess(parsedInstructions::add));
			} else if (inst.getMicroOp() == REInstruction.REOp.LDOWN) {
				Optional.ofNullable(localUpParticles.remove(Ints.fromByteArray(inst.getData())))
					.map(ParticleWithSpin::down)
					.ifPresent(parsedInstructions::add);
			} else if (inst.getMicroOp() == REInstruction.REOp.MSG) {
				// just skip it
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.END) {
				// just skip it
			} else {
				throw new IllegalStateException("Unknown CM Operation: " + inst.getMicroOp());
			}

			instructionIndex.incrementAndGet();
		}

		return parsedInstructions;
	}
}
