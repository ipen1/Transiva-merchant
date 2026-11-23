package com.transiva.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MerchantPosSalesActivity extends MerchantBaseActivity {
    private static final int EXPORT=851;
    private static final String[] COLUMNS={"tanggal","id","kasir","kasir_id","metode","subtotal","diskon","pajak","poin_rp","total","modal","laba_kotor","sisa_kasbon"};
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final NumberFormat currency=NumberFormat.getIntegerInstance(new Locale("id","ID"));
    private final TimeZone zone=TimeZone.getTimeZone("Asia/Makassar");
    private String outlet, filterKey, filterName, method="all", export="";
    private int days=1, shown=40;
    private JSONObject book,plan;
    private LinearLayout body;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(sessionManager.getToken().isEmpty()){finish();return;}
        outlet=getIntent().getStringExtra("outlet");filterKey=getIntent().getStringExtra("staff_key");filterName=getIntent().getStringExtra("staff_name");
        if(outlet==null||!outlet.matches("[0-9]+")||filterKey==null){finish();return;}
        if(state!=null){days=state.getInt("days",1);method=state.getString("method","all");export=state.getString("export","");}
        load();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putInt("days",days);state.putString("method",method);state.putString("export",export);super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy(){work.shutdown();super.onDestroy();}
    private boolean alive(){return !isFinishing()&&!isDestroyed();}
    private void load(){
        layout();body.addView(sub("Memuat penjualan outlet…"));
        work.execute(()->{
            try{
                JSONObject result=new JSONObject(get(BASE+"merchant_pos.php?outlet="+outlet));
                if(!result.optBoolean("success"))throw new java.io.IOException(result.optString("message"));
                runOnUiThread(()->{if(alive()){book=result.optJSONObject("book");plan=result.optJSONObject("plan");draw();}});
            }catch(Exception error){runOnUiThread(()->{if(alive()){
                layout();body.addView(sub(error instanceof java.io.IOException?error.getMessage():"Laporan belum bisa dimuat."));
                Button retry=outlineBtn("Coba lagi");retry.setOnClickListener(v->load());body.addView(retry);
            }});}
        });
    }
    private void layout(){
        body=new LinearLayout(this);setContentView(page(body,new android.view.View(this)));
        Button back=outlineBtn("‹ Pegawai");back.setOnClickListener(v->finish());body.addView(back);
        body.addView(title("Penjualan kasir"));body.addView(sub(filterName));
    }
    private List<JSONObject> records(String key){
        List<JSONObject> out=new ArrayList<>();
        JSONArray arr=book==null?null:book.optJSONArray(key);
        if(arr!=null)for(int i=0;i<arr.length();i++)if(arr.optJSONObject(i)!=null)out.add(arr.optJSONObject(i));
        return out;
    }
    private String date(long at,String pattern){
        SimpleDateFormat formatter=new SimpleDateFormat(pattern,new Locale("id","ID"));formatter.setTimeZone(zone);
        return formatter.format(new Date(at));
    }
    private long nowStart(){
        Calendar cal=Calendar.getInstance(zone);
        cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
        cal.add(Calendar.DAY_OF_YEAR,1-days);return cal.getTimeInMillis();
    }
    private boolean matches(JSONObject sale){
        if(filterKey.isEmpty())return true;
        if(sale.has("staffKey"))return filterKey.equals(sale.optString("staffKey"));
        if(!Objects.equals(filterName,sale.optString("staff")))return false;
        if(filterKey.startsWith("owner"))return records("staff").stream().noneMatch(row->filterName.equals(row.optString("name")));
        if(filterKey.startsWith("staff:"))return !"Pemilik outlet".equals(filterName)&&records("staff").stream().filter(row->filterName.equals(row.optString("name"))).count()==1;
        return false;
    }
    private String money(long amount){return "Rp "+currency.format(amount);}
    private void button(LinearLayout group,String label,boolean selected,Runnable action){
        Button button=selected?btn(label):outlineBtn(label);button.setPadding(0,0,0,0);button.setMinWidth(0);
        button.setOnClickListener(v->action.run());group.addView(button,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void metric(String label,String value){
        LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(tv(label,14,NAVY,true),new LinearLayout.LayoutParams(0,-2,1));
        row.addView(tv(value,14,NAVY,true));row.setPadding(dp(5),dp(6),dp(5),dp(6));body.addView(row);
    }
    private void draw(){
        layout();
        LinearLayout periods=new LinearLayout(this);body.addView(periods);
        for(int value:new int[]{1,7,30})button(periods,value+" hari",value==days,()->{days=value;shown=40;draw();});
        long start=nowStart();
        List<JSONObject> selected=new ArrayList<>();
        Map<String,Long> payments=new HashMap<>();
        for(JSONObject row:records("payments")) payments.merge(row.optString("sale"),row.optLong("amount"),Long::sum);
        Map<String,Long> sums=new HashMap<>();long total=0,paid=0,cost=0,profit=0,known=0;
        for(JSONObject sale:records("sales")){
            long time=sale.optLong("at");if(time<start||time>System.currentTimeMillis()||!matches(sale))continue;
            String date=date(time,"yyyy-MM-dd");
            if(plan!=null&&(date.compareTo(plan.optString("starts_on"))<0||date.compareTo(plan.optString("ends_on"))>0))continue;
            long amount=sale.optLong("total");String how=sale.optString("method");
            sums.merge(how,amount,Long::sum);total+=amount;
            paid+="debt".equals(how)?Math.min(amount,payments.getOrDefault(sale.optString("id"),0L)):amount;
            if(!sale.isNull("costOfGoods")&&!sale.isNull("grossProfit")){cost+=sale.optLong("costOfGoods");profit+=sale.optLong("grossProfit");known++;}
            if("all".equals(method)||method.equals(how))selected.add(sale);
        }
        metric("Total penjualan",money(total));metric("Sudah diterima dari penjualan ini",money(paid));
        metric("Sisa kasbon",money(total-paid));
        metric("Tunai",money(sums.getOrDefault("cash",0L)));metric("QRIS",money(sums.getOrDefault("qris",0L)));
        metric("Kasbon",money(sums.getOrDefault("debt",0L)));
        if(known>0){metric("Modal tercatat · "+known+" transaksi",money(cost));metric("Laba kotor tercatat",money(profit));}
        else body.addView(sub("Modal dan laba belum tersedia untuk transaksi dalam periode ini."));
        body.addView(title("Rincian penjualan"));
        LinearLayout filters=new LinearLayout(this);body.addView(filters);
        String[] keys={"all","cash","qris","debt"}, labels={"Semua","Tunai","QRIS","Kasbon"};
        for(int i=0;i<keys.length;i++){String value=keys[i];button(filters,labels[i],method.equals(value),()->{method=value;shown=40;draw();});}
        selected.sort((a,b)->Long.compare(b.optLong("at"),a.optLong("at")));
        Button csv=outlineBtn("Ekspor penjualan CSV");csv.setOnClickListener(v->saveCsv(selected,payments));body.addView(csv);
        if(selected.isEmpty())body.addView(sub("Belum ada transaksi pada filter ini."));
        for(int i=0;i<Math.min(shown,selected.size());i++){
            JSONObject sale=selected.get(i);Button detail=outlineBtn(date(sale.optLong("at"),"dd MMM · HH:mm")+" · "+money(sale.optLong("total"))+" · "+sale.optString("staff")+" · Lihat selengkapnya");
            detail.setOnClickListener(v->detail(sale,payments));body.addView(detail);
        }
        if(selected.size()>shown){Button more=outlineBtn("Tampilkan lebih banyak");more.setOnClickListener(v->{shown+=40;draw();});body.addView(more);}
    }
    private long remainder(JSONObject sale,Map<String,Long> payments){
        return "debt".equals(sale.optString("method"))?Math.max(0,sale.optLong("total")-payments.getOrDefault(sale.optString("id"),0L)):0;
    }
    private void detail(JSONObject sale,Map<String,Long> payments){
        StringBuilder info=new StringBuilder("Kasir: ").append(sale.optString("staff","—")).append('\n')
                .append("Metode: ").append(sale.optString("method").toUpperCase(Locale.ROOT)).append('\n');
        JSONArray lines=sale.optJSONArray("items");
        if(lines!=null)for(int i=0;i<lines.length();i++){
            JSONObject item=lines.optJSONObject(i);if(item!=null)info.append(item.optInt("qty")).append(" × ").append(item.optString("name")).append(" · ")
                    .append(money(item.optInt("qty")*item.optLong("price"))).append('\n');
        }
        info.append("\nTotal ").append(money(sale.optLong("total")));
        if("debt".equals(sale.optString("method")))info.append("\nSisa kasbon ").append(money(remainder(sale,payments)));
        if(!sale.isNull("costOfGoods"))info.append("\nModal ").append(money(sale.optLong("costOfGoods")))
                .append("\nLaba kotor ").append(money(sale.optLong("grossProfit")));
        new AlertDialog.Builder(this).setTitle("Rincian penjualan").setMessage(info.toString()).setPositiveButton("Tutup",null).show();
    }
    static void csvLine(StringBuilder out,String... cells){
        for(int i=0;i<cells.length;i++){
            if(i>0)out.append(',');
            String value=cells[i];
            if(value.matches("(?s)^(?:\\s*[=+@-]|[\\t\\r\\n]).*")&&!value.matches("-[0-9]+"))value="'"+value;
            out.append('"').append(value.replace("\"","\"\"")).append('"');
        }out.append("\r\n");
    }
    private void saveCsv(List<JSONObject> selected,Map<String,Long> payments){
        StringBuilder csv=new StringBuilder();csvLine(csv,COLUMNS);
        for(JSONObject sale:selected)csvLine(csv,date(sale.optLong("at"),"yyyy-MM-dd HH:mm:ss"),sale.optString("id"),sale.optString("staff"),sale.optString("staffKey"),
                sale.optString("method"),sale.optString("subtotal"),sale.optString("discount"),sale.optString("tax"),
                sale.optString("redeem"),sale.optString("total"),sale.isNull("costOfGoods")?"":sale.optString("costOfGoods"),
                sale.isNull("grossProfit")?"":sale.optString("grossProfit"),Long.toString(remainder(sale,payments)));
        if(csv.length()>300000){toast("Pilih periode atau metode lebih kecil untuk ekspor.");return;}
        export=csv.toString();
        Intent file=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE);
        file.putExtra(Intent.EXTRA_TITLE,"penjualan-kasir.csv");startActivityForResult(file,EXPORT);
    }
    @Override protected void onActivityResult(int request,int result,Intent file){
        super.onActivityResult(request,result,file);
        if(request!=EXPORT||result!=RESULT_OK||file==null||file.getData()==null)return;
        android.net.Uri uri=file.getData();String text=export;export="";
        work.execute(()->{
            try(OutputStream stream=getContentResolver().openOutputStream(uri)){
                if(stream==null)throw new java.io.IOException();stream.write(text.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(()->{if(alive())toast("Laporan CSV tersimpan.");});
            }catch(Exception error){runOnUiThread(()->{if(alive())toast("Laporan belum dapat disimpan.");});}
        });
    }
}
