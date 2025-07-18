/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.end2end;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.ClassFinder;
import org.apache.hadoop.hbase.ClassFinder.FileNameFilter;
import org.apache.hadoop.hbase.ClassTestFinder;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.IntegrationTestingUtility;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.junit.internal.TextListener;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.phoenix.thirdparty.com.google.common.annotations.VisibleForTesting;
import org.apache.phoenix.thirdparty.org.apache.commons.cli.CommandLine;
import org.apache.phoenix.thirdparty.org.apache.commons.cli.CommandLineParser;
import org.apache.phoenix.thirdparty.org.apache.commons.cli.DefaultParser;
import org.apache.phoenix.thirdparty.org.apache.commons.cli.HelpFormatter;
import org.apache.phoenix.thirdparty.org.apache.commons.cli.Option;
import org.apache.phoenix.thirdparty.org.apache.commons.cli.Options;
import org.apache.phoenix.thirdparty.org.apache.commons.cli.ParseException;

/**
 * This class drives the End2End tests suite execution against an already deployed distributed
 * cluster.
 */
public class End2EndTestDriver extends Configured implements Tool {

  private static final Logger LOGGER = LoggerFactory.getLogger(End2EndTestDriver.class);

  private static Option SHORT_REGEX_OPTION =
    new Option("r", true, "Java regex to use selecting tests to run: e.g. .*TestBig.*"
      + " will select all tests that include TestBig in their name.  Default: " + ".*end2end.*");
  private static Option SKIP_TESTS_OPTION =
    new Option("n", false, "Print list of End2End test suits without running them.");

  private End2EndTestFilter end2endTestFilter = new End2EndTestFilter();
  private boolean skipTests = false;

  public static void main(String[] args) throws Exception {
    int ret = ToolRunner.run(new End2EndTestDriver(), args);
    System.exit(ret);
  }

  @Override
  public int run(String[] args) throws Exception {
    try {
      parseOptions(args);
    } catch (IllegalStateException e) {
      printHelpAndExit(e.getMessage(), getOptions());
      return -1;
    }
    setConf(HBaseConfiguration.addHbaseResources(getConf()));
    return doWork();
  }

  /**
   * Parses the commandline arguments, throws IllegalStateException if mandatory arguments are
   * missing.
   * @param args supplied command line arguments
   * @return the parsed command line
   */
  @VisibleForTesting
  public CommandLine parseOptions(String[] args) {

    final Options options = getOptions();

    CommandLineParser parser = DefaultParser.builder().setAllowPartialMatching(false)
      .setStripLeadingAndTrailingQuotes(false).build();
    CommandLine cmdLine = null;
    try {
      cmdLine = parser.parse(options, args);
    } catch (ParseException e) {
      printHelpAndExit("Error parsing command line options: " + e.getMessage(), options);
    }

    String testFilterString = cmdLine.getOptionValue(SHORT_REGEX_OPTION);
    if (testFilterString != null) {
      end2endTestFilter.setPattern(testFilterString);
    }
    skipTests = cmdLine.hasOption(SKIP_TESTS_OPTION);

    return cmdLine;
  }

  public static class End2EndFileNameFilter implements FileNameFilter {

    @Override
    public boolean isCandidateFile(String fileName, String absFilePath) {
      return fileName.contains("IT");
    }
  };

  public class End2EndTestFilter extends ClassTestFinder.TestClassFilter {
    private Pattern testFilterRe = Pattern.compile(".*end2end.*");

    public End2EndTestFilter() {
      super();
    }

    public void setPattern(String pattern) {
      try {
        testFilterRe = Pattern.compile(pattern);
      } catch (PatternSyntaxException e) {
        LOGGER.error("Failed to find tests using pattern '" + pattern
          + "'. Is it a valid Java regular expression?", e);
        throw e;
      }
    }

    @Override
    public boolean isCandidateClass(Class<?> c) {
      Annotation[] annotations = c.getAnnotations();
      for (Annotation curAnnotation : annotations) {
        if (curAnnotation.toString().contains("NeedsOwnMiniClusterTest")) {
          /*
           * Skip tests that aren't designed to run against a live cluster. For a live cluster, we
           * cannot bring it up and down as required for these tests to run.
           */
          return false;
        }
      }
      return testFilterRe.matcher(c.getName()).find() &&
      // Our pattern will match the below NON-IntegrationTest. Rather than
      // do exotic regex, just filter it out here
        super.isCandidateClass(c);
    }
  }

  private Options getOptions() {
    final Options options = new Options();
    options.addOption(SHORT_REGEX_OPTION);
    options.addOption(SKIP_TESTS_OPTION);
    return options;
  }

  private void printHelpAndExit(String errorMessage, Options options) {
    System.err.println(errorMessage);
    printHelpAndExit(options, 1);
  }

  private void printHelpAndExit(Options options, int exitCode) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("help", options);
    System.exit(exitCode);
  }

  /**
   * Returns test classes annotated with @Category(IntegrationTests.class), according to the filter
   * specific on the command line (if any).
   */
  private Class<?>[] findEnd2EndTestClasses()
    throws ClassNotFoundException, LinkageError, IOException {
    End2EndFileNameFilter nameFilter = new End2EndFileNameFilter();
    ClassFinder classFinder = new ClassFinder(null, nameFilter, end2endTestFilter);
    Set<Class<?>> classes = classFinder.findClasses("org.apache.phoenix.end2end", true);
    return classes.toArray(new Class<?>[classes.size()]);
  }

  public static class End2EndTestListenter extends TextListener {
    private final PrintStream fWriter;
    List<String> completes;

    public End2EndTestListenter(PrintStream writer) {
      super(writer);
      completes = new ArrayList<String>();
      fWriter = writer;
    }

    @Override
    protected void printHeader(long runTime) {
      fWriter.println();
      fWriter.println("=========== Test Result ===========");
      fWriter.println("Time: " + elapsedTimeAsString(runTime));
    }

    @Override
    public void testStarted(Description description) {
      fWriter.println();
      fWriter.println("===> " + description.getDisplayName() + " starts");
    }

    @Override
    public void testFinished(Description description) throws Exception {
      super.testFinished(description);
      completes.add(description.getDisplayName());
    }

    void printSummary(Result result) {
      Set<String> failures = new HashSet<String>();
      for (Failure f : result.getFailures()) {
        failures.add(f.getTestHeader());
      }
      fWriter.println();
      fWriter.println("==== Test Summary ====");
      String status;
      for (String curTest : completes) {
        status = "passed";
        if (failures.contains(curTest)) {
          status = "failed";
        }
        fWriter.println(curTest + "   " + status + "!");
      }
    }

    @Override
    public void testRunFinished(Result result) {
      printHeader(result.getRunTime());
      printFailures(result);
      printSummary(result);
      fWriter.println();
      printFooter(result);
    }
  };

  protected int doWork() throws Exception {
    // this is called from the command line, so we should set to use the distributed cluster
    IntegrationTestingUtility.setUseDistributedCluster(getConf());
    Class<?>[] classes = findEnd2EndTestClasses();
    System.out.println("Found " + classes.length + " end2end tests to run:");
    for (Class<?> aClass : classes) {
      System.out.println("  " + aClass);
    }
    if (skipTests) return 0;

    JUnitCore junit = new JUnitCore();
    junit.addListener(new End2EndTestListenter(System.out));
    Result result = junit.run(classes);

    return result.wasSuccessful() ? 0 : 1;
  }

}
