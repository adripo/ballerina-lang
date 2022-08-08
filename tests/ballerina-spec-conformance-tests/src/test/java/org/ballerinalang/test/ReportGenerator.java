/*
 * Copyright (c) 2021, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.ballerinalang.test.TestRunnerUtils.ABS_LINE_NUM;
import static org.ballerinalang.test.TestRunnerUtils.ACTUAL_LINE_NUM;
import static org.ballerinalang.test.TestRunnerUtils.ACTUAL_VALUE;
import static org.ballerinalang.test.TestRunnerUtils.BUILD_DIR;
import static org.ballerinalang.test.TestRunnerUtils.EXPECTED_LINE_NUM;
import static org.ballerinalang.test.TestRunnerUtils.EXPECTED_VALUE;
import static org.ballerinalang.test.TestRunnerUtils.FILENAME;
import static org.ballerinalang.test.TestRunnerUtils.KIND;
import static org.ballerinalang.test.TestRunnerUtils.TEST_DIR;

/**
 * Report generator for spec conformance tests.
 *
 * @since 2.0.0
 */
public class ReportGenerator {

    public static final Path REPORT_DIR = BUILD_DIR.resolve("reports");
    private static final String HTML_EXTENSION = ".html";
    public static final String ERROR_KIND_TESTS_REPORT = "error_kind_tests_report_template.html";
    public static final String SKIPPED_TESTS_REPORT = "skipped_tests_report_template.html";
    public static final String FAILED_TESTS_REPORT = "failed_tests_report_template.html";
    private static final String START_TABLE_ROW = "<tr class=\"active-row\">";
    private static final String END_TABLE_ROW  = "</tr>";
    private static final Pattern TABLE_DATA_PATTERN = Pattern.compile("<td></td>");
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("FileName");
    private static List<Map<String, String>> detailsOfFailedTests = new ArrayList<>();
    private static List<Map<String, String>> detailsOfSkippedTests = new ArrayList<>();
    private static Map<String, List<Map<String, String>>> detailsOfErrorKindTests = new LinkedHashMap<>();

    public static void addDetailsOfSkippedTests(Map<String, String> detailsOfTests) {
        detailsOfSkippedTests.add(detailsOfTests);
    }

    public static void addDetailsOfFailedTests(Map<String, String> detailsOfTests) {
        detailsOfFailedTests.add(detailsOfTests);
    }

    public static void addDetailsOfErrorKindTests(Map<String, String> detailsOfTest) {
        String fileName = detailsOfTest.get(FILENAME);
        List<Map<String, String>> details = detailsOfErrorKindTests.computeIfAbsent(fileName,
                k -> new ArrayList<Map<String, String>>());
        details.add(detailsOfTest);
    }

