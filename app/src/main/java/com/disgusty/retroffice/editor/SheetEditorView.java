package com.disgusty.retroffice.editor;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import com.disgusty.retroffice.R;
import com.disgusty.retroffice.compat.Compat;
import com.disgusty.retroffice.sheet.Cell;
import com.disgusty.retroffice.sheet.CellRef;
import com.disgusty.retroffice.sheet.SheetDoc;
import com.disgusty.retroffice.ui.Icons;
import com.disgusty.retroffice.ui.Skin;

/** Spreadsheet editor: formula bar, sheet switcher and the grid. */
public final class SheetEditorView implements DocEditor {
    private final Context ctx;
    private final Skin skin;
    private final SheetDoc doc;
    private final Host host;
    private final LinearLayout root;
    private final SheetGridView grid;
    private final EditText field;
    private final TextView cellLabel;
    private TextView sheetButton;
    private int sheetIdx;
    private int editRow, editCol;
    private boolean fieldDirty, settingField;

    public SheetEditorView(Context ctx, Skin skin, SheetDoc doc, Host host) {
        this.ctx = ctx;
        this.skin = skin;
        this.doc = doc;
        this.host = host;
        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(skin.windowBg);

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(skin.dp(8), skin.dp(6), skin.dp(4), skin.dp(6));
        bar.setBackgroundColor(skin.surface);
        cellLabel = skin.text("A1", 14, skin.textPrimary, skin.medium());
        cellLabel.setGravity(Gravity.CENTER);
        bar.addView(cellLabel, new LinearLayout.LayoutParams(skin.dp(56), ViewGroup.LayoutParams.WRAP_CONTENT));
        field = new EditText(ctx);
        skin.styleField(field);
        field.setSingleLine(true);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        if (Compat.SDK >= 3) {
            EditorCompat.singleLineDone(field, new Runnable() {
                @Override
                public void run() {
                    commitAndMove(1, 0);
                }
            });
        }
        field.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                    commitAndMove(1, 0);
                    return true;
                }
                return keyCode == KeyEvent.KEYCODE_ENTER;
            }
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!settingField) fieldDirty = true;
            }
        });
        bar.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(skin.iconButton(new Skin.Action(Icons.CHECK, ctx.getString(R.string.apply), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                commitAndMove(0, 0);
            }
        }, null), skin.textPrimary, 48));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(skin.dividerView());

        if (doc.sheets.size() > 1) {
            sheetButton = skin.text("", 14, skin.textPrimary, skin.medium());
            sheetButton.setGravity(Gravity.CENTER_VERTICAL);
            sheetButton.setPadding(skin.dp(16), 0, skin.dp(16), 0);
            sheetButton.setMinHeight(skin.dp(40));
            sheetButton.setBackgroundColor(skin.surface);
            sheetButton.setClickable(true);
            sheetButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chooseSheet(v);
                }
            });
            root.addView(sheetButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(skin.dividerView());
        }

        grid = new SheetGridView(ctx, skin.accent);
        grid.setListener(new SheetGridView.Listener() {
            @Override
            public void onCellSelected(int row, int col) {
                if (fieldDirty) commit();
                showCell(row, col);
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        switchSheet(0);
    }

    private void chooseSheet(View anchor) {
        ArrayList<Skin.MenuItem> items = new ArrayList<Skin.MenuItem>();
        for (int i = 0; i < doc.sheets.size(); i++) {
            final int idx = i;
            items.add(new Skin.MenuItem(doc.sheets.get(i).name, true, new Runnable() {
                @Override
                public void run() {
                    if (fieldDirty) commit();
                    switchSheet(idx);
                }
            }));
        }
        skin.popup(anchor, items);
    }

    private void switchSheet(int idx) {
        sheetIdx = idx;
        grid.setSheet(doc.sheets.get(idx));
        if (sheetButton != null) sheetButton.setText(ctx.getString(R.string.sheet) + ": " + doc.sheets.get(idx).name + "  ▾");
        showCell(0, 0);
    }

    private void showCell(int row, int col) {
        editRow = row;
        editCol = col;
        cellLabel.setText(CellRef.name(col, row));
        Cell c = doc.sheets.get(sheetIdx).get(row, col);
        settingField = true;
        field.setText(c == null ? "" : c.inputText());
        settingField = false;
        fieldDirty = false;
    }

    private void commitAndMove(int dr, int dc) {
        commit();
        if (dr != 0 || dc != 0) grid.select(editRow + dr, editCol + dc);
    }

    private void commit() {
        if (!fieldDirty) return;
        fieldDirty = false;
        final int s = sheetIdx, r = editRow, c = editCol;
        Cell existing = doc.sheets.get(s).get(r, c);
        final String before = existing == null ? "" : existing.inputText();
        final String after = field.getText().toString();
        if (after.equals(before)) return;
        String err = doc.setInput(s, r, c, after);
        if (err != null) {
            host.toast(SheetDoc.ERR_COVERED.equals(err) ? R.string.cell_covered
                    : SheetDoc.ERR_READONLY.equals(err) ? R.string.sheet_read_only : R.string.formula_unsupported);
            showCell(r, c);
            return;
        }
        host.history().recordCustom(new EditHistory.Custom() {
            @Override
            public void undo() {
                doc.setInput(s, r, c, before);
                refreshAfterUndo(s, r, c);
            }

            @Override
            public void redo() {
                doc.setInput(s, r, c, after);
                refreshAfterUndo(s, r, c);
            }
        });
        host.onEdited();
        grid.invalidate();
        showCell(r, c);
    }

    private void refreshAfterUndo(int s, int r, int c) {
        if (s != sheetIdx) switchSheet(s);
        grid.select(r, c);
        grid.invalidate();
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public boolean hasFormatting() {
        return false;
    }

    @Override
    public DocEditText focusedField() {
        return null;
    }

    @Override
    public void commitPending() {
        commit();
    }

    @Override
    public void release() {
    }
}
