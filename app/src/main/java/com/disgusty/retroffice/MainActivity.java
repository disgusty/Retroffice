package com.disgusty.retroffice;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.Editable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.disgusty.retroffice.compat.Compat;
import com.disgusty.retroffice.doc.Doc;
import com.disgusty.retroffice.doc.DocLoader;
import com.disgusty.retroffice.doc.FileUtil;
import com.disgusty.retroffice.doc.Images;
import com.disgusty.retroffice.doc.PlainDoc;
import com.disgusty.retroffice.doc.Templates;
import com.disgusty.retroffice.editor.DocEditText;
import com.disgusty.retroffice.editor.DocEditor;
import com.disgusty.retroffice.editor.EditHistory;
import com.disgusty.retroffice.editor.SheetEditorView;
import com.disgusty.retroffice.editor.SlidesView;
import com.disgusty.retroffice.editor.SpanUtil;
import com.disgusty.retroffice.editor.TextDocView;
import com.disgusty.retroffice.io.DocumentIO;
import com.disgusty.retroffice.io.HistoryStore;
import com.disgusty.retroffice.io.RecoveryStore;
import com.disgusty.retroffice.sheet.CsvDoc;
import com.disgusty.retroffice.sheet.SheetDoc;
import com.disgusty.retroffice.slides.SlidesDoc;
import com.disgusty.retroffice.text.Spans;
import com.disgusty.retroffice.ui.Icons;
import com.disgusty.retroffice.ui.RootLayout;
import com.disgusty.retroffice.ui.Skin;

public class MainActivity extends Activity implements DocEditor.Host {
    static final int REQ_OPEN = 1, REQ_CREATE = 2, REQ_BROWSE_OPEN = 3, REQ_BROWSE_SAVE = 4;
    static final int TAB_OFFLINE = 0, TAB_HISTORY = 1, TAB_ONLINE = 2;
    static final String EXTRA_TAB = "tab", EXTRA_STYLE_CHANGED = "styleChanged";

    static final String[] MIMES = {
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/csv", "text/comma-separated-values", "text/tab-separated-values", "text/plain",
            "application/octet-stream", "application/zip",
    };

    private final Handler ui = new Handler();
    private Skin skin;
    private int style;
    private RootLayout root;
    private int homeTab = TAB_OFFLINE;
    private Dialog busy;

    // Open document session
    private Doc doc;
    private Uri target;
    private File workDir;
    private boolean isNew, dirty, forceDirty;
    private DocEditor editor;
    private EditHistory history;
    private View undoButton, redoButton, moreButton;
    private LinearLayout formatBar;
    private Runnable afterSave;
    private boolean saving;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        style = prefs.getInt("style", Skin.defaultStyle());
        skin = Skin.create(this, style);
        setTheme(skin.nativeTheme());
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = new RootLayout(this);
        root.setBackgroundColor(skin.windowBg);
        setContentView(root);
        skin.decorate(this);

        Intent in = getIntent();
        homeTab = in.getIntExtra(EXTRA_TAB, TAB_OFFLINE);
        if (in.getBooleanExtra(EXTRA_STYLE_CHANGED, false)) {
            Toast.makeText(this, getString(R.string.style_now, styleName(style)), Toast.LENGTH_SHORT).show();
        }
        DocumentIO.cleanWork(this, null);
        Uri incoming = incomingUri(in);
        showHome();
        if (incoming != null) {
            openUri(incoming);
        } else {
            offerRecovery();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        final Uri u = incomingUri(intent);
        if (u == null) return;
        if (doc != null) {
            requestClose(new Runnable() {
                @Override
                public void run() {
                    openUri(u);
                }
            });
        } else {
            openUri(u);
        }
    }

