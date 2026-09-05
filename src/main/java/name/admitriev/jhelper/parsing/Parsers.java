package name.admitriev.jhelper.parsing;

import net.egork.chelper.parser.CodeforcesParser;
import net.egork.chelper.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * The parsers the plugin offers, with any that need adapting to current markup wrapped.
 *
 * Use this rather than {@link Parser#PARSERS} directly, so a fix applies everywhere a parser is used.
 */
public class Parsers {
	private Parsers() {
	}

	private static final Parser CODEFORCES = new CodeforcesParserFix(new CodeforcesParser());

	/**
	 * Parsers offered in the Parse contest dialog.
	 *
	 * CHelper also bundles Timus and Russian CodeCup, which are not offered. Both scrape markup that
	 * has not been maintained for years, and neither is in use here.
	 *
	 * This is only the contest-browsing list. Parsing a page sent by the Chrome extension goes through
	 * {@code ChromeParser}, which still recognises the other judges CHelper supports.
	 */
	public static Parser[] all() {
		List<Parser> parsers = new ArrayList<>();
		for (Parser parser : Parser.PARSERS) {
			if (parser instanceof CodeforcesParser) {
				parsers.add(CODEFORCES);
			}
		}
		return parsers.toArray(new Parser[0]);
	}

	/**
	 * The parser to use for a given CHelper parser id, as sent by the Chrome extension.
	 */
	public static Parser codeforces() {
		return CODEFORCES;
	}
}
