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
import java.util.Optional;

/** Group methods for {@link Optional}. */
public interface Optionals {
  static <T1> Mapper1<T1> allOf(Optional<T1> op1) {
    return () -> op1.flatMap(v1 -> Optional.of(tuple(v1)));
  }

  static <T1, T2> Mapper2<T1, T2> allOf(Optional<T1> op1, Optional<T2> op2) {
    return () -> op1.flatMap(v1 -> op2.flatMap(v2 -> Optional.of(tuple(v1, v2))));
  }

  static <T1, T2, T3> Mapper3<T1, T2, T3> allOf(
      Optional<T1> op1, Optional<T2> op2, Optional<T3> op3) {
    return () ->
        op1.flatMap(v1 -> op2.flatMap(v2 -> op3.flatMap(v3 -> Optional.of(tuple(v1, v2, v3)))));
  }

  static <T1, T2, T3, T4> Mapper4<T1, T2, T3, T4> allOf(
      Optional<T1> op1, Optional<T2> op2, Optional<T3> op3, Optional<T4> op4) {
    return () ->
        op1.flatMap(
            v1 ->
                op2.flatMap(
                    v2 ->
                        op3.flatMap(v3 -> op4.flatMap(v4 -> Optional.of(tuple(v1, v2, v3, v4))))));
  }

