package utils

class Generic {
    static String listToDelimitedString(List array, String delimiter = ",") {
        String str = ""
        array.collect {
            it ->
                if (it == array.last())
                    str += "${it}"
                else
                    str += "${it}${delimiter}"
        }
        return str
    }

    static String keyStorePath() {
        return "${System.getProperty('user.dir')}/system-tests/src/test/resources/keystore/test-key.json"
    }

    static String pathToCLIJar() {
        return "${System.getProperty('user.dir')}/system-tests/target/cli/radixdlt-cli-all.jar"
    }
}
