package name.admitriev.jhelper.components;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.StringTokenizer;
import name.admitriev.jhelper.IDEUtils;
import name.admitriev.jhelper.network.SimpleHttpServer;
import name.admitriev.jhelper.task.TaskData;
import name.admitriev.jhelper.task.TaskUtils;
import name.admitriev.jhelper.ui.Notificator;
import name.admitriev.jhelper.ui.UIUtils;
import net.egork.chelper.parser.*;
import net.egork.chelper.task.Task;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A project service to monitor requests from the CHelper Chrome Extension and parse them to Tasks.
 *
 * Started by {@link ChromeParserStartup}; {@code ProjectComponent}, which this used to implement, was
 * removed from the platform.
 */
@Service(Service.Level.PROJECT)
public final class ChromeParser implements Disposable {
	private static final int PORT = 4243;
	private static final Map<String, Parser> PARSERS;

	static {
		Map<String, Parser> taskParsers = new HashMap<>();
		taskParsers.put("yandex", new YandexParser());
		taskParsers.put("codeforces", name.admitriev.jhelper.parsing.Parsers.codeforces());
		taskParsers.put("hackerrank", new HackerRankParser());
		taskParsers.put("facebook", new FacebookParser());
		taskParsers.put("usaco", new UsacoParser());
		taskParsers.put("gcj", new GCJParser());
		taskParsers.put("bayan", new BayanParser());
		taskParsers.put("kattis", new KattisParser());
		taskParsers.put("codechef", new CodeChefParser());
		taskParsers.put("hackerearth", new HackerEarthParser());
		taskParsers.put("atcoder", new AtCoderParser());
		taskParsers.put("csacademy", new CSAcademyParser());
		taskParsers.put("new-gcj", new NewGCJParser());
		taskParsers.put("json", new JSONParser());
		PARSERS = Collections.unmodifiableMap(taskParsers);
	}

	private SimpleHttpServer server = null;
	private final Project project;

	public ChromeParser(Project project) {
		this.project = project;
	}

	public synchronized void start() {
		if (server != null) {
			return;
		}
		try {
			server = new SimpleHttpServer(
					new InetSocketAddress("localhost", PORT),
					request -> {
						StringTokenizer st = new StringTokenizer(request);
						String type = st.nextToken();
						Parser parser = PARSERS.get(type);
						if (parser == null) {
							Notificator.showNotification(
									"Unknown parser",
									"Parser " + type + " unknown, request ignored",
									NotificationType.INFORMATION
							);
							return;
						}
						String page = request.substring(st.getCurrentPosition());
						Collection<Task> tasks = parser.parseTaskFromHTML(page);
						if (tasks.isEmpty()) {
							Notificator.showNotification(
									"Couldn't parse any task",
									"Maybe format changed?",
									NotificationType.WARNING
							);
						}

						Configurator configurator = project.getService(Configurator.class);
						Configurator.State configuration = configurator.getState();
						String path = configuration.getTasksDirectory();
						for (Task rawTask : tasks) {
							TaskData task = new TaskData(
									rawTask.name,
									rawTask.taskClass,
									TaskUtils.cppPathFor(path, rawTask.contestName, rawTask.taskClass),
									rawTask.input,
									rawTask.output,
									rawTask.testType,
									rawTask.tests
							);
							VirtualFile generatedFile = TaskUtils.saveNewTask(task, project);
							UIUtils.openMethodInEditor(project, generatedFile, "solve");
						}

						IDEUtils.reloadProject(project);
					}
			);

			new Thread(server, "ChromeParserThread").start();
		}
		catch (IOException ignored) {
			Notificator.showNotification(
					"Could not create serverSocket for Chrome parser",
					"Probably another CHelper or JHelper project is running?",
					NotificationType.ERROR
			);
		}
	}

	@Override
	public synchronized void dispose() {
		if (server != null) {
			server.stop();
			server = null;
		}
	}
}
