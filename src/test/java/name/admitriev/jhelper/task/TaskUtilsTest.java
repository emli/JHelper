package name.admitriev.jhelper.task;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Every contest has a problem A, so its task class is TaskA in all of them. Putting each contest in its
 * own folder is what stops the second contest parsed from colliding with the first.
 */
public class TaskUtilsTest {

	@Test
	public void tasksGoBelowAFolderNamedAfterTheirContest() {
		assertEquals(
				"tasks/Codeforces_Round_1119_Div._3/TaskA.cpp",
				TaskUtils.cppPathFor("tasks", "Codeforces Round 1119 (Div. 3)", "TaskA")
		);
	}

	@Test
	public void thesameProblemLetterInTwoContestsDoesNotCollide() {
		String first = TaskUtils.cppPathFor("tasks", "Codeforces Round 1119 (Div. 3)", "TaskA");
		String second = TaskUtils.cppPathFor("tasks", "Codeforces Round 1120 (Div. 2)", "TaskA");
		assertFalse("two contests must not write to the same file", first.equals(second));
	}

	@Test
	public void aContestWithoutANameKeepsTheFlatLayout() {
		// inventing a folder would be worse than leaving the task where it has always gone
		assertEquals("tasks/TaskA.cpp", TaskUtils.cppPathFor("tasks", null, "TaskA"));
		assertEquals("tasks/TaskA.cpp", TaskUtils.cppPathFor("tasks", "", "TaskA"));
		assertEquals("tasks/TaskA.cpp", TaskUtils.cppPathFor("tasks", "   ", "TaskA"));
	}

	@Test
	public void separatorsInAContestNameCannotCreateNestedDirectories() {
		// a name carrying a slash would otherwise write outside the intended folder
		assertEquals("tasks/a_b/TaskA.cpp", TaskUtils.cppPathFor("tasks", "a/b", "TaskA"));
		assertEquals("tasks/a_b/TaskA.cpp", TaskUtils.cppPathFor("tasks", "a\\b", "TaskA"));
	}

	@Test
	public void aNameThatWouldEscapeOrHideTheDirectoryIsNeutralised() {
		assertEquals("tasks/TaskA.cpp", TaskUtils.cppPathFor("tasks", "..", "TaskA"));
		assertEquals("tasks/TaskA.cpp", TaskUtils.cppPathFor("tasks", ".", "TaskA"));
		// a leading dot would make the folder hidden
		assertEquals("tasks/hidden/TaskA.cpp", TaskUtils.cppPathFor("tasks", ".hidden", "TaskA"));
	}

	@Test
	public void runsOfDisallowedCharactersCollapseIntoASingleSeparator() {
		assertEquals("Round_1", TaskUtils.folderNameFor("Round     1"));
		// hyphens are kept: contest names use them, and they are valid in a directory name
		assertEquals("Round_---_1", TaskUtils.folderNameFor("Round  ---  1"));
	}

	@Test
	public void ordinaryCharactersSurviveUntouched() {
		assertEquals("ABC-123_x.y", TaskUtils.folderNameFor("ABC-123_x.y"));
	}
}
