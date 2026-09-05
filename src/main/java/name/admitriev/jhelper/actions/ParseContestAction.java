package name.admitriev.jhelper.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import name.admitriev.jhelper.IDEUtils;
import name.admitriev.jhelper.task.TaskData;
import name.admitriev.jhelper.task.TaskUtils;
import name.admitriev.jhelper.ui.ParseDialog;
import name.admitriev.jhelper.ui.UIUtils;

public class ParseContestAction extends BaseAction {
	@Override
	protected void performAction(AnActionEvent e) {
		Project project = e.getProject();
		ParseDialog dialog = new ParseDialog(project);
		dialog.show();
		if (!dialog.isOK()) {
			return;
		}
		for (TaskData taskData : dialog.getResult()) {
			VirtualFile generatedFile = TaskUtils.saveNewTask(taskData, project);
			UIUtils.openMethodInEditor(project, generatedFile, "solve");
		}

		IDEUtils.reloadProject(project);
	}
}
