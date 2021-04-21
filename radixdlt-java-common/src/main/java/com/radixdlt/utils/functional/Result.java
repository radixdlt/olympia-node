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

package com.radixdlt.utils.functional;

import com.radixdlt.utils.functional.Functions.FN1;
import com.radixdlt.utils.functional.Functions.FN2;
import com.radixdlt.utils.functional.Functions.FN3;
import com.radixdlt.utils.functional.Functions.FN4;
import com.radixdlt.utils.functional.Functions.FN5;
import com.radixdlt.utils.functional.Functions.FN6;
import com.radixdlt.utils.functional.Functions.FN7;
import com.radixdlt.utils.functional.Functions.FN8;
import com.radixdlt.utils.functional.Functions.FN9;
import com.radixdlt.utils.functional.Tuple.Tuple1;
import com.radixdlt.utils.functional.Tuple.Tuple2;
import com.radixdlt.utils.functional.Tuple.Tuple3;
import com.radixdlt.utils.functional.Tuple.Tuple4;
import com.radixdlt.utils.functional.Tuple.Tuple5;
import com.radixdlt.utils.functional.Tuple.Tuple6;
import com.radixdlt.utils.functional.Tuple.Tuple7;
import com.radixdlt.utils.functional.Tuple.Tuple8;
import com.radixdlt.utils.functional.Tuple.Tuple9;

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.radixdlt.utils.functional.Tuple.tuple;

/**
 * Representation of the operation result. The result can be either success or failure.
 * In case of success it holds value returned by the operation. In case of failure it
 * holds a failure description.
 *
 * @param <T> Type of value in case of success.
 */
public interface Result<T> {
	/**
	 * Handle success and failure cases and produce some resulting value.
	 *
	 * @param leftMapper Function to transform the error value.
	 * @param rightMapper Function to transform the success value.
	 *
	 * @return transformed value.
	 */
	<R> R fold(Function<? super Failure, ? extends R> leftMapper, Function<? super T, ? extends R> rightMapper);

	/**
	 * Transform operation result value into value of other type and wrap new
	 * value into {@link Result}. Transformation takes place if current instance
	 * (this) contains successful result, otherwise current instance remains
	 * unchanged and transformation function is not invoked.
	 *
	 * @param mapper Function to transform successful value
	 *
	 * @return transformed value (in case of success) or current instance (in case of failure)
	 */
	@SuppressWarnings("unchecked")
	default <R> Result<R> map(final Function<? super T, R> mapper) {
		return fold(l -> (Result<R>) this, r -> ok(mapper.apply(r)));
	}

	/**
	 * Transform operation result into another operation result. In case if current
	 * instance (this) is an error, transformation function is not invoked
	 * and value remains the same.
	 *
	 * @param mapper Function to apply to result
	 *
	 * @return transformed value (in case of success) or current instance (in case of failure)
	 */
	@SuppressWarnings("unchecked")
	default <R> Result<R> flatMap(final Function<? super T, Result<R>> mapper) {
		return fold(t -> (Result<R>) this, mapper);
	}

	/**
	 * Apply consumers to result value. Note that depending on the result (success or failure) only one consumer will be
	 * applied at a time.
	 *
	 * @param failureConsumer Consumer for failure result
	 * @param successConsumer Consumer for success result
	 *
	 * @return current instance
	 */
	default Result<T> apply(final Consumer<? super Failure> failureConsumer, final Consumer<? super T> successConsumer) {
		return fold(t -> {
			failureConsumer.accept(t);
			return this;
		}, t -> {
			successConsumer.accept(t);
			return this;
		});
	}

	/**
	 * Combine current instance with another result. If current instance holds
	 * success then result is equivalent to current instance, otherwise other
	 * instance (passed as {@code replacement} parameter) is returned.
	 *
	 * @param replacement Value to return if current instance contains failure operation result
	 *
	 * @return current instance in case of success or replacement instance in case of failure.
	 */
	default Result<T> or(final Result<T> replacement) {
		return fold(t -> replacement, t -> this);
	}

	/**
	 * Combine current instance with another result. If current instance holds
	 * success then result is equivalent to current instance, otherwise instance provided by
	 * specified supplier is returned.
	 *
	 * @param supplier Supplier for replacement instance if current instance contains failure operation result
	 *
	 * @return current instance in case of success or result returned by supplier in case of failure.
	 */
	default Result<T> or(final Supplier<Result<T>> supplier) {
		return fold(t -> supplier.get(), t -> this);
	}

