package name.admitriev.jhelper.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UIUtils {
	private UIUtils() {
	}

	/**
	 * Make two fields change simultaneously until the second one ({@code copy}) changed manually.
	 * Maintains equality of {@code String.format(format, main.getText())} and {@code copy.getText()}
	 *
	 * Does nothing if this equality is wrong when method is called.
	 *
	 * @param format format for {@link String#format}. Should contain exactly one format specifier equal to %s
	 */
	public static void mirrorFields(JTextField main, JTextField copy, String format) {
		if (!String.format(format, main.getText()).equals(copy.getText())) {
			// The copy is already changed.
			return;
		}
		AtomicBoolean changingFirst = new AtomicBoolean(false);
		AtomicBoolean secondChanged = new AtomicBoolean(false);
		main.getDocument().addDocumentListener(

				new DocumentAdapter() {

					@Override
					protected void textChanged(DocumentEvent e) {
						if (secondChanged.get()) {
							return;
						}
						while (!changingFirst.compareAndSet(false, true)) {
							// intentionally empty
						}

						copy.setText(String.format(format, main.getText()));

						changingFirst.set(false);
					}
				}
		);

		copy.getDocument().addDocumentListener(
				new DocumentAdapter() {
					@Override
					protected void textChanged(DocumentEvent e) {
						if (!changingFirst.get()) {
							secondChanged.set(true);
						}
					}
				}
		);
	}

	/**
	 * Make two fields change simultaneously until the second one ({@code copy}) changed manually.
	 * Maintains equality of {@code main.getText()} and {@code copy.getText()}
	 *
	 * Does nothing if this equality is wrong when method is called.
	 */
	public static void mirrorFields(JTextField main, JTextField copy) {
		mirrorFields(main, copy, "%s");
	}

	/**
	 * Finds method @{code methodName} in @{code file} and opens it in an editor.
	 */
	public static void openMethodInEditor(Project project, VirtualFile file, String methodName) {
		new OpenFileDescriptor(project, file, findMethodBodyOffset(file, methodName)).navigate(true);
	}

	/**
	 * Locates the first statement of {@code method}'s body by scanning the file text.
	 *
	 * CLion Nova exposes no frontend PSI (see CPP-39813), so the function definition can't be found by
	 * walking a syntax tree. Falls back to the top of the file when the method isn't found.
	 */
	private static int findMethodBodyOffset(VirtualFile file, @NotNull String method) {
		Document document = FileDocumentManager.getInstance().getDocument(file);
		if (document == null) {
			return 0;
		}
		// matches `solve(...) {`, tolerating a return type, qualifiers and line breaks, but not
		// crossing a `;` (a declaration) or another `{` (an unrelated body)
		Matcher matcher = Pattern
				.compile("\\b" + Pattern.quote(method) + "\\s*\\([^;{}]*\\)[^;{}]*\\{")
				.matcher(document.getText());
		return matcher.find() ? matcher.end() : 0;
	}
}
