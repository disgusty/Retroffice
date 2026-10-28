package com.disgusty.retroffice.legacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.disgusty.retroffice.xml.XDoc;
import com.disgusty.retroffice.xml.XNode;
import com.disgusty.retroffice.xml.Xml;
import com.disgusty.retroffice.xml.XmlParser;

/** Converts real legacy files (made by LibreOffice from the test documents) and checks the result. */
public class LegacyConverterTest {
    private static final File OUT = new File("build/legacy-test-out");

    private static File resource(String name) throws Exception {
        InputStream in = LegacyConverterTest.class.getResourceAsStream("/legacy/" + name);
        assertNotNull("missing test file " + name, in);
        OUT.mkdirs();
        File f = new File(OUT, name);
        FileOutputStream out = new FileOutputStream(f);
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        out.close();
        in.close();
        return f;
    }

    private static XDoc content(File odf) throws Exception {
        ZipFile z = new ZipFile(odf);
        try {
            ZipEntry e = z.getEntry("content.xml");
            InputStream in = z.getInputStream(e);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) b.write(buf, 0, r);
            return XmlParser.parse(b.toByteArray());
        } finally {
            z.close();
        }
    }

    private static LegacyConverter.Result convert(String name, String ext) throws Exception {
        File src = resource(name);
        File dir = new File(OUT, name + "-out");
        dir.mkdirs();
        LegacyConverter.Result r = LegacyConverter.convert(src, dir);
        assertNotNull(r);
        assertEquals(ext, r.extension);
        // Keep a copy under a readable name for checking with an office suite.
        File copy = new File(OUT, name + "." + ext);
        copy.delete();
        assertTrue(r.file.renameTo(copy));
        return new LegacyConverter.Result(copy, r.extension, r.sourceFormat);
    }

    /** Text of an ODF element with encoded spaces (text:s) and tabs expanded. */
    private static String odfText(XNode n) {
        StringBuilder sb = new StringBuilder();
        appendOdf(n, sb);
        return sb.toString();
    }

    private static void appendOdf(XNode n, StringBuilder sb) {
        for (XNode c : n.children) {
            if (c.type == XNode.TEXT) sb.append(c.text);
            else if (c.is(Xml.TEXT, "s")) {
                String cnt = c.attr(Xml.TEXT, "c");
                for (int i = 0, k = cnt == null ? 1 : Integer.parseInt(cnt); i < k; i++) sb.append(' ');
            } else if (c.is(Xml.TEXT, "tab")) sb.append('\t');
            else if (c.isElement()) appendOdf(c, sb);
        }
    }

    /** True if some span containing {@code word} uses a style with bold text. */
    private static boolean boldSpan(XDoc doc, String word) {
        ArrayList<XNode> spans = new ArrayList<XNode>();
        doc.root.findAll(Xml.TEXT, "span", spans);
        ArrayList<XNode> styles = new ArrayList<XNode>();
        doc.root.findAll(Xml.STYLE, "style", styles);
        for (XNode s : spans) {
            if (!s.textContent().contains(word)) continue;
            String name = s.attr(Xml.TEXT, "style-name");
            for (XNode st : styles) {
                if (!name.equals(st.attr(Xml.STYLE, "name"))) continue;
                XNode tp = st.child(Xml.STYLE, "text-properties");
                if (tp != null && "bold".equals(tp.attr(Xml.FO, "font-weight"))) return true;
            }
        }
        return false;
    }

    private static void assertTextDocument(String name) throws Exception {
        LegacyConverter.Result r = convert(name, "odt");
        XDoc doc = content(r.file);
        String text = odfText(doc.root);
        for (String s : new String[]{"Заголовок документа", "жирным", "курсивом", "Первый пункт", "Третий пункт",
                "Альфа", "Последний абзац документа.", "example.org"}) {
            assertTrue(name + " lacks \"" + s + "\": " + text, text.contains(s));
        }
        assertTrue(name + ": bold lost", boldSpan(doc, "жирным"));
    }

    @Test
    public void doc() throws Exception {
        assertTextDocument("text.doc");
    }

    @Test
    public void rtf() throws Exception {
        assertTextDocument("text.rtf");
    }

    @Test
    public void rtfCyrillicCodePage() throws Exception {
        // Typical Russian RTF: bytes in cp1251 written as hex escapes, plus Unicode escapes with a fallback character.
        String rtf = "{\\rtf1\\ansi\\ansicpg1251\\deff0{\\fonttbl{\\f0\\fcharset204 Arial;}}"
                + "\\f0 \\'cf\\'f0\\'e8\\'e2\\'e5\\'f2 {\\b \\'ec\\'e8\\'f0}\\par \\u1046?\\u1091? \\'b8\\par}";
        File f = new File(OUT, "cp1251.rtf");
        OUT.mkdirs();
        FileOutputStream o = new FileOutputStream(f);
        o.write(rtf.getBytes("US-ASCII"));
        o.close();
        File dir = new File(OUT, "cp1251-out");
        dir.mkdirs();
        XDoc doc = content(LegacyConverter.convert(f, dir).file);
        String text = odfText(doc.root);
        assertTrue(text, text.contains("Привет мир"));
        assertTrue(text, text.contains("Жу ё"));
        assertTrue(boldSpan(doc, "мир"));
    }

    @Test
    public void xls() throws Exception {
        LegacyConverter.Result r = convert("sheet.xls", "ods");
        XDoc doc = content(r.file);
        ArrayList<XNode> tables = new ArrayList<XNode>();
        doc.root.findAll(Xml.TABLE, "table", tables);
        assertEquals(2, tables.size());
        assertEquals("Данные", tables.get(0).attr(Xml.TABLE, "name"));
        assertEquals("Второй", tables.get(1).attr(Xml.TABLE, "name"));
        String text = odfText(doc.root);
        for (String s : new String[]{"Товар", "Яблоки", "Груши", "Итого", "10.5", "31.5", "71.5", "Ссылка", "143", "TRUE"}) {
            assertTrue("xls lacks " + s + ": " + text, text.contains(s));
        }
        // Numbers stay numbers (not text).
        ArrayList<XNode> cells = new ArrayList<XNode>();
        doc.root.findAll(Xml.TABLE, "table-cell", cells);
        boolean found = false;
        for (XNode c : cells) {
            if ("71.5".equals(c.attr(Xml.OFFICE, "value"))) found = "float".equals(c.attr(Xml.OFFICE, "value-type"));
        }
        assertTrue("71.5 should be a number", found);
    }

    @Test
    public void ppt() throws Exception {
        LegacyConverter.Result r = convert("slides.ppt", "odp");
        XDoc doc = content(r.file);
        ArrayList<XNode> pages = new ArrayList<XNode>();
        doc.root.findAll(Xml.DRAW, "page", pages);
        assertEquals(2, pages.size());
        String p1 = odfText(pages.get(0)), p2 = odfText(pages.get(1));
        assertTrue(p1, p1.contains("Первый слайд"));
        assertTrue(p1, p1.contains("Пункт один") && p1.contains("Пункт два"));
        assertTrue(p2, p2.contains("Второй слайд"));
        assertTrue(p2, p2.contains("Фигура с текстом"));
    }

    @Test
    public void excelDates() {
        assertEquals("2024-03-15", OdfBuilder.isoDate(45366));
        assertEquals("1900-01-01", OdfBuilder.isoDate(1));
        assertEquals("1900-03-01", OdfBuilder.isoDate(61));
        assertEquals("2000-01-01T12:00:00", OdfBuilder.isoDate(36526.5));
    }
}
