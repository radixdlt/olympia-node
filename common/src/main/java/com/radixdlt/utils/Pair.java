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

package com.radixdlt.utils;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An immutable pair of elements.
 *
 * <p>Note that in reality, as for all containers, instances of this class are only immutable if
 * their contained objects are also immutable.
 *
 * @param <F> Type of the first element
 * @param <S> Type of the second element
 */
public final class Pair<F, S> {
  private final F first;
  private final S second;

  /**
   * Create a pair from the specified arguments.
   *
   * @param first The first element of the pair.
   * @param second The second element of the pair.
   * @return A {@link Pair} containing {@code first} and {@code second}.
   */
  public static <A, B> Pair<A, B> of(final A first, final B second) {
    return new Pair<>(first, second);
  }

  /**
   * Constructor for a pair of items.
   *
   * <p>Please consider using the factory method {@link #of(Object, Object)} instead of this
   * constructor.
   *
   * @param first The first element of the pair.
   * @param second The second element of the pair.
   */
  public Pair(final F first, final S second) {
    this.first = first;
    this.second = second;
  }

  /**
   * Retrieve the first element from this pair.
   *
   * @return The first element.
   */
  public F getFirst() {
    return this.first;
  }

  /**
   * Retrieve the second element from this pair.
   *
   * @return The second element.
   */
  public S getSecond() {
    return this.second;
  }

  /**
   * Maps the first element, returning a new pair with the mapped first element, and the original
   * second element.
   *
   * @param mapper the mapper to apply to the first element
   * @return the new pair
   */
  public <R> Pair<R, S> mapFirst(Function<? super F, ? extends R> mapper) {
    return Pair.of(mapper.apply(this.first), this.second);
  }

  /**
   * Maps the second element, returning a new pair with the original first element, and the mapped
   * second element.
   *
   * @param mapper the mapper to apply to the second element
   * @return the new pair
   */
  public <R> Pair<F, R> mapSecond(Function<? super S, ? extends R> mapper) {
    return Pair.of(this.first, mapper.apply(this.second));
  }

  /**
   * Maps both elements into single result.
   *
   * @param mapper the mapper to apply to both elements
   * @return result of the mapping
   */
  public <R> R map(BiFunction<? super F, ? super S, ? extends R> mapper) {
    return mapper.apply(first, second);
  }

  /**
   * Returns {@code true} if the first element is non-null, {@code false} otherwise.
   *
   * @return {@code true} if the first element is non-null, else {@code false}
   */
  public boolean firstNonNull() {
    return this.first != null;
  }

  /**
   * Returns {@code true} if the first element is null, {@code false} otherwise.
   *
   * @return {@code true} if the first element is null, else {@code false}
   */
  public boolean firstIsNull() {
    return this.first == null;
  }

  /**
   * Returns {@code true} if the second element is non-null, {@code false} otherwise.
   *
   * @return {@code true} if the second element is non-null, else {@code false}
   */
  public boolean secondNonNull() {
    return this.second != null;
  }

  /**
   * Returns {@code true} if the second element is null, {@code false} otherwise.
   *
   * @return {@code true} if the second element is null, else {@code false}
   */
  public boolean secondIsNull() {
    return this.second == null;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.first, this.second);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Pair<?, ?>)) {
      return false;
    }

    var p = (Pair<?, ?>) obj;
    return Objects.equals(this.first, p.first) && Objects.equals(this.second, p.second);
  }

  @Override
  public String toString() {
    return String.format("%s[first=%s, second=%s]", getClass().getSimpleName(), first, second);
  }
}
