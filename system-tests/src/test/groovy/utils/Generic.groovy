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
        return "${System.getProperty('user.dir')}/src/test/resources/keystore/test-key.json"
    }

    static String pathToCLIJar() {
        return "target/cli/radixdlt-cli-all.jar"
    }
}
