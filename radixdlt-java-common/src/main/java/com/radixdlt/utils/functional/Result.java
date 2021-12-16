/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.utils.functional;

import static com.radixdlt.utils.functional.Tuple.tuple;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Representation of the operation result. The result can be either success or failure. In case of
 * success it holds value returned by the operation. In case of failure it holds a failure
 * description.
 *
 * @param <T> Type of value in case of success.
 */
public interface Result<T> {
  /**
   * Handle success and failure cases and produce some resulting value.
   *
   * @param leftMapper Function to transform the error value.
   * @param rightMapper Function to transform the success value.
   * @return transformed value.
   */
  <R> R fold(
      Function<? super Failure, ? extends R> leftMapper,
      Function<? super T, ? extends R> rightMapper);

  /**
   * Version of {@link #fold(Function, Function)} which uses one function to handle both cases.
   *
   * @param mapper Function to transform the error and success values.
   * @return transformed value.
   */
  default <R> R fold(BiFunction<Failure, T, R> mapper) {
    return fold(f -> mapper.apply(f, null), v -> mapper.apply(null, v));
  }

  /**
   * Transform operation result value into value of other type and wrap new value into {@link
   * Result}. Transformation takes place if current instance (this) contains successful result,
   * otherwise current instance remains unchanged and transformation function is not invoked.
   *
   * @param mapper Function to transform successful value
   * @return transformed value (in case of success) or current instance (in case of failure)
   */
  @SuppressWarnings("unchecked")
  default <R> Result<R> map(Function<? super T, R> mapper) {
    return fold(l -> (Result<R>) this, r -> ok(mapper.apply(r)));
  }

  /**
   * Transform operation result into another operation result. In case if current instance (this) is
   * an error, transformation function is not invoked and value remains the same.
   *
   * @param mapper Function to apply to result
   * @return transformed value (in case of success) or current instance (in case of failure)
   */
  @SuppressWarnings("unchecked")
  default <R> Result<R> flatMap(Function<? super T, Result<R>> mapper) {
    return fold(t -> (Result<R>) this, mapper);
  }

  /**
   * Apply consumers to result value. Note that depending on the result (success or failure) only
   * one consumer will be applied at a time.
   *
   * @param failureConsumer Consumer for failure result
   * @param successConsumer Consumer for success result
   * @return current instance
   */
  default Result<T> apply(
      Consumer<? super Failure> failureConsumer, Consumer<? super T> successConsumer) {
    return fold(
        t -> {
          failureConsumer.accept(t);
          return this;
        },
        t -> {
          successConsumer.accept(t);
          return this;
        });
  }

  /**
   * Combine current instance with another result. If current instance holds success then result is
   * equivalent to current instance, otherwise other instance (passed as {@code replacement}
   * parameter) is returned.
   *
   * @param replacement Value to return if current instance contains failure operation result
   * @return current instance in case of success or replacement instance in case of failure.
   */
  default Result<T> or(Result<T> replacement) {
    return fold(t -> replacement, t -> this);
  }

  /**
   * Combine current instance with another result. If current instance holds success then result is
   * equivalent to current instance, otherwise instance provided by specified supplier is returned.
   *
   * @param supplier Supplier for replacement instance if current instance contains failure
   *     operation result
   * @return current instance in case of success or result returned by supplier in case of failure.
   */
  default Result<T> or(Supplier<Result<T>> supplier) {
    return fold(t -> supplier.get(), t -> this);
  }

  /**
   * Pass successful operation result value into provided consumer.
   *
   * @param consumer Consumer to pass value to
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
   * Filter contained value with given predicate. Provided failure is used for the result if
   * predicate returns {@code false}.
   *
   * @param predicate Predicate to check
   * @param failure Failure which will be used in case if predicate returns {@code false}
   * @return the same instance if predicate returns {@code true} or new failure result with provided
   *     failure.
   */
  default Result<T> filter(Predicate<T> predicate, Failure failure) {
    return flatMap(v -> predicate.test(v) ? this : failure.with(v).result());
  }

  /**
   * Convert instance into {@link Optional} of the same type. Successful instance is converted into
   * present {@link Optional} and failure - into empty {@link Optional}. Note that during such a
   * conversion error information may get lost.
   *
   * @return {@link Optional} instance which is present in case of success and missing in case of
   *     failure.
   */
  default Optional<T> toOptional() {
    return fold(t1 -> Optional.empty(), Optional::of);
  }

  /**
   * Convert instance into {@link Result}
   *
   * @param failure failure to use when input is empty instance.
   * @param source input instance of {@link Optional}
   * @return created instance
   */
  static <T> Result<T> fromOptional(Failure failure, Optional<T> source) {
    return source.map(Result::ok).orElseGet(failure::result);
  }

