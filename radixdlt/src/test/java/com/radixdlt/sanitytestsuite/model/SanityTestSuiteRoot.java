/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.sanitytestsuite.model;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public final class SanityTestSuiteRoot {
	public SanityTestIntegrity integrity;
	public Suite suite;

	public static final class Suite {
		public List<Suite.Scenario> scenarios;

		public static final class Scenario {
			public Description description;
			public String identifier;
			public String name;
			public Tests tests;

			public static final class Description {
				public String implementationInfo;
				public String purpose;
				public String troubleshooting;
			}

			public static final class Tests {
				public Source source;
				public List<Map<String, Object>> vectors;

				public static final class Source {
					@Nullable
					public String link;
					@Nullable
					public String comment;
					@Nullable
					public String originalSourceLink;
					@Nullable
					public ModifiedByTool modifiedByTool;

					public static final class ModifiedByTool {
						public static final class ToolInfo {
							public String name;
							public String link;
							public String version;
						}

						public String expression;
						public ToolInfo tool;
					}
				}
			}

			public String failDescriptionWithAssertionError(AssertionError testAssertionError) {
				return String.format(
						"\n⚠️⚠️⚠️\nFailed test scenario: '%s'\n" +
								"Identifier: '%s'\n" +
								"Purpose of scenario: '%s'\n" +
								"Troubleshooting: '%s'\n" +
								"Implementation info: '%s'\n" +
								"Test vectors found at: '%s'\n" +
								"Test vectors modified?: '%s'\n" +
								"Failure reason: '%s'\n⚠️⚠️⚠️\n",
						this.name,
						this.identifier,
						this.description.purpose,
						this.description.troubleshooting,
						this.description.implementationInfo,
						this.tests.source.link,
						this.tests.source.modifiedByTool == null ? "NO" : "YES, modified with tool (see 'expression' for how): " + this.tests.source.modifiedByTool.tool.link,
						testAssertionError.getLocalizedMessage()
				);
			}
		}
	}

	public static final class SanityTestIntegrity {
		public String hashOfSuite;
		public String implementationInfo;
	}
}
// CHECKSTYLE:ON checkstyle:VisibilityModifier