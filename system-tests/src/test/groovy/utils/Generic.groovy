package utils

class Generic {

    public static final String pathToKeyStoreFile = "src/test/resources/keystore/test-key.json"
    public static final String pathToCliJar = "target/cli/radixdlt-cli-all.jar"

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
        return System.getProperty('user.dir').contains("system-tests")
                ? "${System.getProperty('user.dir')}/" + pathToKeyStoreFile
                : "${System.getProperty('user.dir')}/system-tests/" + pathToKeyStoreFile
    }

    static String pathToCLIJar() {
        return System.getProperty('user.dir').contains("system-tests")
                ? "${System.getProperty('user.dir')}" + pathToCliJar
                : "${System.getProperty('user.dir')}/system-tests/" + pathToCliJar
    }
}