  /**
   * Convert instance into {@link Result}
   *
   * @param failure supplier of failure which is used when input is empty instance.
   * @param source input instance of {@link Optional}
   * @return created instance
   */
  static <T> Result<T> fromOptional(Supplier<Failure> failure, Optional<T> source) {
    return source.map(Result::ok).orElseGet(() -> failure.get().result());
  }

  /**
   * Wrap call to function which may throw an exception.
   *
   * @param failure the failure to represent the error which may happen during call
   * @param supplier the function to call.
   * @return success instance if call was successful and failure instance if function threw an
   *     exception.
   */
  static <T> Result<T> wrap(Failure failure, ThrowingSupplier<T> supplier) {
    return wrap(e -> failure.with(e.getMessage()), supplier);
  }

  /**
   * Wrap call to function which may throw an exception.
   *
   * @param failure the supplier of failure used to represent the error which may happen during call
   * @param supplier the function to call.
   * @return success instance if call was successful and failure instance if function threw an
   *     exception.
   */
  static <T> Result<T> wrap(Supplier<Failure> failure, ThrowingSupplier<T> supplier) {
    return wrap(e -> failure.get().with(e.getMessage()), supplier);
  }

  /**
   * Wrap call to function which may throw an exception.
   *
   * @param errorMapper the mapper which translates the exception into failure
   * @param supplier the function to call.
   * @return success instance if call was successful and failure instance if function threw an
   *     exception.
   */
  static <T> Result<T> wrap(
      Function<Throwable, Failure> errorMapper, ThrowingSupplier<T> supplier) {
    try {
      return ok(supplier.get());
    } catch (Throwable e) {
      return errorMapper.apply(e).result();
    }
  }

  /**
   * Create an instance of successful operation result.
   *
   * @param value Operation result
   * @return created instance
   */
  static <R> Result<R> ok(R value) {
    return new ResultOk<>(value);
  }

  /**
   * Create an instance of failure operation result.
   *
   * @param value Operation error value
   * @return created instance
   */
  static <R> Result<R> fail(Failure value) {
    return new ResultFail<>(value);
  }

  final class ResultOk<R> implements Result<R> {
    private final R value;

    protected ResultOk(R value) {
      this.value = value;
    }

    @Override
    public <T> T fold(
        Function<? super Failure, ? extends T> leftMapper,
        Function<? super R, ? extends T> rightMapper) {
      return rightMapper.apply(value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      return (obj instanceof Result<?>)
          ? ((Result<?>) obj).fold($ -> false, val -> Objects.equals(val, value))
          : false;
    }

    @Override
    public String toString() {
      return new StringJoiner(", ", "Result-success(", ")").add(value.toString()).toString();
    }

    @Override
    public Result<R> onSuccess(Consumer<R> consumer) {
      consumer.accept(value);
      return this;
    }

    @Override
    public Result<R> onSuccessDo(Runnable action) {
      action.run();
      return this;
    }

    @Override
    public Result<R> onFailure(Consumer<? super Failure> consumer) {
      return this;
    }

    @Override
    public Result<R> onFailureDo(Runnable action) {
      return this;
    }
  }

  final class ResultFail<R> implements Result<R> {
    private final Failure value;

    protected ResultFail(Failure value) {
      this.value = value;
    }

    @Override
    public <T> T fold(
        Function<? super Failure, ? extends T> leftMapper,
        Function<? super R, ? extends T> rightMapper) {
      return leftMapper.apply(value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      return (obj instanceof Result<?>)
          ? ((Result<?>) obj).fold(val -> Objects.equals(val, value), $ -> false)
          : false;
    }

    @Override
    public String toString() {
      return new StringJoiner(", ", "Result-failure(", ")").add(value.toString()).toString();
    }

    @Override
    public Result<R> onSuccess(Consumer<R> consumer) {
      return this;
    }

    @Override
    public Result<R> onSuccessDo(Runnable action) {
      return this;
    }

    @Override
    public Result<R> onFailure(Consumer<? super Failure> consumer) {
      consumer.accept(value);
      return this;
    }

    @Override
    public Result<R> onFailureDo(Runnable action) {
      action.run();
      return this;
    }
  }

  @SuppressWarnings("unchecked")
  static <T> Result<List<T>> all(Iterable<Result<T>> results) {
    var list = new ArrayList<T>();

    for (var result : results) {
      if (!result.isSuccess()) {
        return (Result<List<T>>) result;
      }

      result.onSuccess(list::add);
    }

    return ok(list);
  }

  static <T1> Mapper1<T1> allOf(Result<T1> op1) {
    return () -> op1.flatMap(v1 -> ok(tuple(v1)));
  }

  static <T1, T2> Mapper2<T1, T2> allOf(Result<T1> op1, Result<T2> op2) {
    return () -> op1.flatMap(v1 -> op2.flatMap(v2 -> ok(tuple(v1, v2))));
  }

