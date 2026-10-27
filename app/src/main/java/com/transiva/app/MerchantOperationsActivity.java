package com.transiva.app;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class MerchantOperationsActivity extends MerchantBaseActivity {
    private LinearLayout scheduleBox;
    private TextView pauseState,scheduleSummary;
    private Spinner prepSpinner;
    private Button pauseButton,prepSave,scheduleSave,scheduleToggle;
    private boolean loaded,loading,busy,restoring,scheduleDirty,prepDirty,paused;
    private int serverPrep;
    private final String[] days={"Senin","Selasa","Rabu","Kamis","Jumat","Sabtu","Minggu"};
    private final List<CheckBox> enabled=new ArrayList<>();
    private final List<EditText> opens=new ArrayList<>(),closes=new ArrayList<>();
    @Override protected void onCreate(Bundle state){super.onCreate(state);build();load();}
    private void build(){
        LinearLayout root=new LinearLayout(this);setContentView(page(root));
        LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);head.addView(title("Operasional"),new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=outlineBtn("↻");refresh.setContentDescription("Perbarui operasional");refresh.setOnClickListener(v->load());head.addView(refresh,new LinearLayout.LayoutParams(dp(48),-2));root.addView(head);
        LinearLayout status=section("Penerimaan pesanan");root.addView(status);
        pauseState=tv("Memuat status toko…",13,MUTED,false);status.addView(pauseState);
        pauseButton=outlineBtn("Jeda pesanan");pauseButton.setOnClickListener(v->{if(paused)send("resume",0,null);else choosePause();});status.addView(pauseButton);
        LinearLayout preparation=section("Waktu persiapan");root.addView(preparation);
        LinearLayout prepRow=actionRow();prepSpinner=new Spinner(this);prepSpinner.setMinimumHeight(dp(48));prepSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"10 menit","15 menit","20 menit","30 menit","45 menit","60 menit"}));
        prepRow.addView(prepSpinner,actionLp());prepSave=outlineBtn("Simpan");prepSave.setOnClickListener(v->savePrep());prepRow.addView(prepSave,actionLp());preparation.addView(prepRow);
        prepSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View v,int position,long id){if(loaded&&!restoring)prepDirty=position!=serverPrep;}public void onNothingSelected(AdapterView<?> parent){}});
        LinearLayout schedule=section("Jadwal mingguan");root.addView(schedule);
        scheduleSummary=tv("Memuat jadwal…",12,MUTED,false);schedule.addView(scheduleSummary);
        scheduleToggle=outlineBtn("Atur jadwal ▾");scheduleToggle.setOnClickListener(v->{boolean show=scheduleBox.getVisibility()!=View.VISIBLE;scheduleBox.setVisibility(show?View.VISIBLE:View.GONE);scheduleToggle.setText(show?"Tutup jadwal ▴":"Atur jadwal ▾");});schedule.addView(scheduleToggle);
        scheduleBox=new LinearLayout(this);scheduleBox.setOrientation(LinearLayout.VERTICAL);scheduleBox.setVisibility(View.GONE);schedule.addView(scheduleBox);
        scheduleBox.addView(sub("Urutan jam: Buka → Tutup. Hilangkan centang pada hari libur."));
        for(String day:days)addDay(day);
        scheduleSave=btn("Simpan jadwal");scheduleSave.setOnClickListener(v->saveSchedule());scheduleBox.addView(scheduleSave);setControls();
    }
    private LinearLayout section(String name){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(8));
        card.setBackground(stroke(android.graphics.Color.WHITE,android.graphics.Color.parseColor("#DCE6F1"),dp(14)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(3),0,dp(8));card.setLayoutParams(lp);card.addView(tv(name,14,NAVY,true));return card;
    }
    private void addDay(String day){
        LinearLayout row=actionRow();CheckBox check=new CheckBox(this);check.setText(day);check.setTextSize(13);check.setTextColor(TEXT);check.setMinHeight(dp(48));check.setChecked(true);
        EditText open=time("08:00","Jam buka "+day),close=time("22:00","Jam tutup "+day);
        row.addView(check,actionLp());row.addView(open,actionLp());row.addView(close,actionLp());scheduleBox.addView(row);
        enabled.add(check);opens.add(open);closes.add(close);
        check.setOnCheckedChangeListener((v,on)->{if(!restoring){scheduleDirty=true;updateSummary();}open.setEnabled(on&&loaded&&!busy&&!loading);close.setEnabled(on&&loaded&&!busy&&!loading);});
    }
    private EditText time(String initial,String description){
        EditText field=input(initial,InputType.TYPE_CLASS_DATETIME);field.setText(initial);field.setTextSize(13);field.setFocusable(false);field.setCursorVisible(false);field.setContentDescription(description);
        field.setOnClickListener(v->{String[] value=field.getText().toString().split(":");int hour=8,minute=0;try{hour=Integer.parseInt(value[0]);minute=Integer.parseInt(value[1]);}catch(Exception ignored){}
            new TimePickerDialog(this,(picker,h,m)->{field.setText(String.format(Locale.US,"%02d:%02d",h,m));scheduleDirty=true;},hour,minute,true).show();});return field;
    }
    private void choosePause(){new AlertDialog.Builder(this).setTitle("Jeda pesanan baru").setItems(new String[]{"15 menit","30 menit","60 menit","Sampai diaktifkan kembali"},(d,w)->send("pause",new int[]{15,30,60,0}[w],null)).setNegativeButton("Batal",null).show();}
    private void setControls(){boolean ready=loaded&&!busy&&!loading;pauseButton.setEnabled(ready);prepSave.setEnabled(ready);scheduleSave.setEnabled(ready);prepSpinner.setEnabled(ready);
        for(int i=0;i<enabled.size();i++){enabled.get(i).setEnabled(ready);opens.get(i).setEnabled(ready&&enabled.get(i).isChecked());closes.get(i).setEnabled(ready&&enabled.get(i).isChecked());}}
    private void updateSummary(){int count=0;for(CheckBox cb:enabled)if(cb.isChecked())count++;scheduleSummary.setText(count+" hari buka • "+(7-count)+" hari tutup"+(scheduleDirty?" • belum disimpan":""));}
    private void load(){
        if(loading||busy)return;loading=true;setControls();
        java.util.concurrent.Future<?> request=MerchantNetworkExecutor.executeRead(this,"operations",()->{try{
            JSONObject response=new JSONObject(get(BASE+"merchant_operations.php?v="+System.currentTimeMillis()));
            if(!response.optBoolean("success",true)||response.optJSONObject("operations")==null)throw new Exception("Invalid operations");
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;loading=false;apply(response.optJSONObject("operations"));});
        }catch(Exception error){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;loading=false;pauseState.setText("Belum dapat memuat. Tekan ↻ untuk mencoba lagi.");setControls();});}});
        if(request==null){loading=false;setControls();pauseState.setText("Pemeriksaan sedang sibuk. Coba perbarui lagi.");}
    }
    private void apply(JSONObject operation){
        restoring=true;
        if(!prepDirty){String duration=operation.optString("duration","15 menit");for(int i=0;i<prepSpinner.getCount();i++)if(duration.equals(prepSpinner.getItemAtPosition(i).toString())){serverPrep=i;prepSpinner.setSelection(i);break;}}
        paused=operation.optBoolean("paused",false);String until=operation.optString("pause_until","");
        pauseState.setText(paused?(until.isEmpty()?"Pesanan baru dijeda sampai Anda aktifkan kembali.":"Pesanan baru dijeda hingga "+until):"Toko menerima pesanan baru.");pauseButton.setText(paused?"Terima pesanan lagi":"Jeda pesanan");
        if(!scheduleDirty){JSONArray schedule=operation.optJSONArray("schedule");if(schedule!=null)for(int i=0;i<schedule.length();i++){JSONObject row=schedule.optJSONObject(i);if(row==null)continue;int index=row.optInt("day",i+1)-1;if(index<0||index>=7)continue;enabled.get(index).setChecked(row.optBoolean("enabled",true));opens.get(index).setText(row.optString("open","08:00"));closes.get(index).setText(row.optString("close","22:00"));}}
        loaded=true;restoring=false;updateSummary();setControls();
    }
    private void send(String action,int minutes,JSONArray schedule){
        if(!loaded||busy||loading)return;busy=true;setControls();
        boolean accepted=MerchantNetworkExecutor.executeWrite("merchant-operations-change",()->{try{JSONObject body=new JSONObject();body.put("action",action);if(minutes>0)body.put("minutes",minutes);if(schedule!=null)body.put("schedule",schedule);
            JSONObject response=new JSONObject(postJson(BASE+"merchant_operations.php",body));runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;
                if(response.optBoolean("success")){if("schedule".equals(action))scheduleDirty=false;toast(response.optString("message","Tersimpan"));load();}else alert("Belum tersimpan",response.optString("message","Coba lagi."));setControls();});
        }catch(Exception error){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;setControls();alert("Koneksi","Pengaturan belum tersimpan. Coba lagi.");});}});
        if(!accepted){busy=false;setControls();toast("Permintaan sebelumnya masih diproses.");}
    }
    private void savePrep(){
        if(!loaded||busy||loading)return;final String duration=prepSpinner.getSelectedItem().toString();busy=true;setControls();
        boolean accepted=MerchantNetworkExecutor.executeWrite("merchant-operations-change",()->{try{JSONObject body=new JSONObject();body.put("duration",duration);JSONObject response=new JSONObject(postJson(BASE+"merchant_update_duration.php",body));
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;if(response.optBoolean("success")){prepDirty=false;serverPrep=prepSpinner.getSelectedItemPosition();toast(response.optString("message","Tersimpan"));}else alert("Belum tersimpan",response.optString("message","Coba lagi."));setControls();});
        }catch(Exception error){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;setControls();alert("Koneksi","Waktu persiapan belum tersimpan.");});}});
        if(!accepted){busy=false;setControls();toast("Permintaan sebelumnya masih diproses.");}
    }
    private void saveSchedule(){try{
        JSONArray schedule=new JSONArray();for(int i=0;i<7;i++){String open=opens.get(i).getText().toString().trim(),close=closes.get(i).getText().toString().trim();
            if(!open.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")||!close.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")){alert("Periksa jadwal",days[i]+": pilih jam yang valid.");return;}
            if(enabled.get(i).isChecked()&&close.compareTo(open)<=0){alert("Periksa jadwal",days[i]+": jam tutup harus setelah jam buka. Jadwal melewati tengah malam belum didukung.");return;}
            JSONObject row=new JSONObject();row.put("day",i+1);row.put("enabled",enabled.get(i).isChecked());row.put("open",open);row.put("close",close);schedule.put(row);
        }send("schedule",0,schedule);
    }catch(Exception error){alert("Periksa jadwal","Jadwal belum valid.");}}
}
