package com.radixdlt.sanitytestsuite;


import javax.annotation.Nullable;
import java.util.List;

public final class SanityTestSuiteRoot {

	static final class SanityTestSuite {

		static final class SanityTestScenario {
			static final class SanityTestScenarioDescription {
				public String implementationInfo;
				public String purpose;
				public String troubleshooting;
			}

			static final class SanityTestScenarioTests {

				static final class TestSource {

					static final class ModifiedByTool {

						static final class ToolInfo {
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

		}

		public List<SanityTestScenario> scenarios;

	}

	public String hashOfSuite;
	public SanityTestSuite suite;

}