package dk.dsg;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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

	@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
	private final List<Anchor> anchors = new ArrayList<>();

	@SuppressWarnings({
			"PMD.UnnecessaryConstructor",
			"PMD.UncommentedEmptyConstructor",
	})
	public StepMarkup() {
	}

	/**
	 * Transforms HTML into STEP-markup: a UTF-8 format with a few SGML-inspired
	 * markup primitives. HTML without equivalent STEP-markup, such as anchors
	 * and tables, uses other primitives to retain legibility.
	 *
	 * <p>All encountered anchors will be aggregated in {@link #anchors()}
	 *
	 * @param escapedHtml  possibly-escaped HTML to transform
	 * @return valid STEP-markup, or null if {@code escapedHtml} was null
	 */
	@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
	@Nullable
	public String parse(final String escapedHtml) {
		if (escapedHtml == null) {
			return null;
		}

		final Document parse = Jsoup.parseBodyFragment(escapedHtml);
		parse.outputSettings().prettyPrint(false);
		final StringBuilder buf = new StringBuilder();
		final NodeTraversor trav = new NodeTraversor(new StepMarkupNodeVisitor(buf));
		trav.traverse(parse);

		for (final Element anchor : parse.getElementsByTag("a")) {
			anchors.add(new Anchor(anchor.text(), anchor.attr("href").trim()));
		}

		final String trimmed = PAT_TRIM_LINEBREAK
				.matcher(buf)
				.replaceAll("");
		final String unescaped = PAT_EXCESSIVE_LINEBREAK
				.matcher(trimmed).
				replaceAll("<return/>\n<return/>\n");
		return StringEscapeUtils.unescapeHtml(unescaped);
	}

	/**
	 * All anchors encountered since the first invocation of {@code parse}.
	 *
	 * @return a safe copy of all anchors
	 * @see Anchor
	 */
	public List<Anchor> anchors() {
		return new ArrayList<>(anchors);
	}

	/**
	 * An immutable representation of an anchor tag with hyperlink and text.
	 */
	public final class Anchor {
		public final String text;
		public final String href;

		public Anchor(final String text, final String href) {
			this.text = text;
			this.href = href;
		}
	}
}
