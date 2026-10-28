package com.disgusty.retroffice;

import android.test.InstrumentationTestCase;
import android.text.SpannableStringBuilder;

import java.io.File;
import java.io.InputStream;

import com.disgusty.retroffice.doc.Doc;
import com.disgusty.retroffice.doc.DocLoader;
import com.disgusty.retroffice.doc.FileUtil;
import com.disgusty.retroffice.doc.Images;
import com.disgusty.retroffice.doc.TextDoc;
import com.disgusty.retroffice.editor.SpanUtil;
import com.disgusty.retroffice.text.Spans;

/** Opens converted legacy documents the way the app does, edits and saves them (outputs in files/testout). */
public class ConvertedSaveTest extends InstrumentationTestCase {
    private File dir(String name) {
        File d = new File(getInstrumentation().getTargetContext().getFilesDir(), "testout/" + name);
        FileUtil.deleteTree(d);
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private Doc open(String asset) throws Exception {
        File d = dir(asset);
        File src = new File(d, "source." + FileUtil.extension(asset));
        InputStream in = getInstrumentation().getContext().getAssets().open(asset);
        try {
            FileUtil.copy(in, src);
        } finally {
            in.close();
        }
        return DocLoader.open(src, asset, Images.NONE);
    }

    private void save(Doc d, String outName) throws Exception {
        Doc.SaveJob job = d.prepareSave();
        File out = new File(getInstrumentation().getTargetContext().getFilesDir(), "testout/" + outName);
        //noinspection ResultOfMethodCallIgnored
        out.delete();
        job.write(out);
        job.verify(out);
    }

    private void editAndSave(String asset) throws Exception {
        Doc d = open(asset);
        assertNotNull(d.convertedFrom);
        save(d, "plain-" + asset + ".odt");
        TextDoc td = (TextDoc) d;
        SpannableStringBuilder t = td.blocks.get(0).segment.text;
        int nl = t.toString().indexOf('\n');
        t.insert(nl < 0 ? t.length() : nl, " ПРАВКА");
        int w = t.toString().indexOf("Обычный");
        if (w >= 0) SpanUtil.setFormat(t, w, w + 7, Spans.ITALIC, true);
        t.append("\nНовый абзац в конце");
        save(d, "edit-" + asset + ".odt");
    }

    public void testDoc() throws Exception {
        editAndSave("text.doc");
    }

    public void testRtf() throws Exception {
        editAndSave("text.rtf");
    }

    /** Real legacy documents pushed to files/real-legacy (manual check; empty in normal runs). */
    public void testRealFiles() throws Exception {
        File dir = new File(getInstrumentation().getTargetContext().getFilesDir(), "real-legacy");
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isFile()) continue;
            Doc d = DocLoader.open(f, f.getName(), Images.NONE);
            TextDoc td = (TextDoc) d;
            SpannableStringBuilder t = null;
            for (TextDoc.Block b : td.blocks) if (b.segment != null) t = b.segment.text;
            t.insert(0, "ПРАВКА ");
            SpanUtil.setFormat(t, 0, 6, Spans.BOLD, true);
            t.append("\nКонец правки");
            save(d, "edit-real-" + f.getName() + ".odt");
            d.close();
            // Second pass: touch every paragraph so every rebuild path is exercised.
            Doc d2 = DocLoader.open(f, f.getName(), Images.NONE);
            for (TextDoc.Block b : ((TextDoc) d2).blocks) {
                if (b.segment == null) continue;
                SpannableStringBuilder s = b.segment.text;
                for (int p = s.length(); p >= 0; p--) {
                    if (p == s.length() || s.charAt(p) == '\n') s.insert(p, "~");
                }
            }
            save(d2, "all-real-" + f.getName() + ".odt");
            d2.close();
        }
    }
}
