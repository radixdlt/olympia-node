package com.radixdlt.examples.tictactoe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;

/**
 * Base particle for particles with tic tac toe board and players.
 *
 * TODO: Really this shouldn't be an abstract class but constraints of current serialization mechanism prevent this
 * TODO: from being a particle interface
 */
abstract class TicTacToeBaseParticle extends Particle {
	enum TicTacToeSquareValue { X, O, EMPTY }
	public static final int TIC_TAC_TOE_BOARD_SIZE = 9;

	@JsonProperty("xPlayer")
	@DsonOutput(Output.ALL)
	private final RadixAddress xPlayer;

	@JsonProperty("oPlayer")
	@DsonOutput(Output.ALL)
	private final RadixAddress oPlayer;

	@JsonProperty("board")
	@DsonOutput(Output.ALL)
	private final ImmutableList<TicTacToeSquareValue> board;

	TicTacToeBaseParticle(RadixAddress xPlayer, RadixAddress oPlayer, ImmutableList<TicTacToeSquareValue> board) {
		super(ImmutableSet.of(xPlayer.getUID(), oPlayer.getUID()));

		this.xPlayer = xPlayer;
		this.oPlayer = oPlayer;
		this.board = board;
	}

	public final ImmutableList<TicTacToeSquareValue> getBoard() {
		return board;
	}

	public final RadixAddress getXPlayer() {
		return xPlayer;
	}

	public final RadixAddress getOPlayer() {
		return oPlayer;
	}
}
