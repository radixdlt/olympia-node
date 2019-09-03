package com.radixdlt.examples.tictactoe;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedCompute;
import com.radixdlt.constraintmachine.VoidParticle;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.examples.tictactoe.TicTacToeBaseParticle.TicTacToeSquare;
import com.radixdlt.serialization.SerializerId2;
import java.util.Optional;

/**
 * Constraint Scrypt which describes an extended finite state machine of a Tic Tac Toe game
 * between two players (each represented by an EC Key Pair).
 */
public class TicTacToeConstraintScrypt implements ConstraintScrypt {

	// Each of the following particle classes describe a state in the extended finite state machine

	@SerializerId2("o-to-move-particle")
	static class OToMoveParticle extends TicTacToeBaseParticle {
		OToMoveParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquare> board) {
			super(xPlayer, oPlayer, board);
		}
	}

	@SerializerId2("x-to-move-particle")
	static class XToMoveParticle extends TicTacToeBaseParticle {
		XToMoveParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquare> board) {
			super(xPlayer, oPlayer, board);
		}
	}

	@SerializerId2("o-wins-particle")
	static class OWinsParticle extends TicTacToeBaseParticle {
		OWinsParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquare> board) {
			super(xPlayer, oPlayer, board);
		}
	}

	@SerializerId2("x-wins-particle")
	static class XWinsParticle extends TicTacToeBaseParticle {
		XWinsParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquare> board) {
			super(xPlayer, oPlayer, board);
		}
	}

	@SerializerId2("draw-particle")
	static class DrawParticle extends TicTacToeBaseParticle {
		DrawParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquare> board) {
			super(xPlayer, oPlayer, board);
		}
	}


	private static ImmutableList<ImmutableList<Integer>> LINES = ImmutableList.of(
		ImmutableList.of(0, 1, 2),
		ImmutableList.of(3, 4, 5),
		ImmutableList.of(6, 7, 8),
		ImmutableList.of(0, 3, 6),
		ImmutableList.of(1, 4, 7),
		ImmutableList.of(2, 5, 8),
		ImmutableList.of(0, 4, 8),
		ImmutableList.of(2, 4, 6)
	);

	enum GameStatus {
		IN_PROGRESS, X_WINS, O_WINS, DRAW
	}

	/**
	 * Checks that a tic tac toe state in the state machine is a valid one.
	 */
	private static Result staticCheck(TicTacToeBaseParticle ticTacToe, GameStatus requiredGameStatus) {
		if (ticTacToe.getBoard() == null) {
			return Result.error("Tic Tac Toe board cannot be null.");
		}

		if (ticTacToe.getBoard().size() != TicTacToeBaseParticle.TIC_TAC_TOE_BOARD_SIZE) {
			return Result.error("Tic Tac Toe board must be size 9.");
		}

		for (TicTacToeSquare square : ticTacToe.getBoard()) {
			if (square == null) {
				return Result.error("No square can be null.");
			}
		}

		if (ticTacToe.getXPlayer() == null) {
			return Result.error("X player cannot be null.");
		}

		if (ticTacToe.getOPlayer() == null) {
			return Result.error("O player cannot be null.");
		}

		// Compute what the new game state is
		GameStatus gameStatus = null;
		for (ImmutableList<Integer> line : LINES) {
			ImmutableList<TicTacToeSquare> board = ticTacToe.getBoard();
			if (board.get(line.get(0)) != TicTacToeSquare.EMPTY
				&& board.get(line.get(0)) == board.get(line.get(1))
				&& board.get(line.get(1)) == board.get(line.get(2))) {
				gameStatus = board.get(line.get(0)) == TicTacToeSquare.X ? GameStatus.X_WINS : GameStatus.O_WINS;
				break;
			}
		}

		if (gameStatus == null) {
			gameStatus = ticTacToe.getBoard().stream().allMatch(s -> s != TicTacToeSquare.EMPTY) ? GameStatus.DRAW : GameStatus.IN_PROGRESS;
		}

		// Check that the game state matches
		if (gameStatus != requiredGameStatus) {
			return Result.error("Required game state is " + requiredGameStatus + " but was " + gameStatus);
		}

		return Result.success();
	}

	@Override
	public void main(SysCalls os) {

		// First, we must define and register each of the 5 qualitative states in the extended state machine.
		// During this process we also define where each of these states will "live". In our case we want
		// the game to be stored in both player's addresses.
		os.registerParticleMultipleAddresses(
			XToMoveParticle.class,
			ticTacToe -> ImmutableSet.of(ticTacToe.getXPlayer(), ticTacToe.getOPlayer()),
			t -> staticCheck(t, GameStatus.IN_PROGRESS)
		);
		os.registerParticleMultipleAddresses(
			OToMoveParticle.class,
			ticTacToe -> ImmutableSet.of(ticTacToe.getXPlayer(), ticTacToe.getOPlayer()),
			t -> staticCheck(t, GameStatus.IN_PROGRESS)
		);
		os.registerParticleMultipleAddresses(
			XWinsParticle.class,
			ticTacToe -> ImmutableSet.of(ticTacToe.getXPlayer(), ticTacToe.getOPlayer()),
			t -> staticCheck(t, GameStatus.X_WINS)
		);
		os.registerParticleMultipleAddresses(
			OWinsParticle.class,
			ticTacToe -> ImmutableSet.of(ticTacToe.getXPlayer(), ticTacToe.getOPlayer()),
			t -> staticCheck(t, GameStatus.O_WINS)
		);
		os.registerParticleMultipleAddresses(
			DrawParticle.class,
			ticTacToe -> ImmutableSet.of(ticTacToe.getXPlayer(), ticTacToe.getOPlayer()),
			t -> staticCheck(t, GameStatus.DRAW)
		);

		// Next, we must define the state machine transitions which are allowed in our state machine.
		// These are described as tokens and in our case, we have 6 transition tokens to define.
		TransitionToken<VoidParticle, VoidUsedData, XToMoveParticle, VoidUsedData> newGameToken = new TransitionToken<>(
			VoidParticle.class, TypeToken.of(VoidUsedData.class), XToMoveParticle.class, TypeToken.of(VoidUsedData.class)
		);
		TransitionToken<XToMoveParticle, VoidUsedData, OToMoveParticle, VoidUsedData> xMovesToken = new TransitionToken<>(
			XToMoveParticle.class, TypeToken.of(VoidUsedData.class), OToMoveParticle.class, TypeToken.of(VoidUsedData.class)
		);
		TransitionToken<OToMoveParticle, VoidUsedData, XToMoveParticle, VoidUsedData> oMovesToken = new TransitionToken<>(
			OToMoveParticle.class, TypeToken.of(VoidUsedData.class), XToMoveParticle.class, TypeToken.of(VoidUsedData.class)
		);
		TransitionToken<OToMoveParticle, VoidUsedData, OWinsParticle, VoidUsedData> oWinsToken = new TransitionToken<>(
			OToMoveParticle.class, TypeToken.of(VoidUsedData.class), OWinsParticle.class, TypeToken.of(VoidUsedData.class)
		);
		TransitionToken<XToMoveParticle, VoidUsedData, XWinsParticle, VoidUsedData> xWinsToken = new TransitionToken<>(
			XToMoveParticle.class, TypeToken.of(VoidUsedData.class), XWinsParticle.class, TypeToken.of(VoidUsedData.class)
		);
		TransitionToken<XToMoveParticle, VoidUsedData, DrawParticle, VoidUsedData> drawsToken = new TransitionToken<>(
			XToMoveParticle.class, TypeToken.of(VoidUsedData.class), DrawParticle.class, TypeToken.of(VoidUsedData.class)
		);

		// Next we must define the quantitative part of our extended state machine, that is
		// under what conditions are the transitions we defined above allowed?
		os.createTransition(newGameToken, new TransitionProcedure<VoidParticle, VoidUsedData, XToMoveParticle, VoidUsedData>() {

			/**
			 * The precondition defines the state conditions under which the transition is allowed.
			 */
			@Override
			public Result precondition(VoidParticle inputParticle, VoidUsedData inputUsed, XToMoveParticle outputParticle, VoidUsedData outputUsed) {
				for (int squareIndex = 0; squareIndex < 9; squareIndex++) {
					TicTacToeSquare nextSquareState = outputParticle.getBoard().get(squareIndex);

					if (nextSquareState != TicTacToeSquare.EMPTY) {
						return Result.error("Game must start with an empty board");
					}
				}

				return Result.success();
			}

			/**
			 * The UsedCompute can effectively be ignored for this example for now as it's more for advanced use.
			 */
			@Override
			public UsedCompute<VoidParticle, VoidUsedData, XToMoveParticle, VoidUsedData> inputUsedCompute() {
				return (in, inUsed, out, outUsed) -> Optional.empty();
			}

			/**
			 * The UsedCompute can effectively be ignored for this example for now as it's more for advanced use.
			 */
			@Override
			public UsedCompute<VoidParticle, VoidUsedData, XToMoveParticle, VoidUsedData> outputUsedCompute() {
				return (in, inUsed, out, outUsed) -> Optional.empty();
			}

			/**
			 * The witness validator defines the permissions under which the transition is allowed
			 */
			@Override
			public WitnessValidator<VoidParticle> inputWitnessValidator() {
				return (p, w) -> WitnessValidatorResult.success();
			}

			/**
			 * The witness validator defines the permissions under which the transition is allowed
			 */
			@Override
			public WitnessValidator<XToMoveParticle> outputWitnessValidator() {
				return (p, w) -> w.isSignedBy(p.getXPlayer().getKey()) || w.isSignedBy(p.getOPlayer().getKey())
					? WitnessValidatorResult.success()
					: WitnessValidatorResult.error("Game must be started by either one of the players.");
			}
		});

		// The rest of the transition constraints are defined for succinct purposes in the TicTacToeMoveTransitionProcedure class
		os.createTransition(xMovesToken, new TicTacToeMoveTransitionProcedure<>(TicTacToeSquare.X));
		os.createTransition(oMovesToken, new TicTacToeMoveTransitionProcedure<>(TicTacToeSquare.O));
		os.createTransition(xWinsToken, new TicTacToeMoveTransitionProcedure<>(TicTacToeSquare.X));
		os.createTransition(oWinsToken, new TicTacToeMoveTransitionProcedure<>(TicTacToeSquare.O));
		os.createTransition(drawsToken, new TicTacToeMoveTransitionProcedure<>(TicTacToeSquare.X));
	}
}
