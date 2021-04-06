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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.Txn;
import com.radixdlt.client.store.ParsedTx;
import com.radixdlt.client.store.ParticleWithSpin;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Functions.FN1;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Tuple.Tuple2;
import com.radixdlt.utils.functional.Tuple.Tuple3;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.radixdlt.constraintmachine.ConstraintMachine.toInstructions;
import static com.radixdlt.serialization.SerializationUtils.restore;
import static com.radixdlt.utils.functional.Tuple.tuple;

public final class ClientApiUtils {
	private ClientApiUtils() { }

	private static final Serialization serialization = DefaultSerialization.getInstance();
	private static final Logger log = LogManager.getLogger();

	public static Result<ParsedTx> toParsedTx(Txn txn, byte universeMagic, FN1<Optional<Particle>, SubstateId> loader) {
		return restore(serialization, txn.getPayload(), Atom.class)
			.map(atom -> extractInstructionsAndAuthor(universeMagic, atom))
			.map(ClientApiUtils::extractAuthor)
			.map(tuple -> extractParticlesWithSpin(tuple, loader))
			.flatMap(tuple -> toParsedTx(txn.getId(), tuple));
	}

	private static Result<ParsedTx> toParsedTx(
		AID id, Tuple3<List<ParticleWithSpin>, Optional<RadixAddress>, Optional<String>> tuple
	) {
		return tuple.map((particles, creator, message) -> ParsedTx.create(id, particles, message, creator));
	}

	private static Tuple3<List<ParticleWithSpin>, Optional<RadixAddress>, Optional<String>> extractParticlesWithSpin(
		Tuple3<List<REInstruction>, Optional<RadixAddress>, Optional<String>> tuple, FN1<Optional<Particle>, SubstateId> loader
	) {
		return tuple.map((instructions, creator, message) -> tuple(instructionsToParticles(instructions, loader), creator, message));
	}

	private static Tuple3<List<REInstruction>, Optional<RadixAddress>, Optional<String>> extractAuthor(
		Tuple2<List<REInstruction>, Optional<RadixAddress>> tuple
	) {
		return tuple.map((instructions, creator) -> tuple(instructions, creator, extractMessage(instructions)));
	}

	private static Tuple2<List<REInstruction>, Optional<RadixAddress>> extractInstructionsAndAuthor(byte universeMagic, Atom atom) {
		return tuple(toInstructions(atom.getInstructions()), extractCreator(atom, universeMagic));
	}

	public static Result<Atom> txToAtom(Txn txn) {
		return restore(serialization, txn.getPayload(), Atom.class);
	}
	public static Optional<RadixAddress> extractCreator(Txn tx, byte universeMagic) {
		return txToAtom(tx).toOptional().flatMap(atom -> extractCreator(atom, universeMagic));
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

	private static List<ParticleWithSpin> instructionsToParticles(
		List<REInstruction> instructions, FN1<Optional<Particle>, SubstateId> loader
	) {
		return instructions.stream()
			.filter(REInstruction::isPush)
			.map(i -> toParticleWithSpin(i, instructions, loader))
			.peek(substate -> substate.onFailure(ClientApiUtils::reportError))
			.filter(Result::isSuccess)
			.map(p -> p.fold(ClientApiUtils::shouldNeverHappen, v -> v))
			.collect(Collectors.toList());
	}

	private static Result<ParticleWithSpin> toParticleWithSpin(
		REInstruction instruction, List<REInstruction> raw, FN1<Optional<Particle>, SubstateId> loader
	) {
		if (instruction.getMicroOp() == REInstruction.REOp.UP) {
			return restore(serialization, instruction.getData(), Particle.class)
				.map(particle -> ParticleWithSpin.create(particle, instruction.getNextSpin()));
		} else if (instruction.getMicroOp() == REInstruction.REOp.VDOWN) {
			return restore(serialization, instruction.getData(), Particle.class)
				.map(particle -> ParticleWithSpin.create(particle, instruction.getNextSpin()));

		} else if (instruction.getMicroOp() == REInstruction.REOp.DOWN) {
			var substateId = SubstateId.fromBytes(instruction.getData());

			return Result.fromOptional(loader.apply(substateId), "Unable to find particle")
				.map(particle -> ParticleWithSpin.create(particle, instruction.getNextSpin()));
		} else if (instruction.getMicroOp() == REInstruction.REOp.LDOWN) {
			var index = Ints.fromByteArray(instruction.getData());

			return restore(serialization, raw.get(index).getData(), Particle.class)
				.map(particle -> ParticleWithSpin.create(particle, instruction.getNextSpin()));
		} else {
			return Result.fail("Unable to reconstruct particle");
		}
	}

	private static void reportError(Failure failure) {
		log.error(failure.message());
	}

	private static <T> T shouldNeverHappen(Failure f) {
		log.error("Should never happen {}", f.message());
		return null;
	}
}
