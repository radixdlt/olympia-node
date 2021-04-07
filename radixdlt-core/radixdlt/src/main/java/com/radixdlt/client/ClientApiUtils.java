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
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateSerializer;
import com.radixdlt.atom.Txn;
import com.radixdlt.client.store.ParsedTx;
import com.radixdlt.client.store.ParticleWithSpin;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.ParsedInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.radixdlt.serialization.SerializationUtils.restore;

public final class ClientApiUtils {
	private ClientApiUtils() { }

	private static final Serialization serialization = DefaultSerialization.getInstance();
	private static final Logger log = LogManager.getLogger();

	public static Result<ParsedTx> toParsedTx(Txn txn, byte universeMagic) {
		var atomResult = restore(serialization, txn.getPayload(), Atom.class);
		var insnsResult = atomResult.map(Atom::getInstructions).map(ConstraintMachine::toInstructions);
		var particlesResult = insnsResult.map(ClientApiUtils::toParticles);

		var message = insnsResult.toOptional().flatMap(ClientApiUtils::extractMessage);
		var creator = atomResult.toOptional().flatMap(atom -> extractCreator(atom, universeMagic));

		return atomResult.flatMap(
			atom -> insnsResult.flatMap(
				instructions -> particlesResult.flatMap(
					particleList -> Result.ok(ParsedTx.create(txn, particleList, message, creator)))));
	}

