package org.yinwang.pysonar.demos;

import org.jetbrains.annotations.NotNull;
import org.yinwang.pysonar.Indexer;
import org.yinwang.pysonar.Progress;
import org.yinwang.pysonar.Util;

import java.io.File;
import java.util.List;

/**
 * Simple proof-of-concept demo app for the indexer.  Generates a static-html
 * cross-referenced view of the code in a file or directory, using the index to
 * create links and outlines.  <p>
 *
 * The demo not attempt to show general cross references (declarations and uses
 * of a symbol) from the index, nor does it display the inferred type
 * information or generated error/warning diagnostics.  It could be made to do
 * these things, as well as be made more configurable and generally useful, with
 * additional effort.<p>
 *
 * Run it from jython source tree root dir with; e.g., to index <code>/usr/lib/python2.4/email</code>
 * <pre>
 * ant jar &amp;&amp; java -classpath ./dist/jython.jar org.yinwang.pysonar.demos.HtmlDemo /usr/lib/python2.4 /usr/lib/python2.4/email
 * </pre>
 *
 * Fully indexing the Python standard library may require a more complete build to pick up all the dependencies:
 * <pre>
 * rm -rf ./html/ &amp;&amp; ant clean &amp;&amp; ant jar &amp;&amp; ant jar-complete &amp;&amp; java -classpath ./dist/jython.jar org.yinwang.pysonar.demos.HtmlDemo /usr/lib/python2.4 /usr/lib/python2.4
 * </pre>
 *
 * You can alternately use Jython's version of the Python library.
 * The following command will index the whole thing:
 * <pre>
 * ant jar-complete &amp;&amp; java -classpath ./dist/jython.jar org.yinwang.pysonar.demos.HtmlDemo ./CPythonLib ./CPythonLib
 * </pre>
 */
public class HtmlDemo {

    private static final File OUTPUT_DIR =
            new File(new File("html").getAbsolutePath());

    private static final String CSS =
            "a {text-decoration: none; color: #2e8b57}\n" +
                    "table, th, td { border: 1px solid lightgrey; padding: 5px; corner: rounded; }\n" +
                    ".builtin {color: #5b4eaf;}\n" +
                    ".comment, .block-comment {color: #aaaaaa; font-style: italic;}\n" +
                    ".constant {color: #888888;}\n" +
                    ".decorator {color: #778899;}\n" +
                    ".doc-string {color: #005000;}\n" +
                    ".error {border-bottom: 1px solid red;}\n" +
                    ".field-name {color: #2e8b57;}\n" +
                    ".function {color: #880000;}\n" +
                    ".identifier {color: #8b7765;}\n" +
                    ".info {border-bottom: 1px dotted RoyalBlue;}\n" +
                    ".keyword {color: #0000cd;}\n" +
                    ".lineno {color: #aaaaaa;}\n" +
                    ".number {color: #483d8b;}\n" +
                    ".parameter {color: #2e8b57;}\n" +
                    ".string {color: #4169e1;}\n" +
                    ".type-name {color: #4682b4;}\n" +
                    ".warning {border-bottom: 1px dotted orange;}\n";

    private  static final String JS =
            "<script language=\"JavaScript\" type=\"text/javascript\">\n" +
                    "var highlighted = new Array();\n" +
                    "function highlight()\n" +
                    "{\n" +
                    "    // clear existing highlights\n" +
                    "    for (var i = 0; i < highlighted.length; i++) {\n" +
                    "        var elm = document.getElementById(highlighted[i]);\n" +
                    "        if (elm != null) {\n" +
                    "            elm.style.backgroundColor = 'white';\n" +
                    "        }\n" +
                    "    }\n" +
                    "    highlighted = new Array();\n" +
                    "    for (var i = 0; i < arguments.length; i++) {\n" +
                    "        var elm = document.getElementById(arguments[i]);\n" +
                    "        if (elm != null) {\n" +
                    "            elm.style.backgroundColor='gold';\n" +
                    "        }\n" +
                    "        highlighted.push(arguments[i]);\n" +
                    "    }\n" +
                    "} </script>\n";


    private Indexer indexer;
    private String rootPath;
    private Linker linker;

    private void makeOutputDir() {
        if (!OUTPUT_DIR.exists()) {
            OUTPUT_DIR.mkdirs();
            Util.msg("Created directory: " + OUTPUT_DIR.getAbsolutePath());
        }
    }

