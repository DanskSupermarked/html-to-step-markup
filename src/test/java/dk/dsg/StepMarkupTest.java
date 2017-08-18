package dk.dsg;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StepMarkupTest {

	private final String corpus;

	public StepMarkupTest(final String corpus) {
		this.corpus = corpus;
	}

	@Test
	public void parse() throws IOException {
		final String input = Resource.input.read(corpus);
		final String expected = Resource.expected.read(corpus);

		Assert.assertEquals(expected, StepMarkup.parse(input));
	}

	@Parameterized.Parameters(name = "{index}: {0}")
	public static List<String> data() throws URISyntaxException {
		final String[] inputs = new File(StepMarkupTest.class.getResource(
				"/step-markup-translation/input").toURI()).list();
		assert inputs != null;
		Arrays.sort(inputs);

		final List<String> data = new ArrayList<>(inputs.length);
		for (final String input : inputs) {
			data.add(input.substring(0, input.lastIndexOf('.')));
		}

		return data;
	}

	private enum Resource {
		input("html"),
		expected("txt");

		private final String ext;

		Resource(final String ext) {
			this.ext = ext;
		}

		private String read(final String corpus)
				throws IOException {
			final String name = String.format(
					"/step-markup-translation/%s/%s.%s",
					this.name(),
					corpus,
					ext);
			try (final InputStream input =
					StepMarkupTest.class.getResourceAsStream(name);
					final Reader isr =
							new InputStreamReader(input, StandardCharsets.UTF_8);
					final BufferedReader reader = new BufferedReader(isr)) {
				return reader.lines().collect(Collectors.joining("\n"));
			}
		}
	}
}
