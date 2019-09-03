package com.radixdlt.examples.tictactoe;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;

import static com.radixdlt.examples.tictactoe.TicTacToeBaseParticle.TicTacToeSquare.*;

import com.radixdlt.examples.tictactoe.TicTacToeConstraintScrypt.OToMoveParticle;
import com.radixdlt.examples.tictactoe.TicTacToeConstraintScrypt.XToMoveParticle;

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

	private static XToMoveParticle buildInitialBoard(ECKeyPair xPlayer, ECKeyPair oPlayer) {
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
	private static BasicRadixEngineAtom buildAtom(
		String description,
		TicTacToeBaseParticle prevBoard,
		TicTacToeBaseParticle nextBoard,
		ECKeyPair player
	) throws CryptoException {

		// Program the board transition
		ImmutableList.Builder<CMMicroInstruction> microInstructions = ImmutableList.builder();
		if (prevBoard != null) {
			microInstructions.add(CMMicroInstruction.checkSpin(prevBoard, Spin.UP));
			microInstructions.add(CMMicroInstruction.push(prevBoard));
		}
		microInstructions.add(CMMicroInstruction.checkSpin(nextBoard, Spin.NEUTRAL));
		microInstructions.add(CMMicroInstruction.push(nextBoard));
		microInstructions.add(CMMicroInstruction.particleGroup());

		// The zero hash is a hack and just used to simply test for some signature
		CMInstruction instruction = new CMInstruction(
			microInstructions.build(),
			Hash.ZERO_HASH,
			ImmutableMap.of(player.getUID(), player.sign(Hash.ZERO_HASH))
		);

		return new BasicRadixEngineAtom(instruction, description);
	}

	public static void main(String[] args) throws CryptoException {

		// Build the engine based on the constraint machine configured by the AtomOS
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new TicTacToeConstraintScrypt());
		ConstraintMachine cm = new ConstraintMachine.Builder()
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();
		InMemoryEngineStore<BasicRadixEngineAtom> engineStore = new InMemoryEngineStore<>();
		RadixEngine<BasicRadixEngineAtom> engine = new RadixEngine<>(
			cm,
			cmAtomOS.buildVirtualLayer(),
			engineStore
		);
		engine.start();

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
		ImmutableList<BasicRadixEngineAtom> atomsToTest = ImmutableList.of(
			// [ , , ]
			// [ , , ]
			// [ , ]
			buildAtom("Illegal Initial board", null, illegalInitialBoard, xPlayer),

			// [ , , ]
			// [ , , ]
			// [ , , ]
			buildAtom("Legal Initial board", null, initialBoard, xPlayer),

			// [ , , ]    [ , , ]
			// [ , , ] => [ ,X, ]
			// [ , , ]    [ , , ]
			buildAtom("Legal first move", initialBoard, firstMove, xPlayer),

			// [ , , ]    [ , , ]
			// [ , , ] => [ , , ]
			// [ , , ]    [ , ,X]
			buildAtom("Illegal takeback", initialBoard, illegalTakebackFirstMove, xPlayer),

			// [ , , ]    [ , , ]
			// [ ,X, ] => [ ,O, ]
			// [ , , ]    [ , , ]
			buildAtom("Illegal overwrite", firstMove, illegalOMove, oPlayer),

			// [ , , ]    [ , , ]
			// [ ,X, ] => [ ,X, ]
			// [ , , ]    [ , ,O]
			buildAtom("Illegal O move to O move", firstMove, illegalOtoMoveOtoMove, oPlayer),

			// [ , , ]    [ , , ]
			// [ ,X, ] => [ ,X, ]
			// [ , , ]    [ , ,O]
			buildAtom("Legal second move", firstMove, legalOMove, oPlayer)
		);

		// Execute each atom on the engine and see what happens
		for (BasicRadixEngineAtom atom : atomsToTest) {
			engine.store(atom, new AtomEventListener<BasicRadixEngineAtom>() {
				@Override
				public void onCMError(BasicRadixEngineAtom cmAtom, CMError error) {
					System.out.println("ERROR:   " + cmAtom + " CM verification " + error);
				}

				@Override
				public void onStateConflict(BasicRadixEngineAtom cmAtom, DataPointer issueParticle, BasicRadixEngineAtom conflictingAtom) {
					System.out.println("ERROR:   " + cmAtom + " Conflict with atom " + conflictingAtom);
				}

				@Override
				public void onStateStore(BasicRadixEngineAtom cmAtom) {
					System.out.println("SUCCESS: " + cmAtom);
				}
			});
		}
	}
}
