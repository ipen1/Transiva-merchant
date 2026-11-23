package com.transiva.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.text.NumberFormat;
import java.util.*;

public final class MerchantPosCheckoutActivity extends MerchantBaseActivity {
    static final String DATA="checkout_data", SALE="checkout_sale";
    private static final int BLUE=0xff0756c7;
    private JSONObject data;
    private LinearLayout form, payment;
    private TextView totals;
    private EditText points, cash, reference;
    private Spinner type, customer, promo, operator, method;
    private JSONArray lines;
    private final List<CheckBox> productDiscounts=new ArrayList<>();
    private final NumberFormat currency=NumberFormat.getIntegerInstance(new Locale("id","ID"));

    @Override protected EditText input(String hint,int type) {
        EditText input=super.input(hint,type);
        input.setTextColor(0xff0b1530);
        return input;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (sessionManager.getToken().isEmpty()) {finish();return;}
        try {data=new JSONObject(getIntent().getStringExtra(DATA));lines=data.getJSONArray("items");}
        catch(Exception error){finish();return;}
        form=column();LinearLayout footer=column();footer.setPadding(dp(12),dp(6),dp(12),dp(8));
        Button save=btn("Simpan transaksi");footer.addView(save);
        setContentView(page(form,footer));
        Button back=outlineBtn("‹  Transaksi");back.setOnClickListener(v->finish());form.addView(back);
        form.addView(title("Checkout"));form.addView(sub(data.optString("outlet_name")));
        LinearLayout items=card();items.addView(title("Ringkasan belanja"));
        for (int i=0;i<lines.length();i++) {
            JSONObject line=lines.optJSONObject(i);if(line==null)continue;
            items.addView(sub(line.optInt("qty")+" × "+line.optString("name")+"  ·  "+money(line.optInt("qty")*line.optLong("price"))));
            CheckBox discount=null;
            if (line.optDouble("productPercent",0)>0) {
                discount=new CheckBox(this);discount.setText("Diskon produk "+line.optDouble("productPercent")+"%");discount.setChecked(true);
                items.addView(discount);
            }
            productDiscounts.add(discount);
        }
        addCard(items);
        LinearLayout detail=card();detail.addView(title("Rincian transaksi"));
        type=spinner(detail,"Jenis order",names("types",""));
        customer=spinner(detail,"Pelanggan",names("people","Pelanggan umum"));
        promo=spinner(detail,"Diskon transaksi",names("discounts","Tanpa diskon"));
        if (!isStaff()) operator=spinner(detail,"Petugas",names("staff","Pemilik outlet"));
        points=input("Tukar poin (1 poin = Rp100)",InputType.TYPE_CLASS_NUMBER);
        points.setText("0");detail.addView(sub("Tukar poin (1 poin = Rp100)"));detail.addView(points);
        addCard(detail);
        LinearLayout pay=card();pay.addView(title("Pembayaran"));
        method=spinner(pay,"Metode bayar",new String[]{"Tunai","QRIS · konfirmasi manual","Kasbon"});
        payment=column();pay.addView(payment);
        totals=tv("",15,BLUE,true);pay.addView(totals);
        addCard(pay);
        cash=input("Uang diterima (Rp)",InputType.TYPE_CLASS_NUMBER);
        reference=input("Referensi QRIS",InputType.TYPE_CLASS_TEXT);
        if (state!=null) {
            type.setSelection(state.getInt("type"));customer.setSelection(state.getInt("customer"));promo.setSelection(state.getInt("promo"));
            if (operator!=null) operator.setSelection(state.getInt("operator"));method.setSelection(state.getInt("method"));
            points.setText(state.getString("points","0"));cash.setText(state.getString("cash",""));reference.setText(state.getString("ref",""));
            boolean[] enabled=state.getBooleanArray("product_discounts");
            if(enabled!=null) for(int i=0;i<Math.min(enabled.length,productDiscounts.size());i++) if(productDiscounts.get(i)!=null)productDiscounts.get(i).setChecked(enabled[i]);
        }
        for(CheckBox box:productDiscounts) if(box!=null)box.setOnCheckedChangeListener((v,on)->refresh());
        react(customer,false);react(promo,false);react(method,true);
        points.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void onTextChanged(CharSequence s,int a,int b,int c){refresh();}
            public void afterTextChanged(android.text.Editable s){}
        });
        updateMethod();refresh();save.setOnClickListener(v->submit());
    }
    private LinearLayout column(){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);return row;}
    private LinearLayout card(){LinearLayout row=column();row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setBackground(round(MerchantAppSettings.isDarkMode(this)?0xff17263a:Color.WHITE,dp(12)));return row;}
    private void addCard(LinearLayout card){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(9);form.addView(card,p);}
    private String money(long value){return "Rp "+currency.format(value);}
    private String[] names(String key,String placeholder){
        JSONArray list=data.optJSONArray(key);int offset=placeholder.isEmpty()?0:1;
        String[] names=new String[list.length()+offset];if(offset>0)names[0]=placeholder;
        for(int i=0;i<list.length();i++)names[i+offset]=list.optJSONObject(i).optString("name");return names;
    }
    private JSONObject chosen(String key,Spinner view,boolean firstPlaceholder){
        JSONObject value=data.optJSONArray(key).optJSONObject(view.getSelectedItemPosition()-(firstPlaceholder?1:0));
        return value==null?new JSONObject():value;
    }
    private Spinner spinner(LinearLayout host,String label,String[] values){
        host.addView(sub(label));Spinner picker=new Spinner(this);picker.setContentDescription(label);picker.setMinimumHeight(dp(44));
        picker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));host.addView(picker);return picker;
    }
    private void react(Spinner spinner,boolean changeMethod){
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){
                if(changeMethod)updateMethod();refresh();
            }
        });
    }
    private void updateMethod(){
        payment.removeAllViews();
        if(method.getSelectedItemPosition()==0){payment.addView(sub("Uang diterima (Rp)"));payment.addView(cash);}
        else if(method.getSelectedItemPosition()==1){
            String qr=data.optString("qr");
            if(qr.startsWith("data:image/jpeg;base64,"))try {
                byte[] bytes=android.util.Base64.decode(qr.substring(23),android.util.Base64.DEFAULT);
                ImageView image=new ImageView(this);image.setContentDescription("QRIS outlet");
                image.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length));
                payment.addView(image,new LinearLayout.LayoutParams(-1,dp(144)));
            }catch(IllegalArgumentException ignored){}
            payment.addView(sub("Cocokkan nominal dan dana masuk di aplikasi penyedia QRIS."));payment.addView(reference);
        } else payment.addView(sub("Pilih pelanggan untuk transaksi kasbon."));
    }
    private JSONObject quote() throws JSONException {
        JSONObject buyer=chosen("people",customer,true);
        String raw=points.getText().toString().trim();
        long count=raw.isEmpty()?0:Long.parseLong(raw);
        if(count<0||count>Math.min(100000,buyer.optLong("points")))throw new IllegalArgumentException("Poin melebihi saldo pelanggan.");
        double rate=Math.min(100,buyer.optDouble("member",0)+chosen("discounts",promo,true).optDouble("percent",0));
        long subtotal=0, productDiscount=0, tax=0;
        JSONArray finalLines=new JSONArray();
        for(int i=0;i<lines.length();i++){
            JSONObject line=new JSONObject(lines.getJSONObject(i).toString());
            long amount=line.optInt("qty")*line.optLong("price");subtotal+=amount;
            CheckBox option=productDiscounts.get(i);
            long reduction=option!=null&&option.isChecked()?Math.round(amount*line.optDouble("productPercent",0)/100):0;
            productDiscount+=reduction;
            tax+=Math.round((amount-reduction)*(1-rate/100)*line.optDouble("tax",0)/100);
            line.remove("productPercent");
            line.put("productDiscount",reduction);
            finalLines.put(line);
        }
        long discount=productDiscount+Math.round((subtotal-productDiscount)*rate/100);
        long total=subtotal-discount+tax-count*100;
        if(total<0)throw new IllegalArgumentException("Poin melebihi jumlah tagihan.");
        return new JSONObject().put("items",finalLines).put("subtotal",subtotal).put("discount",discount).put("tax",tax)
                .put("redeem",count*100).put("total",total);
    }
    private void refresh(){
        try{JSONObject q=quote();totals.setText("Subtotal  "+money(q.optLong("subtotal"))+
                "\nDiskon  "+money(q.optLong("discount"))+"   ·   Pajak  "+money(q.optLong("tax"))+
                "\nTukar poin  "+money(q.optLong("redeem"))+"\nTotal pembayaran  "+money(q.optLong("total")));}
        catch(Exception invalid){totals.setText("Periksa jumlah poin.");}
    }
    private void submit(){
        try{
            JSONObject sale=quote(),buyer=chosen("people",customer,true);
            String mode=new String[]{"cash","qris","debt"}[method.getSelectedItemPosition()];
            long paid=0,total=sale.optLong("total");
            String ref=reference.getText().toString().trim();
            if("cash".equals(mode)){
                String entered=cash.getText().toString().trim();paid=entered.isEmpty()?0:Long.parseLong(entered);
                if(paid<total||paid>100000000000L)throw new IllegalArgumentException("Periksa jumlah uang diterima.");
            }
            if("qris".equals(mode)&&(ref.isEmpty()||ref.length()>120))throw new IllegalArgumentException("Isi referensi QRIS yang sudah dikonfirmasi.");
            if("debt".equals(mode)&&buyer.optString("id").isEmpty())throw new IllegalArgumentException("Pilih pelanggan untuk kasbon.");
            sale.put("id",UUID.randomUUID().toString()).put("at",System.currentTimeMillis())
                .put("method",mode).put("customer",buyer.optString("id")).put("customerName",buyer.optString("name","Pelanggan umum"))
                .put("staff",isStaff()?username():chosen("staff",operator,true).optString("name","Pemilik outlet"))
                .put("type",chosen("types",type,false).optString("name")).put("outlet",data.optString("outlet_name"))
                .put("cash",paid).put("reference","qris".equals(mode)?ref:"");
            setResult(RESULT_OK,new Intent().putExtra(SALE,sale.toString()).putExtra("outlet",data.optString("outlet"))
                    .putExtra("revision",data.optInt("revision")));finish();
        }catch(Exception invalid){toast(invalid instanceof IllegalArgumentException&&!(invalid instanceof NumberFormatException)?invalid.getMessage():"Periksa isian pembayaran.");}
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);
        if(data==null||method==null)return;
        state.putInt("type",type.getSelectedItemPosition());state.putInt("customer",customer.getSelectedItemPosition());
        state.putInt("promo",promo.getSelectedItemPosition());state.putInt("method",method.getSelectedItemPosition());
        if(operator!=null)state.putInt("operator",operator.getSelectedItemPosition());
        state.putString("points",points.getText().toString());state.putString("cash",cash.getText().toString());state.putString("ref",reference.getText().toString());
        boolean[] enabled=new boolean[productDiscounts.size()];
        for(int i=0;i<enabled.length;i++)enabled[i]=productDiscounts.get(i)!=null&&productDiscounts.get(i).isChecked();
        state.putBooleanArray("product_discounts",enabled);
    }
}
