# html-to-step-markup

This project demonstrates a small, naive utility for converting HTML to a
proprietary plain-text format, which we have internally taken to calling
*STEP-markup*, for use in Stibo Systems' STEP Trailblazer [[step]] ("STEP").
We needed this for a fire-and-forget migration of some 50,000 products from
our existing product management system to STEP and STEP did not provide a
conversion mechanism; and for that same reason we released this utility.

We *believe* to have been integrating with versions of STEP ranging from 8.0
through 8.2.

The repository was extracted from our internal proprietary distribution that
powers https://www.bilka.dk and https://www.foetex.dk; anonymized; and
Maven-ified. There is nothing technically elegant about the solution because
we didn't need it long-term and kept it out of critical execution paths; focus
was purely on the output quality.

## Usage

This is not a library so we don't provide a ready-made package to integrate;
if you find yourself needing this you have to integrate it manually. There is
a good chance you will want to make changes to the transformation anyway.

Create a new `StepMarkup` instance and invoke `parse` on a string of HTML:

```java
StepMarkup parser = new StepMarkup();
String markup = parser.parse(html);
```

## Transformation overview

Here we provide a overview of the transformations performed. For details,
refer to the extensive test suite.

STEP-markup is a UTF-8 plain-text format with a few simple SGML-like
primitives. We generally map HTML tags to STEP-markup according to the
following table:

| HTML       | STEP           |
| ---------- | -------------- |
| `<b>`      | `<bold>`       |
| `<strong>` | `<bold>`       |
| `<i>`      | `<italic>`     |
| `<em>`     | `<italic>`     |
| `<u>`      | `<underlined>` |
| `<sub>`    | `<sub>`        |
| `<sup>`    | `<sup>`        |
| `<h*>`     | `<H*>`         |
| `<li>`     | `<bullet>`     |
| `<ul>`     | `<bulletlist>` |
| `<ol>`     | `<bulletlist>` |
| `<br>`     | `\n`           |

### Limitations

> Speculation: observation suggests STEP-markup is transformed into its
> destination output using string replacement, not text parsing. Limitations
> exhibit symptoms of STEP-markup not being a tree structure.

STEP-markup offers considerable opportunity for customization and the degree
of standardization of *our* configuration is unknown. As an example, though,
we do know that `<underlined>` in the table above is one such customization.

STEP-markup is case sensitive. This is most significant for header tags, which
are arbitrarily uppercased.

The closing `<bullet>` tag must be followed by a line break, because reasons;
that is, `</bullet>\n`.

STEP-markup does not support tables or hyperlinks. We rewrite tables into a
simple, legible plain-text format:

    <bold>th1</bold> | <bold>th2</bold>
    td 1.1 | td 1.2
    td 2.1 | td 2.2

We *rewrite* external hyperlinks to include the schemeless URL after the link
text. We *remove* internal hyperlinks except for one edge-case that we
*rewrite* to a specific domain.

STEP-markup only has implicit paragraphs and STEP's HTML generator naively
transforms `\n` into `<br>`. In light of this we fake paragraphs by separating
consecutive `<p>` tags with 2 `\n` characters and limiting the number of
successive `\n` characters to 2, and we avoid excessive "paragraphing" around
block elements by removing redundant `\n` characters. This has the unfortunate
downside of looking "wrong" *inside* STEP but that's all right because STEP's
presentation, by definition, is not canonical to begin with. As an example of
this, we transform

```html
foo

<H1>bar</H1>
<p>baz
<br>
<br>
<br>
</p><p>boz</p>
```

into

```
foo
<H1>bar</H1>
baz

boz
```

*not*

```
foo

<H1>bar</H1>

baz



boz
```


[step]: https://www.stibosystems.com/solution/step-overview