    private Uri incomingUri(Intent in) {
        String a = in.getAction();
        if (Intent.ACTION_VIEW.equals(a) || Intent.ACTION_EDIT.equals(a)) return in.getData();
        if (Intent.ACTION_SEND.equals(a)) {
            Parcelable p = in.getParcelableExtra(Intent.EXTRA_STREAM);
            if (p instanceof Uri) return (Uri) p;
        }
        return null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (doc != null && (dirty || forceDirty) && !saving) writeRecoveryDraft();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (busy != null) return true;
            if (doc != null) {
                requestClose(null);
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU && doc != null && moreButton != null) {
            showMoreMenu(moreButton);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------------ home screen

    private List<Integer> homeTabs() {
        ArrayList<Integer> tabs = new ArrayList<Integer>();
        tabs.add(TAB_OFFLINE);
        if (!HistoryStore.isEmpty(this)) tabs.add(TAB_HISTORY);
        tabs.add(TAB_ONLINE);
        return tabs;
    }

    private void showHome() {
        root.removeAllViews();
        root.setBarColors(skin.statusBarColor(), skin.navBg);
        final List<Integer> tabs = homeTabs();
        if (!tabs.contains(homeTab)) homeTab = TAB_OFFLINE;
        boolean hasHistory = tabs.contains(TAB_HISTORY);

        ArrayList<Skin.Action> actions = new ArrayList<Skin.Action>();
        if (hasHistory) {
            actions.add(new Skin.Action(Icons.CLEAR_HISTORY, getString(R.string.clear_history), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmClearHistory();
                }
            }, new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    clearHistory();
                    toast(R.string.history_cleared);
                    return true;
                }
            }));
        }
        actions.add(new Skin.Action(Icons.BRUSH, getString(R.string.change_style), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleStyle();
            }
        }, null));
        root.addView(skin.appBar(getString(R.string.app_name), null, actions, true));

        View content;
        if (homeTab == TAB_HISTORY) content = historyContent();
        else if (homeTab == TAB_ONLINE) content = onlineContent();
        else content = offlineContent();
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        ArrayList<Skin.NavItem> items = new ArrayList<Skin.NavItem>();
        for (int t : tabs) {
            if (t == TAB_OFFLINE) items.add(new Skin.NavItem(Icons.HOME, getString(R.string.tab_offline)));
            else if (t == TAB_HISTORY) items.add(new Skin.NavItem(Icons.SAVE, getString(R.string.tab_history)));
            else items.add(new Skin.NavItem(Icons.CLOUD, getString(R.string.tab_online)));
        }
        root.addView(skin.bottomNav(items, tabs.indexOf(homeTab), new Skin.NavListener() {
            @Override
            public void onSelect(int index) {
                homeTab = tabs.get(index);
                showHome();
            }
        }));
    }

    private View offlineContent() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(skin.windowBg);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = skin.dp(24);
        col.setPadding(p, p + skin.dp(24), p, p);
        TextView sub = skin.text(getString(R.string.subtitle), 14, skin.textSecondary, skin.regular());
        sub.setGravity(Gravity.CENTER);
        col.addView(sub, matchWrap());
        space(col, 32);
        col.addView(skin.primaryButton(getString(R.string.open_document), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDocument();
            }
        }));
        space(col, 4);
        TextView formats = skin.text(getString(R.string.opens_formats), 12, skin.textSecondary, skin.regular());
        formats.setGravity(Gravity.CENTER);
        col.addView(formats, matchWrap());
        space(col, 16);
        col.addView(skin.secondaryButton(getString(R.string.new_doc), newDocListener(Templates.TEXT)));
        space(col, 8);
        col.addView(skin.secondaryButton(getString(R.string.new_sheet), newDocListener(Templates.SHEET)));
        space(col, 8);
        col.addView(skin.secondaryButton(getString(R.string.new_slides), newDocListener(Templates.SLIDES)));
        sv.addView(col);
        // Keep the column a comfortable width on tablets.
        int screen = getResources().getDisplayMetrics().widthPixels;
        if (screen > skin.dp(560)) {
            int side = (screen - skin.dp(520)) / 2;
            col.setPadding(side, col.getPaddingTop(), side, col.getPaddingBottom());
        }
        return sv;
    }

    private View.OnClickListener newDocListener(final int kind) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newDocument(kind);
            }
        };
    }

    private View historyContent() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(skin.windowBg);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        List<HistoryStore.Entry> entries = HistoryStore.load(this);
        for (int i = 0; i < entries.size(); i++) {
            final HistoryStore.Entry e = entries.get(i);
            String ext = FileUtil.extension(e.name);
            String title = FileUtil.baseName(e.name);
            String subtitle = (ext.length() > 0 ? ext.toUpperCase(java.util.Locale.US) + "  ·  " : "")
                    + getString(R.string.last_opened, df.format(new Date(e.time)));
            list.addView(skin.listItem(iconFor(ext), title, subtitle, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openFromHistory(e);
                }
            }, null));
            if (i < entries.size() - 1) list.addView(skin.dividerView());
        }
        sv.addView(list);
        return sv;
    }

    private View onlineContent() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(skin.windowBg);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = skin.dp(24);
        col.setPadding(p, p + skin.dp(24), p, p);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(Icons.make(Icons.CLOUD, skin.textSecondary, skin.dp(48)));
        col.addView(icon, new LinearLayout.LayoutParams(skin.dp(64), skin.dp(64)));
        space(col, 12);
        TextView t = skin.text(getString(R.string.online_title), 20, skin.textPrimary, skin.medium());
        t.setGravity(Gravity.CENTER);
        col.addView(t, matchWrap());
        space(col, 12);
        TextView m = skin.text(getString(R.string.online_message), 14, skin.textSecondary, skin.regular());
        m.setGravity(Gravity.CENTER);
        col.addView(m, matchWrap());
        sv.addView(col);
        return sv;
    }

    static String iconFor(String ext) {
        if (ext.equals("ods") || ext.equals("xlsx") || ext.equals("csv") || ext.equals("tsv") || ext.equals("ots")) return Icons.SHEET;
        if (ext.equals("odp") || ext.equals("pptx") || ext.equals("otp")) return Icons.SLIDES;
        return Icons.DOC;
    }

    private void space(LinearLayout l, int dp) {
        View v = new View(this);
        l.addView(v, new LinearLayout.LayoutParams(1, skin.dp(dp)));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private String styleName(int s) {
        switch (s) {
            case Skin.CLASSIC: return getString(R.string.style_classic);
            case Skin.HOLO: return getString(R.string.style_holo);
            case Skin.MATERIAL: return getString(R.string.style_material);
            default: return getString(R.string.style_material3);
        }
    }

    /** Classic -> Holo -> Material -> Material 3 -> Classic. */
    private void cycleStyle() {
        int next = (style + 1) % 4;
        getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("style", next).commit();
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(EXTRA_TAB, homeTab);
        i.putExtra(EXTRA_STYLE_CHANGED, true);
        finish();
        startActivity(i);
    }

    private void confirmClearHistory() {
        Skin.DialogSpec s = new Skin.DialogSpec();
        s.title = getString(R.string.clear_history_title);
        s.message = getString(R.string.clear_history_message);
        s.positive = getString(R.string.clear);
        s.negative = getString(R.string.cancel);
        s.destructive = true;
        s.onPositive = new Runnable() {
            @Override
            public void run() {
                clearHistory();
            }
        };
        skin.dialog(s).show();
    }

    /** Forgets the list only; the files stay where they are. */
    private void clearHistory() {
        for (HistoryStore.Entry e : HistoryStore.load(this)) {
            Uri u = Uri.parse(e.uri);
            if ("content".equals(u.getScheme())) Compat.releaseUri(this, u);
        }
        HistoryStore.clear(this);
        if (homeTab == TAB_HISTORY) homeTab = TAB_OFFLINE;
        showHome();
    }

    // ------------------------------------------------------------------ opening

    private void pickDocument() {
        if (Compat.hasSaf()) {
            try {
                startActivityForResult(Compat.openDocumentIntent(MIMES), REQ_OPEN);
                return;
            } catch (ActivityNotFoundException ignored) {
                // fall back to the built-in browser
            }
        }
        Intent i = new Intent(this, FileBrowserActivity.class);
        i.putExtra(FileBrowserActivity.EXTRA_SAVE, false);
        startActivityForResult(i, REQ_BROWSE_OPEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == REQ_CREATE || requestCode == REQ_BROWSE_SAVE) afterSave = null;
            return;
        }
        Uri uri = data.getData();
        switch (requestCode) {
            case REQ_OPEN:
                Compat.persistUri(this, uri, data.getFlags());
                openUri(uri);
                break;
            case REQ_CREATE:
                Compat.persistUri(this, uri, data.getFlags() | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                saveTo(uri);
                break;
            case REQ_BROWSE_OPEN:
                openUri(uri);
                break;
            case REQ_BROWSE_SAVE:
                saveTo(uri);
                break;
        }
    }

    private Images images() {
        return new Images(getResources().getDisplayMetrics().density, getResources().getDisplayMetrics().widthPixels);
    }

    private void openFromHistory(final HistoryStore.Entry e) {
        openUri(Uri.parse(e.uri), e);
    }

    private void openUri(Uri uri) {
        openUri(uri, null);
    }

    private void openUri(final Uri uri, final HistoryStore.Entry fromHistory) {
        showBusy(R.string.opening);
        final Images img = images();
        new Thread(new Runnable() {
            @Override
            public void run() {
                File wd = null;
                try {
                    String name = DocumentIO.displayName(MainActivity.this, uri);
                    if (fromHistory != null && (name == null || name.equals("document"))) name = fromHistory.name;
                    wd = DocumentIO.newWorkDir(MainActivity.this);
                    String ext = FileUtil.extension(name);
                    File local = new File(wd, "source" + (ext.length() > 0 ? "." + ext : ""));
                    DocumentIO.copyIn(MainActivity.this, uri, local);
                    final Doc d = DocLoader.open(local, name, img);
                    final File fwd = wd;
                    final String fname = name;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            hideBusy();
                            HistoryStore.add(MainActivity.this, uri.toString(), fname);
                            showEditor(d, uri, fwd, false, false);
                        }
                    });
                } catch (final Throwable t) {
                    if (wd != null) FileUtil.deleteTree(wd);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            hideBusy();
                            openFailed(t, fromHistory);
                        }
                    });
                }
            }
        }).start();
    }

    private void openFailed(Throwable t, final HistoryStore.Entry fromHistory) {
        String msg;
        if (t instanceof DocLoader.OpenException) {
            DocLoader.OpenException oe = (DocLoader.OpenException) t;
            switch (oe.reason) {
                case DocLoader.OpenException.PROTECTED: msg = getString(R.string.err_protected); break;
                case DocLoader.OpenException.LEGACY: msg = getString(R.string.err_legacy); break;
                case DocLoader.OpenException.UNSUPPORTED: msg = getString(R.string.err_unsupported); break;
                case DocLoader.OpenException.TOO_LARGE: msg = getString(R.string.err_too_large); break;
                default: msg = getString(R.string.err_damaged, String.valueOf(oe.getMessage()));
            }
        } else if (t instanceof SecurityException || t instanceof FileNotFoundException) {
            msg = getString(R.string.err_access);
        } else if (t instanceof OutOfMemoryError) {
            msg = getString(R.string.err_too_large);
        } else {
            String m = t.getMessage();
            if (m != null && m.contains("too large")) msg = getString(R.string.err_too_large);
            else msg = getString(R.string.err_damaged, String.valueOf(m));
        }
        Skin.DialogSpec s = new Skin.DialogSpec();
        s.title = getString(R.string.open_failed_title);
        s.message = msg;
        s.positive = getString(R.string.ok);
        if (fromHistory != null) {
            s.negative = getString(R.string.remove_from_history);
            s.onNegative = new Runnable() {
                @Override
                public void run() {
                    HistoryStore.remove(MainActivity.this, fromHistory.uri);
                    showHome();
                }
            };
        }
        skin.dialog(s).show();
    }

    private void newDocument(final int kind) {
        showBusy(R.string.opening);
        final Images img = images();
        final String name = getString(kind == Templates.TEXT ? R.string.untitled_doc
                : kind == Templates.SHEET ? R.string.untitled_sheet : R.string.untitled_slides) + "." + Templates.extension(kind);
        new Thread(new Runnable() {
            @Override
            public void run() {
                File wd = DocumentIO.newWorkDir(MainActivity.this);
                try {
                    File f = new File(wd, "source." + Templates.extension(kind));
                    Templates.write(kind, f);
                    final Doc d = DocLoader.open(f, name, img);
                    final File fwd = wd;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            hideBusy();
                            showEditor(d, null, fwd, true, false);
                        }
                    });
                } catch (final Throwable t) {
                    FileUtil.deleteTree(wd);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            hideBusy();
                            openFailed(t, null);
                        }
                    });
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------ editor

    private void showEditor(Doc d, Uri uri, File wd, boolean isNewDoc, boolean restored) {
        doc = d;
        target = uri;
        workDir = wd;
        isNew = isNewDoc;
        dirty = false;
        forceDirty = restored;
        history = new EditHistory(new EditHistory.Listener() {
            @Override
            public void onHistoryChanged() {
                updateUndoRedo();
            }

            @Override
            public void onEdited() {
                MainActivity.this.onEdited();
            }
        });
        buildEditorScreen();
    }

    private void buildEditorScreen() {
        root.removeAllViews();
        root.setBarColors(skin.statusBarColor(), skin.navBg);
        if (editor != null) editor.release();
        Skin.Action back = new Skin.Action(Icons.BACK, getString(R.string.back), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestClose(null);
            }
        }, null);
        List<Skin.Action> actions = Skin.actions(
                new Skin.Action(Icons.UNDO, getString(R.string.undo), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        undo();
                    }
                }, null),
                new Skin.Action(Icons.REDO, getString(R.string.redo), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        redo();
                    }
                }, null),
                new Skin.Action(Icons.SAVE, getString(R.string.save), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        save();
                    }
                }, null),
                new Skin.Action(Icons.MORE, getString(R.string.more), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showMoreMenu(v);
                    }
                }, null));
        View bar = skin.appBar(doc.displayName, back, actions, false);
        root.addView(bar);
        // The last three icon buttons of the bar: undo, redo, save, more (in that order).
        ArrayList<View> buttons = new ArrayList<View>();
        collectClickable(bar, buttons);
        int n = buttons.size();
        if (n >= 4) {
            undoButton = buttons.get(n - 4);
            redoButton = buttons.get(n - 3);
            moreButton = buttons.get(n - 1);
        }

        if (doc instanceof SheetDoc) editor = new SheetEditorView(this, skin, (SheetDoc) doc, this);
        else if (doc instanceof SlidesDoc) editor = new SlidesView(this, skin, (SlidesDoc) doc, this);
        else editor = new TextDocView(this, skin, doc, this);
        // Formatting bar sits right under the title bar, above the text (not above the keyboard).
        formatBar = null;
        if (editor.hasFormatting()) {
            formatBar = new LinearLayout(this);
            formatBar.setOrientation(LinearLayout.HORIZONTAL);
            formatBar.setGravity(Gravity.CENTER);
            formatBar.setBackgroundColor(skin.navBg);
            root.addView(formatBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(skin.dividerView());
            updateFormatBar();
        }
        root.addView(editor.view(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        updateUndoRedo();
    }

    private void collectClickable(View v, ArrayList<View> out) {
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectClickable(g.getChildAt(i), out);
        } else if (v.isClickable()) {
            out.add(v);
        }
    }

    private void updateFormatBar() {
        if (formatBar == null || editor == null) return;
        formatBar.removeAllViews();
        DocEditText f = editor.focusedField();
        Editable t = f == null ? null : f.getText();
        int a = f == null ? 0 : Math.min(f.getSelectionStart(), f.getSelectionEnd());
        int b = f == null ? 0 : Math.max(f.getSelectionStart(), f.getSelectionEnd());
        String[] icons = {Icons.BOLD, Icons.ITALIC, Icons.UNDERLINE, Icons.STRIKE};
        int[] labels = {R.string.bold, R.string.italic, R.string.underline, R.string.strikethrough};
        int[] bits = {Spans.BOLD, Spans.ITALIC, Spans.UNDERLINE, Spans.STRIKE};
        for (int i = 0; i < 4; i++) {
            final int bit = bits[i];
            boolean on = t != null && a >= 0 && SpanUtil.hasFormat(t, a, b, bit);
            formatBar.addView(skin.toggle(icons[i], getString(labels[i]), on, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleFormat(bit);
                }
            }));
        }
    }

    private void toggleFormat(int bit) {
        DocEditText f = editor == null ? null : editor.focusedField();
        if (f == null) return;
        Editable t = f.getText();
        int a = Math.min(f.getSelectionStart(), f.getSelectionEnd());
        int b = Math.max(f.getSelectionStart(), f.getSelectionEnd());
        if (a < 0) return;
        if (a == b) {
            int[] w = SpanUtil.wordAt(t, a);
            if (w == null) {
                toast(R.string.select_text_first);
                return;
            }
            a = w[0];
            b = w[1];
        }
        boolean on = !SpanUtil.hasFormat(t, a, b, bit);
        history.record(t, f.getSelectionStart());
        history.breakGroup();
        SpanUtil.setFormat(t, a, b, bit, on);
        onEdited();
        updateFormatBar();
    }

    private void updateUndoRedo() {
        if (undoButton != null) setEnabledLook(undoButton, history != null && history.canUndo());
        if (redoButton != null) setEnabledLook(redoButton, history != null && history.canRedo());
    }

    private static void setEnabledLook(View v, boolean enabled) {
        v.setEnabled(enabled);
        if (v instanceof ImageView) ((ImageView) v).setAlpha(enabled ? 255 : 90);
    }

    private void undo() {
        if (history == null) return;
        history.undo();
        updateFormatBar();
    }

    private void redo() {
        if (history == null) return;
        history.redo();
        updateFormatBar();
    }

    @Override
    public EditHistory history() {
        return history;
    }

    @Override
    public void onSelectionChanged(DocEditText field) {
        updateFormatBar();
    }

    @Override
    public void onEdited() {
        dirty = true;
    }

    @Override
    public void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private void showMoreMenu(View anchor) {
        ArrayList<Skin.MenuItem> items = new ArrayList<Skin.MenuItem>();
        items.add(new Skin.MenuItem(getString(R.string.save), true, new Runnable() {
            @Override
            public void run() {
                save();
            }
        }));
        items.add(new Skin.MenuItem(getString(R.string.doc_info), true, new Runnable() {
            @Override
            public void run() {
                showInfo();
            }
        }));
        items.add(new Skin.MenuItem(getString(R.string.share_text), true, new Runnable() {
            @Override
            public void run() {
                shareText();
            }
        }));
        items.add(new Skin.MenuItem(getString(R.string.close_document), true, new Runnable() {
            @Override
            public void run() {
                requestClose(null);
            }
        }));
        skin.popup(anchor, items);
    }

    private void showInfo() {
        if (editor != null) editor.commitPending();
        String text = doc.plainText();
        int words = 0;
        boolean in = false;
        for (int i = 0; i < text.length(); i++) {
            boolean ws = Character.isWhitespace(text.charAt(i));
            if (!ws && !in) words++;
            in = !ws;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.info_format, doc.formatName())).append('\n');
        if (doc.kind() == Doc.TEXT || doc.kind() == Doc.PLAIN || doc.kind() == Doc.SLIDES) {
            sb.append(getString(R.string.info_words, words)).append('\n');
            sb.append(getString(R.string.info_chars, text.replace("\n", "").length())).append('\n');
        }
        if (doc instanceof PlainDoc) sb.append(getString(R.string.info_encoding, ((PlainDoc) doc).charsetName())).append('\n');
        String loc = target == null ? getString(R.string.not_saved_yet)
                : ("file".equals(target.getScheme()) ? target.getPath() : DocumentIO.displayName(this, target));
        sb.append(getString(R.string.info_location, loc));
        Skin.DialogSpec s = new Skin.DialogSpec();
        s.title = doc.displayName;
        s.message = sb.toString();
        s.positive = getString(R.string.ok);
        skin.dialog(s).show();
    }

    private void shareText() {
        if (editor != null) editor.commitPending();
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, doc.displayName);
        i.putExtra(Intent.EXTRA_TEXT, doc.plainText());
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share_text)));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    // ------------------------------------------------------------------ closing

    private boolean hasUnsavedChanges() {
        if (forceDirty) return true;
        if (!dirty) return false;
        return doc.isModified();
    }

    private void requestClose(final Runnable then) {
        if (doc == null) {
            if (then != null) then.run();
            return;
        }
        if (editor != null) editor.commitPending();
        if (!hasUnsavedChanges()) {
            closeEditor();
            if (then != null) then.run();
            return;
        }
        Skin.DialogSpec s = new Skin.DialogSpec();
        s.title = getString(R.string.unsaved_title);
        s.message = getString(R.string.unsaved_message, doc.displayName);
        s.positive = getString(R.string.save);
        s.negative = getString(R.string.dont_save);
        s.onPositive = new Runnable() {
            @Override
            public void run() {
                afterSave = new Runnable() {
                    @Override
                    public void run() {
                        closeEditor();
                        if (then != null) then.run();
                    }
                };
                save();
            }
        };
        s.onNegative = new Runnable() {
            @Override
            public void run() {
                RecoveryStore.clear(MainActivity.this);
                closeEditor();
                if (then != null) then.run();
            }
        };
        skin.dialog(s).show();
    }

    private void closeEditor() {
        if (editor != null) editor.release();
        editor = null;
        if (doc != null) doc.close();
        doc = null;
        if (workDir != null) FileUtil.deleteTree(workDir);
        workDir = null;
        target = null;
        history = null;
        undoButton = redoButton = moreButton = null;
        formatBar = null;
        showHome();
    }

    // ------------------------------------------------------------------ saving

    private void save() {
        if (doc == null || saving) return;
        if (editor != null) editor.commitPending();
        if (target != null && !isNew && !hasUnsavedChanges()) {
            toast(R.string.nothing_to_save);
            runAfterSave();
            return;
        }
        if (target == null) {
            chooseSaveTarget();
            return;
        }
        final Uri t = target;
        showBusy(R.string.saving);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean writable = DocumentIO.canWrite(MainActivity.this, t);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        hideBusy();
                        if (writable) {
                            saveTo(t);
                        } else {
                            Skin.DialogSpec s = new Skin.DialogSpec();
                            s.title = getString(R.string.read_only_title);
                            s.message = getString(R.string.read_only_message);
                            s.positive = getString(R.string.choose_location);
                            s.negative = getString(R.string.cancel);
                            s.onPositive = new Runnable() {
                                @Override
                                public void run() {
                                    chooseSaveTarget();
                                }
                            };
                            s.onNegative = new Runnable() {
                                @Override
                                public void run() {
                                    afterSave = null;
                                }
                            };
                            skin.dialog(s).show();
                        }
                    }
                });
            }
        }).start();
    }

    /** Asks where a document without a writable location should go (first save of a new file). */
    private void chooseSaveTarget() {
        String name = doc.displayName;
        if (Compat.hasSaf()) {
            try {
                startActivityForResult(Compat.createDocumentIntent(mimeFor(name), name), REQ_CREATE);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        Intent i = new Intent(this, FileBrowserActivity.class);
        i.putExtra(FileBrowserActivity.EXTRA_SAVE, true);
        i.putExtra(FileBrowserActivity.EXTRA_NAME, name);
        startActivityForResult(i, REQ_BROWSE_SAVE);
    }

    static String mimeFor(String name) {
        String ext = FileUtil.extension(name);
        if (ext.equals("odt")) return "application/vnd.oasis.opendocument.text";
        if (ext.equals("ods")) return "application/vnd.oasis.opendocument.spreadsheet";
        if (ext.equals("odp")) return "application/vnd.oasis.opendocument.presentation";
        if (ext.equals("docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (ext.equals("xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (ext.equals("pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (ext.equals("csv")) return "text/csv";
        if (ext.equals("tsv")) return "text/tab-separated-values";
        return "text/plain";
    }

    private void saveTo(final Uri uri) {
        if (doc == null) return;
        if (editor != null) editor.commitPending();
        final Doc.SaveJob job;
        try {
            job = doc.prepareSave();
        } catch (Throwable t) {
            saveFailed(t);
            return;
        }
        saving = true;
        showBusy(R.string.saving);
        final File out = new File(workDir, "last-saved");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DocumentIO.save(MainActivity.this, job, uri, out);
                    final String name = DocumentIO.displayName(MainActivity.this, uri);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            saving = false;
                            hideBusy();
                            saved(job, uri, name);
                        }
                    });
                } catch (final Throwable t) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            saving = false;
                            hideBusy();
                            saveFailed(t);
                        }
                    });
                }
            }
        }).start();
    }

    private void saved(Doc.SaveJob job, Uri uri, String name) {
        if (doc == null) return;
        doc.markSaved(job);
        boolean renamed = isNew && name != null && name.length() > 0 && !name.equals(doc.displayName) && !name.equals("document");
        if (renamed) doc.displayName = name;
        target = uri;
        isNew = false;
        forceDirty = false;
        dirty = doc.isModified();
        HistoryStore.add(this, uri.toString(), doc.displayName);
        RecoveryStore.clear(this);
        boolean utf8 = (doc instanceof PlainDoc && ((PlainDoc) doc).switchedToUtf8)
                || (doc instanceof CsvDoc && ((CsvDoc) doc).switchedToUtf8);
        Toast.makeText(this, utf8 ? R.string.switched_utf8 : R.string.saved, utf8 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        if (renamed && afterSave == null) buildEditorScreen();
        runAfterSave();
    }

    private void runAfterSave() {
        Runnable r = afterSave;
        afterSave = null;
        if (r != null) r.run();
    }

    private void saveFailed(Throwable t) {
        afterSave = null;
        writeRecoveryDraft();
        String detail = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        if (t instanceof OutOfMemoryError) detail = getString(R.string.err_too_large);
        Skin.DialogSpec s = new Skin.DialogSpec();
        s.title = getString(R.string.save_failed_title);
        s.message = getString(R.string.save_failed_message, detail);
        s.positive = getString(R.string.ok);
        skin.dialog(s).show();
    }

    // ------------------------------------------------------------------ recovery

    private void writeRecoveryDraft() {
        if (doc == null) return;
        final Doc.SaveJob job;
        try {
            if (editor != null) editor.commitPending();
            job = doc.prepareSave();
        } catch (Throwable t) {
            return;
        }
        final String name = doc.displayName;
        final String tgt = target == null ? null : target.toString();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File part = RecoveryStore.partial(MainActivity.this);
                    //noinspection ResultOfMethodCallIgnored
                    part.delete();
                    job.write(part);
                    job.verify(part);
                    RecoveryStore.commit(MainActivity.this, name, tgt);
                } catch (Throwable ignored) {
                    // Best effort: the open document itself is unaffected.
                }
            }
        }).start();
    }

    private void offerRecovery() {
        if (!RecoveryStore.has(this)) return;
        final String name = RecoveryStore.name(this);
        Skin.DialogSpec s = new Skin.DialogSpec();
        s.title = getString(R.string.recover_title);
        s.message = getString(R.string.recover_message, name);
        s.positive = getString(R.string.restore);
        s.negative = getString(R.string.discard);
        s.onPositive = new Runnable() {
            @Override
            public void run() {
                restoreDraft(name);
            }
        };
        s.onNegative = new Runnable() {
            @Override
            public void run() {
                RecoveryStore.clear(MainActivity.this);
            }
        };
        skin.dialog(s).show();
    }

    private void restoreDraft(final String name) {
        showBusy(R.string.opening);
        final Images img = images();
        final String tgt = RecoveryStore.target(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                File wd = DocumentIO.newWorkDir(MainActivity.this);
                try {
                    String ext = FileUtil.extension(name);
                    File local = new File(wd, "source" + (ext.length() > 0 ? "." + ext : ""));
                    FileInputStream in = new FileInputStream(RecoveryStore.draft(MainActivity.this));
                    try {
                        FileUtil.copy(in, local);
                    } finally {
                        in.close();
                    }
                    final Doc d = DocLoader.open(local, name, img);
                    final File fwd = wd;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            hideBusy();
                            showEditor(d, tgt == null ? null : Uri.parse(tgt), fwd, tgt == null, true);
                        }
                    });
                } catch (final Throwable t) {
                    FileUtil.deleteTree(wd);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            hideBusy();
                            openFailed(t, null);
                        }
                    });
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------ busy indicator

    private void showBusy(int msg) {
        hideBusy();
        Skin.DialogSpec s = new Skin.DialogSpec();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, skin.dp(8), 0, skin.dp(8));
        ProgressBar pb = new ProgressBar(this);
        row.addView(pb, new LinearLayout.LayoutParams(skin.dp(40), skin.dp(40)));
        TextView t = skin.text(getString(msg), 16, skin.dialogText, skin.regular());
        t.setPadding(skin.dp(16), 0, 0, 0);
        row.addView(t);
        s.content = row;
        busy = skin.dialog(s);
        busy.setCancelable(false);
        busy.setCanceledOnTouchOutside(false);
        busy.show();
    }

    private void hideBusy() {
        if (busy != null) {
            try {
                busy.dismiss();
            } catch (Exception ignored) {
            }
            busy = null;
        }
    }
}
