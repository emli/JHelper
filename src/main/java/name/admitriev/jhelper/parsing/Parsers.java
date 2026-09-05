package name.admitriev.jhelper.parsing;

import net.egork.chelper.parser.CodeforcesParser;
import net.egork.chelper.parser.Parser;

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
	 * The bundled parser list, with Codeforces replaced by the adapted one.
	 */
	public static Parser[] all() {
		Parser[] parsers = Parser.PARSERS.clone();
		for (int i = 0; i < parsers.length; i++) {
			if (parsers[i] instanceof CodeforcesParser) {
				parsers[i] = CODEFORCES;
			}
		}
		return parsers;
	}

	/**
	 * The parser to use for a given CHelper parser id, as sent by the Chrome extension.
	 */
	public static Parser codeforces() {
		return CODEFORCES;
	}
}