    private static String reportTemplate(String templateFileName) throws IOException {
        String pathOfTemplate = TEST_DIR + "/src/test/resources/report/" + templateFileName;
        Path templateFilePath = Path.of(pathOfTemplate);
        String fileContent = "";
        try {
            byte[] bytes = Files.readAllBytes(templateFilePath);
            fileContent = new String(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileContent;
    }

    public static void generateReport() throws IOException {
        generateFailedTestsReports();
        generateErrorVerificationTestReports();
        generateSkippedTestReports();
    }

    private static void generateFailedTestsReports() throws IOException {
        if (detailsOfFailedTests.isEmpty()) {
            return;
        }
        String template = reportTemplate(FAILED_TESTS_REPORT);
        StringBuilder detailsOfTests = new StringBuilder(50 * detailsOfFailedTests.size());
        for (Map<String, String> test : detailsOfFailedTests) {
            String diagnostics = test.get(TestRunnerUtils.FORMAT_ERRORS);
            if (diagnostics != null) {
                detailsOfTests.append(generateFailedTestsDetails(test.get(FILENAME), test.get(KIND),
                                      test.get(ABS_LINE_NUM), null, diagnostics, null));
            } else {
                detailsOfTests.append(generateFailedTestsDetails(test.get(FILENAME), test.get(KIND),
                                      test.get(ACTUAL_LINE_NUM), test.get(EXPECTED_LINE_NUM), test.get(ACTUAL_VALUE),
                                      test.get(EXPECTED_VALUE)));
            }
        }
        generateReport(template, "failed_tests_summary", detailsOfTests);
    }

    private static void generateErrorVerificationTestReports() throws IOException {
        if (detailsOfErrorKindTests.isEmpty()) {
            return;
        }
        String template = reportTemplate(ERROR_KIND_TESTS_REPORT);
        for (Map.Entry<String, List<Map<String, String>>> entry : detailsOfErrorKindTests.entrySet()) {
            String file = entry.getKey();
            StringBuilder detailsOfTests = new StringBuilder();
            List<Map<String, String>> detailsOfErrorKindTest = entry.getValue();
            for (Map<String, String> test : detailsOfErrorKindTest) {
                detailsOfTests.append(generateErrorDetails(test.get(ACTUAL_LINE_NUM), test.get(EXPECTED_LINE_NUM),
                                                           test.get(ACTUAL_VALUE), test.get(EXPECTED_VALUE)));
            }
            generateReport(template, file.substring(0, file.indexOf(".")), detailsOfTests);
        }
    }

    private static void generateSkippedTestReports() throws IOException {
        if (detailsOfSkippedTests.isEmpty()) {
            return;
        }
        String template = reportTemplate(SKIPPED_TESTS_REPORT);
        StringBuilder detailsOfTests = new StringBuilder();
        for (Map<String, String> test : detailsOfSkippedTests) {
            detailsOfTests.append(generateSkippedTestsDetails(test.get(FILENAME), test.get(KIND),
                                  test.get(ABS_LINE_NUM)));
        }
        generateReport(template, "skipped_tests_summary", detailsOfTests);
    }

    private static void generateReport(String template, String filename, StringBuilder results)
                                       throws IOException {
        File file = new File(REPORT_DIR + "/" + filename + HTML_EXTENSION);
        String newContent = FILE_NAME_PATTERN.matcher(TABLE_DATA_PATTERN.matcher(template).replaceAll(results.toString())).replaceAll(filename);
        FileWriter tempFileWriter = new FileWriter(file);
        tempFileWriter.write(newContent);
        tempFileWriter.close();
    }

    private static int tableRowStringSizeCalculation(String... strings) {
        int tableRowStringSize = START_TABLE_ROW.length() + END_TABLE_ROW.length();
        tableRowStringSize += strings.length * 9;
        for (String st : strings) {
            tableRowStringSize += st != null? st.length() : 0;
        }
        return tableRowStringSize;
    }

    private static String generateFailedTestsDetails(String fileName, String testKind, String actualLineNum,
                                                     String expectedLineNum, String actualOutput,
                                                     String expectedOutput) {
        StringBuilder tableRowSb = new StringBuilder(tableRowStringSizeCalculation(fileName, testKind, actualLineNum,
                expectedLineNum, actualOutput, expectedOutput));
        tableRowSb.append(START_TABLE_ROW);
        tableRowSb.append("<td>").append(fileName).append("</td>");
        tableRowSb.append("<td>").append(testKind).append("</td>");
        tableRowSb.append("<td>").append(expectedLineNum).append("</td>");
        tableRowSb.append("<td>").append(actualLineNum).append("</td>");
        tableRowSb.append("<td>").append(expectedOutput).append("</td>");
        tableRowSb.append("<td>").append(actualOutput).append("</td>");
        tableRowSb.append(END_TABLE_ROW);

        return tableRowSb.toString();
    }

    private static String generateErrorDetails(String actualLineNum, String expectedLineNum, String actualErrorMsg,
                                               String errorDesc) {
        StringBuilder tableRowSb = new StringBuilder(tableRowStringSizeCalculation(expectedLineNum, actualLineNum,
                actualErrorMsg, errorDesc));
        tableRowSb.append(START_TABLE_ROW);
        tableRowSb.append("<td>").append(expectedLineNum).append("</td>");
        tableRowSb.append("<td>").append(actualLineNum).append("</td>");
        tableRowSb.append("<td>").append(actualErrorMsg).append("</td>");
        tableRowSb.append("<td>").append(errorDesc).append("</td>");
        tableRowSb.append(END_TABLE_ROW);

        return tableRowSb.toString();
    }

    private static String generateSkippedTestsDetails(String fileName, String testKind, String lineNum) {

        StringBuilder tableRowSb = new StringBuilder(tableRowStringSizeCalculation(fileName, testKind,
                lineNum));
        tableRowSb.append(START_TABLE_ROW);
        tableRowSb.append("<td>").append(fileName).append("</td>");
        tableRowSb.append("<td>").append(testKind).append("</td>");
        tableRowSb.append("<td>").append(lineNum).append("</td>");
        tableRowSb.append(END_TABLE_ROW);

        return tableRowSb.toString();
    }
}
