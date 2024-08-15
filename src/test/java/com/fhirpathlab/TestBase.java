package com.fhirpathlab;

import org.junit.jupiter.api.Assertions;
import org.springframework.util.Assert;

import java.io.File;
import com.google.common.io.Files;
import java.nio.charset.Charset;

class TestBase {
    protected static String ReadTestFile(String testName, String testSuffix, String format) {
        try {
            String workingDir = System.getProperty("user.dir");
            return Files.asCharSource(new File(
                    workingDir + "/src/test/data/" + testName + "." + testSuffix + "." + format),
                    Charset.defaultCharset()).read();
        } catch (Exception e) {
            Assert.isTrue(false, e.getMessage());
            return null;
        }
    }

    protected static void WriteTestFile(String testName, String testSuffix, String format, String content) {
        try {
            String workingDir = System.getProperty("user.dir");
            Files.asCharSink(new File(
                    workingDir + "/src/test/data/" + testName + "." + testSuffix + "." + format),
                    Charset.defaultCharset()).write(content);
        } catch (Exception e) {
            System.out.println(e);
            Assertions.fail(e.getMessage());
        }
    }
}
