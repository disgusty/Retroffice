package com.disgusty.retroffice;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

import com.disgusty.retroffice.doc.FileUtil;
import com.disgusty.retroffice.ui.Icons;
import com.disgusty.retroffice.ui.RootLayout;
import com.disgusty.retroffice.ui.Skin;

/**
 * File picker for Android versions without the system document picker (before 4.4): browse the
 * external storage to open a document, or choose a folder and name for a new one.
 */
public class FileBrowserActivity extends Activity {
    public static final String EXTRA_SAVE = "save", EXTRA_NAME = "name";
    private static final HashSet<String> EXTS = new HashSet<String>(Arrays.asList("odt", "ods", "odp", "docx",
            "xlsx", "pptx", "csv", "tsv", "txt", "text", "md", "log", "xml", "json", "ini", "fodt"));

    private Skin skin;
    private RootLayout root;
    private File dir;
    private boolean saveMode;
    private EditText nameField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int style = getSharedPreferences("settings", MODE_PRIVATE).getInt("style", Skin.defaultStyle());
        skin = Skin.create(this, style);
        setTheme(skin.nativeTheme());
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = new RootLayout(this);
        root.setBackgroundColor(skin.windowBg);
        setContentView(root);
        skin.decorate(this);
        saveMode = getIntent().getBooleanExtra(EXTRA_SAVE, false);
        File storage = Environment.getExternalStorageDirectory();
        dir = storage != null && storage.canRead() ? storage : new File("/");
        String last = getSharedPreferences("settings", MODE_PRIVATE).getString("lastDir", null);
        if (last != null && new File(last).isDirectory() && new File(last).canRead()) dir = new File(last);
        nameField = new EditText(this);
        skin.styleField(nameField);
        nameField.setSingleLine(true);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (name != null) nameField.setText(name);
        show();
    }

    private void show() {
        root.removeAllViews();
        root.setBarColors(skin.statusBarColor(), skin.windowBg);
        ArrayList<Skin.Action> actions = new ArrayList<Skin.Action>();
        if (dir.getParentFile() != null) {
            actions.add(new Skin.Action(Icons.UP, getString(R.string.parent_folder), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dir = dir.getParentFile();
                    show();
                }
            }, null));
        }
        Skin.Action back = new Skin.Action(Icons.BACK, getString(R.string.back), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        }, null);
        root.addView(skin.appBar(getString(saveMode ? R.string.browse_save_title : R.string.browse_title), back, actions, false));
        TextView path = skin.text(dir.getAbsolutePath(), 12, skin.textSecondary, skin.regular());
        path.setPadding(skin.dp(16), skin.dp(6), skin.dp(16), skin.dp(6));
        root.addView(path);
        root.addView(skin.dividerView());

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        File[] files = dir.listFiles();
        if (files == null) {
            TextView t = skin.body(getString(R.string.storage_unavailable));
            t.setPadding(skin.dp(16), skin.dp(16), skin.dp(16), skin.dp(16));
            list.addView(t);
        } else {
            ArrayList<File> items = new ArrayList<File>();
            for (File f : files) {
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory() || (!saveMode && EXTS.contains(FileUtil.extension(f.getName())))) items.add(f);
                else if (saveMode && f.isFile()) items.add(f);
            }
            Collections.sort(items, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            for (final File f : items) {
                String icon = f.isDirectory() ? Icons.FOLDER : MainActivity.iconFor(FileUtil.extension(f.getName()));
                list.addView(skin.listItem(icon, f.getName(), null, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (f.isDirectory()) {
                            dir = f;
                            show();
                        } else if (saveMode) {
                            nameField.setText(f.getName());
                        } else {
                            remember();
                            finishWith(f);
                        }
                    }
                }, null));
                list.addView(skin.dividerView());
            }
        }
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (saveMode) {
            root.addView(skin.dividerView());
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(skin.dp(12), skin.dp(8), skin.dp(12), skin.dp(8));
            bar.setBackgroundColor(skin.surface);
            if (nameField.getParent() != null) ((ViewGroup) nameField.getParent()).removeView(nameField);
            nameField.setHint(R.string.file_name);
            bar.addView(nameField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            View saveBtn = skin.primaryButton(getString(R.string.save), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmSave();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, saveBtn.getLayoutParams().height);
            lp.leftMargin = skin.dp(8);
            bar.addView(saveBtn, lp);
            root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void confirmSave() {
        String name = nameField.getText().toString().trim();
        if (name.length() == 0 || name.indexOf('/') >= 0) return;
        String wanted = getIntent().getStringExtra(EXTRA_NAME);
        String ext = FileUtil.extension(wanted);
        // Keep the document's format: the extension decides how other apps open it.
        if (ext.length() > 0 && !FileUtil.extension(name).equals(ext)) name = name + "." + ext;
        final File f = new File(dir, name);
        if (f.exists()) {
            Skin.DialogSpec s = new Skin.DialogSpec();
            s.title = getString(R.string.file_exists_title);
            s.message = getString(R.string.file_exists_message, name);
            s.positive = getString(R.string.replace);
            s.negative = getString(R.string.cancel);
            s.onPositive = new Runnable() {
                @Override
                public void run() {
                    remember();
                    finishWith(f);
                }
            };
            skin.dialog(s).show();
            return;
        }
        remember();
        finishWith(f);
    }

    private void remember() {
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("lastDir", dir.getAbsolutePath()).commit();
    }

    private void finishWith(File f) {
        Intent data = new Intent();
        data.setData(Uri.fromFile(f));
        setResult(RESULT_OK, data);
        finish();
    }
}
