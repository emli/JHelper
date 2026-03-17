package name.admitriev.jhelper.components;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import name.admitriev.jhelper.configuration.TaskConfiguration;
import name.admitriev.jhelper.ui.Notificator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP server that exposes a local remote-control API for JHelper.
 *
 * <p>Endpoints (all bound to localhost only):
 * <ul>
 *   <li>{@code GET  /status} – returns the currently selected task as JSON</li>
 *   <li>{@code GET  /tasks}  – returns the list of all task configurations</li>
 *   <li>{@code POST /switch} – switches to the task named in the JSON body
 *                              {@code {"task":"<name>"}}</li>
 * </ul>
 */
public class RemoteControlServer implements ProjectComponent {
	private static final int PORT = 4244;

	private final Project project;
	private ServerSocket serverSocket = null;

	public RemoteControlServer(Project project) {
		this.project = project;
	}

	@Override
	public void projectOpened() {
		try {
			serverSocket = new ServerSocket();
			serverSocket.bind(new InetSocketAddress("localhost", PORT));
			Thread thread = new Thread(this::run, "JHelperRemoteControlThread");
			thread.setDaemon(true);
			thread.start();
		}
		catch (IOException ignored) {
			Notificator.showNotification(
					"Remote Control",
					"Could not start remote control server on port " + PORT
							+ ". Another JHelper project may already be running.",
					NotificationType.WARNING
			);
		}
	}

	private void run() {
		while (true) {
			if (serverSocket.isClosed()) {
				return;
			}
			try (Socket socket = serverSocket.accept()) {
				handleRequest(socket);
			}
			catch (IOException ignored) {
			}
		}
	}

	private void handleRequest(Socket socket) throws IOException {
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
		);
		OutputStream out = socket.getOutputStream();

		String requestLine = reader.readLine();
		if (requestLine == null || requestLine.isEmpty()) {
			sendResponse(out, 400, "{\"error\":\"empty_request\"}");
			return;
		}

		String[] parts = requestLine.split(" ");
		if (parts.length < 2) {
			sendResponse(out, 400, "{\"error\":\"bad_request\"}");
			return;
		}

		String method = parts[0];
		String path = parts[1];

		int contentLength = 0;
		String headerLine;
		while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
			if (headerLine.toLowerCase().startsWith("content-length:")) {
				try {
					contentLength = Integer.parseInt(
							headerLine.substring("content-length:".length()).trim()
					);
				}
				catch (NumberFormatException ignored) {
				}
			}
		}

		String body = "";
		if (contentLength > 0) {
			char[] buffer = new char[contentLength];
			int read = reader.read(buffer, 0, contentLength);
			if (read > 0) {
				body = new String(buffer, 0, read);
			}
		}

		String responseBody = dispatch(method, path, body);
		sendResponse(out, 200, responseBody);
	}

	private String dispatch(String method, String path, String body) {
		if ("GET".equals(method)) {
			if ("/status".equals(path)) {
				return getStatus();
			}
			if ("/tasks".equals(path)) {
				return getTasks();
			}
		}
		else if ("POST".equals(method)) {
			if ("/switch".equals(path)) {
				return switchTask(body);
			}
		}
		return "{\"error\":\"not_found\"}";
	}

	private String getStatus() {
		RunManager runManager = RunManager.getInstance(project);
		RunnerAndConfigurationSettings selected = runManager.getSelectedConfiguration();
		if (selected == null || !(selected.getConfiguration() instanceof TaskConfiguration)) {
			return "{\"task\":null}";
		}
		TaskConfiguration config = (TaskConfiguration) selected.getConfiguration();
		return "{\"task\":\"" + escapeJson(selected.getName())
				+ "\",\"file\":\"" + escapeJson(config.getCppPath()) + "\"}";
	}

	private String getTasks() {
		RunManager runManager = RunManager.getInstance(project);
		List<String> names = new ArrayList<>();
		for (RunnerAndConfigurationSettings settings : runManager.getAllSettings()) {
			if (settings.getConfiguration() instanceof TaskConfiguration) {
				names.add("\"" + escapeJson(settings.getName()) + "\"");
			}
		}
		return "{\"tasks\":[" + String.join(",", names) + "]}";
	}

	private String switchTask(String body) {
		String taskName = parseStringField(body, "task");
		if (taskName == null) {
			return "{\"ok\":false,\"error\":\"missing_task_field\"}";
		}
		final String[] result = {null};
		ApplicationManager.getApplication().invokeAndWait(() -> {
			RunManager runManager = RunManager.getInstance(project);
			for (RunnerAndConfigurationSettings settings : runManager.getAllSettings()) {
				if (settings.getName().equals(taskName)
						&& settings.getConfiguration() instanceof TaskConfiguration) {
					runManager.setSelectedConfiguration(settings);
					result[0] = "{\"ok\":true,\"task\":\"" + escapeJson(taskName) + "\"}";
					return;
				}
			}
			result[0] = "{\"ok\":false,\"error\":\"task_not_found\"}";
		}, ModalityState.NON_MODAL);
		return result[0] != null ? result[0] : "{\"ok\":false,\"error\":\"switch_failed\"}";
	}

	private void sendResponse(OutputStream out, int statusCode, String body) throws IOException {
		byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
		String statusText = statusCode == 200 ? "OK" : "Bad Request";
		String header = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n"
				+ "Content-Type: application/json\r\n"
				+ "Content-Length: " + bodyBytes.length + "\r\n"
				+ "Access-Control-Allow-Origin: *\r\n"
				+ "Connection: close\r\n"
				+ "\r\n";
		out.write(header.getBytes(StandardCharsets.UTF_8));
		out.write(bodyBytes);
		out.flush();
	}

	/**
	 * Minimal single-field JSON string parser for objects like {@code {"key":"value"}}.
	 * Returns {@code null} when the field is absent.
	 */
	private static String parseStringField(String json, String fieldName) {
		String key = "\"" + fieldName + "\"";
		int keyIndex = json.indexOf(key);
		if (keyIndex < 0) {
			return null;
		}
		int colonIndex = json.indexOf(':', keyIndex + key.length());
		if (colonIndex < 0) {
			return null;
		}
		int quoteStart = json.indexOf('"', colonIndex + 1);
		if (quoteStart < 0) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		boolean escaped = false;
		for (int i = quoteStart + 1; i < json.length(); i++) {
			char c = json.charAt(i);
			if (escaped) {
				sb.append(c);
				escaped = false;
			}
			else if (c == '\\') {
				escaped = true;
			}
			else if (c == '"') {
				break;
			}
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	@Override
	public void projectClosed() {
		if (serverSocket != null) {
			try {
				serverSocket.close();
			}
			catch (IOException ignored) {
			}
		}
	}
}