	/**
	 * Pass successful operation result value into provided consumer.
	 *
	 * @param consumer Consumer to pass value to
	 *
	 * @return current instance for fluent call chaining
	 */
	Result<T> onSuccess(Consumer<T> consumer);

	/**
	 * Run provided action in case of success.
	 *
	 * @return current instance for fluent call chaining
	 */
	Result<T> onSuccessDo(Runnable action);

	/**
	 * Run provided action in case of failure.
	 *
	 * @return current instance for fluent call chaining
	 */
	Result<T> onFailureDo(Runnable action);

	/**
	 * Pass failure operation result value into provided consumer.
	 *
	 * @param consumer Consumer to pass value to
	 *
	 * @return current instance for fluent call chaining
	 */
	Result<T> onFailure(Consumer<? super Failure> consumer);

	/**
	 * Check for success.
	 *
	 * @return {@code true} if result is a success
	 */
	default boolean isSuccess() {
		return fold(__ -> false, __ -> true);
	}

	/**
	 * Filter contained value with given predicate. Provided string is passed as failure reason
	 * if predicate returns {@code false}.
	 *
	 * @param predicate Predicate to check
	 * @param message Message which will be used in case if predicate returns {@code false}
	 *
	 * @return the same instance if predicate returns {@code true} or new failure result with provided message.
	 */
	default Result<T> filter(Predicate<T> predicate, String message) {
		return flatMap(v -> predicate.test(v) ? this : fail(message));
	}

	/**
	 * Filter contained value with given predicate. Provided string and parameters are passed
	 * as failure reason if predicate returns {@code false}.
	 *
	 * @param predicate Predicate to check
	 * @param message Message which will be used in case if predicate returns {@code false}
	 * @param values Message parameters
	 *
	 * @return the same instance if predicate returns {@code true} or new failure result with provided message.
	 *
	 * @see Failure#failure(String, Object...) for more details
	 */
	default Result<T> filter(Predicate<T> predicate, String message, Object... values) {
		return flatMap(v -> predicate.test(v) ? this : fail(message, values));
	}

	/**
	 * Convert instance into {@link Optional} of the same type. Successful instance
	 * is converted into present {@link Optional} and failure - into empty {@link Optional}.
	 * Note that during such a conversion error information may get lost.
	 *
	 * @return {@link Optional} instance which is present in case of success and missing
	 * 	in case of failure.
	 */
	default Optional<T> toOptional() {
		return fold(t1 -> Optional.empty(), Optional::of);
	}

	/**
	 * Convert instance into {@link Result}
	 *
	 * @param source input instance of {@link Optional}
	 * @param message message to use when input is empty instance.
	 *
	 * @return created instance
	 */
	static <T> Result<T> fromOptional(Optional<T> source, String message) {
		return source.map(Result::ok).orElseGet(() -> fail(message));
	}

	/**
	 * Convert instance into {@link Result}
	 *
	 * @param source input instance of {@link Optional}
	 * @param format format string to use when input is empty instance.
	 * @param args additional arguments for the message format
	 *
	 * @return created instance
	 *
	 * @see Failure#failure(String, Object...) for more details
	 */
	static <T> Result<T> fromOptional(Optional<T> source, String format, Object... args) {
		return source.map(Result::ok).orElseGet(() -> fail(format, args));
	}

	/**
	 * Wrap call to function which may throw an exception.
	 *
	 * @param supplier the function to call.
	 *
	 * @return success instance if call was successful and failure instance if function threw an exception.
	 */
	static <T> Result<T> wrap(ThrowingSupplier<T> supplier) {
		try {
			return ok(supplier.get());
		} catch (Throwable e) {
			return fail(e);
		}
	}

	/**
	 * Create an instance of successful operation result.
	 *
	 * @param value Operation result
	 *
	 * @return created instance
	 */
	static <R> Result<R> ok(final R value) {
		return new ResultOk<>(value);
	}

	/**
	 * Create an instance of failure operation result.
	 *
	 * @param value Operation error value
	 *
	 * @return created instance
	 */
	static <R> Result<R> fail(final Failure value) {
		return new ResultFail<R>(value);
	}

	/**
	 * Create an instance of simple failure operation result.
	 *
	 * @param message Error message
	 *
	 * @return created instance
	 */
	static <R> Result<R> fail(final String message) {
		return new ResultFail<R>(Failure.failure(message));
	}