	private static List<ParticleWithSpin> toParticles(List<REInstruction> instructions) {
		var reInstructions = instructions.stream()
			.filter(REInstruction::isPush)
			.collect(Collectors.toList());

		//TODO: rework it
		return unwrap(toParticleWithSpin(reInstructions));


//		return reInstructions.stream()
//			.map(instruction -> toParticleWithSpin(instruction, reInstructions))
//			.map(ClientApiUtils::reportError)
//			.filter(Result::isSuccess)
//			.map(ClientApiUtils::unwrap)
//			.collect(Collectors.toList());
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

	private static Result<List<ParticleWithSpin>> toParticleWithSpin(
		List<REInstruction> rawInstructions
	) {
		long particleGroupIndex = 0;
		long particleIndex = 0;
		int instructionIndex = 0;
		int numMessages = 0;

		var parsedInstructions = new ArrayList<ParticleWithSpin>();

		for (var inst : rawInstructions) {
			final DataPointer dp = DataPointer.ofParticle(particleGroupIndex, particleIndex);

			if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.UP) {
				// TODO: Cleanup indexing of substate class
				SubstateSerializer.deserializeFromBytes(inst.getData())
					.map(ParticleWithSpin::up)
					.onSuccess(parsedInstructions::add);
				particleIndex++;
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.VDOWN) {
				SubstateSerializer.deserializeFromBytes(inst.getData())
					.map(ParticleWithSpin::down)
					.onSuccess(parsedInstructions::add);
				particleIndex++;
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.DOWN) {
				var substateId = SubstateId.fromBytes(inst.getData());
				var maybeParticle = validationState.shutdown(substateId);
				if (maybeParticle.isEmpty()) {
					return Optional.of(new CMError(dp, CMErrorCode.SPIN_CONFLICT, validationState));
				}

				var particle = maybeParticle.get();
				Optional<CMError> error = validateParticle(validationState, particle, true, dp);
				if (error.isPresent()) {
					return error;
				}

				var substate = Substate.create(particle, substateId);
				parsedInstructions.add(ParsedInstruction.of(inst, substate, Spin.DOWN));
				particleIndex++;
			} else if (inst.getMicroOp() == REInstruction.REOp.LDOWN) {
				int index = Ints.fromByteArray(inst.getData());
				var maybeParticle = validationState.localShutdown(index);
				if (maybeParticle.isEmpty()) {
					return Optional.of(new CMError(dp, CMErrorCode.LOCAL_NONEXISTENT, validationState));
				}

				var particle = maybeParticle.get();
				Optional<CMError> error = validateParticle(validationState, particle, true, dp);
				if (error.isPresent()) {
					return error;
				}

				var substateId = SubstateId.ofSubstate(atom, index);
				var substate = Substate.create(particle, substateId);
				parsedInstructions.add(ParsedInstruction.of(inst, substate, Spin.DOWN));
				particleIndex++;
			} else if (inst.getMicroOp() == REInstruction.REOp.MSG) {
				numMessages++;
				if (numMessages > MAX_NUM_MESSAGES) {
					return Optional.of(
						new CMError(dp, CMErrorCode.TOO_MANY_MESSAGES, validationState)
					);
				}
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.END) {
				if (particleIndex == 0) {
					return Optional.of(
						new CMError(dp, CMErrorCode.EMPTY_PARTICLE_GROUP, validationState)
					);
				}

				if (!validationState.isEmpty()) {
					return Optional.of(new CMError(dp, CMErrorCode.UNEQUAL_INPUT_OUTPUT, validationState));
				}
				particleGroupIndex++;
				particleIndex = 0;
			} else {
				throw new IllegalStateException("Unknown CM Operation: " + inst.getMicroOp());
			}

			instructionIndex++;
		}

		if (particleIndex != 0) {
			return Optional.of(new CMError(
				DataPointer.ofParticle(particleGroupIndex, particleIndex),
				CMErrorCode.MISSING_PARTICLE_GROUP,
				validationState
			));
		}

		return Optional.empty();

	}

//	private static Result<ParsedTx> toParsedTx(
//		AID id, Tuple3<List<ParticleWithSpin>, Optional<RadixAddress>, Optional<String>> tuple
//	) {
//		return tuple.map((particles, creator, message) -> ParsedTx.create(id, particles, message, creator));
//	}
//
//	private static Tuple3<List<ParticleWithSpin>, Optional<RadixAddress>, Optional<String>> extractParticlesWithSpin(
//		Tuple3<List<REInstruction>, Optional<RadixAddress>, Optional<String>> tuple, FN1<Optional<Particle>, SubstateId> loader
//	) {
//		return tuple.map((instructions, creator, message) -> tuple(instructionsToParticles(instructions, loader), creator, message));
//	}
//
//	private static Tuple3<List<REInstruction>, Optional<RadixAddress>, Optional<String>> extractAuthor(
//		Tuple2<List<REInstruction>, Optional<RadixAddress>> tuple
//	) {
//		return tuple.map((instructions, creator) -> tuple(instructions, creator, extractMessage(instructions)));
//	}
//
//	private static Tuple2<List<REInstruction>, Optional<RadixAddress>> extractInstructionsAndAuthor(byte universeMagic, Atom atom) {
//		return tuple(toInstructions(atom.getInstructions()), extractCreator(atom, universeMagic));
//	}
//
//	private static List<ParticleWithSpin> instructionsToParticles(
//		List<REInstruction> instructions, FN1<Optional<Particle>, SubstateId> loader
//	) {
//		return instructions.stream()
//			.filter(REInstruction::isPush)
//			.map(i -> toParticleWithSpin(i, instructions, loader))
//			.peek(substate -> substate.onFailure(ClientApiUtils::reportError))
//			.filter(Result::isSuccess)
//			.map(p -> p.fold(ClientApiUtils::shouldNeverHappen, v -> v))
//			.collect(Collectors.toList());
//	}
//
//	private static Result<ParticleWithSpin> toParticleWithSpin(
//		REInstruction instruction, List<REInstruction> raw, FN1<Optional<Particle>, SubstateId> loader
//	) {
//		if (instruction.getMicroOp() == REInstruction.REOp.UP) {
//			return restore(serialization, instruction.getData(), Particle.class)
//				.map(particle -> ParticleWithSpin.create(particle, instruction.getNextSpin()));
//		} else if (instruction.getMicroOp() == REInstruction.REOp.VDOWN) {
//			return restore(serialization, instruction.getData(), Particle.class)
//				.map(particle -> ParticleWithSpin.create(particle, instruction.getNextSpin()));
//
//		} else if (instruction.getMicroOp() == REInstruction.REOp.DOWN) {
//			var substateId = SubstateId.fromBytes(instruction.getData());
//
//			return Result.fromOptional(loader.apply(substateId), "Unable to find particle")
//				.map(particle -> ParticleWithSpin.create(particle, instruction.getNextSpin()));
//		} else if (instruction.getMicroOp() == REInstruction.REOp.LDOWN) {
//			var index = Ints.fromByteArray(instruction.getData());
//
//			return restore(serialization, raw.get(index).getData(), Particle.class)
//				.map(particle -> ParticleWithSpin.create(particle, instruction.getNextSpin()));
//		} else {
//			return Result.fail("Unable to reconstruct particle");
//		}
//	}

	private static <T> Result<T> reportError(Result<T> input) {
		return input.onFailure(failure -> log.error(failure.message()));
	}

	private static <T> T unwrap(Result<T> input) {
		return input.fold(ClientApiUtils::shouldNeverHappen, Function.identity());
	}

	private static <T> T shouldNeverHappen(Failure f) {
		log.error("Should never happen {}", f.message());
		return null;
	}
}
