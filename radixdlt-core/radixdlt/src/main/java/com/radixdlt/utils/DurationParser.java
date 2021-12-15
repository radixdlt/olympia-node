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

import static java.util.Optional.ofNullable;

import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility method for parsing strings containing description of duration.
 *
 * <p>Recognized string format:
 *
 * <pre>
 *     [0-9]+(s|m|h)?
 * </pre>
 *
 * Leading number represents value duration and must be greater than zero. Fractional values are not
 * supported. Optional trailing character defines units of duration value:
 *
 * <ul>
 *   <li><b>s</b> - second(s)
 *   <li><b>m</b> - minute(s)
 *   <li><b>h</b> - hour(s)
 * </ul>
 *
 * If units are omitted or not recognized then <b>seconds</b> are assumed by default.
 */
public final class DurationParser {
  private DurationParser() {}

  private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)(\\w)?");
  private static final String DEFAULT_UNITS = "s";
  private static final Map<String, TemporalUnit> UNIT_MAP =
      ImmutableMap.of(
          "s", ChronoUnit.SECONDS,
          "m", ChronoUnit.MINUTES,
          "h", ChronoUnit.HOURS);

  /**
   * Parse input string into duration value.
   *
   * @param input Input string
   * @return {@link Optional} with parsed value. If input is empty or value is less or equal to 0
   *     then empty instance is returned.
   */
  public static Optional<Duration> parse(String input) {
    Objects.requireNonNull(input);

    final Matcher matcher = DURATION_PATTERN.matcher(input.toLowerCase());

    if (!matcher.find()) {
      return Optional.empty();
    }

    long value = Long.parseLong(matcher.group(1));

    if (value <= 0) {
      return Optional.empty();
    }

    final String units = ofNullable(matcher.group(2)).orElse(DEFAULT_UNITS);

    return ofNullable(UNIT_MAP.get(units)).map(unit -> Duration.of(value, unit));
  }
}
