package com.disgusty.retroffice;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.text.SpannableStringBuilder;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.disgusty.retroffice.doc.Doc;
import com.disgusty.retroffice.doc.DocLoader;
import com.disgusty.retroffice.doc.FileUtil;
import com.disgusty.retroffice.doc.Images;
import com.disgusty.retroffice.doc.PlainDoc;
import com.disgusty.retroffice.doc.Templates;
import com.disgusty.retroffice.doc.TextDoc;
import com.disgusty.retroffice.editor.SpanUtil;
import com.disgusty.retroffice.sheet.Cell;
import com.disgusty.retroffice.sheet.SheetDoc;
import com.disgusty.retroffice.slides.SlidesDoc;
import com.disgusty.retroffice.text.Spans;
import com.disgusty.retroffice.text.TextSegment;

/**
 * Open/edit/save round trips on real Android text classes. Outputs land in files/testout for
 * checking with an independent office suite on the host.
 */
public class RoundTripTest extends InstrumentationTestCase {
    private File out;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context target = getInstrumentation().getTargetContext();
        out = new File(target.getFilesDir(), "testout");
        //noinspection ResultOfMethodCallIgnored
        out.mkdirs();
    }

    private File asset(String name) throws Exception {
        InputStream in = getInstrumentation().getContext().getAssets().open(name);
        File f = new File(out, "src-" + name);
        try {
            FileUtil.copy(in, f);
        } finally {
            in.close();
        }
        return f;
    }

    private Doc open(String name) throws Exception {
        return DocLoader.open(asset(name), name, Images.NONE);
    }

    private File save(Doc d, String outName) throws Exception {
        Doc.SaveJob job = d.prepareSave();
        File f = new File(out, outName);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        job.write(f);
        job.verify(f);
        d.markSaved(job);
        assertFalse("saved state must not be modified", d.isModified());
        return f;
    }

    static HashMap<String, byte[]> entries(File f) throws Exception {
        HashMap<String, byte[]> m = new HashMap<String, byte[]>();
        ZipInputStream z = new ZipInputStream(new java.io.FileInputStream(f));
        try {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) m.put(e.getName(), FileUtil.readAll(z, Long.MAX_VALUE));
        } finally {
            z.close();
        }
        return m;
    }

    /** Every entry must be byte-identical except the listed ones. */
    private void assertSameExcept(File a, File b, String... changed) throws Exception {
        HashMap<String, byte[]> ea = entries(a), eb = entries(b);
        assertEquals("entry names", new java.util.TreeSet<String>(ea.keySet()), new java.util.TreeSet<String>(eb.keySet()));
        java.util.List<String> ch = java.util.Arrays.asList(changed);
        for (String k : ea.keySet()) {
            if (ch.contains(k)) continue;
            assertTrue("entry changed: " + k, java.util.Arrays.equals(ea.get(k), eb.get(k)));
        }
    }

    // ------------------------------------------------------------------ unchanged saves

    public void testUnchangedSavesAreIdentical() throws Exception {
        String[] names = {"text.odt", "text.docx", "sheet.ods", "sheet.xlsx", "slides.odp", "slides.pptx"};
        for (String n : names) {
            Doc d = open(n);
            assertFalse(n + " modified after open", d.isModified());
            File o = save(d, "same-" + n);
            assertSameExcept(new File(out, "src-" + n), o);
            d.close();
        }
        for (String n : new String[]{"cp1251.csv", "notes.txt"}) {
            Doc d = open(n);
            File o = save(d, "same-" + n);
            assertTrue(n + " bytes", java.util.Arrays.equals(FileUtil.readAll(new File(out, "src-" + n), Long.MAX_VALUE),
                    FileUtil.readAll(o, Long.MAX_VALUE)));
        }
    }

    // ------------------------------------------------------------------ text documents

    private void editText(String name, String part) throws Exception {
        TextDoc d = (TextDoc) open(name);
        TextSegment first = null;
        for (TextDoc.Block b : d.blocks) if (b.segment != null) {
            first = b.segment;
            break;
        }
        assertNotNull(first);
        SpannableStringBuilder t = first.text;
        String before = t.toString();
        assertTrue("heading read: " + before, before.contains("Заголовок документа"));
        // 1) append to the first line
        int nl = t.toString().indexOf('\n');
        t.insert(nl, " — изменено");
        // 2) new paragraph after the first line
        nl = t.toString().indexOf('\n');
        t.insert(nl, "\nНовый абзац & <теги> \"кавычки\"  два пробела");
        // 3) bold the word "Обычный"
        int w = t.toString().indexOf("Обычный");
        assertTrue(w >= 0);
        SpanUtil.setFormat(t, w, w + "Обычный".length(), Spans.BOLD, true);
        // 4) un-italic "курсивом"
        int k = t.toString().indexOf("курсивом");
        assertTrue(SpanUtil.hasFormat(t, k, k + 8, Spans.ITALIC));
        SpanUtil.setFormat(t, k, k + 8, Spans.ITALIC, false);
        // 5) merge two paragraphs (delete a newline) in the last segment
        TextSegment last = null;
        for (TextDoc.Block b : d.blocks) if (b.segment != null) last = b.segment;
        String lt = last.text.toString();
        int pos = lt.lastIndexOf('\n');
        if (pos > 0) last.text.delete(pos, pos + 1);
        last.text.append(" Конец.");
        assertTrue(d.isModified());
        File o = save(d, "edit-" + name);
        // Only the main part changes.
        assertSameExcept(new File(out, "src-" + name), o, part);
        Doc again = DocLoader.open(o, name, Images.NONE);
        String txt = again.plainText();
        assertTrue(txt, txt.contains("Заголовок документа — изменено"));
        assertTrue(txt, txt.contains("Новый абзац & <теги> \"кавычки\"  два пробела"));
        assertTrue(txt, txt.contains("Конец."));
        // Formatting survived.
        TextDoc td = (TextDoc) again;
        SpannableStringBuilder t2 = null;
        for (TextDoc.Block b : td.blocks) if (b.segment != null) {
            t2 = b.segment.text;
            break;
        }
        int w2 = t2.toString().indexOf("Обычный");
        assertTrue("bold kept", SpanUtil.hasFormat(t2, w2, w2 + 7, Spans.BOLD));
        int k2 = t2.toString().indexOf("курсивом");
        assertFalse("italic removed", SpanUtil.hasFormat(t2, k2, k2 + 8, Spans.ITALIC));
        int z = t2.toString().indexOf("жирным");
        assertTrue("original bold kept", SpanUtil.hasFormat(t2, z, z + 6, Spans.BOLD));
        // Saving again from the edited state must still be consistent.
        t2.append(" Второе сохранение.");
        save(again, "edit2-" + name);
    }

    public void testEditOdt() throws Exception {
        editText("text.odt", "content.xml");
    }

    public void testEditDocx() throws Exception {
        editText("text.docx", "word/document.xml");
    }

    // ------------------------------------------------------------------ spreadsheets

    private void editSheet(String name) throws Exception {
        SheetDoc d = (SheetDoc) open(name);
        assertEquals(2, d.sheets.size());
        Cell total = d.sheets.get(0).get(3, 3);
        assertEquals(71.5, total.num, 1e-9);
        assertNull(d.setInput(0, 1, 1, "12"));               // B2: 10.5 -> 12
        assertEquals(36.0, d.sheets.get(0).get(1, 3).num, 1e-9);  // D2 recalculated
        assertEquals(76.0, d.sheets.get(0).get(3, 3).num, 1e-9);  // D4 = SUM
        assertEquals(152.0, d.sheets.get(1).get(0, 1).num, 1e-9); // cross-sheet
        assertNull(d.setInput(0, 0, 5, "=SUM(B2:B3)*2"));     // F1 new formula
        assertEquals(64.0, d.sheets.get(0).get(0, 5).num, 1e-9);
        assertNull(d.setInput(0, 30, 7, "Новая строка"));     // far outside the used range
        assertNull(d.setInput(0, 2, 0, ""));                  // clear A3
        assertNull(d.setInput(0, 5, 2, "0,5"));               // decimal comma inside a repeated empty row
        assertEquals(SheetDoc.ERR_COVERED, d.setInput(0, 3, 1, "x"));
        File o = save(d, "edit-" + name);
        SheetDoc again = (SheetDoc) DocLoader.open(o, name, Images.NONE);
        assertEquals(12.0, again.sheets.get(0).get(1, 1).num, 1e-9);
        assertEquals(76.0, again.sheets.get(0).get(3, 3).num, 1e-9);
        assertEquals("Новая строка", again.sheets.get(0).get(30, 7).str);
        assertEquals(0.5, again.sheets.get(0).get(5, 2).num, 1e-9);
        Cell a3 = again.sheets.get(0).get(2, 0);
        assertTrue(a3 == null || a3.type == Cell.EMPTY);
        assertEquals("Итого", again.sheets.get(0).get(3, 0).str);
        assertTrue(again.sheets.get(0).get(3, 1).covered);
        // Save again with another edit on top.
        assertNull(again.setInput(1, 3, 0, "=B1+1"));
        save(again, "edit2-" + name);
    }

    public void testEditOds() throws Exception {
        editSheet("sheet.ods");
    }

    public void testEditXlsx() throws Exception {
        editSheet("sheet.xlsx");
    }

    public void testCsv() throws Exception {
        SheetDoc d = (SheetDoc) open("cp1251.csv");
        assertEquals("Пётр; мл.", d.sheets.get(0).get(2, 0).str);
        assertNull(d.setInput(0, 1, 1, "031"));
        assertNull(d.setInput(0, 4, 0, "Новая; запись \"с кавычками\""));
        File o = save(d, "edit-cp1251.csv");
        String s = new String(FileUtil.readAll(o, Long.MAX_VALUE), "windows-1251");
        assertTrue(s, s.contains("Иван;031;Москва\r\n"));
        assertTrue(s, s.contains("\"Новая; запись \"\"с кавычками\"\"\""));
        // A character cp1251 can't hold switches the file to UTF-8 instead of losing it.
        assertNull(d.setInput(0, 1, 2, "東京"));
        File o2 = save(d, "edit2-cp1251.csv");
        String u = new String(FileUtil.readAll(o2, Long.MAX_VALUE), "UTF-8");
        assertTrue(u, u.contains("東京"));
    }

    public void testPlain() throws Exception {
        PlainDoc d = (PlainDoc) open("notes.txt");
        d.text.append("\nДобавлено");
        File o = save(d, "edit-notes.txt");
        byte[] b = FileUtil.readAll(o, Long.MAX_VALUE);
        assertEquals((byte) 0xEF, b[0]);
        String s = new String(b, 3, b.length - 3, "UTF-8");
        assertEquals("Первая строка\r\nВторая строка  с двумя пробелами\r\n\r\nКонец\r\nДобавлено", s);
    }

    // ------------------------------------------------------------------ presentations

    private void editSlides(String name, String slidePart) throws Exception {
        SlidesDoc d = (SlidesDoc) open(name);
        assertEquals(2, d.slides.size());
        SlidesDoc.Shape title = null;
        for (SlidesDoc.Shape s : d.slides.get(0).shapes) if (s.text != null && s.text.plainText().contains("Первый")) title = s;
        assertNotNull("title found", title);
        title.text.text.append(" (ред.)");
        File o = save(d, "edit-" + name);
        if (slidePart != null) assertSameExcept(new File(out, "src-" + name), o, slidePart);
        SlidesDoc again = (SlidesDoc) DocLoader.open(o, name, Images.NONE);
        assertTrue(again.plainText(), again.plainText().contains("Первый слайд (ред.)"));
        assertTrue(again.plainText(), again.plainText().contains("Пункт два"));
    }

    public void testEditOdp() throws Exception {
        editSlides("slides.odp", "content.xml");
    }

    public void testEditPptx() throws Exception {
        editSlides("slides.pptx", "ppt/slides/slide1.xml");
    }

    // ------------------------------------------------------------------ new documents

    public void testNewDocuments() throws Exception {
        for (int kind : new int[]{Templates.TEXT, Templates.SHEET, Templates.SLIDES}) {
            String name = "new." + Templates.extension(kind);
            File f = new File(out, "src-" + name);
            Templates.write(kind, f);
            Doc d = DocLoader.open(f, name, Images.NONE);
            if (d instanceof TextDoc) {
                SpannableStringBuilder t = ((TextDoc) d).blocks.get(0).segment.text;
                t.append("Привет, мир!\nВторая строка");
                SpanUtil.setFormat(t, 0, 6, Spans.BOLD, true);
            } else if (d instanceof SheetDoc) {
                assertNull(((SheetDoc) d).setInput(0, 0, 0, "Итог"));
                assertNull(((SheetDoc) d).setInput(0, 0, 1, "42"));
                assertNull(((SheetDoc) d).setInput(0, 1, 1, "=B1*2"));
            } else {
                ((SlidesDoc) d).slides.get(0).shapes.get(0).text.text.append("Заголовок презентации");
            }
            File saved = save(d, "new-" + name);
            if (kind == Templates.SHEET) {
                // Formula cells must bind the "of:" syntax prefix, or office suites report an error.
                com.disgusty.retroffice.xml.XDoc x = com.disgusty.retroffice.xml.XmlParser.parse(entries(saved).get("content.xml"));
                java.util.ArrayList<com.disgusty.retroffice.xml.XNode> cells = new java.util.ArrayList<com.disgusty.retroffice.xml.XNode>();
                x.root.findAll(com.disgusty.retroffice.xml.Xml.TABLE, "table-cell", cells);
                boolean found = false;
                for (com.disgusty.retroffice.xml.XNode c : cells) {
                    String formula = c.attr(com.disgusty.retroffice.xml.Xml.TABLE, "formula");
                    if (formula == null) continue;
                    found = true;
                    assertEquals(com.disgusty.retroffice.xml.Xml.OF, c.resolvePrefix(formula.substring(0, formula.indexOf(':'))));
                }
                assertTrue(found);
            }
        }
    }
}
