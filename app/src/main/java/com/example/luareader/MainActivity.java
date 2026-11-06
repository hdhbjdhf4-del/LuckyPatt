package com.example.luareader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Typeface;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int OPEN_FILE=1001, OPEN_APK=1002, SAVE_FILE=1003;
    private static final int MAX_FILE=80*1024*1024, MAX_ENTRY=8*1024*1024;
    private EditText editor;
    private TextView status, info;
    private String currentName="No file opened", lastQuery="";
    private byte[] originalBytes=new byte[0];
    private boolean hexMode=false;
    private int searchFrom=0;
    private Uri currentApkUri;
    private final ArrayList<String> apkEntries=new ArrayList<>();

    @Override public void onCreate(Bundle state){ super.onCreate(state); buildUi(); }

    private void buildUi(){
        LinearLayout page=new LinearLayout(this); page.setOrientation(1); page.setPadding(12,12,12,8); page.setBackgroundColor(0xFFF1F5F9);
        TextView title=new TextView(this); title.setText("Easy File Reader"); title.setTextSize(24); title.setTypeface(null,Typeface.BOLD); title.setTextColor(0xFF172554); page.addView(title);
        TextView sub=new TextView(this); sub.setText("Read text • Explore APK files • View Lua / JSON / XML / HEX"); sub.setTextSize(12); sub.setTextColor(0xFF475569); page.addView(sub);
        LinearLayout row1=new LinearLayout(this); row1.setOrientation(0);
        addButton(row1,"Open File",v->openPicker(OPEN_FILE)); addButton(row1,"Explore APK",v->openPicker(OPEN_APK)); page.addView(row1);
        LinearLayout row2=new LinearLayout(this); row2.setOrientation(0);
        addButton(row2,"Search",v->findDialog()); addButton(row2,"HEX / Text",v->toggleView()); addButton(row2,"Explain",v->explainFile()); page.addView(row2);
        LinearLayout row3=new LinearLayout(this); row3.setOrientation(0);
        addButton(row3,"Copy",v->copyText()); addButton(row3,"Save As",v->savePicker()); addButton(row3,"APK Contents",v->showApkEntries()); page.addView(row3);
        info=new TextView(this); info.setText("File information will appear here."); info.setTextSize(12); info.setTextColor(0xFF334155); info.setPadding(4,7,4,3); page.addView(info);
        status=new TextView(this); status.setText(currentName); status.setTextSize(12); status.setTextColor(0xFF1D4ED8); status.setPadding(4,3,4,7); page.addView(status);
        editor=new EditText(this); editor.setGravity(Gravity.TOP|Gravity.START); editor.setTextSize(13); editor.setTypeface(Typeface.MONOSPACE); editor.setPadding(12,12,12,12); editor.setBackgroundColor(0xFFFFFFFF); editor.setHorizontallyScrolling(false); editor.setTextIsSelectable(true);
        page.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
        TextView note=new TextView(this); note.setText("Easy guide: Open File reads one file. Explore APK lists files inside an APK. Tap a file name to view it. Explain describes the file in simple English. Encrypted or compiled files may need a valid key or original source code."); note.setTextSize(10); note.setTextColor(0xFF64748B); note.setPadding(4,7,4,0); page.addView(note);
        setContentView(page);
    }

    private void addButton(LinearLayout row,String label,View.OnClickListener click){ Button b=new Button(this); b.setText(label); b.setTextSize(11); row.addView(b,new LinearLayout.LayoutParams(0,-2,1)); b.setOnClickListener(click); }
    private void openPicker(int request){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i,request); }
    private void savePicker(){ if(hexMode){toast("Switch to Text view before saving.");return;} Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TITLE,"readable_output.txt"); startActivityForResult(i,SAVE_FILE); }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data); if(res!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            if(req==OPEN_FILE)readFile(data.getData());
            else if(req==OPEN_APK)openApk(data.getData());
            else if(req==SAVE_FILE)writeFile(data.getData());
        }catch(Exception e){ status.setText("Error: "+e.getMessage()); toast("Could not read this file."); }
    }

    private byte[] readBytes(Uri uri,int limit)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("File is not available");
            byte[] b=new byte[8192]; int n;
            while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("File is larger than the supported limit.");out.write(b,0,n);}
        }
        return out.toByteArray();
    }

    private void readFile(Uri uri)throws Exception{
        currentName=queryName(uri); originalBytes=readBytes(uri,MAX_FILE); currentApkUri=null; apkEntries.clear();
        hexMode=isBinary(originalBytes); showCurrentBytes();
    }

    private void openApk(Uri uri)throws Exception{
        currentApkUri=uri; currentName=queryName(uri); apkEntries.clear();
        try(ZipInputStream zin=new ZipInputStream(getContentResolver().openInputStream(uri))){
            if(zin==null)throw new IOException("Could not open APK archive");
            ZipEntry e; int count=0;
            while((e=zin.getNextEntry())!=null){
                if(!e.isDirectory()){apkEntries.add(e.getName());count++;}
                zin.closeEntry();
                if(count>=12000)break;
            }
        }
        originalBytes=new byte[0]; hexMode=false;
        editor.setText("APK archive opened successfully.\n\nFiles found: "+apkEntries.size()+"\n\nTap APK Contents to browse the files.\nChoose a file to read its text or HEX preview.\n\nNote: This viewer does not bypass encryption, app security, or compile protection.");
        info.setText("Type: Android APK / ZIP archive | Entries: "+apkEntries.size());
        status.setText(currentName+" • APK Explorer");
        showApkEntries();
    }

    private void showApkEntries(){
        if(apkEntries.isEmpty()){toast("Open an APK first.");return;}
        String[] names=apkEntries.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("APK Contents ("+names.length+")")
            .setItems(names,(d,which)->{try{readApkEntry(names[which]);}catch(Exception ex){toast("Could not read entry: "+ex.getMessage());}})
            .setNegativeButton("Close",null).show();
    }

    private void readApkEntry(String entryName)throws Exception{
        if(currentApkUri==null)throw new IOException("No APK is open");
        try(ZipInputStream zin=new ZipInputStream(getContentResolver().openInputStream(currentApkUri))){
            ZipEntry e;
            while((e=zin.getNextEntry())!=null){
                if(!e.isDirectory()&&entryName.equals(e.getName())){
                    ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n;
                    while((n=zin.read(b))!=-1){if(out.size()+n>MAX_ENTRY)throw new IOException("This APK entry is larger than 8 MB.");out.write(b,0,n);}
                    originalBytes=out.toByteArray(); currentName=entryName; hexMode=isBinary(originalBytes); showCurrentBytes(); return;
                }
                zin.closeEntry();
            }
        }
        throw new IOException("Entry was not found");
    }

    private void showCurrentBytes(){
        String kind=identify(originalBytes,currentName);
        editor.setText(hexMode?makeHex(originalBytes,MAX_FILE):decode(originalBytes));
        info.setText("Type: "+kind+" | Size: "+formatSize(originalBytes.length)+" ("+originalBytes.length+" bytes)");
        status.setText(currentName+(hexMode?" • HEX view":" • Text view")); searchFrom=0;
    }

    private String queryName(Uri uri){String name=null;try(android.database.Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(i>=0)name=c.getString(i);}}catch(Exception ignored){}return name==null?"selected_file":name;}

    private boolean isBinary(byte[] b){
        if(b.length==0)return false;
        if(starts(b,"RY_QP_2016"))return true;
        if(b.length>=4&&b[0]==0x1b&&b[1]=='L'&&b[2]=='u'&&b[3]=='a')return true;
        int controls=0,checked=Math.min(b.length,8192);
        for(int i=0;i<checked;i++){int x=b[i]&255;if(x==0)return true;if((x<32&&x!=9&&x!=10&&x!=13)||x==127)controls++;}
        return controls>checked*0.08;
    }

    private String identify(byte[] b,String name){
        String n=name.toLowerCase(Locale.ROOT);
        if(starts(b,"RY_QP_2016"))return "Custom RY_QP_2016 format (raw data)";
        if(b.length>=4&&b[0]==0x1b&&b[1]=='L'&&b[2]=='u'&&b[3]=='a')return "Lua compiled bytecode (.luac)";
        if(starts(b,"%PDF"))return "PDF document";
        if(starts(b,"PK"))return "ZIP / Office archive";
        if(b.length>=8&&(b[0]&255)==137&&b[1]=='P')return "PNG image";
        if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216)return "JPEG image";
        if(n.endsWith(".lua"))return "Lua source code";
        if(n.endsWith(".json"))return "JSON data";
        if(n.endsWith(".xml"))return "XML document";
        if(n.endsWith(".csv"))return "CSV table";
        if(n.endsWith(".txt")||n.endsWith(".log"))return "Text file";
        return isBinary(b)?"Binary / compiled data":"Text / UTF file";
    }

    private void explainFile(){
        String name=currentName.toLowerCase(Locale.ROOT); String kind=identify(originalBytes,currentName);
        StringBuilder msg=new StringBuilder();
        msg.append("File: ").append(currentName).append("\nType: ").append(kind).append("\nSize: ").append(formatSize(originalBytes.length)).append("\n\n");
        if(starts(originalBytes,"RY_QP_2016"))msg.append("Simple meaning: This file starts with the RY_QP_2016 marker. The remaining bytes are not readable plain text. It may be protected, encoded, compressed, or use a private format. The marker alone does not reveal the key or decoding method.");
        else if(kind.contains("Lua compiled"))msg.append("Simple meaning: This is compiled Lua bytecode, not normal Lua source text. It contains instructions for a Lua virtual machine. This app can show raw bytes, but cannot reliably turn it back into the original source.");
        else if(name.endsWith(".lua"))msg.append("Simple meaning: This is Lua source code. It is a text file containing commands used by a program or game.");
        else if(name.endsWith(".json"))msg.append("Simple meaning: JSON stores structured information as names, values, lists, and objects. It is commonly used for settings and data.");
        else if(name.endsWith(".xml"))msg.append("Simple meaning: XML stores information using named tags, similar to <item>value</item>.");
        else if(name.endsWith(".so"))msg.append("Simple meaning: This is a native Android/Linux library. It contains compiled machine code and is not ordinary text.");
        else if(name.endsWith(".dex"))msg.append("Simple meaning: This is Android app bytecode. It contains compiled app instructions, not the original Java/Kotlin source.");
        else if(name.endsWith(".arsc"))msg.append("Simple meaning: This is Android's compiled resource table. It stores app resources such as labels, colors, and references.");
        else if(name.endsWith(".png")||name.endsWith(".jpg")||name.endsWith(".jpeg"))msg.append("Simple meaning: This is an image file. The current viewer shows its file data, not the picture itself.");
        else if(kind.contains("Text"))msg.append("Simple meaning: This file contains readable text. You can search, copy, and save the text.");
        else msg.append("Simple meaning: This file contains binary or structured data. The HEX view shows its raw bytes. A readable explanation or conversion may require the format specification or original source.");
        new AlertDialog.Builder(this).setTitle("Easy File Explanation").setMessage(msg.toString()).setPositiveButton("OK",null).show();
    }

    private boolean starts(byte[] b,String s){if(b.length<s.length())return false;for(int i=0;i<s.length();i++)if(b[i]!=(byte)s.charAt(i))return false;return true;}
    private String decode(byte[] b){
        try{
            if(b.length>=3&&(b[0]&255)==239&&(b[1]&255)==187&&(b[2]&255)==191)return new String(b,3,b.length-3,StandardCharsets.UTF_8);
            if(b.length>=2&&(b[0]&255)==255&&(b[1]&255)==254)return new String(b,2,b.length-2,StandardCharsets.UTF_16LE);
            if(b.length>=2&&(b[0]&255)==254&&(b[1]&255)==255)return new String(b,2,b.length-2,StandardCharsets.UTF_16BE);
            String s=new String(b,StandardCharsets.UTF_8);if(s.indexOf('\uFFFD')>=0)return new String(b,Charset.forName("windows-1252"));return s;
        }catch(Exception e){return new String(b,Charset.forName("windows-1252"));}
    }
    private String makeHex(byte[] b,int limit){
        StringBuilder s=new StringBuilder();int len=Math.min(b.length,limit);
        for(int i=0;i<len;i+=16){s.append(String.format(Locale.ROOT,"%08X  ",i));for(int j=i;j<Math.min(i+16,len);j++)s.append(String.format(Locale.ROOT,"%02X ",b[j]&255));s.append(" | ");for(int j=i;j<Math.min(i+16,len);j++){int x=b[j]&255;s.append(x>=32&&x<127?(char)x:'.');}s.append('\n');}
        return s.toString();
    }
    private String formatSize(long n){if(n<1024)return n+" B";if(n<1048576)return String.format(Locale.ROOT,"%.1f KB",n/1024.0);return String.format(Locale.ROOT,"%.2f MB",n/1048576.0);}
    private void toggleView(){if(originalBytes.length==0){toast("Open a file first.");return;}if(hexMode){editor.setText(decode(originalBytes));hexMode=false;status.setText(currentName+" • Text view");}else{editor.setText(makeHex(originalBytes,MAX_FILE));hexMode=true;status.setText(currentName+" • HEX view");}}
    private void findDialog(){
        if(hexMode){toast("Switch to Text view before searching.");return;}
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Enter a word or text");input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Search in file").setView(input).setPositiveButton("Find Next",(d,w)->{lastQuery=input.getText().toString();searchFrom=0;findNext();}).setNegativeButton("Cancel",null).show();
    }
    private void findNext(){
        if(lastQuery.isEmpty())return;String text=editor.getText().toString();int at=text.toLowerCase(Locale.ROOT).indexOf(lastQuery.toLowerCase(Locale.ROOT),searchFrom);
        if(at<0&&searchFrom>0)at=text.toLowerCase(Locale.ROOT).indexOf(lastQuery.toLowerCase(Locale.ROOT));
        if(at<0){toast("Text not found.");return;}editor.requestFocus();editor.setSelection(at,at+lastQuery.length());searchFrom=at+lastQuery.length();status.setText("Found: "+lastQuery);
    }
    private void copyText(){int a=editor.getSelectionStart(),b=editor.getSelectionEnd();String value=(a>=0&&b>a)?editor.getText().subSequence(a,b).toString():editor.getText().toString();((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("File Reader",value));toast("Copied to clipboard.");}
    private void writeFile(Uri uri)throws Exception{try(OutputStream o=getContentResolver().openOutputStream(uri,"wt")){if(o==null)throw new IOException("Save location not available");o.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));}toast("File saved.");}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}