    private void start(@NotNull File stdlib, @NotNull File fileOrDir) throws Exception {
        long start = System.currentTimeMillis();

        File rootDir = fileOrDir.isFile() ? fileOrDir.getParentFile() : fileOrDir;
        try {
            rootPath = rootDir.getCanonicalPath();
        } catch (Exception e) {
            Util.die("Doh");
        }

        indexer = new Indexer();

        try {
            indexer.addPath(stdlib.getCanonicalPath());
        } catch (Exception e) {
            Util.die("Doh");
        }

        Util.msg("Building index");

        indexer.loadFileRecursive(fileOrDir.getCanonicalPath());

        indexer.finish();

        Util.msg(indexer.getStatusReport());

        long end = System.currentTimeMillis();
        Util.msg("Finished indexing in: " + Util.timeString(end - start));

        start = System.currentTimeMillis();
        generateHtml();
        end = System.currentTimeMillis();
        Util.msg("Finished generating HTML in: " + Util.timeString(end - start));
    }

    private void generateHtml() {
        Util.msg("\nGenerating HTML");
        makeOutputDir();

        linker = new Linker(rootPath, OUTPUT_DIR);
        linker.findLinks(indexer);

        int rootLength = rootPath.length();
        Progress progress = new Progress(100, 100);

        for (String path : indexer.getLoadedFiles()) {
            if (path.startsWith(rootPath)) {
                progress.tick();
                File destFile = Util.joinPath(OUTPUT_DIR, path.substring(rootLength));
                destFile.getParentFile().mkdirs();
                String destPath = destFile.getAbsolutePath() + ".html";
                String html = markup(path);
                try {
                    Util.writeFile(destPath, html);
                } catch (Exception e) {
                    Util.msg("Failed to write: " + html);
                }
            }
        }
        progress.end();
        Util.msg("Wrote " + indexer.getLoadedFiles().size() + " files to " + OUTPUT_DIR);
    }

    @NotNull
    private String markup(String path) {
        String source;

        try {
            source = Util.readFile(path);
        } catch (Exception e) {
            Util.die("Failed to read file: " + path);
            return "";
        }

        List<StyleRun> styles = new Styler(indexer, linker).addStyles(path, source);
        styles.addAll(linker.getStyles(path));

        String styledSource = new StyleApplier(path, source, styles).apply();
        String outline = new HtmlOutline(indexer).generate(path);

        StringBuilder sb = new StringBuilder();
        sb.append("<html><head title=\"").append(path).append("\">")
                .append("<style type='text/css'>\n").append(CSS).append("</style>\n")
                .append(JS)
                .append("</head>\n<body>\n")
                .append("<table width=100% border='1px solid gray'><tr><td valign='top'>")
                .append(outline)
                .append("</td><td>")
                .append("<pre>").append(addLineNumbers(styledSource)).append("</pre>")
                .append("</td></tr></table></body></html>");
        return sb.toString();
    }

    @NotNull
    private String addLineNumbers(@NotNull String source) {
        StringBuilder result = new StringBuilder((int)(source.length() * 1.2));
        int count = 1;
        for (String line : source.split("\n")) {
            result.append("<span class='lineno'>");
            result.append(count++);
            result.append("</span> ");
            result.append(line);
            result.append("\n");
        }
        return result.toString();
    }


    private static void usage() {
        Util.msg("Usage:  java org.yinwang.pysonar.HtmlDemo <python-stdlib> <file-or-dir>");
        Util.msg("  first arg specifies the root of the python standard library");
        Util.msg("  second arg specifies file or directory for which to generate the index");
        Util.msg("Example that generates an index for just the email libraries:");
        Util.msg(" java org.yinwang.pysonar.HtmlDemo ./CPythonLib ./CPythonLib/email");
        System.exit(0);
    }

    @NotNull
    private static File checkFile(String path) {
        File f = new File(path);
        if (!f.canRead()) {
            Util.die("Path not found or not readable: " + path);
        }
        return f;
    }

    public static void main(@NotNull String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            usage();
        }

        File fileOrDir = checkFile(args[1]);
        File stdlib = checkFile(args[0]);

        if (!stdlib.isDirectory()) {
            Util.die("Not a directory: " + stdlib);
        }

        new HtmlDemo().start(stdlib, fileOrDir);
    }
}