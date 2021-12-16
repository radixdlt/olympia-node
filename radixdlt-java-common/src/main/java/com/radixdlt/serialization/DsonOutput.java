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

package com.radixdlt.serialization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.EnumSet;

/**
 * Annotation used to indicate which fields are included in coded DSON for which output
 * requirements. As an example, the "serializer" field and any signature fields, are not included in
 * "HASH" output, as they are not included in any hash computation.
 *
 * <p>As an example, to include a field only in data persisted to disk, the following annotation
 * might be used:
 *
 * <pre>
 *         &#64;DsonOutput(Output.PERSIST)
 *         &#64;JsonProperty("diskTimestamp")
 *         private long diskTimestamp;
 * </pre>
 *
 * To exclude data from being included in a hash, the following annotation could be used:
 *
 * <pre>
 *         &#64;DsonOutput(value = Output.HASH, include = false)
 *         &#64;JsonProperty("signature")
 *         private byte[] signature;
 * </pre>
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DsonOutput {

  /**
   * The serialization output modes for which this field should be included or excluded, depending
   * on the value of {@link #include()}.
   *
   * @return The output modes for which this field should be included or excluded.
   * @see #include()
   */
  Output[] value();

  /**
   * {@code true} if {@link #value()} specified output modes where this field is to be included.
   * Otherwise {@link #value()} specifies modes where this field is to be excluded.
   *
   * @return {@code true} if field to be included for specified modes, {@code false} otherwise.
   */
  boolean include() default true;

  /**
   * Output modes for serialization.
   *
   * <p>There are four concrete output modes, {@link #HASH}, {@link #API}, {@link #WIRE} and {@link
   * #PERSIST}. Two additional modes are provided for ease of use {@link #ALL} and {@link #NONE},
   * representing the union of all the concrete modes, and the empty set respectively.
   *
   * <p>Note that the output mode {@link #NONE} is of limited use.
   */
  enum Output {
    /** An output mode that never results in output. Of limited use. */
    NONE,
    /** An output mode for calculating hashes. */
    HASH,
    /** An output mode for use with application interfaces. */
    API,
    /** An output mode for use when communicating to other nodes. */
    WIRE,
    /** An output mode for use when writing data to persistent storage. */
    PERSIST,
    /** An output mode that always results in output. */
    ALL;

    private static final EnumSet<Output> NONE_OF = EnumSet.noneOf(Output.class);
    private static final EnumSet<Output> ALL_OF = EnumSet.allOf(Output.class);

    /**
     * Convert enclosing annotation values to an {@link EnumSet}.
     *
     * @param value The values from the annotation
     * @param include The include flag from the annotation
     * @return An {@link EnumSet} identifying the {@link DsonOutput.Output} modes to output fields
     *     for.
     */
    public static EnumSet<Output> toEnumSet(Output[] value, boolean include) {
      EnumSet<Output> set = EnumSet.copyOf(Arrays.asList(value));
      if (set.contains(NONE)) {
        if (value.length == 1) {
          set = NONE_OF;
        } else {
          throw new IllegalArgumentException(
              "Can't include additional outputs with NONE: " + Arrays.toString(value));
        }
      }
      if (set.contains(ALL)) {
        if (value.length == 1) {
          set = ALL_OF;
        } else {
          throw new IllegalArgumentException(
              "Can't include additional outputs with ALL: " + Arrays.toString(value));
        }
      }
      if (!include) {
        set = EnumSet.complementOf(set);
      }
      set.remove(ALL);
      set.remove(NONE);
      return set;
    }
  }
}
