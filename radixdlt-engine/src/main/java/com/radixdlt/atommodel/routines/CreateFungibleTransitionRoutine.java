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

package com.radixdlt.atommodel.routines;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atommodel.tokens.Fungible;
import com.radixdlt.atomos.ConstraintRoutine;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.RoutineCalls;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.InputOutputReducer;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.store.ReadableAddrs;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

/**
 * Transition Procedure for one to one fungible types
 */
public class CreateFungibleTransitionRoutine<I extends Fungible, O extends Fungible> implements ConstraintRoutine {
	public interface ActionMapper<I, O> {
		TxAction map(I i, O o, ReadableAddrs index);
	}

	public static final class UsedAmount implements ReducerState {
		private final UInt256 amount;
		private final boolean isInput;
		// FIXME: super hack
		private final TxAction txAction;

		public UsedAmount(boolean isInput, UInt256 usedAmount, TxAction txAction) {
			this.isInput = isInput;
			this.amount = Objects.requireNonNull(usedAmount);
			this.txAction = txAction;
		}

		public boolean isInput() {
			return isInput;
		}

		public UInt256 getUsedAmount() {
			return this.amount;
		}

		@Override
		public TypeToken<? extends ReducerState> getTypeToken() {
			return TypeToken.of(UsedAmount.class);
		}

		@Override
		public int hashCode() {
			return Objects.hash(isInput, amount, txAction);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof UsedAmount)) {
				return false;
			}

			UsedAmount u = (UsedAmount) obj;
			return this.isInput == u.isInput
				&& Objects.equals(this.amount, u.amount)
				&& Objects.equals(this.txAction, u.txAction);
		}

		@Override
		public String toString() {
			return String.valueOf(this.amount);
		}
	}

	private final Class<I> inputClass;
	private final Class<O> outputClass;
	private final Verifier<I, O> transitionVerifier;
	private final SignatureValidator<I, O> signatureValidator;
	private final ActionMapper<I, O> actionMapper;

	public interface Verifier<I extends Fungible, O extends Fungible> {
		Result verify(I in, O out, ReadableAddrs readableAddrs);
	}

	public CreateFungibleTransitionRoutine(
		Class<I> inputClass,
		Class<O> outputClass,
		Verifier<I, O> transitionVerifier,
		SignatureValidator<I, O> signatureValidator,
		ActionMapper<I, O> actionMapper
	) {
		Objects.requireNonNull(transitionVerifier);

		this.inputClass = inputClass;
		this.outputClass = outputClass;
		this.transitionVerifier = transitionVerifier;
		this.signatureValidator = signatureValidator;
		this.actionMapper = actionMapper;
	}

	@Override
	public void main(RoutineCalls calls) {
		calls.createTransition(
			new TransitionToken<>(inputClass, outputClass, TypeToken.of(VoidReducerState.class)),
			getProcedure0()
		);

		calls.createTransition(
			new TransitionToken<>(inputClass, outputClass, TypeToken.of(UsedAmount.class)),
			getProcedure1()
		);
	}

	public TransitionProcedure<I, O, VoidReducerState> getProcedure0() {
		return new TransitionProcedure<I, O, VoidReducerState>() {
			@Override
			public Result precondition(
				SubstateWithArg<I> in,
				O outputParticle,
				VoidReducerState outputUsed,
				ReadableAddrs readable
			) {
				return transitionVerifier.verify(in.getSubstate(), outputParticle, readable);
			}

			@Override
			public InputOutputReducer<I, O, VoidReducerState> inputOutputReducer() {
				return (input, output, index, v) -> {
					var i = input.getSubstate().getAmount();
					var o = output.getAmount();
					var compare = i.compareTo(o);
					var txAction = actionMapper.map(input.getSubstate(), output, index);
					if (compare == 0) {
						return ReducerResult.complete(txAction);
					}
					return compare > 0
						? ReducerResult.incomplete(new UsedAmount(true, o, txAction), true)
						: ReducerResult.incomplete(new UsedAmount(false, i, txAction), false);
				};
			}

			@Override
			public SignatureValidator<I, O> signatureValidator() {
				return signatureValidator;
			}
		};
	}

	public TransitionProcedure<I, O, UsedAmount> getProcedure1() {
		return new TransitionProcedure<I, O, UsedAmount>() {
			@Override
			public Result precondition(SubstateWithArg<I> in, O outputParticle, UsedAmount used, ReadableAddrs readable) {
				return transitionVerifier.verify(in.getSubstate(), outputParticle, readable);
			}

			@Override
			public InputOutputReducer<I, O, UsedAmount> inputOutputReducer() {
				return (input, output, index, used) -> {
					var i = input.getSubstate().getAmount();
					var o = output.getAmount();
					if (used.isInput) {
						i = i.subtract(used.getUsedAmount());
					} else {
						o = o.subtract(used.getUsedAmount());
					}
					var compare = i.compareTo(o);
					if (compare == 0) {
						return ReducerResult.complete(used.txAction);
					}

					var keepInput = compare > 0;

					final UInt256 nextUsedAmt;
					if (keepInput == used.isInput) {
						nextUsedAmt = used.getUsedAmount().add(keepInput ? o : i);
					} else {
						nextUsedAmt = keepInput ? o : i;
					}

					return ReducerResult.incomplete(new UsedAmount(keepInput, nextUsedAmt, used.txAction), keepInput);
				};
			}

			@Override
			public SignatureValidator<I, O> signatureValidator() {
				return signatureValidator;
			}
		};
	}
}