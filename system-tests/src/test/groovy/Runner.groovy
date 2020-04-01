import io.cucumber.junit.CucumberOptions
import io.cucumber.junit.Cucumber
import org.junit.runner.RunWith

@RunWith(Cucumber.class)
@CucumberOptions(
        plugin = ["json:target/cucumber/cucumber.json","pretty","html:target/cucumber-reports"],
        features= "src/test/resources/features/")
class Runner {}

