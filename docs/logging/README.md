### Logging Configuration

#### Preface
The purpose of this document is to cover some most frequent use cases. There is no intent to provide complete guide on
logging configuration as it is already covered by [Log4j2 Configuration Document](https://logging.apache.org/log4j/2.x/manual/configuration.html).

#### Default Configuration
Default logging configuration for the Radix Node has been prepared for simple use cases such as development, 
local or small scale deployments. Such configurations usually have logs stored in text log files and analyzed 
manually with tools like `grep` or `awk`.
#### Changing Default Logging Level
Default configuration uses `INFO` logging level, which is moderately verbose. This logging level can be changed without
updating configuration files by setting environment variable `RADIXDLT_LOG_LEVEL` to necessary logging level.    

#### Logging with Dedicated Logging Infrastructure
Complex deployments as well as deployments into cloud-based infrastructure usually rely on centralized 
log collection and analysis tools like Splunk, AWS Centralized Logging or Loggly.
For such use cases it's usually more convenient to have other format for logs than plain text. Most popular approach is
to use JSON as the logging format. To enable JSON logging format it is necessary to provide logging configuration file.

##### Logging Configuration File Location amd Name 
The logging configuration file named `log4j2.properties` is looked up using _classpath_. Launch scripts provided with 
Radix Node binaries include content of `RADIXDLT_HOME` environment variable into application classpath. 
So, in order to change default logging configuration it is necessary to:
- Set environment `RADIXDLT_HOME` variable to desired value.
- Put `log4j2.properties` file into directory specified in the `RADIXDLT_HOME` environment variable. 

The `log4j2.properties` file present in `radixdlt-core` project, directory `radixdlt/src/main/resources` can be used
as an initial point for custom logging configuration.    

##### Enabling JSON logging

The default `log4j2.properties` file already contains necessary pieces to enable JSON logging, but they are commented 
out by default. So, in order to enable JSON logging it is necessary to uncomment them and instead comment out regular 
logging. Resulting `log4j2.properties` will produce two sets of log files, one in regular text format and one in JSON 
format. Usually this is redundant and undesirable. So, beside uncommenting JSON logging in configuration file it is 
also necessary to comment out or remove regular logging. Resulting configuration file may look like one provided below:

```
# Default logging setup for ledger

rootLogger.level = ${env:RADIXDLT_LOG_LEVEL:-info}
rootLogger.type = asyncRoot
rootLogger.includeLocation = true
rootLogger.appenderRef.stdout.ref = STDOUT
rootLogger.appenderRef.json.ref = JSON

appender.console.type = Console
appender.console.name = STDOUT
appender.console.layout.type = PatternLayout
appender.console.layout.pattern = # %highlight{%d{ISO8601} [%p/%c{1}/%t] (%F:%L) - %m}{OFF=blink bright red, FATAL=blink bright red, ERROR=bright red, WARN=bright yellow, INFO=bright white, DEBUG=dim cyan, TRACE=dim white}%n

appender.json.type = RollingRandomAccessFile
appender.json.name = JSON
appender.json.fileName = logs/radixdlt-core.json
appender.json.filePattern = logs/radixdlt-core-%d{yyy-MM-dd}.json
appender.json.layout.type = JsonLayout
appender.json.layout.compact = true
appender.json.layout.eventEol = true
appender.json.layout.objectMessageAsJsonObject = true
appender.json.policies.type = Policies
appender.json.policies.time.type = TimeBasedTriggeringPolicy
appender.json.policies.time.interval = 1
appender.json.policies.time.modulate = true
appender.json.strategy.type = DefaultRolloverStrategy
# Two weeks = 14 days of log rollover
appender.json.strategy.max = ${env:RADIXDLT_LOG_RETENTION_IN_DAYS:-14}
``` 
#### Customizing Logged Information 
Sometimes it is necessary to increase or reduce the amount of information logged. For example, for issue troubleshooting
it might be useful to log more details for part of the application. Or it might be useful to suppress logging
of some irrelevant details.
##### Changing Logging Details
In order to configure logging for specific part of the Radix Node it is necessary to define a `logger` in the 
configuration file. Usually logger configuration looks like this:

```
...
loggers=my-logger
logger.my-logger.name=<package name>
logger.my-logger.level=<logging level>
logger.my-logger.additivity=false
...

```
The first line contains names of the custom loggers. It should be present only once and contain the comma separated 
list of all custom loggers. 

Second line contains name of Java package which in turn identifies either an external library or some subsystem 
inside Radix Node. See list of packages and how they are related to the Radix Node functionality below.

Third line defines logging level.

Fourth line disables the [additivity](https://logging.apache.org/log4j/2.x/manual/configuration.html#Additivity) and 
prevents logging same information twice.

##### Package Names and Functionality

|  Package | Functionality |
| -------- | ------------- |
|com.radixdlt.consensus| BFT consensus|
|com.radixdlt.sync.epochs| Epoch management and processing|
|com.radixdlt.ledger| Ledger management|
|com.radixdlt.mempool| Memory pool management|
|com.radixdlt.network| Network communication|
|com.radixdlt.store| Persistence layer|

##### Logging Details Configuration Example
The example shown below suppresses unnecessary messages generated by Netty if default logging level is 
`debug` or `trace`:
```
loggers=netty-platformdependent0-nodebug
logger.netty-platformdependent0-nodebug.name=io.netty.util.internal.PlatformDependent0
logger.netty-platformdependent0-nodebug.level = info
logger.netty-platformdependent0-nodebug.additivity = false
```
