package com.radixdlt.sanitytestsuite.model;


import javax.annotation.Nullable;
import java.util.List;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public final class SanityTestSuiteRoot {

	public static final class SanityTestSuite {

		public static final class SanityTestScenario {
			public static final class SanityTestScenarioDescription {
				public String implementationInfo;
				public String purpose;
				public String troubleshooting;
			}

			public static final class SanityTestScenarioTests {

				public static final class TestSource {

					public static final class ModifiedByTool {

						public static final class ToolInfo {
							public String name;
							public String link;
							public String version;
						}

						public String expression;
						public ToolInfo tool;
					}

					public @Nullable
					String link;
					public @Nullable String comment;
					public @Nullable String originalSourceLink;
					public @Nullable ModifiedByTool modifiedByTool;
				}

				public TestSource source;
				public List<UnknownTestVector> vectors;

			}

			public SanityTestScenarioDescription description;
			public String identifier;
			public String name;
			public SanityTestScenarioTests tests;

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

		public List<SanityTestScenario> scenarios;

	}

	public String hashOfSuite;
	public SanityTestSuite suite;
}
// CHECKSTYLE:ON checkstyle:VisibilityModifier