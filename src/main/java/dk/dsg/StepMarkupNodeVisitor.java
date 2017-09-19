package dk.dsg;

import java.util.Locale;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;
import org.jsoup.select.NodeVisitor;

@SuppressWarnings("PMD.GodClass")
/* package */ final class StepMarkupNodeVisitor implements NodeVisitor {
	private static final String BOLD_START = "<bold>";
	private static final String BOLD_END = "</bold>";

	private static final String ITALIC_START = "<italic>";
	private static final String ITALIC_END = "</italic>";

	private static final String UNDERLINED_START = "<underlined>";
	private static final String UNDERLINED_END = "</underlined>";

	// Semantic significance of linefeed unknown.
	private static final String UNORDERED_LIST_START = "<bulletlist>\n";
	private static final String UNORDERED_LIST_END = "</bulletlist>";

	private static final String UNORDERED_LIST_ITEM_START = "<bullet>";
	// Semantically-significant linefeed.
	private static final String UNORDERED_LIST_ITEM_END = "</bullet>\n";

	private static final String SUPERSCRIPT_START = "<sup>";
	private static final String SUPERSCRIPT_END = "</sup>";

	private static final String SUBSCRIPT_START = "<sub>";
	private static final String SUBSCRIPT_END = "</sub>";

	private static final String TABULATOR = " | ";

	private static final Tag HTML_P = Tag.valueOf("p");
	private static final Tag HTML_BR = Tag.valueOf("br");

	/**
	 * Pattern for the http/s scheme and, because lazy, the optional www
	 * subdomain, for uniformly substituting with {@code www.}.
	 */
	private static final Pattern LINK_SCHEME =
			Pattern.compile("^https?://(?:www.)?");

	// Yes, it is possible to abuse buffers. No, I'm not abusing it.
	@SuppressWarnings("PMD.AvoidStringBufferField")
	private final StringBuilder buf;

	/**
	 * A "naive list" is raw text written to emulate an unordered list using
	 * dashes and UTF-8 bullet characters.
	 */
	private boolean inNaiveList;

	/**
	 * An "ugly href" is an href pointing to an internal link other than a
	 * buying guide, for instance a document asset. We want to strip those from
	 * the output. In general such links are less valuable than buying guides
	 * and they can't be made to look "nice", for some definition thereof.
	 */
	private boolean inUglyHref;

	/* package */ StepMarkupNodeVisitor(final StringBuilder buf) {
		this.buf = buf;
	}

	// Inherently tricky problem space. The canonical solution builds another
	// tree; it's more expressive but costs more in implementation and runtime
	// and this code is fire-and-forget.
	@SuppressWarnings({
			"PMD.CyclomaticComplexity",
			"PMD.SwitchStmtsShouldHaveDefault",
			 // No, "".startsWith('x') is not equivalent to "".charAt(0) == 'x'.
			"PMD.SimplifyStartsWith",
	})
	@Override
	public void head(final Node node, final int depth) {
		if (node instanceof Element) {
			final Element element = ((Element) node);

			switch (element.tagName()) {
				case "b":
					// Fall through.
				case "strong":
					correctInlineSpacingStart();
					if (element.hasText()) {
						buf.append(BOLD_START);
					}
					break;
				case "i":
					// Fall through.
				case "em":
					correctInlineSpacingStart();
					if (element.hasText()) {
						buf.append(ITALIC_START);
					}
					break;
				case "u":
					correctInlineSpacingStart();
					if (element.hasText()) {
						buf.append(UNDERLINED_START);
					}
					break;
				case "br":
					// Disregard manual linebreaks between list items.
					if (!inNaiveList) {
						linebreak();
					}
					break;
				case "ol":
					// Fall through.
				case "ul":
					buf.append(UNORDERED_LIST_START);
					break;
				case "li":
					buf.append(UNORDERED_LIST_ITEM_START);
					break;
				case "sup":
					buf.append(SUPERSCRIPT_START);
					break;
				case "sub":
					buf.append(SUBSCRIPT_START);
					break;
				case "h1":
				case "h2":
				case "h3":
				case "h4":
				case "h5":
				case "h6":
					// STEP markup is case sensitive and headers are uppercase.
					buf.append('<')
							.append(element.tagName().toUpperCase(Locale.ROOT))
							.append('>');
					break;
				case "blockquote":
					linebreak();
					break;
				case "caption":
					buf.append(ITALIC_START);
					break;
				case "th":
					buf.append(BOLD_START);
					break;
				case "address":
					linebreak();
					break;
				case "a":
					final String href = element.attr("href");
					inUglyHref = href.startsWith("/") && !href.startsWith("/gd/");
					break;
			}
		} else if (node instanceof TextNode && !inUglyHref) {
			final String text = aggressiveTrim(((TextNode) node).text());
			if (text.isEmpty()) {
				return;
			}

			final char firstChar = text.charAt(0);
			// This looks like a naive bullet item.
			// Replace it with a real bullet item in a bullet list.
			// &bull; character. Ancient Checkstyle barfs on the glyph.
			if (firstChar == '\u2022' || firstChar == '-') {
				naiveListItem(text);
			} else {
				naiveListEnd(node);

				if (firstChar != '.') {
					correctInlineSpacingEnd(node);
				}

				buf.append(text);
			}
		}
	}

	@SuppressWarnings({
			"PMD.MissingBreakInSwitch",
			"PMD.SwitchStmtsShouldHaveDefault",
			"PMD.CyclomaticComplexity",
			"PMD.ExcessiveMethodLength",
	})
	@Override
	public void tail(final Node node, final int depth) {
		if (node instanceof Element) {
			final Element element = ((Element) node);
			final String text = aggressiveTrim(element.text());

			final Element nextElement = element.parent() != null
					? element.nextElementSibling()
					: null;

			switch (element.tagName()) {
				case "a":
					href(element);
					break;
				case "b":
					// Fall through.
				case "strong":
					if (!text.isEmpty()) {
						buf.append(BOLD_END);
						if (text.endsWith(":")) {
							buf.append(' ');
						}
					}
					break;
				case "i":
					// Fall through.
				case "em":
					if (!text.isEmpty()) {
						buf.append(ITALIC_END);
					}
					break;
				case "u":
					if (!text.isEmpty()) {
						buf.append(UNDERLINED_END);
						if (text.endsWith(":")) {
							buf.append(' ');
						}
					}
					break;
				case "p":
					// Don't break if <p> is the final element
					if (nextElement != null) {
						// Double-break between <p>s.
						if (HTML_P == nextElement.tag()) {
							// Don't break if the next <p> is empty.
							// Element::hasText is too conservative.
							if (text.isEmpty()) {
								break;
							}
							linebreak();
						}
						linebreak();
					}
					// If the naive list is the last non-whitespace content
					// we won't see more content to trigger list closure.
					// In that case, force closure.
					naiveListEnd(element);
					break;
				case "li":
					buf.append(UNORDERED_LIST_ITEM_END);
					break;
				case "ol":
					// Fall through.
				case "ul":
					endList(element);
					break;
				case "sup":
					buf.append(SUPERSCRIPT_END);
					break;
				case "sub":
					buf.append(SUBSCRIPT_END);
					break;
				case "h1":
				case "h2":
				case "h3":
				case "h4":
				case "h5":
				case "h6":
					// Semantic significance of linefeed unknown.
					buf.append("</")
							.append(element.tagName().toUpperCase(Locale.ROOT))
							.append(">\n");
					break;
				case "blockquote":
					linebreak();
					linebreak();
					break;
				case "caption":
					buf.append(ITALIC_END);
				case "tr":
					linebreak();
					break;
				case "th":
					buf.append(BOLD_END);
					// Fall through.
				case "td":
					if (nextElement != null) {
						buf.append(TABULATOR);
					}
					break;
				case "address":
					linebreak();
					linebreak();
					break;
			}
		}
	}

	private static String aggressiveTrim(final String input) {
		// https://stackoverflow.com/questions/28295504/how-to-trim-no-break-space-in-java
		return input.replaceAll("(^\\h*)|(\\h*$)", "");
	}

	/**
	 * Ensures spacing before inline elements.
	 *
	 * By aggressively trimming all text nodes we lose a lot of bad spacing
	 * but also a few desirable spaces; for instance, we get
	 * {@code <p>foo<bold><em>bar</em></bold>baz</p>}.
	 *
	 * This method partially corrects this problem, yielding
	 * {@code <p>foo <bold><em>bar</em></bold>baz</p>}. Note that
	 * {@code <bold><em>} were not split, as desired, but {@code baz} was
	 * not properly spaced away.
	 *
	 * @see #aggressiveTrim(String)
	 * @see #correctInlineSpacingEnd(Node)
	 */
	private void correctInlineSpacingStart() {
		final int len = buf.length();
		if (len > 1) {
			final char lastChar = buf.charAt(len - 1);
			if (lastChar != ' ' && lastChar != '\n' && lastChar != '>') {
				buf.append(' ');
			}
		}
	}

	/**
	 * Ensures spacing after inline elements.
	 *
	 * This is the second part of the solution to the inline element spacing
	 * problem. Building on the previous example this method gives us
	 * {@code <p>foo <bold><em>bar</em></bold> baz</p>}. Note that
	 * {@code baz} is properly spaced away.
	 *
	 * <p>If the previous tag was a {@code <br/>} the method does nothing.
	 *
	 * @param node  the current node (that comes after the inline tag)
	 * @see #correctInlineSpacingStart()
	 */
	// Tag has guaranteed == semantics.
	@SuppressWarnings("PMD.CompareObjectsWithEquals")
	private void correctInlineSpacingEnd(final Node node) {
		final Node prev = node.previousSibling();
		if (prev instanceof Element) {
			final Tag previousTag = ((Element) prev).tag();
			if (previousTag.isInline() && HTML_BR != previousTag) {
				buf.append(' ');
			}
		}
	}

	/**
	 * Rewrites the anchor href attribute.
	 *
	 * <ul>
	 * <li>Values starting with {@code /gd/} are buying guides. We would like to
	 * preserve such links but in a friendly manner. The best we can do is to
	 * make them copy-/paste-friendly, but since we can't (won't) tell where
	 * the guide comes from we assume it to be Bilka.dk becaues of its size.
	 * Consequently, we prefix {@code www.bilka.dk}.
	 * <li>Any remaining internal links and link texts we toss because
	 * "they look ugly."
	 * <li>We strip the http/s scheme because "it looks ugly" and many external
	 * links to Bilka.dk use legacy http scheme anyway.
	 * </ul>
	 *
	 * @param element  the anchor element
	 */
	private void href(final Element element) {
		final String href = element.attr("href").trim();
		if (href.startsWith("/gd/")) {
			// Assume all buying guides are on Bilka.dk and make them copyable.
			// Bilka.dk is bigger and føtex doesn't use guides much if at all.
			buf.append(" www.bilka.dk").append(href);
		} else if (!inUglyHref) {
			buf.append(' ')
					.append(LINK_SCHEME.matcher(href).replaceFirst("www."));
		}
	}

	/**
	 * Transforms a manually created unordered list item into a bullet list.
	 * The leading bullet or dash and subsequent whitespace is stripped.
	 * After the last list item, call {@link #naiveListEnd}.
	 *
	 * @param item  the non-null list item
	 * @see #naiveListStart()
	 * @see #naiveListEnd(Node)
	 */
	private void naiveListItem(final String item) {
		naiveListStart();
		buf.append(item.replaceFirst("[•-]\\s*", UNORDERED_LIST_ITEM_START));
		buf.append(UNORDERED_LIST_ITEM_END);
	}

	private void naiveListStart() {
		if (!inNaiveList) {
			buf.append(UNORDERED_LIST_START);
			inNaiveList = true;
		}
	}

	private void naiveListEnd(final Node node) {
		if (inNaiveList) {
			endList(node);
			inNaiveList = false;
		}
	}

	private void endList(final Node node) {
		buf.append(UNORDERED_LIST_END);
		// Assumption: if more content follows, ensure linebreak.
		if (node.parent() != null && node.nextSibling() != null) {
			linebreak();
		}
	}

	private void linebreak() {
		// This trailing linefeed is purely for legibility. STEP currently
		// strips most linefeeds, although a problem with the STEP Web UI's
		// Enter key may cause that to change.
		buf.append("<return/>\n");
	}
}
