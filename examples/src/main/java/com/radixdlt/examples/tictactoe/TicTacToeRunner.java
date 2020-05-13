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

package com.radixdlt.examples.tictactoe;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngineAtom;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.examples.tictactoe.TicTacToeConstraintScrypt.OToMoveParticle;
import com.radixdlt.examples.tictactoe.TicTacToeConstraintScrypt.XToMoveParticle;
import com.radixdlt.store.EngineStore;

import java.util.Collections;

import static com.radixdlt.examples.tictactoe.TicTacToeBaseParticle.TicTacToeSquareValue.EMPTY;
import static com.radixdlt.examples.tictactoe.TicTacToeBaseParticle.TicTacToeSquareValue.O;
import static com.radixdlt.examples.tictactoe.TicTacToeBaseParticle.TicTacToeSquareValue.X;

/**
 * Executable example showing how to use the Radix Engine with the TicTacToeConstraintScrypt
 */
public class TicTacToeRunner {
	private static XToMoveParticle buildIllegalInitialBoard(ECKeyPair xPlayer, ECKeyPair oPlayer) {
		return new XToMoveParticle(
			new RadixAddress((byte) 0, xPlayer.getPublicKey()),
			new RadixAddress((byte) 0, oPlayer.getPublicKey()),
			ImmutableList.of(
				EMPTY, EMPTY, EMPTY,
				EMPTY, EMPTY, EMPTY,
				EMPTY, EMPTY
			)
		);
	}

	@VisibleForTesting
	static XToMoveParticle buildInitialBoard(ECKeyPair xPlayer, ECKeyPair oPlayer) {
		return new XToMoveParticle(
			new RadixAddress((byte) 0, xPlayer.getPublicKey()),
			new RadixAddress((byte) 0, oPlayer.getPublicKey()),
			ImmutableList.of(
				EMPTY, EMPTY, EMPTY,
				EMPTY, EMPTY, EMPTY,
				EMPTY, EMPTY, EMPTY
			)
		);
	}

	private static OToMoveParticle buildFirstMove(ECKeyPair xPlayer, ECKeyPair oPlayer) {
		return new OToMoveParticle(
			new RadixAddress((byte) 0, xPlayer.getPublicKey()),
			new RadixAddress((byte) 0, oPlayer.getPublicKey()),
			ImmutableList.of(
				EMPTY, EMPTY, EMPTY,
				EMPTY, X, EMPTY,
				EMPTY, EMPTY, EMPTY
			)
		);
	}

	private static OToMoveParticle buildFirstMove2(ECKeyPair xPlayer, ECKeyPair oPlayer) {
		return new OToMoveParticle(
			new RadixAddress((byte) 0, xPlayer.getPublicKey()),
			new RadixAddress((byte) 0, oPlayer.getPublicKey()),
			ImmutableList.of(
				EMPTY, EMPTY, EMPTY,
				EMPTY, EMPTY, EMPTY,
				EMPTY, EMPTY, X
			)
		);
	}


	private static XToMoveParticle buildIllegalOverwriteMove(ECKeyPair xPlayer, ECKeyPair oPlayer) {
		return new XToMoveParticle(
			new RadixAddress((byte) 0, xPlayer.getPublicKey()),
			new RadixAddress((byte) 0, oPlayer.getPublicKey()),
			ImmutableList.of(
				EMPTY, EMPTY, EMPTY,
				EMPTY, O, EMPTY,
				EMPTY, EMPTY, EMPTY
			)
		);
	}

	private static OToMoveParticle buildIllegalOToMoveOtoMove(ECKeyPair xPlayer, ECKeyPair oPlayer) {
		return new OToMoveParticle(
			new RadixAddress((byte) 0, xPlayer.getPublicKey()),
			new RadixAddress((byte) 0, oPlayer.getPublicKey()),
			ImmutableList.of(
				EMPTY, EMPTY, EMPTY,
				EMPTY, X, EMPTY,
				EMPTY, EMPTY, O
			)
		);
	}

	private static XToMoveParticle buildLegalOMove(ECKeyPair xPlayer, ECKeyPair oPlayer) {
		return new XToMoveParticle(
			new RadixAddress((byte) 0, xPlayer.getPublicKey()),
			new RadixAddress((byte) 0, oPlayer.getPublicKey()),
			ImmutableList.of(
				EMPTY, EMPTY, EMPTY,
				EMPTY, X, EMPTY,
				EMPTY, EMPTY, O
			)
		);
	}

	static class TicTacToeAtom implements RadixEngineAtom {
		private final CMInstruction cmInstruction;
		private final AID aid;
		private final boolean shouldPass;

