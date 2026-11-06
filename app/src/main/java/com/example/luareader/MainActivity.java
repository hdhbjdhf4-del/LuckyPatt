package com.example.luareader;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.widget.*;
import android.graphics.Typeface;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int OPEN_FILE = 1001;
    private static final int SAVE_FILE = 1002;
    private EditText editor;
    private TextView status;
    private String currentName = "No file opened";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(14, 14, 14, 10);
        page.setBackgroundColor(0xFFF4F6FA);

        TextView title = new TextView(this);
        title.setText("Lua Reader");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF172554);
        page.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Lua source (.lua) और bytecode (.luac) फ़ाइल पढ़ें");
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFF475569);
        page.addView(subtitle);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button open = new Button(this); open.setText("फ़ाइल खोलें");
        Button save = new Button(this); save.setText("Save As");
        buttons.addView(open, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(buttons);

        status = new TextView(this);
        status.setText(currentName);
        status.setTextSize(12);
        status.setTextColor(0xFF334155);
        status.setPadding(4, 8, 4, 8);
        page.addView(status);

        editor = new EditText(this);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTextSize(13);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setPadding(12, 12, 12, 12);
        editor.setBackgroundColor(0xFFFFFFFF);
        editor.setHorizontallyScrolling(true);
        editor.setTextIsSelectable(true);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, 0, 1);
        page.addView(editor, ep);

        TextView hint = new TextView(this);
        hint.setText("नोट: .lua टेक्स्ट को पढ़/एडिट किया जा सकता है। Compiled .luac bytecode को यह ऐप पहचानकर hex रूप में दिखाता है; उसे मूल source code में बदलने की गारंटी नहीं है।");
        hint.setTextSize(11);
        hint.setTextColor(0xFF64748B);
        hint.setPadding(4, 8, 4, 0);
        page.addView(hint);

        open.setOnClickListener(v -> openPicker());
        save.setOnClickListener(v -> savePicker());
        setContentView(page);
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, OPEN_FILE);
    }

    private void savePicker() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, currentName.endsWith(".lua") ? currentName : "edited.lua");
        startActivityForResult(i, SAVE_FILE);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (request == OPEN_FILE) readFile(uri);
            else if (request == SAVE_FILE) writeFile(uri);
        } catch (Exception e) {
            status.setText("त्रुटि: " + e.getMessage());
            Toast.makeText(this, "फ़ाइल नहीं खुल सकी", Toast.LENGTH_LONG).show();
        }
    }

    private void readFile(Uri uri) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("फ़ाइल उपलब्ध नहीं है");
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > 10 * 1024 * 1024) throw new IOException("फ़ाइल 10 MB से बड़ी है");
                out.write(buf, 0, n);
            }
        }
        byte[] bytes = out.toByteArray();
        currentName = getName(uri);
        if (bytes.length >= 4 && bytes[0] == 0x1b && bytes[1] == 'L' && bytes[2] == 'u' && bytes[3] == 'a') {
            String hex = toHex(bytes, 4096);
            editor.setText("यह Lua compiled bytecode फ़ाइल है।\n\nLua signature: 1B 4C 75 61\nकुल आकार: " + bytes.length + " bytes\n\nपहले अधिकतम 4096 bytes का HEX dump:\n" + hex +
                (bytes.length > 4096 ? "\n\n[बाकी डेटा नहीं दिखाया गया]" : ""));
            status.setText(currentName + " • Lua bytecode");
        } else {
            editor.setText(decodeText(bytes));
            status.setText(currentName + " • " + bytes.length + " bytes • UTF-8/Text");
        }
    }

    private String decodeText(byte[] b) {
        String s = new String(b, StandardCharsets.UTF_8);
        if (s.indexOf('\uFFFD') >= 0) return new String(b, Charset.forName("windows-1252"));
        return s;
    }

    private String toHex(byte[] b, int limit) {
        StringBuilder s = new StringBuilder();
        int len = Math.min(b.length, limit);
        for (int i = 0; i < len; i += 16) {
            s.append(String.format("%08X  ", i));
            for (int j = i; j < Math.min(i + 16, len); j++) s.append(String.format("%02X ", b[j] & 255));
            s.append('\n');
        }
        return s.toString();
    }

    private String getName(Uri uri) {
        String path = uri.getLastPathSegment();
        if (path == null) return "selected.lua";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void writeFile(Uri uri) throws Exception {
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Save location नहीं मिली");
            out.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
        }
        Toast.makeText(this, "फ़ाइल सेव हो गई", Toast.LENGTH_LONG).show();
    }
}