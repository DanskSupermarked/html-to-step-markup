package dk.dsg;

import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.NodeTraversor;

public final class StepMarkup {

	/**
	 * Some edge-cases generate undesirable leading or trailing linebreaks.
	 * Instead of eliminating those one by one, blow them away with a cannon.
	 */
	private static final Pattern PAT_TRIM_LINEBREAK =
			Pattern.compile("(?:^<return/>\\n*)+|(?:<return/>\\n*)+$");

	/**
	 * Some edge-cases generate undesirable repeat linebreaks.
	 */
	private static final Pattern PAT_EXCESSIVE_LINEBREAK =
			Pattern.compile("(?:<return/>\\n*){3}");

	private StepMarkup() {
	}

	/**
	 * Transforms HTML into STEP-markup: a UTF-8 format with a few SGML-inspired
	 * markup primitives. HTML without equivalent STEP-markup, such as anchors
	 * and tables, uses other primitives to retain legibility.
	 *
	 * @param escapedHtml  possibly-escaped HTML to transform
	 * @return valid STEP-markup, or null if {@code escapedHtml} was null
	 */
	@Nullable
	public static String parse(final String escapedHtml) {
		if (escapedHtml == null) {
			return null;
		}

		final Document parse = Jsoup.parseBodyFragment(escapedHtml);
		parse.outputSettings().prettyPrint(false);
		final StringBuilder buf = new StringBuilder();
		final NodeTraversor trav = new NodeTraversor(new StepMarkupNodeVisitor(buf));
		trav.traverse(parse);
		final String trimmed = PAT_TRIM_LINEBREAK
				.matcher(buf)
				.replaceAll("");
		final String unescaped = PAT_EXCESSIVE_LINEBREAK
				.matcher(trimmed).
				replaceAll("<return/>\n<return/>\n");
		return StringEscapeUtils.unescapeHtml(unescaped);
	}

}