		TicTacToeAtom(
			TicTacToeBaseParticle prevBoard,
			TicTacToeBaseParticle nextBoard,
			ECKeyPair player,
			Hash hash,
			boolean shouldPass
		) {
			this.shouldPass = shouldPass;

			ImmutableList.Builder<CMMicroInstruction> instructions = ImmutableList.builder();
			if (prevBoard != null) {
				instructions.add(CMMicroInstruction.checkSpin(prevBoard, Spin.UP));
				instructions.add(CMMicroInstruction.push(prevBoard));
			}
			if (nextBoard != null) {
				instructions.add(CMMicroInstruction.checkSpin(nextBoard, Spin.NEUTRAL));
				instructions.add(CMMicroInstruction.push(nextBoard));
			}
			instructions.add(CMMicroInstruction.particleGroup());

			this.cmInstruction = new CMInstruction(
				instructions.build(),
				hash,
				ImmutableMap.of(player.euid(), player.sign(hash))
			);
			this.aid = AID.from(hash, Collections.singleton(0L));
		}

		@Override
		public CMInstruction getCMInstruction() {
			return cmInstruction;
		}

		@Override
		public AID getAID() {
			return aid;
		}

		@Override
		public String toString() {
			return aid + " shouldPass: " + shouldPass;
		}
	}

	public static void main(String[] args) {

		// Build the engine based on the constraint machine configured by the AtomOS
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new TicTacToeConstraintScrypt());
		ConstraintMachine cm = new ConstraintMachine.Builder()
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();
		EngineStore<TicTacToeAtom> engineStore = new InMemoryEngineStore();
		RadixEngine<TicTacToeAtom> engine = new RadixEngine<>(
			cm,
			cmAtomOS.buildVirtualLayer(),
			engineStore
		);

		// Our two tic toe players
		ECKeyPair xPlayer = ECKeyPair.generateNew();
		ECKeyPair oPlayer = ECKeyPair.generateNew();

		// Build out real particle states for each of the boards
		XToMoveParticle illegalInitialBoard = buildIllegalInitialBoard(xPlayer, oPlayer);
		XToMoveParticle initialBoard = buildInitialBoard(xPlayer, oPlayer);
		OToMoveParticle firstMove = buildFirstMove(xPlayer, oPlayer);
		OToMoveParticle illegalTakebackFirstMove = buildFirstMove2(xPlayer, oPlayer);
		XToMoveParticle illegalOMove = buildIllegalOverwriteMove(xPlayer, oPlayer);
		OToMoveParticle illegalOtoMoveOtoMove = buildIllegalOToMoveOtoMove(xPlayer, oPlayer);
		XToMoveParticle legalOMove = buildLegalOMove(xPlayer, oPlayer);

		// Build out the atoms which represent transition events
		ImmutableList<TicTacToeAtom> atomsToTest = ImmutableList.of(
			// [ , , ]
			// [ , , ]
			// [ , ]
			//Illegal Initial board
			new TicTacToeAtom(null, illegalInitialBoard, xPlayer, Hash.random(), false),

			// [ , , ]
			// [ , , ]
			// [ , , ]
			//Legal Initial board
			new TicTacToeAtom(null, initialBoard, xPlayer, Hash.random(), true),

			// [ , , ]    [ , , ]
			// [ , , ] => [ ,X, ]
			// [ , , ]    [ , , ]
			//Legal first move
			new TicTacToeAtom(initialBoard, firstMove, xPlayer, Hash.random(), true),

			// [ , , ]    [ , , ]
			// [ , , ] => [ , , ]
			// [ , , ]    [ , ,X]
			//Illegal takeback
			new TicTacToeAtom(initialBoard, illegalTakebackFirstMove, xPlayer, Hash.random(), false),

			// [ , , ]    [ , , ]
			// [ ,X, ] => [ ,O, ]
			// [ , , ]    [ , , ]
			//Illegal overwrite
			new TicTacToeAtom(firstMove, illegalOMove, oPlayer, Hash.random(), false),

			// [ , , ]    [ , , ]
			// [ ,X, ] => [ ,X, ]
			// [ , , ]    [ , ,O]
			//Illegal O move to O move
			new TicTacToeAtom(firstMove, illegalOtoMoveOtoMove, oPlayer, Hash.random(), false),

			// [ , , ]    [ , , ]
			// [ ,X, ] => [ ,X, ]
			// [ , , ]    [ , ,O]
			//Legal second move
			new TicTacToeAtom(firstMove, legalOMove, oPlayer, Hash.random(), true)
		);

		// Execute each atom on the engine and see what happens
		for (TicTacToeAtom atom : atomsToTest) {
			try {
				engine.checkAndStore(atom);
			} catch (RadixEngineException e) {
				System.out.println("ERROR:   " + atom);
			}
		}
	}
}
