package name.admitriev.jhelper.actions;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import name.admitriev.jhelper.IDEUtils;
import name.admitriev.jhelper.exceptions.NotificationException;
import name.admitriev.jhelper.task.TaskData;
import name.admitriev.jhelper.task.TaskUtils;
import name.admitriev.jhelper.ui.Notificator;
import name.admitriev.jhelper.ui.ParseDialog;
import name.admitriev.jhelper.ui.UIUtils;

import java.util.Collection;

public class ParseContestAction extends BaseAction {
	@Override
	protected void performAction(AnActionEvent e) {
		Project project = e.getProject();
		ParseDialog dialog = new ParseDialog(project);
		dialog.show();
		if (!dialog.isOK()) {
			return;
		}

		Collection<TaskData> tasks = dialog.getResult();
		if (tasks.isEmpty()) {
			// Previously this loop simply did nothing, so a contest that listed fine but yielded no tasks
			// looked like the action had silently failed.
			throw new NotificationException(
					"No tasks were created",
					"No problems were selected, or none of the selected problems could be fetched. " +
					"Select the problems you want in the right-hand list before pressing OK."
			);
		}

		int created = 0;
		for (TaskData taskData : tasks) {
			try {
				VirtualFile generatedFile = TaskUtils.saveNewTask(taskData, project);
				UIUtils.openMethodInEditor(project, generatedFile, "solve");
				created++;
			}
			catch (NotificationException exception) {
				// Report and carry on. Aborting here left the rest of the contest uncreated, which was
				// especially easy to hit when one task file already existed.
				Notificator.showNotification(
						"Couldn't create task " + taskData.getName(),
						exception.getContent(),
						NotificationType.WARNING
				);
			}
		}

		if (created > 0) {
			IDEUtils.reloadProject(project);
		}
	}
}
