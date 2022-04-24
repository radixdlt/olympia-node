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

package com.radixdlt.sanitytestsuite.model;

import java.util.List;
import java.util.Map;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public final class SanityTestSuiteRoot {
  public final Integrity integrity;
  public final Suite suite;

  private SanityTestSuiteRoot() {
    /* Jackson will properly populate this fields during deserialize from JSON. */
    this.integrity = null;
    this.suite = null;
  }

  public static final class Suite {
    public final List<Suite.Scenario> scenarios;

    private Suite() {
      /* Jackson will properly populate this fields during deserialize from JSON. */
      this.scenarios = null;
    }

    public static final class Scenario {
      public final Description description;
      public final String identifier;
      public final String name;
      public final Tests tests;

      private Scenario() {
        /* Jackson will properly populate this fields during deserialize from JSON. */
        this.description = null;
        this.identifier = null;
        this.name = null;
        this.tests = null;
      }

      public static final class Description {
        public final String implementationInfo;
        public final String purpose;
        public final String troubleshooting;

        private Description() {
          /* Jackson will properly populate this fields during deserialize from JSON. */
          this.implementationInfo = null;
          this.purpose = null;
          this.troubleshooting = null;
        }
      }

      public static final class Tests {
        public final Source source;
        public final List<Map<String, Object>> vectors;

        private Tests() {
          /* Jackson will properly populate this fields during deserialize from JSON. */
          this.source = null;
          this.vectors = null;
        }

        public static final class Source {
          public final String link;
          public final String comment;
          public final String originalSourceLink;
          public final ModifiedByTool modifiedByTool;

          private Source() {
            /* Jackson will properly populate this fields during deserialize from JSON. */
            this.link = null;
            this.comment = null;
            this.originalSourceLink = null;
            this.modifiedByTool = null;
          }

          public static final class ModifiedByTool {
            public final String expression;
            public final ToolInfo tool;

            private ModifiedByTool() {
              /* Jackson will properly populate this fields during deserialize from JSON. */
              this.expression = null;
              this.tool = null;
            }

            public static final class ToolInfo {
              public String name;
              public String link;
              public String version;
            }
          }
        }
      }

      public String failDescriptionWithAssertionError(AssertionError testAssertionError) {
        return String.format(
            "\n⚠️⚠️⚠️\nFailed test scenario: '%s'\n"
                + "Identifier: '%s'\n"
                + "Purpose of scenario: '%s'\n"
                + "Troubleshooting: '%s'\n"
                + "Implementation info: '%s'\n"
                + "Test vectors found at: '%s'\n"
                + "Test vectors modified?: '%s'\n"
                + "Failure reason: '%s'\n⚠️⚠️⚠️\n",
            this.name,
            this.identifier,
            this.description.purpose,
            this.description.troubleshooting,
            this.description.implementationInfo,
            this.tests.source.link,
            this.tests.source.modifiedByTool == null
                ? "NO"
                : "YES, modified with tool (see 'expression' for how): "
                    + this.tests.source.modifiedByTool.tool.link,
            testAssertionError.getLocalizedMessage());
      }
    }
  }

  public static final class Integrity {
    public final String hashOfSuite;
    public final String implementationInfo;

    private Integrity() {
      /* Jackson will properly populate this fields during deserialize from JSON. */
      this.hashOfSuite = null;
      this.implementationInfo = null;
    }
  }
}
// CHECKSTYLE:ON checkstyle:VisibilityModifier
