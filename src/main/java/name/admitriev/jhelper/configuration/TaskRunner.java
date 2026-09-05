package name.admitriev.jhelper.configuration;

import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import name.admitriev.jhelper.IDEUtils;
import name.admitriev.jhelper.exceptions.NotificationException;
import name.admitriev.jhelper.generation.CodeGenerationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Class for Running TaskConfiguration
 * It isn't fully compliant with {@link ProgramRunner} Interface because {@link #execute} doesn't call {@link RunProfile#getState}
 * as described in <a href="http://confluence.jetbrains.com/display/IDEADEV/Run+Configurations#RunConfigurations-RunningaProcess">IDEA DEV Confluence</a>
 */
public class TaskRunner implements ProgramRunner<RunnerSettings> {
	private static final String RUN_CONFIGURATION_NAME = "testrunner";
	private static final Logger LOG = Logger.getInstance(TaskRunner.class);

	@NotNull
	@Override
	public String getRunnerId() {
		return "name.admitriev.jhelper.configuration.TaskRunner";
	}

	@Override
	public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
		return profile instanceof TaskConfiguration;
	}

	/**
	 * Runs specified TaskConfiguration: generates code and then runs output configuration.
	 *
	 * @throws ClassCastException if {@code environment.getRunProfile()} is not {@link TaskConfiguration}.
	 * @see ExecutionEnvironment#getRunProfile()
	 */
	@Override
	public void execute(@NotNull ExecutionEnvironment environment) {
		Project project = environment.getProject();

		TaskConfiguration taskConfiguration = (TaskConfiguration) environment.getRunProfile();
		CodeGenerationUtils.generateSubmissionFileForTask(project, taskConfiguration);

		generateRunFileForTask(project, taskConfiguration);

		List<RunnerAndConfigurationSettings> allSettings = RunManager.getInstance(project).getAllSettings();
		RunnerAndConfigurationSettings testRunnerSettings = null;
		for (RunnerAndConfigurationSettings configuration : allSettings) {
			if (configuration.getName().equals(RUN_CONFIGURATION_NAME)) {
				testRunnerSettings = configuration;
			}
		}
		if (testRunnerSettings == null) {
			throw new NotificationException(
					"No run configuration found",
					"It should be called (" + RUN_CONFIGURATION_NAME + ")"
			);
		}

		ExecutionTarget originalExecutionTarget = environment.getExecutionTarget();
		ExecutionTarget testRunnerExecutionTarget = ((TaskConfigurationExecutionTarget)originalExecutionTarget).getOriginalTarget();
		RunnerAndConfigurationSettings originalSettings = environment.getRunnerAndConfigurationSettings();

		IDEUtils.chooseConfigurationAndTarget(project, testRunnerSettings, testRunnerExecutionTarget);
		executeInTestConsole(project, testRunnerSettings, environment.getExecutor());

		IDEUtils.chooseConfigurationAndTarget(project, originalSettings, originalExecutionTarget);
	}

	/**
	 * Runs the test runner configuration with its output shown as a test tree rather than plain text.
	 *
	 * The configuration belongs to the user's CMake project, so rather than executing it directly its
	 * profile is wrapped: the wrapper delegates {@code getState} and then points the resulting state at
	 * a test console. The settings are still passed to the environment, so the before-run task that
	 * rebuilds the target is preserved.
	 *
	 * Falls back to running the configuration normally if any of this does not apply — a state that is
	 * not a {@link CommandLineState}, no runner for the executor, or an unexpected failure. Showing the
	 * results as plain output is a far better outcome than not running the tests at all.
	 */
	private static void executeInTestConsole(
			@NotNull Project project,
			@NotNull RunnerAndConfigurationSettings settings,
			@NotNull Executor executor
	) {
		try {
			RunConfiguration configuration = settings.getConfiguration();
			ProgramRunner<?> runner = ProgramRunner.getRunner(executor.getId(), configuration);
			if (runner == null) {
				LOG.info("No runner for executor " + executor.getId() + "; running without the test console");
				ProgramRunnerUtil.executeConfiguration(settings, executor);
				return;
			}

			RunProfile profile = withTestConsole(configuration, executor);
			if (profile == null) {
				ProgramRunnerUtil.executeConfiguration(settings, executor);
				return;
			}

			ExecutionEnvironment testEnvironment = new ExecutionEnvironmentBuilder(project, executor)
					.runnerAndSettings(runner, settings)
					.runProfile(profile)
					.activeTarget()
					.build();

			runner.execute(testEnvironment);
		}
		catch (Throwable e) {
			LOG.warn("Couldn't attach the test console; running the configuration normally", e);
			ProgramRunnerUtil.executeConfiguration(settings, executor);
		}
	}

	/**
	 * Wraps the configuration so the state it produces writes to a test console.
	 *
	 * A hand-written {@link RunProfile} is not enough. CLion's own runner casts the environment's profile
	 * to {@link RunConfiguration} and hands it to its vetoers, so the wrapper has to genuinely be one. A
	 * proxy over every interface the configuration itself implements satisfies that, while still letting
	 * {@code getState} be intercepted.
	 *
	 * @return the wrapped profile, or {@code null} if it cannot be built, in which case the caller should
	 * run the configuration as it normally would
	 */
	private static @Nullable RunProfile withTestConsole(
			@NotNull RunConfiguration configuration,
			@NotNull Executor executor
	) {
		Set<Class<?>> interfaces = new LinkedHashSet<>();
		for (Class<?> type = configuration.getClass(); type != null; type = type.getSuperclass()) {
			for (Class<?> each : type.getInterfaces()) {
				collectInterfaces(each, interfaces);
			}
		}
		if (!interfaces.contains(RunConfiguration.class)) {
			LOG.info(configuration.getClass().getName() + " is not a RunConfiguration; not wrapping it");
			return null;
		}

		InvocationHandler handler = (proxy, method, args) -> {
			Object result;
			try {
				result = method.invoke(configuration, args);
			}
			catch (InvocationTargetException e) {
				// unwrap, so the configuration's own failures surface as themselves
				throw e.getCause() == null ? e : e.getCause();
			}
			if ("getState".equals(method.getName()) && result instanceof CommandLineState) {
				((CommandLineState) result).setConsoleBuilder(TestConsole.builderFor(configuration, executor));
			}
			return result;
		};

		return (RunProfile) Proxy.newProxyInstance(
				TaskRunner.class.getClassLoader(),
				interfaces.toArray(new Class<?>[0]),
				handler
		);
	}

	private static void collectInterfaces(@NotNull Class<?> type, @NotNull Set<Class<?>> collected) {
		if (collected.add(type)) {
			for (Class<?> each : type.getInterfaces()) {
				collectInterfaces(each, collected);
			}
		}
	}

	@Nullable
	public static RunnerAndConfigurationSettings getRunnerSettings(@NotNull Project project) {
		return getSettingsByName(project, RUN_CONFIGURATION_NAME);
	}

	private static void generateRunFileForTask(Project project, TaskConfiguration taskConfiguration) {
		String pathToClassFile = taskConfiguration.getCppPath();
		VirtualFile virtualFile = project.getBaseDir().findFileByRelativePath(pathToClassFile);
		if (virtualFile == null) {
			throw new NotificationException("Task file not found", "Seems your task is in inconsistent state");
		}

		PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
		if (psiFile == null) {
			throw new NotificationException("Couldn't get PSI file for input file");
		}

		CodeGenerationUtils.generateRunFile(project, psiFile, taskConfiguration);
	}

	@Nullable
	private static RunnerAndConfigurationSettings getSettingsByName(@NotNull Project project, String name) {
		for (RunnerAndConfigurationSettings configuration : RunManager.getInstance(project).getAllSettings()) {
			if (configuration.getName().equals(name)) {
				return configuration;
			}
		}
		return null;
	}
}
