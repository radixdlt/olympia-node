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

import com.radixdlt.utils.functional.Functions.FN0;
import com.radixdlt.utils.functional.Functions.FN1;
import com.radixdlt.utils.functional.Functions.FN2;
import com.radixdlt.utils.functional.Functions.FN3;
import com.radixdlt.utils.functional.Functions.FN4;
import com.radixdlt.utils.functional.Functions.FN5;
import com.radixdlt.utils.functional.Functions.FN6;
import com.radixdlt.utils.functional.Functions.FN7;
import com.radixdlt.utils.functional.Functions.FN8;
import com.radixdlt.utils.functional.Functions.FN9;
import java.util.Objects;

/** Tuples of various size. */
public interface Tuple<S extends Tuple<S>> {
  interface Tuple0 extends Tuple<Tuple0> {
    <T> T map(FN0<T> mapper);
  }

  interface Tuple1<T1> extends Tuple<Tuple1<T1>> {
    <T> T map(Functions.FN1<T, T1> mapper);
  }

  interface Tuple2<T1, T2> extends Tuple<Tuple2<T1, T2>> {
    <T> T map(FN2<T, T1, T2> mapper);

    default T1 first() {
      return map((first, __) -> first);
    }

    default T2 last() {
      return map((__, last) -> last);
    }
  }

  interface Tuple3<T1, T2, T3> extends Tuple<Tuple3<T1, T2, T3>> {
    <T> T map(FN3<T, T1, T2, T3> mapper);
  }

  interface Tuple4<T1, T2, T3, T4> extends Tuple<Tuple4<T1, T2, T3, T4>> {
    <T> T map(FN4<T, T1, T2, T3, T4> mapper);
  }

  interface Tuple5<T1, T2, T3, T4, T5> extends Tuple<Tuple5<T1, T2, T3, T4, T5>> {
    <T> T map(FN5<T, T1, T2, T3, T4, T5> mapper);
  }

  interface Tuple6<T1, T2, T3, T4, T5, T6> extends Tuple<Tuple6<T1, T2, T3, T4, T5, T6>> {
    <T> T map(FN6<T, T1, T2, T3, T4, T5, T6> mapper);
  }

  interface Tuple7<T1, T2, T3, T4, T5, T6, T7> extends Tuple<Tuple7<T1, T2, T3, T4, T5, T6, T7>> {
    <T> T map(FN7<T, T1, T2, T3, T4, T5, T6, T7> mapper);
  }

  interface Tuple8<T1, T2, T3, T4, T5, T6, T7, T8>
      extends Tuple<Tuple8<T1, T2, T3, T4, T5, T6, T7, T8>> {
    <T> T map(FN8<T, T1, T2, T3, T4, T5, T6, T7, T8> mapper);
  }

  interface Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9>
      extends Tuple<Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> {
    <T> T map(FN9<T, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper);
  }

  interface Unit extends Tuple0 {}

  Unit UNIT =
      new Unit() {
        @Override
        public <T> T map(final FN0<T> mapper) {
          return mapper.apply();
        }

        @Override
        public boolean equals(final Object obj) {
          return obj instanceof Tuple0;
        }

        @Override
        public int hashCode() {
          return super.hashCode();
        }

        @Override
        public String toString() {
          return "()";
        }
      };

  Result<Unit> UNIT_RESULT = Result.ok(UNIT);

  static Unit unit() {
    return UNIT;
  }

  static Result<Unit> unitResult() {
    return UNIT_RESULT;
  }

  static Tuple0 tuple() {
    return UNIT;
  }

  static <T1> Tuple1<T1> tuple(final T1 param1) {
    return new Tuple1<>() {
      @Override
      public <T> T map(final FN1<T, T1> mapper) {
        return mapper.apply(param1);
      }

      @Override
      public boolean equals(final Object obj) {
        if (this == obj) {
          return true;
        }

        return (obj instanceof Tuple1<?>)
            ? ((Tuple1<?>) obj).map(v1 -> Objects.equals(v1, param1))
            : false;
      }

      @Override
      public int hashCode() {
        return Objects.hash(param1);
      }

      @Override
      public String toString() {
        return "Tuple(" + param1.toString() + ")";
      }
    };
  }

  static <T1, T2> Tuple2<T1, T2> tuple(final T1 param1, final T2 param2) {
    return new Tuple2<>() {
      @Override
      public <T> T map(final FN2<T, T1, T2> mapper) {
        return mapper.apply(param1, param2);
      }

      @Override
      public boolean equals(final Object obj) {
        if (this == obj) {
          return true;
        }

        return (obj instanceof Tuple2<?, ?>)
            ? ((Tuple2<?, ?>) obj)
                .map((v1, v2) -> Objects.equals(v1, param1) && Objects.equals(v2, param2))
            : false;
      }

      @Override
      public int hashCode() {
        return Objects.hash(param1, param2);
      }

      @Override
      public String toString() {
        return "Tuple(" + param1.toString() + ", " + param2.toString() + ")";
      }
    };
  }

