package name.admitriev.jhelper.task;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.application.ApplicationManager;
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

	private TaskUtils() {
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
		createConfigurationForTask(project, taskData);
		return generateCPP(project, taskData);
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
