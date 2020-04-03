import io.cucumber.junit.CucumberOptions
import io.cucumber.junit.Cucumber
import org.junit.runner.RunWith

@RunWith(Cucumber.class)
@CucumberOptions(
        tags = "not @ignore",
        plugin = ["json:target/cucumber/cucumber.json","pretty","html:target/cucumber-reports"],
        features= "system-tests/src/test/resources/features")
class Runner {}

