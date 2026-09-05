package name.admitriev.jhelper.configuration;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.ui.ConsoleView;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the console that turns the generated test runner's output into the IDE's test tree.
 *
 * The generated program prints service messages as it runs each sample. This console parses them, so
 * results appear as real test nodes with pass/fail state, per-test timing, captured input and output,
 * and a diff between expected and actual for a wrong answer — rather than as plain console text.
 */
public class TestConsole {
	public static final String FRAMEWORK_NAME = "JHelper";

	private TestConsole() {
	}

	/**
	 * A console builder the run configuration's state can be pointed at.
	 */
	public static @NotNull TextConsoleBuilder builderFor(
			@NotNull RunConfiguration configuration,
			@NotNull Executor executor
	) {
		return new TextConsoleBuilder() {
			@Override
			public ConsoleView getConsole() {
				SMTRunnerConsoleProperties properties =
						new SMTRunnerConsoleProperties(configuration, FRAMEWORK_NAME, executor);
				// samples are few and fast, so showing the passing ones is more useful than hiding them
				properties.setIfUndefined(TestConsoleProperties.HIDE_PASSED_TESTS, false);
				properties.setIfUndefined(TestConsoleProperties.SCROLL_TO_SOURCE, true);
				return SMTestRunnerConnectionUtil.createConsole(properties);
			}

			@Override
			public void addFilter(@NotNull Filter filter) {
				// the test console does its own output handling; no extra filters are needed
			}

			@Override
			public void setViewer(boolean isViewer) {
			}
		};
	}
}
