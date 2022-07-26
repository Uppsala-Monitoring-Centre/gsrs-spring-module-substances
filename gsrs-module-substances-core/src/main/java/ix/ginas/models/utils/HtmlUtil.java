package ix.ginas.models.utils;

import java.util.Set;
import org.jsoup.nodes.*;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * Utility class for working with HTML strings.
 *
 * Created by epuzanov on 7/25/22.
 */
public final class HtmlUtil {
    private static class TruncateVisitor implements NodeVisitor {
        private int maxLen = 0;
        private Element dst;
        private Element cur;
        private boolean stop = false;
        private static final Set<String> safetags = Set.of("b", "i", "small", "sub", "sup");

        public TruncateVisitor (Element dst, int maxLen) {
            this.maxLen = maxLen;
            this.dst = dst;
            this.cur = dst;
        }

        public void head(Node node, int depth) {
            if (depth > 0) {
                if (node instanceof Element) {
                    Element curElement = (Element) node;
                    if (safetags.contains(curElement.tagName())) {
                        cur = cur.appendElement(curElement.tagName());
                        String resHtml = dst.html();
                        if (resHtml.length() > maxLen) {
                            cur.remove();
                            throw new IllegalStateException();
                        }
                    }
                } else if (node instanceof TextNode) {
                    String parentTag = ((Element)node.parent()).tagName();
                    if (safetags.contains(parentTag) || (parentTag == "body" && depth == 1)) {
                        String curText = ((TextNode) node).getWholeText();
                        String resHtml = dst.html();
                        if (curText.length() + resHtml.length() > maxLen) {
                            cur.appendText(curText.substring(0, maxLen - resHtml.length()));
                            throw new IllegalStateException();
                        } else {
                            cur.appendText(curText);
                        }
                    }
                }
            }
        }

        public void tail(Node node, int depth) {
            if (depth > 0 && node instanceof Element && safetags.contains(((Element)node).tagName())) {
                cur = cur.parent();
            }
        }
    }

    public static String truncateString(String s, int len){
        Document srcDoc = Parser.parseBodyFragment(s, "");
        srcDoc.outputSettings().prettyPrint(false);

        Document dstDoc = Document.createShell(srcDoc.baseUri());
        dstDoc.outputSettings().prettyPrint(false);
        dstDoc.outputSettings().charset("UTF-8");
        Element dst = dstDoc.body();
        NodeVisitor v = new TruncateVisitor(dst, len - 3);

        try {
            NodeTraversor t = new NodeTraversor();
            t.traverse(v, srcDoc.body());
        } catch (IllegalStateException ex) {}

        return dst.html() + "...";
    }
}