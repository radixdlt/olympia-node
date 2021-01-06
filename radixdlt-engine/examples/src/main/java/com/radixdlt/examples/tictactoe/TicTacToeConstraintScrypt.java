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

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedCompute;
import com.radixdlt.constraintmachine.VoidParticle;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.examples.tictactoe.TicTacToeBaseParticle.TicTacToeSquareValue;
import com.radixdlt.serialization.SerializerId2;
import java.util.Optional;

/**
 * Constraint Scrypt which describes an extended finite state machine of a Tic Tac Toe game
 * between two players (each represented by an EC Key Pair).
 *
 * <img src="https://yuml.me/diagram/scruffy/class/%5Bstart%5D-is%20empty%20board%3F%3E%5BX%20to%20Move%5D%2C%5BX%20to%20Move%7CX%20to%20move%20board%5D-valid%20move%3F%3E%5BO%20to%20Move%7CO%20to%20move%20board%5D%2C%5BO%20to%20Move%5D-valid%20move%3F%3E%5BX%20to%20Move%5D%2C%5BO%20to%20Move%5D-valid%20move%3F%3E%5BO%20wins%7C%20O%20winning%20board%5D%2C%20%5BX%20to%20Move%5D-valid%20move%3F%3E%5BX%20wins%7CX%20winning%20board%5D%2C%5BX%20to%20Move%5D-valid%20move%3F%3E%5BDraw%7CDraw%20Board%5D" width=400 />
 */
public class TicTacToeConstraintScrypt implements ConstraintScrypt {

	// Each of the following particle classes describe a state in the extended finite state machine

	@SerializerId2("o-to-move-particle")
	static class OToMoveParticle extends TicTacToeBaseParticle {
		OToMoveParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquareValue> board) {
			super(xPlayer, oPlayer, board);
		}
	}

	@SerializerId2("x-to-move-particle")
	static class XToMoveParticle extends TicTacToeBaseParticle {
		XToMoveParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquareValue> board) {
			super(xPlayer, oPlayer, board);
		}
	}

	@SerializerId2("o-wins-particle")
	static class OWinsParticle extends TicTacToeBaseParticle {
		OWinsParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquareValue> board) {
			super(xPlayer, oPlayer, board);
		}
	}

	@SerializerId2("x-wins-particle")
	static class XWinsParticle extends TicTacToeBaseParticle {
		XWinsParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquareValue> board) {
			super(xPlayer, oPlayer, board);
		}
	}

	@SerializerId2("draw-particle")
	static class DrawParticle extends TicTacToeBaseParticle {
		DrawParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquareValue> board) {
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

		for (TicTacToeSquareValue square : ticTacToe.getBoard()) {
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

		// Compute what the game state is
		GameStatus gameStatus = null;
		for (ImmutableList<Integer> line : LINES) {
			ImmutableList<TicTacToeSquareValue> board = ticTacToe.getBoard();
			if (board.get(line.get(0)) != TicTacToeSquareValue.EMPTY
				&& board.get(line.get(0)) == board.get(line.get(1))
				&& board.get(line.get(1)) == board.get(line.get(2))) {
				gameStatus = board.get(line.get(0)) == TicTacToeSquareValue.X ? GameStatus.X_WINS : GameStatus.O_WINS;
				break;
			}
		}

		if (gameStatus == null) {
			gameStatus = ticTacToe.getBoard().stream().allMatch(s -> s != TicTacToeSquareValue.EMPTY) ? GameStatus.DRAW : GameStatus.IN_PROGRESS;
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
		// We also need to define the quantative aspects of each state, this is done in the staticCheck()
		// call. During this process we also define where each of these states will "live". In our case we want
		// the game to be stored in both player's addresses.
		os.registerParticle(XToMoveParticle.class, ParticleDefinition.<XToMoveParticle>builder()
			.addressMapper(TicTacToeBaseParticle::getPlayers)
			.staticValidation(ttt -> staticCheck(ttt, GameStatus.IN_PROGRESS))
			.build()
		);

		os.registerParticle(OToMoveParticle.class, ParticleDefinition.<XToMoveParticle>builder()
			.addressMapper(TicTacToeBaseParticle::getPlayers)
			.staticValidation(ttt -> staticCheck(ttt, GameStatus.IN_PROGRESS))
			.build()
		);

		os.registerParticle(XWinsParticle.class, ParticleDefinition.<XToMoveParticle>builder()
			.addressMapper(TicTacToeBaseParticle::getPlayers)
			.staticValidation(ttt -> staticCheck(ttt, GameStatus.X_WINS))
			.build()
		);

		os.registerParticle(OWinsParticle.class, ParticleDefinition.<OWinsParticle>builder()
			.addressMapper(TicTacToeBaseParticle::getPlayers)
			.staticValidation(ttt -> staticCheck(ttt, GameStatus.O_WINS))
			.build()
		);

		os.registerParticle(DrawParticle.class, ParticleDefinition.<DrawParticle>builder()
			.addressMapper(TicTacToeBaseParticle::getPlayers)
			.staticValidation(ttt -> staticCheck(ttt, GameStatus.DRAW))
			.build()
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

		// Next we must define the transition guards of our extended state machine, that is
		// under what conditions are the transitions we defined above allowed?
		os.createTransition(newGameToken, new TransitionProcedure<VoidParticle, VoidUsedData, XToMoveParticle, VoidUsedData>() {

			/**
			 * The precondition defines the state conditions under which the transition is allowed.
			 */
			@Override
			public Result precondition(VoidParticle inputParticle, VoidUsedData inputUsed, XToMoveParticle outputParticle, VoidUsedData outputUsed) {
				for (int squareIndex = 0; squareIndex < 9; squareIndex++) {
					TicTacToeSquareValue nextSquareState = outputParticle.getBoard().get(squareIndex);

					if (nextSquareState != TicTacToeSquareValue.EMPTY) {
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
				return (p, w) -> w.isSignedBy(p.getXPlayer().getPublicKey()) || w.isSignedBy(p.getOPlayer().getPublicKey())
					? WitnessValidatorResult.success()
					: WitnessValidatorResult.error("Game must be started by either one of the players.");
			}
		});

		// The rest of the transition constraints are defined for succinct purposes in the TicTacToeMoveTransitionProcedure class
		os.createTransition(xMovesToken, TicTacToeMoveGuard.xToMove());
		os.createTransition(oMovesToken, TicTacToeMoveGuard.oToMove());
		os.createTransition(xWinsToken, TicTacToeMoveGuard.xToMove());
		os.createTransition(oWinsToken, TicTacToeMoveGuard.oToMove());
		os.createTransition(drawsToken, TicTacToeMoveGuard.xToMove());
	}
}
