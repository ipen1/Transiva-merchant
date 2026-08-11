package com.transiva.app;

import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class MerchantOperationsActivity extends MerchantBaseActivity {
    private LinearLayout root, scheduleBox; private TextView pauseState; private final String[] days={"Senin","Selasa","Rabu","Kamis","Jumat","Sabtu","Minggu"};
    private final List<CheckBox> enabled=new ArrayList<>(); private final List<EditText> opens=new ArrayList<>(), closes=new ArrayList<>();
    @Override protected void onCreate(Bundle b){super.onCreate(b);build();load();}
    private void build(){root=new LinearLayout(this);setContentView(page(root));root.addView(title("Operasional Restoran"));root.addView(sub("Atur pause pesanan baru dan jam buka otomatis"));pauseState=card("Memuat status pesanan...");root.addView(pauseState);
        LinearLayout buttons=row(); Button p15=outlineBtn("15 m");Button p30=outlineBtn("30 m");Button p60=outlineBtn("60 m");Button stop=outlineBtn("Manual"); buttons.addView(p15,new LinearLayout.LayoutParams(0,dp(48),1));buttons.addView(p30,new LinearLayout.LayoutParams(0,dp(48),1));buttons.addView(p60,new LinearLayout.LayoutParams(0,dp(48),1));buttons.addView(stop,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(buttons);p15.setOnClickListener(v->pause(15));p30.setOnClickListener(v->pause(30));p60.setOnClickListener(v->pause(60));stop.setOnClickListener(v->pause(0));Button resume=btn("▶ Aktifkan Pesanan Baru");resume.setOnClickListener(v->resume());root.addView(resume);
        root.addView(label("Jam Operasional Mingguan"));scheduleBox=new LinearLayout(this);scheduleBox.setOrientation(LinearLayout.VERTICAL);root.addView(scheduleBox);for(String day:days)addDay(day);Button save=btn("💾 Simpan Jam Operasional");save.setOnClickListener(v->saveSchedule());root.addView(save);Button back=outlineBtn("← Kembali");back.setOnClickListener(v->finish());root.addView(back);}
    private void addDay(String day){LinearLayout r=row();CheckBox cb=new CheckBox(this);cb.setText(day);cb.setChecked(true);EditText o=time("08:00"),c=time("22:00");r.addView(cb,new LinearLayout.LayoutParams(0,dp(52),1.2f));r.addView(o,new LinearLayout.LayoutParams(0,dp(52),1));r.addView(c,new LinearLayout.LayoutParams(0,dp(52),1));scheduleBox.addView(r);enabled.add(cb);opens.add(o);closes.add(c);}
    private EditText time(String hint){EditText e=input(hint,InputType.TYPE_CLASS_DATETIME);e.setSingleLine(true);return e;}
    private void load(){MerchantNetworkExecutor.executeRead(this, "operations", ()->{try{JSONObject r=new JSONObject(get(BASE+"merchant_operations.php?v="+System.currentTimeMillis()));runOnUiThread(()->apply(r.optJSONObject("operations")));}catch(Exception e){runOnUiThread(()->pauseState.setText("Gagal memuat pengaturan operasional."));}});}
    private void apply(JSONObject o){if(o==null)return;boolean paused=o.optBoolean("paused",false);String until=o.optString("pause_until","");pauseState.setText(paused?(until.isEmpty()?"⏸ Pesanan baru dipause sampai diaktifkan kembali":"⏸ Pesanan baru dipause sampai "+until):"🟢 Pesanan baru aktif");JSONArray a=o.optJSONArray("schedule");if(a!=null)for(int i=0;i<Math.min(7,a.length());i++){JSONObject d=a.optJSONObject(i);if(d==null)continue;enabled.get(i).setChecked(d.optBoolean("enabled",true));opens.get(i).setText(d.optString("open","08:00"));closes.get(i).setText(d.optString("close","22:00"));}}
    private void pause(int mins){send("pause",mins,null);} private void resume(){send("resume",0,null);} private void send(String action,int mins,JSONArray schedule){MerchantNetworkExecutor.executeWrite("merchant-operation:"+action,()->{try{JSONObject p=new JSONObject();p.put("action",action);if(mins>0)p.put("minutes",mins);if(schedule!=null)p.put("schedule",schedule);JSONObject r=new JSONObject(postJson(BASE+"merchant_operations.php",p));runOnUiThread(()->{toast(r.optString("message","Tersimpan"));load();});}catch(Exception e){runOnUiThread(()->alert("Error","Gagal menyimpan pengaturan."));}});}
    private void saveSchedule(){try{JSONArray a=new JSONArray();for(int i=0;i<7;i++){JSONObject d=new JSONObject();d.put("day",i+1);d.put("enabled",enabled.get(i).isChecked());d.put("open",opens.get(i).getText().toString().trim());d.put("close",closes.get(i).getText().toString().trim());a.put(d);}send("schedule",0,a);}catch(Exception e){alert("Error","Jadwal tidak valid.");}}
}