  static <T1, T2, T3> Tuple3<T1, T2, T3> tuple(final T1 param1, final T2 param2, final T3 param3) {
    return new Tuple3<>() {
      @Override
      public <T> T map(final FN3<T, T1, T2, T3> mapper) {
        return mapper.apply(param1, param2, param3);
      }

      @Override
      public boolean equals(final Object obj) {
        if (this == obj) {
          return true;
        }

        return (obj instanceof Tuple3<?, ?, ?>)
            ? ((Tuple3<?, ?, ?>) obj)
                .map(
                    (v1, v2, v3) ->
                        Objects.equals(v1, param1)
                            && Objects.equals(v2, param2)
                            && Objects.equals(v3, param3))
            : false;
      }

      @Override
      public int hashCode() {
        return Objects.hash(param1, param2, param3);
      }

      @Override
      public String toString() {
        return "Tuple("
            + param1.toString()
            + ","
            + param2.toString()
            + ","
            + param3.toString()
            + ")";
      }
    };
  }

  static <T1, T2, T3, T4> Tuple4<T1, T2, T3, T4> tuple(
      final T1 param1, final T2 param2, final T3 param3, final T4 param4) {
    return new Tuple4<>() {
      @Override
      public <T> T map(final FN4<T, T1, T2, T3, T4> mapper) {
        return mapper.apply(param1, param2, param3, param4);
      }

      @Override
      public boolean equals(final Object obj) {
        if (this == obj) {
          return true;
        }

        return (obj instanceof Tuple4<?, ?, ?, ?>)
            ? ((Tuple4<?, ?, ?, ?>) obj)
                .map(
                    (v1, v2, v3, v4) ->
                        Objects.equals(v1, param1)
                            && Objects.equals(v2, param2)
                            && Objects.equals(v3, param3)
                            && Objects.equals(v4, param4))
            : false;
      }

      @Override
      public int hashCode() {
        return Objects.hash(param1, param2, param3, param4);
      }

      @Override
      public String toString() {
        return "Tuple("
            + param1.toString()
            + ","
            + param2.toString()
            + ","
            + param3.toString()
            + ","
            + param4.toString()
            + ")";
      }
    };
  }

  static <T1, T2, T3, T4, T5> Tuple5<T1, T2, T3, T4, T5> tuple(
      final T1 param1, final T2 param2, final T3 param3, final T4 param4, final T5 param5) {
    return new Tuple5<>() {
      @Override
      public <T> T map(final FN5<T, T1, T2, T3, T4, T5> mapper) {
        return mapper.apply(param1, param2, param3, param4, param5);
      }

      @Override
      public boolean equals(final Object obj) {
        if (this == obj) {
          return true;
        }

        return (obj instanceof Tuple5<?, ?, ?, ?, ?>)
            ? ((Tuple5<?, ?, ?, ?, ?>) obj)
                .map(
                    (v1, v2, v3, v4, v5) ->
                        Objects.equals(v1, param1)
                            && Objects.equals(v2, param2)
                            && Objects.equals(v3, param3)
                            && Objects.equals(v4, param4)
                            && Objects.equals(v5, param5))
            : false;
      }

      @Override
      public int hashCode() {
        return Objects.hash(param1, param2, param3, param4, param5);
      }

      @Override
      public String toString() {
        return "Tuple("
            + param1.toString()
            + ","
            + param2.toString()
            + ","
            + param3.toString()
            + ","
            + param4.toString()
            + ","
            + param5.toString()
            + ")";
      }
    };
  }

  static <T1, T2, T3, T4, T5, T6> Tuple6<T1, T2, T3, T4, T5, T6> tuple(
      final T1 param1,
      final T2 param2,
      final T3 param3,
      final T4 param4,
      final T5 param5,
      final T6 param6) {
    return new Tuple6<>() {
      @Override
      public <T> T map(final FN6<T, T1, T2, T3, T4, T5, T6> mapper) {
        return mapper.apply(param1, param2, param3, param4, param5, param6);
      }

      @Override
      public boolean equals(final Object obj) {
        if (this == obj) {
          return true;
        }

        return (obj instanceof Tuple6<?, ?, ?, ?, ?, ?>)
            ? ((Tuple6<?, ?, ?, ?, ?, ?>) obj)
                .map(
                    (v1, v2, v3, v4, v5, v6) ->
                        Objects.equals(v1, param1)
                            && Objects.equals(v2, param2)
                            && Objects.equals(v3, param3)
                            && Objects.equals(v4, param4)
                            && Objects.equals(v5, param5)
                            && Objects.equals(v6, param6))
            : false;
      }

      @Override
      public int hashCode() {
        return Objects.hash(param1, param2, param3, param4, param5, param6);
      }

      @Override
      public String toString() {
        return "Tuple("
            + param1.toString()
            + ","
            + param2.toString()
            + ","
            + param3.toString()
            + ","
            + param4.toString()
            + ","
            + param5.toString()
            + ","
            + param6.toString()
            + ")";
      }
    };
  }