	/**
	 * Create an instance of simple failure operation result from exception.
	 *
	 * @param throwable Exception to convert
	 *
	 * @return created instance
	 */
	static <R> Result<R> fail(final Throwable throwable) {
		return new ResultFail<R>(Failure.failure(throwable.getMessage()));
	}

	/**
	 * Create an instance of simple failure operation result.
	 *
	 * @param format Error message format string
	 * @param values Error message values
	 *
	 * @return created instance
	 */
	static <R> Result<R> fail(final String format, Object... values) {
		return new ResultFail<R>(Failure.failure(format, values));
	}

	final class ResultOk<R> implements Result<R> {
		private final R value;

		protected ResultOk(final R value) {
			this.value = value;
		}

		@Override
		public <T> T fold(
			final Function<? super Failure, ? extends T> leftMapper,
			final Function<? super R, ? extends T> rightMapper
		) {
			return rightMapper.apply(value);
		}

		@Override
		public int hashCode() {
			return Objects.hash(value);
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}

			return (obj instanceof Result<?>)
				   ? ((Result<?>) obj).fold($ -> false, val -> Objects.equals(val, value))
				   : false;
		}

		@Override
		public String toString() {
			return new StringJoiner(", ", "Result-success(", ")")
				.add(value.toString())
				.toString();
		}

		@Override
		public Result<R> onSuccess(final Consumer<R> consumer) {
			consumer.accept(value);
			return this;
		}

		@Override
		public Result<R> onSuccessDo(final Runnable action) {
			action.run();
			return this;
		}

		@Override
		public Result<R> onFailure(final Consumer<? super Failure> consumer) {
			return this;
		}

