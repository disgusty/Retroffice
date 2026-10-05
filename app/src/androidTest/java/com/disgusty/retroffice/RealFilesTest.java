package com.disgusty.retroffice;

import android.test.InstrumentationTestCase;
import android.util.Log;

import java.io.File;

import com.disgusty.retroffice.doc.Doc;
import com.disgusty.retroffice.doc.DocLoader;
import com.disgusty.retroffice.doc.Images;
import com.disgusty.retroffice.doc.TextDoc;
import com.disgusty.retroffice.editor.SpanUtil;
import com.disgusty.retroffice.text.Spans;
import com.disgusty.retroffice.text.TextSegment;

/**
 * Opens, saves and verifies every document placed in the app's files/real folder (copied there
 * with adb for manual testing; the folder is empty in normal runs). Logs timings under tag "RealFiles".
 */
public class RealFilesTest extends InstrumentationTestCase {
    public void testRealFiles() throws Exception {
        File dir = new File(getInstrumentation().getTargetContext().getFilesDir(), "real");
        File[] files = dir.listFiles();
        if (files == null) return;
        Images images = new Images(getInstrumentation().getTargetContext().getResources().getDisplayMetrics().density, 1080);
        for (File f : files) {
            if (!f.isFile() || f.getName().startsWith("out-")) continue;
            long t0 = System.currentTimeMillis();
            Doc d = DocLoader.open(f, f.getName(), images);
            long t1 = System.currentTimeMillis();
            Doc.SaveJob job = d.prepareSave();
            File out = new File(dir, "out-" + f.getName());
            job.write(out);
            job.verify(out);
            long t2 = System.currentTimeMillis();
            Log.i("RealFiles", f.getName() + ": open " + (t1 - t0) + " ms, save+verify " + (t2 - t1) + " ms, chars "
                    + d.plainText().length());
            if (d instanceof TextDoc) {
                // Edit in the middle of the document: insert, split, bold, merge.
                java.util.ArrayList<TextSegment> segs = new java.util.ArrayList<TextSegment>();
                for (TextDoc.Block b : ((TextDoc) d).blocks) if (b.segment != null) segs.add(b.segment);
                TextSegment mid = segs.get(segs.size() / 2);
                android.text.SpannableStringBuilder t = mid.text;
                int nl = t.toString().indexOf('\n');
                int at = nl < 0 ? t.length() : nl;
                t.insert(at, " [RETROFFICE-EDIT]\nNew paragraph [RETROFFICE-NEW]");
                SpanUtil.setFormat(t, at + 1, at + 18, Spans.BOLD, true);
                int last = t.toString().lastIndexOf('\n');
                if (last > 0) t.delete(last, last + 1);
                long t3 = System.currentTimeMillis();
                Doc.SaveJob j2 = d.prepareSave();
                File out2 = new File(dir, "out-edit-" + f.getName());
                j2.write(out2);
                j2.verify(out2);
                Log.i("RealFiles", f.getName() + ": edited save+verify " + (System.currentTimeMillis() - t3) + " ms");
            }
            d.close();
        }
    }
}
