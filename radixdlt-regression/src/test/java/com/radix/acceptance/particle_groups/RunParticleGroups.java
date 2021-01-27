package com.radix.acceptance.particle_groups;

import io.cucumber.junit.CucumberOptions;
import io.cucumber.junit.CucumberOptions.SnippetType;
import io.cucumber.junit.Cucumber;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(snippets = SnippetType.UNDERSCORE, monochrome = true, plugin = { "pretty" })
public class RunParticleGroups {
	// Stub for running cucumber tests
}
