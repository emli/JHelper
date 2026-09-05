package name.admitriev.jhelper.generation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Compiles and runs the program the run template generates, and checks the service messages it prints.
 *
 * The IDE builds its test tree from these messages, so a change to the template that breaks their shape
 * or escaping would silently turn the test tree back into plain console output.
 */
public class RunTemplateTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private static final String SOLVER =
			"#include <iostream>\n" +
			"class TestSolver { public: void solve(std::istream& in, std::ostream& out) {\n" +
			"  int a, b; in >> a >> b; out << a + b << \"\\n\"; } };\n";

	private String generate() throws Exception {
		try (InputStream stream = getClass().getResourceAsStream("/name/admitriev/jhelper/templates/run.template")) {
			String template = new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
			return template
					.replace("%TaskFile%", "TestSolver.h")
					.replace(
							"%Tests%",
							// one passing, one failing, one inactive
							"{\"2 3\\n\", \"5\\n\", true, true},"
							+ "{\"10 20\\n\", \"999\\n\", true, true},"
							+ "{\"1 1\\n\", \"\", false, false},"
					)
					.replace("%ClassName%", "TestSolver")
					.replace("%SolverCall%", "solver.solve(in, out);");
		}
	}

	private static boolean hasCompiler() {
		try {
			return new ProcessBuilder("g++", "--version").start().waitFor(30, TimeUnit.SECONDS);
		}
		catch (Exception e) {
			return false;
		}
	}

	@Test
	public void generatedProgramReportsEachSampleAsATest() throws Exception {
		assumeTrue("g++ is needed to build the generated program", hasCompiler());

		File dir = folder.getRoot();
		Files.writeString(new File(dir, "TestSolver.h").toPath(), SOLVER);
		Files.writeString(new File(dir, "main.cpp").toPath(), generate());

		Process compile = new ProcessBuilder("g++", "-std=c++17", "-o", "runner", "main.cpp")
				.directory(dir).redirectErrorStream(true).start();
		String compileOutput = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals("generated program should compile:\n" + compileOutput, 0, compile.waitFor());

		Process run = new ProcessBuilder(new File(dir, "runner").getAbsolutePath())
				.directory(dir).redirectErrorStream(true).start();
		String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int exitCode = run.waitFor();

		List<String> expected = List.of(
				"##teamcity[testingStarted]",
				"##teamcity[testSuiteStarted name='TestSolver']",
				"##teamcity[testStarted name='Test #1' captureStandardOutput='true']",
				"##teamcity[testStarted name='Test #2'",
				// a wrong answer has to be a comparison failure, or the IDE shows no diff
				"##teamcity[testFailed type='comparisonFailure' name='Test #2'",
				// newlines must be escaped as |n, not emitted raw, or the message is truncated
				"expected='999|n' actual='30|n'",
				"##teamcity[testIgnored name='Test #3'",
				"##teamcity[testSuiteFinished name='TestSolver']",
				"##teamcity[testingFinished]"
		);
		for (String fragment : expected) {
			assertTrue("missing from output: " + fragment + "\n\nactual output:\n" + output, output.contains(fragment));
		}

		assertTrue("the passing sample should not be reported as failed", !output.contains("name='Test #1' message='Wrong"));
		assertEquals("a failing sample must make the process exit non-zero", 1, exitCode);
	}
}