		@Override
		public Result<R> onFailureDo(final Runnable action) {
			return this;
		}
	}

	final class ResultFail<R> implements Result<R> {
		private final Failure value;

		protected ResultFail(final Failure value) {
			this.value = value;
		}

		@Override
		public <T> T fold(
			final Function<? super Failure, ? extends T> leftMapper,
			final Function<? super R, ? extends T> rightMapper
		) {
			return leftMapper.apply(value);
		}

		@Override
		public int hashCode() {
			return Objects.hash(value);
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}

			return (obj instanceof Result<?>)
				   ? ((Result<?>) obj).fold(val -> Objects.equals(val, value), $ -> false)
				   : false;
		}

		@Override
		public String toString() {
			return new StringJoiner(", ", "Result-failure(", ")")
				.add(value.toString())
				.toString();
		}

		@Override
		public Result<R> onSuccess(final Consumer<R> consumer) {
			return this;
		}

		@Override
		public Result<R> onSuccessDo(final Runnable action) {
			return this;
		}

		@Override
		public Result<R> onFailure(final Consumer<? super Failure> consumer) {
			consumer.accept(value);
			return this;
		}

		@Override
		public Result<R> onFailureDo(final Runnable action) {
			action.run();
			return this;
		}
	}

	static <T1> Mapper1<T1> allOf(Result<T1> op1) {
		return () -> op1.flatMap(v1 -> Result.ok(tuple(v1)));
	}

	static <T1, T2> Mapper2<T1, T2> allOf(Result<T1> op1, Result<T2> op2) {
		return () -> op1.flatMap(v1 -> op2.flatMap(v2 -> Result.ok(tuple(v1, v2))));
	}

	static <T1, T2, T3> Mapper3<T1, T2, T3> allOf(Result<T1> op1, Result<T2> op2, Result<T3> op3) {
		return () -> op1.flatMap(v1 -> op2.flatMap(v2 -> op3.flatMap(v3 -> Result.ok(tuple(v1, v2, v3)))));
	}

	static <T1, T2, T3, T4> Mapper4<T1, T2, T3, T4> allOf(
		Result<T1> op1, Result<T2> op2, Result<T3> op3, Result<T4> op4
	) {
		return () -> op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> Result.ok(tuple(v1, v2, v3, v4))))));
	}

	static <T1, T2, T3, T4, T5> Mapper5<T1, T2, T3, T4, T5> allOf(
		Result<T1> op1, Result<T2> op2, Result<T3> op3, Result<T4> op4, Result<T5> op5
	) {
		return () -> op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> Result.ok(tuple(v1, v2, v3, v4, v5)))))));
	}

	static <T1, T2, T3, T4, T5, T6> Mapper6<T1, T2, T3, T4, T5, T6> allOf(
		Result<T1> op1, Result<T2> op2, Result<T3> op3,
		Result<T4> op4, Result<T5> op5, Result<T6> op6
	) {
		return () -> op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> op6.flatMap(
								v6 -> Result.ok(tuple(v1, v2, v3, v4, v5, v6))))))));
	}

	static <T1, T2, T3, T4, T5, T6, T7> Mapper7<T1, T2, T3, T4, T5, T6, T7> allOf(
		Result<T1> op1, Result<T2> op2, Result<T3> op3, Result<T4> op4,
		Result<T5> op5, Result<T6> op6, Result<T7> op7
	) {
		return () -> op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> op6.flatMap(
								v6 -> op7.flatMap(
									v7 -> Result.ok(tuple(v1, v2, v3, v4, v5, v6, v7)))))))));
	}

	static <T1, T2, T3, T4, T5, T6, T7, T8> Mapper8<T1, T2, T3, T4, T5, T6, T7, T8> allOf(
		Result<T1> op1, Result<T2> op2, Result<T3> op3, Result<T4> op4,
		Result<T5> op5, Result<T6> op6, Result<T7> op7, Result<T8> op8
	) {
		return () -> op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> op6.flatMap(
								v6 -> op7.flatMap(
									v7 -> op8.flatMap(
										v8 -> Result.ok(tuple(v1, v2, v3, v4, v5, v6, v7, v8))))))))));
	}

	static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Mapper9<T1, T2, T3, T4, T5, T6, T7, T8, T9> allOf(
		Result<T1> op1, Result<T2> op2, Result<T3> op3, Result<T4> op4, Result<T5> op5,
		Result<T6> op6, Result<T7> op7, Result<T8> op8, Result<T9> op9
	) {
		return () -> op1.flatMap(
			v1 -> op2.flatMap(
				v2 -> op3.flatMap(
					v3 -> op4.flatMap(
						v4 -> op5.flatMap(
							v5 -> op6.flatMap(
								v6 -> op7.flatMap(
									v7 -> op8.flatMap(
										v8 -> op9.flatMap(
											v9 -> Result.ok(
												tuple(v1, v2, v3, v4, v5, v6, v7, v8, v9)
											))))))))));
	}

	interface Mapper1<T1> {
		Result<Tuple1<T1>> id();

		default <R> Result<R> map(FN1<R, T1> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN1<Result<R>, T1> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper2<T1, T2> {
		Result<Tuple2<T1, T2>> id();

		default <R> Result<R> map(FN2<R, T1, T2> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN2<Result<R>, T1, T2> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper3<T1, T2, T3> {
		Result<Tuple3<T1, T2, T3>> id();

		default <R> Result<R> map(FN3<R, T1, T2, T3> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN3<Result<R>, T1, T2, T3> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper4<T1, T2, T3, T4> {
		Result<Tuple4<T1, T2, T3, T4>> id();

		default <R> Result<R> map(FN4<R, T1, T2, T3, T4> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN4<Result<R>, T1, T2, T3, T4> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper5<T1, T2, T3, T4, T5> {
		Result<Tuple5<T1, T2, T3, T4, T5>> id();

		default <R> Result<R> map(FN5<R, T1, T2, T3, T4, T5> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN5<Result<R>, T1, T2, T3, T4, T5> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper6<T1, T2, T3, T4, T5, T6> {
		Result<Tuple6<T1, T2, T3, T4, T5, T6>> id();

		default <R> Result<R> map(FN6<R, T1, T2, T3, T4, T5, T6> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN6<Result<R>, T1, T2, T3, T4, T5, T6> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper7<T1, T2, T3, T4, T5, T6, T7> {
		Result<Tuple7<T1, T2, T3, T4, T5, T6, T7>> id();

		default <R> Result<R> map(FN7<R, T1, T2, T3, T4, T5, T6, T7> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN7<Result<R>, T1, T2, T3, T4, T5, T6, T7> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper8<T1, T2, T3, T4, T5, T6, T7, T8> {
		Result<Tuple8<T1, T2, T3, T4, T5, T6, T7, T8>> id();

		default <R> Result<R> map(FN8<R, T1, T2, T3, T4, T5, T6, T7, T8> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN8<Result<R>, T1, T2, T3, T4, T5, T6, T7, T8> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}

	interface Mapper9<T1, T2, T3, T4, T5, T6, T7, T8, T9> {
		Result<Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> id();

		default <R> Result<R> map(FN9<R, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper) {
			return id().map(tuple -> tuple.map(mapper));
		}

		default <R> Result<R> flatMap(FN9<Result<R>, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper) {
			return id().flatMap(tuple -> tuple.map(mapper));
		}
	}
}