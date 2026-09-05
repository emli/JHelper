package name.admitriev.jhelper.parsing;

import com.intellij.openapi.diagnostic.Logger;
import net.egork.chelper.parser.Description;
import net.egork.chelper.parser.DescriptionReceiver;
import net.egork.chelper.parser.Parser;
import net.egork.chelper.task.Task;
import net.egork.chelper.task.TestType;
import net.egork.chelper.util.FileUtilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.Collection;

/**
 * Adapts current Codeforces markup to what the bundled CHelper parser expects.
 *
 * CHelper matches the problem page with exact string literals. Codeforces has since changed three
 * things, and every one of them makes {@code parseTask} return null, silently:
 *
 * <ul>
 *   <li>{@code <div class="input-file">} is now {@code <div class="input-file input-standard">};
 *       the extra class defeats the exact match. Same for {@code output-file}.</li>
 *   <li>sample text is wrapped one line per {@code <div class="test-example-line ...">} inside the
 *       {@code <pre>}, where the parser expects the raw text.</li>
 *   <li>{@code <pre>} now carries no attributes, but the parser matches {@code <pre [^>]+>}, which
 *       requires at least one.</li>
 * </ul>
 *
 * Everything else in CHelper still works, so this only rewrites the page before handing it over, and
 * delegates the rest untouched.
 */
public class CodeforcesParserFix implements Parser {
	private static final Logger LOG = Logger.getInstance(CodeforcesParserFix.class);

	private final Parser delegate;

	public CodeforcesParserFix(@NotNull Parser delegate) {
		this.delegate = delegate;
	}

	/**
	 * Rewrites a Codeforces problem page into the shape CHelper's parser was written against.
	 */
	static @NotNull String adaptToChelper(@NotNull String html) {
		return html
				// drop the added classes so the exact literal matches again
				.replaceAll("<div class=\"input-file[^\"]*\">", "<div class=\"input-file\">")
				.replaceAll("<div class=\"output-file[^\"]*\">", "<div class=\"output-file\">")
				// unwrap the per-line divs back into plain text
				.replaceAll("<div class=\"test-example-line[^\"]*\">(.*?)</div>", "$1\n")
				// give <pre> an attribute, and drop the newline that follows it so samples don't gain a
				// leading blank line
				.replaceAll("<pre>\\s*", "<pre class=\"content\">");
	}

	@Override
	public Task parseTask(Description description) {
		// parseContest hands back ids shaped "<contestId> <letter>", e.g. "2259 A"
		String[] parts = description.id.trim().split("\\s+");
		if (parts.length < 2) {
			LOG.warn("Unexpected Codeforces description id: [" + description.id + "]");
			return delegate.parseTask(description);
		}
		String url = "https://codeforces.com/contest/" + parts[0] + "/problem/" + parts[1];
		String html = FileUtilities.getWebPageContent(url);
		if (html == null) {
			LOG.warn("Couldn't fetch " + url);
			return null;
		}
		Collection<Task> tasks = parseTaskFromHTML(html);
		if (tasks == null || tasks.isEmpty()) {
			LOG.warn("Fetched " + url + " (" + html.length() + " chars) but parsed no task from it");
			return null;
		}
		return tasks.iterator().next();
	}

	@Override
	public Collection<Task> parseTaskFromHTML(String html) {
		return delegate.parseTaskFromHTML(adaptToChelper(html));
	}

	@Override
	public Icon getIcon() {
		return delegate.getIcon();
	}

	@Override
	public String getName() {
		return delegate.getName();
	}

	@Override
	public void getContests(DescriptionReceiver receiver) {
		delegate.getContests(receiver);
	}

	@Override
	public void parseContest(String id, DescriptionReceiver receiver) {
		delegate.parseContest(id, receiver);
	}

	@Override
	public TestType defaultTestType() {
		return delegate.defaultTestType();
	}
}