  static <T1, T2, T3, T4, T5> Mapper5<T1, T2, T3, T4, T5> allOf(
      Optional<T1> op1, Optional<T2> op2, Optional<T3> op3, Optional<T4> op4, Optional<T5> op5) {
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
                                            v5 -> Optional.of(tuple(v1, v2, v3, v4, v5)))))));
  }

  static <T1, T2, T3, T4, T5, T6> Mapper6<T1, T2, T3, T4, T5, T6> allOf(
      Optional<T1> op1,
      Optional<T2> op2,
      Optional<T3> op3,
      Optional<T4> op4,
      Optional<T5> op5,
      Optional<T6> op6) {
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
                                                        Optional.of(
                                                            tuple(v1, v2, v3, v4, v5, v6))))))));
  }

  static <T1, T2, T3, T4, T5, T6, T7> Mapper7<T1, T2, T3, T4, T5, T6, T7> allOf(
      Optional<T1> op1,
      Optional<T2> op2,
      Optional<T3> op3,
      Optional<T4> op4,
      Optional<T5> op5,
      Optional<T6> op6,
      Optional<T7> op7) {
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
                                                                Optional.of(
                                                                    tuple(
                                                                        v1, v2, v3, v4, v5, v6,
                                                                        v7)))))))));
  }

  static <T1, T2, T3, T4, T5, T6, T7, T8> Mapper8<T1, T2, T3, T4, T5, T6, T7, T8> allOf(
      Optional<T1> op1,
      Optional<T2> op2,
      Optional<T3> op3,
      Optional<T4> op4,
      Optional<T5> op5,
      Optional<T6> op6,
      Optional<T7> op7,
      Optional<T8> op8) {
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
                                                                        Optional.of(
                                                                            tuple(
                                                                                v1, v2, v3, v4, v5,
                                                                                v6, v7,
                                                                                v8))))))))));
  }

  static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Mapper9<T1, T2, T3, T4, T5, T6, T7, T8, T9> allOf(
      Optional<T1> op1,
      Optional<T2> op2,
      Optional<T3> op3,
      Optional<T4> op4,
      Optional<T5> op5,
      Optional<T6> op6,
      Optional<T7> op7,
      Optional<T8> op8,
      Optional<T9> op9) {
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
                                                                                Optional.of(
                                                                                    tuple(
                                                                                        v1, v2, v3,
                                                                                        v4, v5, v6,
                                                                                        v7, v8,
                                                                                        v9)))))))))));
  }

  interface Mapper1<T1> {
    Optional<Tuple1<T1>> id();

    default <R> Optional<R> map(FN1<R, T1> mapper) {
      return id().map(tuple -> tuple.map(mapper));
    }

    default <R> Optional<R> flatMap(FN1<Optional<R>, T1> mapper) {
      return id().flatMap(tuple -> tuple.map(mapper));
    }
  }

  interface Mapper2<T1, T2> {
    Optional<Tuple2<T1, T2>> id();

    default <R> Optional<R> map(FN2<R, T1, T2> mapper) {
      return id().map(tuple -> tuple.map(mapper));
    }

    default <R> Optional<R> flatMap(FN2<Optional<R>, T1, T2> mapper) {
      return id().flatMap(tuple -> tuple.map(mapper));
    }
  }

  interface Mapper3<T1, T2, T3> {
    Optional<Tuple3<T1, T2, T3>> id();

    default <R> Optional<R> map(FN3<R, T1, T2, T3> mapper) {
      return id().map(tuple -> tuple.map(mapper));
    }

    default <R> Optional<R> flatMap(FN3<Optional<R>, T1, T2, T3> mapper) {
      return id().flatMap(tuple -> tuple.map(mapper));
    }
  }

  interface Mapper4<T1, T2, T3, T4> {
    Optional<Tuple4<T1, T2, T3, T4>> id();

    default <R> Optional<R> map(FN4<R, T1, T2, T3, T4> mapper) {
      return id().map(tuple -> tuple.map(mapper));
    }

    default <R> Optional<R> flatMap(FN4<Optional<R>, T1, T2, T3, T4> mapper) {
      return id().flatMap(tuple -> tuple.map(mapper));
    }
  }

  interface Mapper5<T1, T2, T3, T4, T5> {
    Optional<Tuple5<T1, T2, T3, T4, T5>> id();

    default <R> Optional<R> map(FN5<R, T1, T2, T3, T4, T5> mapper) {
      return id().map(tuple -> tuple.map(mapper));
    }

    default <R> Optional<R> flatMap(FN5<Optional<R>, T1, T2, T3, T4, T5> mapper) {
      return id().flatMap(tuple -> tuple.map(mapper));
    }
  }

  interface Mapper6<T1, T2, T3, T4, T5, T6> {
    Optional<Tuple6<T1, T2, T3, T4, T5, T6>> id();

    default <R> Optional<R> map(FN6<R, T1, T2, T3, T4, T5, T6> mapper) {
      return id().map(tuple -> tuple.map(mapper));
    }

    default <R> Optional<R> flatMap(FN6<Optional<R>, T1, T2, T3, T4, T5, T6> mapper) {
      return id().flatMap(tuple -> tuple.map(mapper));
    }
  }

  interface Mapper7<T1, T2, T3, T4, T5, T6, T7> {
    Optional<Tuple7<T1, T2, T3, T4, T5, T6, T7>> id();

    default <R> Optional<R> map(FN7<R, T1, T2, T3, T4, T5, T6, T7> mapper) {
      return id().map(tuple -> tuple.map(mapper));
    }

    default <R> Optional<R> flatMap(FN7<Optional<R>, T1, T2, T3, T4, T5, T6, T7> mapper) {
      return id().flatMap(tuple -> tuple.map(mapper));
    }
  }

  interface Mapper8<T1, T2, T3, T4, T5, T6, T7, T8> {
    Optional<Tuple8<T1, T2, T3, T4, T5, T6, T7, T8>> id();

    default <R> Optional<R> map(FN8<R, T1, T2, T3, T4, T5, T6, T7, T8> mapper) {
      return id().map(tuple -> tuple.map(mapper));
    }

    default <R> Optional<R> flatMap(FN8<Optional<R>, T1, T2, T3, T4, T5, T6, T7, T8> mapper) {
      return id().flatMap(tuple -> tuple.map(mapper));
    }
  }

  interface Mapper9<T1, T2, T3, T4, T5, T6, T7, T8, T9> {
    Optional<Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> id();

    default <R> Optional<R> map(FN9<R, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper) {
      return id().map(tuple -> tuple.map(mapper));
    }

    default <R> Optional<R> flatMap(FN9<Optional<R>, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper) {
      return id().flatMap(tuple -> tuple.map(mapper));
    }
  }
}
