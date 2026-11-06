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
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int OPEN_FILE=1001, SAVE_FILE=1002;
    private static final int MAX_BYTES=20*1024*1024;
    private EditText editor;
    private TextView status, info;
    private String currentName="कोई फ़ाइल नहीं खुली";
    private byte[] originalBytes=new byte[0];
    private boolean hexMode=false;
    private String lastQuery="";
    private int searchFrom=0;

    @Override public void onCreate(Bundle state){ super.onCreate(state); buildUi(); }

    private void buildUi(){
        LinearLayout page=new LinearLayout(this); page.setOrientation(1); page.setPadding(12,12,12,8); page.setBackgroundColor(0xFFF1F5F9);
        TextView title=new TextView(this); title.setText("File Reader Pro"); title.setTextSize(24); title.setTypeface(null,Typeface.BOLD); title.setTextColor(0xFF172554); page.addView(title);
        TextView sub=new TextView(this); sub.setText("TXT • LUA • JSON • XML • CSV • LOG • HEX • अन्य फ़ाइलें"); sub.setTextSize(12); sub.setTextColor(0xFF475569); page.addView(sub);
        LinearLayout row1=new LinearLayout(this); row1.setOrientation(0);
        addButton(row1,"फ़ाइल खोलें",v->openPicker()); addButton(row1,"Save As",v->savePicker()); page.addView(row1);
        LinearLayout row2=new LinearLayout(this); row2.setOrientation(0);
        addButton(row2,"खोजें",v->findDialog()); addButton(row2,"HEX / Text",v->toggleView()); addButton(row2,"कॉपी",v->copyText()); page.addView(row2);
        info=new TextView(this); info.setText("फ़ाइल की जानकारी यहाँ दिखेगी"); info.setTextSize(11); info.setTextColor(0xFF334155); info.setPadding(4,7,4,3); page.addView(info);
        status=new TextView(this); status.setText(currentName); status.setTextSize(12); status.setTextColor(0xFF1D4ED8); status.setPadding(4,3,4,7); page.addView(status);
        editor=new EditText(this); editor.setGravity(Gravity.TOP|Gravity.START); editor.setTextSize(13); editor.setTypeface(Typeface.MONOSPACE); editor.setPadding(12,12,12,12); editor.setBackgroundColor(0xFFFFFFFF); editor.setHorizontallyScrolling(false); editor.setTextIsSelectable(true);
        page.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
        TextView note=new TextView(this); note.setText("टिप: टेक्स्ट फ़ाइलें पढ़ें/एडिट करें और Save As से सेव करें। बाइनरी फ़ाइलें HEX + ASCII में दिखेंगी। RY_QP_2016 header पहचानकर दिखाया जाएगा। .luac bytecode का HEX देखा जा सकता है, लेकिन यह संस्करण उसे source code में decompile नहीं करता।"); note.setTextSize(10); note.setTextColor(0xFF64748B); note.setPadding(4,7,4,0); page.addView(note);
        setContentView(page);
    }
    private void addButton(LinearLayout row,String label,View.OnClickListener click){ Button b=new Button(this); b.setText(label); b.setTextSize(12); row.addView(b,new LinearLayout.LayoutParams(0,-2,1)); b.setOnClickListener(click); }
    private void openPicker(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i,OPEN_FILE); }
    private void savePicker(){ if(hexMode){ toast("HEX view को सीधे सेव नहीं किया जा सकता। पहले टेक्स्ट फ़ाइल खोलें।"); return; } Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain"); String n=currentName.matches(".*\\.[A-Za-z0-9]{1,8}$")?currentName:"edited.txt"; i.putExtra(Intent.EXTRA_TITLE,"edited_"+n); startActivityForResult(i,SAVE_FILE); }
    @Override protected void onActivityResult(int req,int res,Intent data){ super.onActivityResult(req,res,data); if(res!=RESULT_OK||data==null||data.getData()==null)return; try{ if(req==OPEN_FILE)readFile(data.getData()); else if(req==SAVE_FILE)writeFile(data.getData()); }catch(Exception e){ status.setText("त्रुटि: "+e.getMessage()); toast("फ़ाइल पढ़ने/सेव करने में समस्या"); } }
    private void readFile(Uri uri)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=getContentResolver().openInputStream(uri)){ if(in==null)throw new IOException("फ़ाइल उपलब्ध नहीं"); byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1){ if(out.size()+n>MAX_BYTES)throw new IOException("20 MB से बड़ी फ़ाइल अभी समर्थित नहीं"); out.write(b,0,n); } }
        originalBytes=out.toByteArray(); currentName=queryName(uri); hexMode=isBinary(originalBytes);
        String kind=identify(originalBytes,currentName);
        if(hexMode) editor.setText(makeHex(originalBytes,MAX_BYTES));
        else editor.setText(decode(originalBytes));
        info.setText("प्रकार: "+kind+"   |   आकार: "+formatSize(originalBytes.length)+"   |   "+originalBytes.length+" bytes");
        status.setText(currentName+(hexMode?" • HEX view":" • Text view"));
        searchFrom=0;
    }
    private String queryName(Uri uri){ String name=null; try(android.database.Cursor c=getContentResolver().query(uri,null,null,null,null)){ if(c!=null&&c.moveToFirst()){ int i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if(i>=0)name=c.getString(i); } }catch(Exception ignored){} return name==null?"selected_file":name; }
    private boolean isBinary(byte[] b){ if(b.length==0)return false; if(starts(b,\"RY_QP_2016\"))return true; if(b.length>=4&&b[0]==0x1b&&b[1]=='L'&&b[2]=='u'&&b[3]=='a')return true; int controls=0, checked=Math.min(b.length,8192); for(int i=0;i<checked;i++){ int x=b[i]&255; if(x==0)return true; if((x<32&&x!=9&&x!=10&&x!=13)||x==127)controls++; } return controls>checked*0.08; }
    private String identify(byte[] b,String name){ String n=name.toLowerCase(Locale.ROOT); if(starts(b,\"RY_QP_2016\"))return \"RY_QP_2016 header वाली custom/encrypted फ़ाइल (HEX preview)\"; if(b.length>=4&&b[0]==0x1b&&b[1]=='L'&&b[2]=='u'&&b[3]=='a')return "Lua compiled bytecode"; if(starts(b,"%PDF"))return "PDF document (binary preview)"; if(starts(b,"PK"))return "ZIP / Office archive"; if(b.length>=8&&(b[0]&255)==137&&b[1]=='P')return "PNG image"; if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216)return "JPEG image"; if(n.endsWith(".lua"))return "Lua source"; if(n.endsWith(".json"))return "JSON text"; if(n.endsWith(".xml"))return "XML text"; if(n.endsWith(".csv"))return "CSV text"; if(n.endsWith(".txt")||n.endsWith(".log"))return "Text"; return isBinary(b)?"Binary file":"Text / UTF file"; }
    private boolean starts(byte[] b,String s){ if(b.length<s.length())return false; for(int i=0;i<s.length();i++)if(b[i]!=(byte)s.charAt(i))return false; return true; }
    private String decode(byte[] b){ try{ if(b.length>=3&&(b[0]&255)==239&&(b[1]&255)==187&&(b[2]&255)==191)return new String(b,3,b.length-3,StandardCharsets.UTF_8); if(b.length>=2&&(b[0]&255)==255&&(b[1]&255)==254)return new String(b,2,b.length-2,StandardCharsets.UTF_16LE); if(b.length>=2&&(b[0]&255)==254&&(b[1]&255)==255)return new String(b,2,b.length-2,StandardCharsets.UTF_16BE); String s=new String(b,StandardCharsets.UTF_8); if(s.indexOf('\uFFFD')>=0)return new String(b,Charset.forName("windows-1252")); return s; }catch(Exception e){return new String(b,Charset.forName("windows-1252"));} }
    private String makeHex(byte[] b,int limit){ StringBuilder s=new StringBuilder(); int len=Math.min(b.length,limit); for(int i=0;i<len;i+=16){ s.append(String.format(Locale.ROOT,"%08X  ",i)); for(int j=i;j<Math.min(i+16,len);j++)s.append(String.format(Locale.ROOT,"%02X ",b[j]&255)); s.append(" | "); for(int j=i;j<Math.min(i+16,len);j++){ int x=b[j]&255; s.append(x>=32&&x<127?(char)x:'.'); } s.append('\n'); } return s.toString(); }
    private String formatSize(long n){ if(n<1024)return n+" B"; if(n<1048576)return String.format(Locale.ROOT,"%.1f KB",n/1024.0); return String.format(Locale.ROOT,"%.2f MB",n/1048576.0); }
    private void toggleView(){ if(originalBytes.length==0){toast("पहले फ़ाइल खोलें");return;} if(hexMode){ editor.setText(decode(originalBytes)); hexMode=false; status.setText(currentName+" • Text view"); }else{ editor.setText(makeHex(originalBytes,MAX_BYTES)); hexMode=true; status.setText(currentName+" • HEX view"); } }
    private void findDialog(){ if(hexMode){toast("खोजने के लिए Text view चुनें");return;} EditText input=new EditText(this); input.setSingleLine(true); input.setHint("शब्द या टेक्स्ट लिखें"); input.setInputType(InputType.TYPE_CLASS_TEXT); new AlertDialog.Builder(this).setTitle("फ़ाइल में खोजें").setView(input).setPositiveButton("अगला", (d,w)->{lastQuery=input.getText().toString(); searchFrom=0; findNext();}).setNegativeButton("रद्द",null).show(); }
    private void findNext(){ if(lastQuery.isEmpty())return; String text=editor.getText().toString(); int at=text.toLowerCase(Locale.ROOT).indexOf(lastQuery.toLowerCase(Locale.ROOT),searchFrom); if(at<0&&searchFrom>0)at=text.toLowerCase(Locale.ROOT).indexOf(lastQuery.toLowerCase(Locale.ROOT)); if(at<0){toast("टेक्स्ट नहीं मिला");return;} editor.requestFocus(); editor.setSelection(at,at+lastQuery.length()); searchFrom=at+lastQuery.length(); status.setText("मिला: "+lastQuery+" • अगला खोजने के लिए फिर 'खोजें' दबाएँ"); }
    private void copyText(){ int a=editor.getSelectionStart(),b=editor.getSelectionEnd(); String value=(a>=0&&b>a)?editor.getText().subSequence(a,b).toString():editor.getText().toString(); ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("File Reader",value)); toast("टेक्स्ट कॉपी हो गया"); }
    private void writeFile(Uri uri)throws Exception{ try(OutputStream o=getContentResolver().openOutputStream(uri,"wt")){if(o==null)throw new IOException("Save location नहीं मिली");o.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));} toast("फ़ाइल सेव हो गई"); }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}