  static <T1, T2, T3, T4, T5, T6, T7> Tuple7<T1, T2, T3, T4, T5, T6, T7> tuple(
      final T1 param1,
      final T2 param2,
      final T3 param3,
      final T4 param4,
      final T5 param5,
      final T6 param6,
      final T7 param7) {
    return new Tuple7<>() {
      @Override
      public <T> T map(final FN7<T, T1, T2, T3, T4, T5, T6, T7> mapper) {
        return mapper.apply(param1, param2, param3, param4, param5, param6, param7);
      }

      @Override
      public boolean equals(final Object obj) {
        if (this == obj) {
          return true;
        }

        return (obj instanceof Tuple7<?, ?, ?, ?, ?, ?, ?>)
            ? ((Tuple7<?, ?, ?, ?, ?, ?, ?>) obj)
                .map(
                    (v1, v2, v3, v4, v5, v6, v7) ->
                        Objects.equals(v1, param1)
                            && Objects.equals(v2, param2)
                            && Objects.equals(v3, param3)
                            && Objects.equals(v4, param4)
                            && Objects.equals(v5, param5)
                            && Objects.equals(v6, param6)
                            && Objects.equals(v7, param7))
            : false;
      }

      @Override
      public int hashCode() {
        return Objects.hash(param1, param2, param3, param4, param5, param6, param7);
      }

      @Override
      public String toString() {
        return "Tuple("
            + param1.toString()
            + ","
            + param2.toString()
            + ","
            + param3.toString()
            + ","
            + param4.toString()
            + ","
            + param5.toString()
            + ","
            + param6.toString()
            + ","
            + param7.toString()
            + ")";
      }
    };
  }

  static <T1, T2, T3, T4, T5, T6, T7, T8> Tuple8<T1, T2, T3, T4, T5, T6, T7, T8> tuple(
      final T1 param1,
      final T2 param2,
      final T3 param3,
      final T4 param4,
      final T5 param5,
      final T6 param6,
      final T7 param7,
      final T8 param8) {
    return new Tuple8<>() {
      @Override
      public <T> T map(final FN8<T, T1, T2, T3, T4, T5, T6, T7, T8> mapper) {
        return mapper.apply(param1, param2, param3, param4, param5, param6, param7, param8);
      }

      @Override
      public boolean equals(final Object obj) {
        if (this == obj) {
          return true;
        }

        return (obj instanceof Tuple8<?, ?, ?, ?, ?, ?, ?, ?>)
            ? ((Tuple8<?, ?, ?, ?, ?, ?, ?, ?>) obj)
                .map(
                    (v1, v2, v3, v4, v5, v6, v7, v8) ->
                        Objects.equals(v1, param1)
                            && Objects.equals(v2, param2)
                            && Objects.equals(v3, param3)
                            && Objects.equals(v4, param4)
                            && Objects.equals(v5, param5)
                            && Objects.equals(v6, param6)
                            && Objects.equals(v7, param7)
                            && Objects.equals(v8, param8))
            : false;
      }

      @Override
      public int hashCode() {
        return Objects.hash(param1, param2, param3, param4, param5, param6, param7, param8);
      }

      @Override
      public String toString() {
        return "Tuple("
            + param1.toString()
            + ","
            + param2.toString()
            + ","
            + param3.toString()
            + ","
            + param4.toString()
            + ","
            + param5.toString()
            + ","
            + param6.toString()
            + ","
            + param7.toString()
            + ","
            + param8.toString()
            + ")";
      }
    };
  }

  static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9> tuple(
      final T1 param1,
      final T2 param2,
      final T3 param3,
      final T4 param4,
      final T5 param5,
      final T6 param6,
      final T7 param7,
      final T8 param8,
      final T9 param9) {
    return new Tuple9<>() {
      @Override
      public <T> T map(final FN9<T, T1, T2, T3, T4, T5, T6, T7, T8, T9> mapper) {
        return mapper.apply(param1, param2, param3, param4, param5, param6, param7, param8, param9);
      }

      @Override
      public boolean equals(final Object obj) {
        if (this == obj) {
          return true;
        }

        return (obj instanceof Tuple9<?, ?, ?, ?, ?, ?, ?, ?, ?>)
            ? ((Tuple9<?, ?, ?, ?, ?, ?, ?, ?, ?>) obj)
                .map(
                    (v1, v2, v3, v4, v5, v6, v7, v8, v9) ->
                        Objects.equals(v1, param1)
                            && Objects.equals(v2, param2)
                            && Objects.equals(v3, param3)
                            && Objects.equals(v4, param4)
                            && Objects.equals(v5, param5)
                            && Objects.equals(v6, param6)
                            && Objects.equals(v7, param7)
                            && Objects.equals(v8, param8)
                            && Objects.equals(v9, param9))
            : false;
      }

      @Override
      public int hashCode() {
        return Objects.hash(param1, param2, param3, param4, param5, param6, param7, param8, param9);
      }

      @Override
      public String toString() {
        return "Tuple("
            + param1.toString()
            + ","
            + param2.toString()
            + ","
            + param3.toString()
            + ","
            + param4.toString()
            + ","
            + param5.toString()
            + ","
            + param6.toString()
            + ","
            + param7.toString()
            + ","
            + param8.toString()
            + ","
            + param9.toString()
            + ")";
      }
    };
  }
}
