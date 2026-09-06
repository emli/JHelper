package name.admitriev.jhelper.task;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import name.admitriev.jhelper.configuration.TaskConfiguration;
import name.admitriev.jhelper.configuration.TaskConfigurationType;
import name.admitriev.jhelper.exceptions.NotificationException;
import name.admitriev.jhelper.generation.FileUtils;
import name.admitriev.jhelper.generation.TemplatesUtils;

import java.io.IOException;

public class TaskUtils {
	private static final Logger LOG = Logger.getInstance(TaskUtils.class);

	private TaskUtils() {
	}

	/**
	 * Where a parsed task's source file goes: below a folder named after its contest.
	 *
	 * Contests reuse problem letters, so every one of them has a TaskA. Writing them all straight into
	 * the tasks directory means the second contest parsed collides with the first, and creating the task
	 * is refused because a file of that name already exists.
	 *
	 * A contest without a usable name falls back to the flat layout rather than inventing a folder.
	 */
	public static String cppPathFor(String tasksDirectory, String contestName, String taskClass) {
		String folder = folderNameFor(contestName);
		if (folder.isEmpty()) {
			return String.format("%s/%s.cpp", tasksDirectory, taskClass);
		}
		return String.format("%s/%s/%s.cpp", tasksDirectory, folder, taskClass);
	}

	/**
	 * Turns a contest name into something usable as a directory name. Contest names carry spaces,
	 * punctuation and separators — "Codeforces Round 1119 (Div. 3)" — none of which belong in a path.
	 */
	static String folderNameFor(String contestName) {
		if (contestName == null) {
			return "";
		}
		String folder = contestName.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
		// leading dots would hide the directory, and a bare "." or ".." would escape it
		folder = folder.replaceAll("^[._]+", "").replaceAll("[._]+$", "");
		return folder;
	}

	/**
	 * Generates task file content depending on custom user template
	 */
	private static String getTaskContent(Project project, String className) {
		String template = TemplatesUtils.getTemplate(project, "task");
		template = TemplatesUtils.replaceAll(template, TemplatesUtils.CLASS_NAME, className);
		return template;
	}

	public static VirtualFile saveNewTask(TaskData taskData, Project project) {
		// Write the file first. Creating the run configuration first meant that if file creation failed
		// the project was left with a configuration pointing at a task file that does not exist.
		VirtualFile file = generateCPP(project, taskData);
		createConfigurationForTask(project, taskData);
		return file;
	}

	/**
	 * Writes the task file straight through the virtual file system.
	 *
	 * The previous implementation built it with {@code PsiFileFactory} under the language registered as
	 * "ObjectiveC", which only exists in the CLion Classic engine. Nova registers C++ under a different
	 * language, so resolving it by that ID fails there.
	 */
	private static VirtualFile generateCPP(Project project, TaskData taskData) {
		VirtualFile parent = FileUtils.findOrCreateByRelativePath(
				project.getBaseDir(),
				FileUtils.getDirectory(taskData.getCppPath())
		);
		String fileName = FileUtils.getFilename(taskData.getCppPath());
		String content = getTaskContent(project, taskData.getClassName());

		LOG.info(
				"Creating task file: cppPath=" + taskData.getCppPath() + ", directory=" +
				FileUtils.getDirectory(taskData.getCppPath()) + ", resolved parent=" +
				(parent == null ? "null" : parent.getPath()) + ", fileName=" + fileName
		);

		return ApplicationManager.getApplication().runWriteAction(
				(Computable<VirtualFile>) () -> {
					// PsiDirectory.add, used before, refused to overwrite an existing file. Keep refusing:
					// silently replacing it would discard a solution the user had already written.
					if (parent.findChild(fileName) != null) {
						throw new NotificationException(
								"Task file already exists",
								fileName + " already exists in " + parent.getPath() +
								". Delete the existing task before creating one with the same name."
						);
					}
					try {
						VirtualFile file = parent.createChildData(TaskUtils.class, fileName);
						VfsUtil.saveText(file, content);
						return file;
					}
					catch (IOException e) {
						throw new NotificationException("Couldn't generate file " + fileName, e);
					}
				}
		);
	}

	private static void createConfigurationForTask(Project project, TaskData taskData) {
		TaskConfigurationType configurationType = new TaskConfigurationType();
		ConfigurationFactory factory = configurationType.getConfigurationFactories()[0];

		RunManager manager = RunManager.getInstance(project);
		TaskConfiguration taskConfiguration = new TaskConfiguration(
				project,
				factory
		);
		taskConfiguration.setFromTaskData(taskData);
		RunnerAndConfigurationSettings configuration = manager.createConfiguration(
				taskConfiguration,
				factory
		);
		configuration.storeInDotIdeaFolder();
		manager.addConfiguration(configuration);

		manager.setSelectedConfiguration(configuration);
	}
}
