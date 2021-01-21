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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;

import java.util.Objects;
import java.util.Set;

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
		this.xPlayer = xPlayer;
		this.oPlayer = oPlayer;
		this.board = board;
	}

	@Override
	public Set<EUID> getDestinations() {
		return ImmutableSet.of(this.xPlayer.euid(), this.oPlayer.euid());
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

	public final Set<RadixAddress> getPlayers() {
		return ImmutableSet.of(getXPlayer(), getOPlayer());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		TicTacToeBaseParticle that = (TicTacToeBaseParticle) o;
		return Objects.equals(xPlayer, that.xPlayer)
			&& Objects.equals(oPlayer, that.oPlayer)
			&& Objects.equals(board, that.board);
	}

	@Override
	public int hashCode() {
		return Objects.hash(xPlayer, oPlayer, board);
	}

}
