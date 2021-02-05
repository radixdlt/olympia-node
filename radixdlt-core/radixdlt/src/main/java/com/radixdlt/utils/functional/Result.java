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
import com.radixdlt.utils.functional.ResultMappers.Mapper1;
import com.radixdlt.utils.functional.ResultMappers.Mapper2;
import com.radixdlt.utils.functional.ResultMappers.Mapper3;
import com.radixdlt.utils.functional.ResultMappers.Mapper4;
import com.radixdlt.utils.functional.ResultMappers.Mapper5;
import com.radixdlt.utils.functional.ResultMappers.Mapper6;
import com.radixdlt.utils.functional.ResultMappers.Mapper7;
import com.radixdlt.utils.functional.ResultMappers.Mapper8;
import com.radixdlt.utils.functional.ResultMappers.Mapper9;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.radixdlt.utils.functional.Tuple.tuple;

/**
 * Representation of the operation result. The result can be either success or failure.
 * In case of success it holds value returned by the operation. In case of failure it
 * holds a failure description.
 *
 * @param <T> Type of value in case of success.
 */
public interface Result<T> extends Foldable<Failure, T> {
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
	default <R> Result<R> map(final FN1<R, ? super T> mapper) {
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
	default <R> Result<R> flatMap(final FN1<Result<R>, ? super T> mapper) {
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
	default Result<T> onSuccess(final Consumer<T> consumer) {
		fold(v -> null, v -> {
			consumer.accept(v);
			return null;
		});
		return this;
	}

	/**
	 * Run provided action in case of success.
	 *
	 * @return current instance for fluent call chaining
	 */
	default Result<T> onSuccessDo(final Runnable action) {
		fold(v -> null, v -> {
			action.run();
			return null;
		});
		return this;
	}

	/**
	 * Pass failure operation result value into provided consumer.
	 *
	 * @param consumer Consumer to pass value to
	 *
	 * @return current instance for fluent call chaining
	 */
	default Result<T> onFailure(final Consumer<? super Failure> consumer) {
		fold(v -> {
			consumer.accept(v);
			return null;
		}, v -> null);
		return this;
	}

	/**
	 * Run provided action in case of failure.
	 *
	 * @return current instance for fluent call chaining
	 */
	default Result<T> onFailureDo(final Runnable action) {
		fold(v -> {
			action.run();
			return null;
		}, v -> null);
		return this;
	}

	/**
	 * Convert instance into {@link Option} of the same type. Successful instance
	 * is converted into present {@link Option} and failure - into empty {@link Option}.
	 * Note that during such a conversion error information may get lost.
	 *
	 * @return {@link Option} instance which is present in case of success and missing
	 * in case of failure.
	 */
	default Option<T> toOption() {
		return fold(t1 -> Option.empty(), Option::option);
	}

	/**
	 * Create an instance of successful operation result.
	 *
	 * @param value Operation result
	 *
	 * @return created instance
	 */
	static <R> Result<R> ok(final R value) {
		return new ResultBase<R>() {
			@Override
			public <T> T fold(
				final FN1<? extends T, ? super Failure> leftMapper,
				final FN1<? extends T, ? super R> rightMapper
			) {
				return rightMapper.apply(value);
			}
		};
	}

	/**
	 * Create an instance of failure operation result.
	 *
	 * @param value Operation error value
	 *
	 * @return created instance
	 */
	static <R> Result<R> fail(final Failure value) {
		return new ResultBase<R>() {
			@Override
			public <T> T fold(
				final FN1<? extends T, ? super Failure> leftMapper,
				final FN1<? extends T, ? super R> rightMapper
			) {
				return leftMapper.apply(value);
			}
		};
	}

	/**
	 * "Flatten" several results into one transformable entity.
	 *
	 * @param value
	 *
	 * @return {@link Mapper1} transformable entity
	 */
	static <T1> Mapper1<T1> allOf(final Result<T1> value) {
		return () -> value.flatMap(vv1 -> ok(tuple(vv1)));
	}

	/**
	 * "Flatten" several results into one transformable entity.
	 *
	 * @param value1
	 * @param value2
	 *
	 * @return {@link Mapper2} transformable entity
	 */
	static <T1, T2> Mapper2<T1, T2> allOf(final Result<T1> value1, final Result<T2> value2) {
		return () -> value1.flatMap(
			vv1 -> value2.flatMap(vv2 -> ok(tuple(vv1, vv2))));
	}

	/**
	 * "Flatten" several results into one transformable entity.
	 *
	 * @param value1
	 * @param value2
	 * @param value3
	 *
	 * @return {@link Mapper3} transformable entity
	 */
	static <T1, T2, T3> Mapper3<T1, T2, T3> allOf(final Result<T1> value1, final Result<T2> value2, final Result<T3> value3) {
		return () -> value1.flatMap(
			vv1 -> value2.flatMap(
				vv2 -> value3.flatMap(
					vv3 -> ok(tuple(vv1, vv2, vv3)))));
	}

	/**
	 * "Flatten" several results into one transformable entity.
	 *
	 * @param value1
	 * @param value2
	 * @param value3
	 * @param value4
	 *
	 * @return {@link Mapper4} transformable entity
	 */
	static <T1, T2, T3, T4> Mapper4<T1, T2, T3, T4> allOf(
		final Result<T1> value1,
		final Result<T2> value2,
		final Result<T3> value3,
		final Result<T4> value4
	) {
		return () -> value1.flatMap(
			vv1 -> value2.flatMap(
				vv2 -> value3.flatMap(
					vv3 -> value4.flatMap(
						vv4 -> ok(tuple(vv1, vv2, vv3, vv4))))));
	}

	/**
	 * "Flatten" several results into one transformable entity.
	 *
	 * @param value1
	 * @param value2
	 * @param value3
	 * @param value4
	 * @param value5
	 *
	 * @return {@link Mapper5} transformable entity
	 */
	static <T1, T2, T3, T4, T5> Mapper5<T1, T2, T3, T4, T5> allOf(
		final Result<T1> value1,
		final Result<T2> value2,
		final Result<T3> value3,
		final Result<T4> value4,
		final Result<T5> value5
	) {
		return () -> value1.flatMap(
			vv1 -> value2.flatMap(
				vv2 -> value3.flatMap(
					vv3 -> value4.flatMap(
						vv4 -> value5.flatMap(
							vv5 -> ok(tuple(vv1, vv2, vv3, vv4, vv5)))))));
	}

	/**
	 * "Flatten" several results into one transformable entity.
	 *
	 * @param value1
	 * @param value2
	 * @param value3
	 * @param value4
	 * @param value5
	 * @param value6
	 *
	 * @return {@link Mapper6} transformable entity
	 */
	static <T1, T2, T3, T4, T5, T6> Mapper6<T1, T2, T3, T4, T5, T6> allOf(
		final Result<T1> value1,
		final Result<T2> value2,
		final Result<T3> value3,
		final Result<T4> value4,
		final Result<T5> value5,
		final Result<T6> value6
	) {
		return () -> value1.flatMap(
			vv1 -> value2.flatMap(
				vv2 -> value3.flatMap(
					vv3 -> value4.flatMap(
						vv4 -> value5.flatMap(
							vv5 -> value6.flatMap(
								vv6 -> ok(tuple(vv1, vv2, vv3, vv4, vv5, vv6))))))));
	}

	/**
	 * "Flatten" several results into one transformable entity.
	 *
	 * @param value1
	 * @param value2
	 * @param value3
	 * @param value4
	 * @param value5
	 * @param value6
	 * @param value7
	 *
	 * @return {@link Mapper7} transformable entity
	 */
	static <T1, T2, T3, T4, T5, T6, T7> Mapper7<T1, T2, T3, T4, T5, T6, T7> allOf(
		final Result<T1> value1,
		final Result<T2> value2,
		final Result<T3> value3,
		final Result<T4> value4,
		final Result<T5> value5,
		final Result<T6> value6,
		final Result<T7> value7
	) {
		return () -> value1.flatMap(
			vv1 -> value2.flatMap(
				vv2 -> value3.flatMap(
					vv3 -> value4.flatMap(
						vv4 -> value5.flatMap(
							vv5 -> value6.flatMap(
								vv6 -> value7.flatMap(
									vv7 -> ok(tuple(vv1, vv2, vv3, vv4, vv5, vv6, vv7)))))))));
	}

	/**
	 * "Flatten" several results into one transformable entity.
	 *
	 * @param value1
	 * @param value2
	 * @param value3
	 * @param value4
	 * @param value5
	 * @param value6
	 * @param value7
	 * @param value8
	 *
	 * @return {@link Mapper8} transformable entity
	 */
	static <T1, T2, T3, T4, T5, T6, T7, T8> Mapper8<T1, T2, T3, T4, T5, T6, T7, T8> allOf(
		final Result<T1> value1,
		final Result<T2> value2,
		final Result<T3> value3,
		final Result<T4> value4,
		final Result<T5> value5,
		final Result<T6> value6,
		final Result<T7> value7,
		final Result<T8> value8
	) {
		return () -> value1.flatMap(
			vv1 -> value2.flatMap(
				vv2 -> value3.flatMap(
					vv3 -> value4.flatMap(
						vv4 -> value5.flatMap(
							vv5 -> value6.flatMap(
								vv6 -> value7.flatMap(
									vv7 -> value8.flatMap(
										vv8 -> ok(tuple(vv1, vv2, vv3, vv4, vv5, vv6, vv7, vv8))))))))));
	}

	/**
	 * "Flatten" several results into one transformable entity.
	 *
	 * @param value1
	 * @param value2
	 * @param value3
	 * @param value4
	 * @param value5
	 * @param value6
	 * @param value7
	 * @param value8
	 * @param value9
	 *
	 * @return {@link Mapper9} transformable entity
	 */
	static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Mapper9<T1, T2, T3, T4, T5, T6, T7, T8, T9> allOf(
		final Result<T1> value1,
		final Result<T2> value2,
		final Result<T3> value3,
		final Result<T4> value4,
		final Result<T5> value5,
		final Result<T6> value6,
		final Result<T7> value7,
		final Result<T8> value8,
		final Result<T9> value9
	) {
		return () -> value1.flatMap(
			vv1 -> value2.flatMap(
				vv2 -> value3.flatMap(
					vv3 -> value4.flatMap(
						vv4 -> value5.flatMap(
							vv5 -> value6.flatMap(
								vv6 -> value7.flatMap(
									vv7 -> value8.flatMap(
										vv8 -> value9.flatMap(
											vv9 -> ok(tuple(vv1, vv2, vv3, vv4, vv5, vv6, vv7, vv8, vv9)))))))))));
	}

	abstract class ResultBase<T> implements Result<T> {
		@Override
		public int hashCode() {
			return Objects.hash(fold(FN1.id(), FN1.id()));
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}

			if (obj instanceof Result<?>) {
				var result = (Result<?>) obj;

				return result.fold(
					v -> Objects.equals(v, fold(FN1.id(), v1 -> null)),
					v -> Objects.equals(v, fold(v1 -> null, FN1.id()))
				);
			}
			return false;
		}

		@Override
		public String toString() {
			return fold(
				f -> "Failure(" + f.toString() + ")",
				s -> "Success(" + s.toString() + ")"
			);
		}
	}
}
