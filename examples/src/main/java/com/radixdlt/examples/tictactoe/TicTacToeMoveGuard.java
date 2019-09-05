package com.radixdlt.examples.tictactoe;

import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.UsedCompute;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.examples.tictactoe.TicTacToeBaseParticle.TicTacToeSquareValue;
import java.util.Objects;
import java.util.Optional;

public class TicTacToeMoveGuard<T extends TicTacToeBaseParticle, U extends TicTacToeBaseParticle>
	implements TransitionProcedure<T, VoidUsedData, U, VoidUsedData> {

	private final TicTacToeSquareValue toMove;

	private TicTacToeMoveGuard(TicTacToeSquareValue toMove) {
		this.toMove = Objects.requireNonNull(toMove);
	}

	public static <T extends TicTacToeBaseParticle, U extends TicTacToeBaseParticle> TicTacToeMoveGuard<T, U> xToMove() {
		return new TicTacToeMoveGuard<>(TicTacToeSquareValue.X);
	}

	public static <T extends TicTacToeBaseParticle, U extends TicTacToeBaseParticle> TicTacToeMoveGuard<T, U> oToMove() {
		return new TicTacToeMoveGuard<>(TicTacToeSquareValue.O);
	}

	@Override
	public Result precondition(
		T inputParticle,
		VoidUsedData inputUsed,
		U outputParticle,
		VoidUsedData outputUsed
	) {
		// Check that players aren't changed
		if (!inputParticle.getOPlayer().equals(outputParticle.getOPlayer())
			&& !inputParticle.getXPlayer().equals(outputParticle.getXPlayer())) {
			return Result.error("Game should have the same players");
		}

		TicTacToeSquareValue foundMove = null;

		// Check that it is a legal next move
		for (int squareIndex = 0; squareIndex < TicTacToeBaseParticle.TIC_TAC_TOE_BOARD_SIZE; squareIndex++) {
			TicTacToeSquareValue initSquareState = inputParticle.getBoard().get(squareIndex);
			TicTacToeSquareValue nextSquareState = outputParticle.getBoard().get(squareIndex);

			if (initSquareState != nextSquareState) {
				if (foundMove != null || initSquareState != TicTacToeSquareValue.EMPTY) {
					return Result.error("Invalid move");
				}

				foundMove = nextSquareState;
			}
		}

		// Check that the mover matches
		if (foundMove != toMove) {
			return Result.error(toMove + " must make a move.");
		}

		return Result.success();
	}

	@Override
	public UsedCompute<T, VoidUsedData, U, VoidUsedData> inputUsedCompute() {
		return (in, inUsed, out, outUsed) -> Optional.empty();
	}

	@Override
	public UsedCompute<T, VoidUsedData, U, VoidUsedData> outputUsedCompute() {
		return (in, inUsed, out, outUsed) -> Optional.empty();
	}

	@Override
	public WitnessValidator<T> inputWitnessValidator() {
		// Move should be signed by player making the move
		return (p, w) -> w.isSignedBy((toMove == TicTacToeSquareValue.X ? p.getXPlayer() : p.getOPlayer()).getKey())
			? WitnessValidatorResult.success()
			: WitnessValidatorResult.error("Move not signed by player");
	}

	@Override
	public WitnessValidator<U> outputWitnessValidator() {
		return (p, w) -> WitnessValidatorResult.success();
	}
}
