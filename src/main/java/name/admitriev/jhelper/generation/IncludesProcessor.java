package name.admitriev.jhelper.generation;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import name.admitriev.jhelper.exceptions.NotificationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recursively inlines project-local {@code #include} directives to produce a single translation unit.
 *
 * This works on file text rather than on the C++ PSI tree. CLion Nova runs its analysis in a separate
 * backend process and exposes no frontend PSI (see CPP-39813), so the {@code OCIncludeDirective} /
 * {@code OCPragma} based implementation this replaces cannot run under it.
 *
 * Includes that resolve to a file inside the project's content roots are inlined. Everything else --
 * angle-bracket includes and quoted includes that don't resolve locally -- is emitted once, deduplicated,
 * so the standard library is left to the compiler.
 */
public class IncludesProcessor {
	private static final Pattern QUOTED_INCLUDE = Pattern.compile("\\s*#\\s*include\\s*\"([^\"]+)\".*");
	private static final Pattern SYSTEM_INCLUDE = Pattern.compile("\\s*#\\s*include\\s*<([^>]+)>.*");
	private static final Pattern PRAGMA_ONCE = Pattern.compile("\\s*#\\s*pragma\\s+once\\s*");

	private final Project project;
	private final ProjectFileIndex fileIndex;
	private final Set<VirtualFile> processedFiles = new HashSet<>();
	private final Set<String> emittedIncludes = new LinkedHashSet<>();
	@SuppressWarnings("StringBufferField")
	private final StringBuilder result = new StringBuilder();

	private IncludesProcessor(@NotNull Project project) {
		this.project = project;
		fileIndex = ProjectFileIndex.getInstance(project);
	}

	private void processFile(@NotNull VirtualFile file) {
		if (!processedFiles.add(file)) {
			return;
		}
		String[] lines = readText(file).split("\n", -1);
		// a trailing newline leaves a final empty element; dropping it keeps every inlined file from
		// contributing an extra blank line to the result
		int lineCount = lines.length > 0 && lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;

		boolean inBlockComment = false;
		for (int i = 0; i < lineCount; i++) {
			String line = lines[i];
			if (!inBlockComment && processDirective(file, line)) {
				inBlockComment = updateBlockCommentState(line, false);
				continue;
			}
			result.append(line).append('\n');
			inBlockComment = updateBlockCommentState(line, inBlockComment);
		}
	}

	/**
	 * Handles a line if it is a directive this processor rewrites.
	 *
	 * @return {@code true} if the line was consumed and should not be copied verbatim
	 */
	private boolean processDirective(@NotNull VirtualFile from, @NotNull String line) {
		Matcher quoted = QUOTED_INCLUDE.matcher(line);
		if (quoted.matches()) {
			VirtualFile target = resolve(from, quoted.group(1));
			if (target != null && fileIndex.isInContent(target)) {
				processFile(target);
			}
			else {
				emitIncludeOnce(line.trim());
			}
			return true;
		}
		if (SYSTEM_INCLUDE.matcher(line).matches()) {
			emitIncludeOnce(line.trim());
			return true;
		}
		return PRAGMA_ONCE.matcher(line).matches();
	}

	private void emitIncludeOnce(@NotNull String include) {
		if (emittedIncludes.add(include)) {
			result.append(include).append('\n');
		}
	}

	/**
	 * Resolves a quoted include first against the including file's own directory, as the C++ preprocessor
	 * does, then against the project's content roots.
	 */
	private @Nullable VirtualFile resolve(@NotNull VirtualFile from, @NotNull String includePath) {
		VirtualFile parent = from.getParent();
		if (parent != null) {
			VirtualFile resolved = parent.findFileByRelativePath(includePath);
			if (resolved != null) {
				return resolved;
			}
		}
		for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
			VirtualFile resolved = root.findFileByRelativePath(includePath);
			if (resolved != null) {
				return resolved;
			}
		}
		return null;
	}

	/**
	 * Tracks whether a line leaves us inside a block comment, so that commented-out directives are copied
	 * verbatim instead of being followed.
	 */
	private static boolean updateBlockCommentState(@NotNull String line, boolean startsInBlockComment) {
		boolean inBlockComment = startsInBlockComment;
		int i = 0;
		while (i < line.length()) {
			if (inBlockComment) {
				int end = line.indexOf("*/", i);
				if (end < 0) {
					return true;
				}
				i = end + 2;
				inBlockComment = false;
			}
			else {
				int blockStart = line.indexOf("/*", i);
				if (blockStart < 0) {
					return false;
				}
				int lineComment = line.indexOf("//", i);
				if (lineComment >= 0 && lineComment < blockStart) {
					// the rest of the line is a line comment, so the /* never opens
					return false;
				}
				i = blockStart + 2;
				inBlockComment = true;
			}
		}
		return inBlockComment;
	}

	/**
	 * Reads through the document when one exists so that unsaved editor changes are picked up.
	 */
	private static @NotNull String readText(@NotNull VirtualFile file) {
		Document document = FileDocumentManager.getInstance().getDocument(file);
		if (document != null) {
			return document.getText();
		}
		try {
			return VfsUtilCore.loadText(file);
		}
		catch (IOException e) {
			throw new NotificationException("Couldn't read " + file.getPath(), e);
		}
	}

	public static @NotNull String process(@NotNull Project project, @NotNull VirtualFile file) {
		return ReadAction.compute(
				() -> {
					IncludesProcessor processor = new IncludesProcessor(project);
					processor.processFile(file);
					return processor.result.toString();
				}
		);
	}
}