  static <T1, T2, T3> Mapper3<T1, T2, T3> allOf(Result<T1> op1, Result<T2> op2, Result<T3> op3) {
    return () -> op1.flatMap(v1 -> op2.flatMap(v2 -> op3.flatMap(v3 -> ok(tuple(v1, v2, v3)))));
  }

  static <T1, T2, T3, T4> Mapper4<T1, T2, T3, T4> allOf(
      Result<T1> op1, Result<T2> op2, Result<T3> op3, Result<T4> op4) {
    return () ->
        op1.flatMap(
            v1 ->
                op2.flatMap(v2 -> op3.flatMap(v3 -> op4.flatMap(v4 -> ok(tuple(v1, v2, v3, v4))))));
  }

  static <T1, T2, T3, T4, T5> Mapper5<T1, T2, T3, T4, T5> allOf(
      Result<T1> op1, Result<T2> op2, Result<T3> op3, Result<T4> op4, Result<T5> op5) {
    return () ->
        op1.flatMap(
            v1 ->
                op2.flatMap(
                    v2 ->
                        op3.flatMap(
                            v3 ->
                                op4.flatMap(
                                    v4 -> op5.flatMap(v5 -> ok(tuple(v1, v2, v3, v4, v5)))))));
  }

  static <T1, T2, T3, T4, T5, T6> Mapper6<T1, T2, T3, T4, T5, T6> allOf(
      Result<T1> op1,
      Result<T2> op2,
      Result<T3> op3,
      Result<T4> op4,
      Result<T5> op5,
      Result<T6> op6) {
    return () ->
        op1.flatMap(
            v1 ->
                op2.flatMap(
                    v2 ->
                        op3.flatMap(
                            v3 ->
                                op4.flatMap(
                                    v4 ->
                                        op5.flatMap(
                                            v5 ->
                                                op6.flatMap(
                                                    v6 -> ok(tuple(v1, v2, v3, v4, v5, v6))))))));
  }

  static <T1, T2, T3, T4, T5, T6, T7> Mapper7<T1, T2, T3, T4, T5, T6, T7> allOf(
      Result<T1> op1,
      Result<T2> op2,
      Result<T3> op3,
      Result<T4> op4,
      Result<T5> op5,
      Result<T6> op6,
      Result<T7> op7) {
    return () ->
        op1.flatMap(
            v1 ->
                op2.flatMap(
                    v2 ->
                        op3.flatMap(
                            v3 ->
                                op4.flatMap(
                                    v4 ->
                                        op5.flatMap(
                                            v5 ->
                                                op6.flatMap(
                                                    v6 ->
                                                        op7.flatMap(
                                                            v7 ->
                                                                ok(
                                                                    tuple(
                                                                        v1, v2, v3, v4, v5, v6,
                                                                        v7)))))))));
  }

  static <T1, T2, T3, T4, T5, T6, T7, T8> Mapper8<T1, T2, T3, T4, T5, T6, T7, T8> allOf(
      Result<T1> op1,
      Result<T2> op2,
      Result<T3> op3,
      Result<T4> op4,
      Result<T5> op5,
      Result<T6> op6,
      Result<T7> op7,
      Result<T8> op8) {
    return () ->
        op1.flatMap(
            v1 ->
                op2.flatMap(
                    v2 ->
                        op3.flatMap(
                            v3 ->
                                op4.flatMap(
                                    v4 ->
                                        op5.flatMap(
                                            v5 ->
                                                op6.flatMap(
                                                    v6 ->
                                                        op7.flatMap(
                                                            v7 ->
                                                                op8.flatMap(
                                                                    v8 ->
                                                                        ok(
                                                                            tuple(
                                                                                v1, v2, v3, v4, v5,
                                                                                v6, v7,
                                                                                v8))))))))));
  }

  static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Mapper9<T1, T2, T3, T4, T5, T6, T7, T8, T9> allOf(
      Result<T1> op1,
      Result<T2> op2,
      Result<T3> op3,
      Result<T4> op4,
      Result<T5> op5,
      Result<T6> op6,
      Result<T7> op7,
      Result<T8> op8,
      Result<T9> op9) {
    return () ->
        op1.flatMap(
            v1 ->
                op2.flatMap(
                    v2 ->
                        op3.flatMap(
                            v3 ->
                                op4.flatMap(
                                    v4 ->
                                        op5.flatMap(
                                            v5 ->
                                                op6.flatMap(
                                                    v6 ->
                                                        op7.flatMap(
                                                            v7 ->
                                                                op8.flatMap(
                                                                    v8 ->
                                                                        op9.flatMap(
                                                                            v9 ->
                                                                                ok(
                                                                                    tuple(
                                                                                        v1, v2, v3,
                                                                                        v4, v5, v6,
                                                                                        v7, v8,
                                                                                        v9)))))))))));
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
