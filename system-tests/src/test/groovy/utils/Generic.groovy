/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package utils

import com.radixdlt.test.LivenessCheck
import org.junit.Assert

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

    static String extractTestName(name) {
        def result = ("${name}" =~ /.*:(.*)]/).findAll()
        return result.size() > 0 ? (result[0][1]).replace(' ','') : name
    }

    static String [] getAWSCredentials(){
        String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY")
        String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID")

        return ["AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}" as String,
                "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}" as String
        ]

    }

    static String getDomainName(String url)  {
        URI uri = new URI(url);
        String domain = uri.getHost();
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    static void assertAssertionErrorIsLivenessError(AssertionError error) {
        Assert.assertNotNull("No error was thrown", error);
        Class classOfExceptionCause = error.getCause().getClass();
        Assert.assertEquals(String.format("Got %s instead of the expected LivenessError", classOfExceptionCause),
                classOfExceptionCause, LivenessCheck.LivenessError.class);
    }
}
