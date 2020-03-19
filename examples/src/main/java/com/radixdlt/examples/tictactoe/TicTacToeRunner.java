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
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.examples.tictactoe.TicTacToeConstraintScrypt.OToMoveParticle;
import com.radixdlt.examples.tictactoe.TicTacToeConstraintScrypt.XToMoveParticle;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.store.EngineStore;

import java.util.ArrayList;
import java.util.List;

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

	/**
	 * Builds an atom based on the transition from one board to another.
	 */
	@VisibleForTesting
	static Atom buildAtom(
		TicTacToeBaseParticle prevBoard,
		TicTacToeBaseParticle nextBoard,
		ECKeyPair player
	) throws CryptoException {
		List<SpunParticle> spunParticles = new ArrayList<>(2);
		if (prevBoard != null) {
			spunParticles.add(SpunParticle.down(prevBoard));
		}
		if (nextBoard != null) {
			spunParticles.add(SpunParticle.up(nextBoard));
		}

		ParticleGroup particleGroup =  ParticleGroup.of(spunParticles);
		Atom atom = new Atom();
		atom.addParticleGroup(particleGroup);
		atom.sign(player);

		return atom;
	}

	public static void main(String[] args) throws CryptoException {

		// Build the engine based on the constraint machine configured by the AtomOS
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new TicTacToeConstraintScrypt());
		ConstraintMachine cm = new ConstraintMachine.Builder()
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();
		EngineStore engineStore = new InMemoryEngineStore();
		RadixEngine engine = new RadixEngine(
			cm,
			cmAtomOS.buildVirtualLayer(),
			engineStore
		);

		// Our two tic toe players
		ECKeyPair xPlayer = new ECKeyPair();
		ECKeyPair oPlayer = new ECKeyPair();

		// Build out real particle states for each of the boards
		XToMoveParticle illegalInitialBoard = buildIllegalInitialBoard(xPlayer, oPlayer);
		XToMoveParticle initialBoard = buildInitialBoard(xPlayer, oPlayer);
		OToMoveParticle firstMove = buildFirstMove(xPlayer, oPlayer);
		OToMoveParticle illegalTakebackFirstMove = buildFirstMove2(xPlayer, oPlayer);
		XToMoveParticle illegalOMove = buildIllegalOverwriteMove(xPlayer, oPlayer);
		OToMoveParticle illegalOtoMoveOtoMove = buildIllegalOToMoveOtoMove(xPlayer, oPlayer);
		XToMoveParticle legalOMove = buildLegalOMove(xPlayer, oPlayer);

		// Build out the atoms which represent transition events
		ImmutableList<Atom> atomsToTest = ImmutableList.of(
			// [ , , ]
			// [ , , ]
			// [ , ]
			//Illegal Initial board
			buildAtom(null, illegalInitialBoard, xPlayer),

			// [ , , ]
			// [ , , ]
			// [ , , ]
			//Legal Initial board
			buildAtom(null, initialBoard, xPlayer),

			// [ , , ]    [ , , ]
			// [ , , ] => [ ,X, ]
			// [ , , ]    [ , , ]
			//Legal first move
			buildAtom(initialBoard, firstMove, xPlayer),

			// [ , , ]    [ , , ]
			// [ , , ] => [ , , ]
			// [ , , ]    [ , ,X]
			//Illegal takeback
			buildAtom(initialBoard, illegalTakebackFirstMove, xPlayer),

			// [ , , ]    [ , , ]
			// [ ,X, ] => [ ,O, ]
			// [ , , ]    [ , , ]
			//Illegal overwrite
			buildAtom(firstMove, illegalOMove, oPlayer),

			// [ , , ]    [ , , ]
			// [ ,X, ] => [ ,X, ]
			// [ , , ]    [ , ,O]
			//Illegal O move to O move
			buildAtom(firstMove, illegalOtoMoveOtoMove, oPlayer),

			// [ , , ]    [ , , ]
			// [ ,X, ] => [ ,X, ]
			// [ , , ]    [ , ,O]
			//Legal second move
			buildAtom(firstMove, legalOMove, oPlayer)
		);

		// Execute each atom on the engine and see what happens
		for (Atom atom : atomsToTest) {
			try {
				engine.store(atom);
			} catch (RadixEngineException e) {
				System.out.println("ERROR:   " + atom);
			}
		}
	}
}
