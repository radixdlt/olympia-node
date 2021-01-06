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