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

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
}