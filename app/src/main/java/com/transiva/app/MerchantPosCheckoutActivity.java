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
    private TextView totalValue,change,subtotalValue,discountValue,taxValue,pointsValue;
    private LinearLayout methods;
    private int paymentMethod;
    private final List<TextView> productReductions=new ArrayList<>();
    private EditText points, cash, reference;
    private Spinner type, customer, promo, operator;
    private JSONArray lines;
    private final List<CheckBox> productDiscounts=new ArrayList<>();
    private final NumberFormat currency=NumberFormat.getIntegerInstance(new Locale("id","ID"));

    @Override protected EditText input(String hint,int type) {
        EditText input=super.input(hint,type);
        input.setTextColor(0xff0b1530);input.setElevation(0);input.setBackground(stroke(Color.WHITE,0xffd4e0ed,dp(8)));
        return input;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (sessionManager.getToken().isEmpty()) {finish();return;}
        try {data=new JSONObject(getIntent().getStringExtra(DATA));lines=data.getJSONArray("items");}
        catch(Exception error){finish();return;}
        form=column();LinearLayout footer=column();footer.setPadding(dp(12),dp(6),dp(12),dp(8));
        footer.setBackground(round(surface(),dp(10)));
        totalValue=amountRow(footer,"Total pembayaran",true);
        Button save=btn("Simpan transaksi");save.setElevation(0);save.setBackground(round(0xff0756c7,dp(10)));footer.addView(save);
        setContentView(page(form,footer));
        LinearLayout top=new LinearLayout(this);top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button back=outlineBtn("‹");back.setContentDescription("Kembali ke transaksi");back.setElevation(0);back.setBackground(round(surface(),dp(8)));back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(dp(48),-2));
        LinearLayout header=column();header.setPadding(dp(9),0,0,0);header.addView(title("Checkout"));header.addView(sub(data.optString("outlet_name")));
        top.addView(header,new LinearLayout.LayoutParams(0,-2,1));form.addView(top);
        Button incoming=outlineBtn("Pesanan masuk");incoming.setOnClickListener(v->{
            String outlet=data.optString("outlet");
            try{
                startActivity(new Intent(this,MerchantOrdersActivity.class)
                    .putExtra(MerchantBaseActivity.EXTRA_BRANCH,outlet).putExtra("pos_outlet_filter",Integer.parseInt(outlet)));
            }catch(NumberFormatException ignored){toast("Outlet tidak valid.");}
        });form.addView(incoming);
        LinearLayout items=card();items.addView(title("Ringkasan belanja"));
        for (int i=0;i<lines.length();i++) {
            JSONObject line=lines.optJSONObject(i);if(line==null)continue;
            LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            ImageView photo=new ImageView(this);photo.setBackground(round(0xffe4efff,dp(7)));photo.setClipToOutline(true);
            photo.setImageResource(R.drawable.ic_merchant_package);photo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(photo,new LinearLayout.LayoutParams(dp(48),dp(48)));
            byte[] bytes=MerchantPosProductActivity.photoBytes(line.optString("photo"));
            String url=line.optString("image");
            Object source=bytes!=null?bytes:url.startsWith("https://transiva.my.id/")?url:null;
            if(source!=null)com.bumptech.glide.Glide.with(this).load(source).placeholder(R.drawable.ic_merchant_package).centerCrop().into(photo);
            LinearLayout info=column();info.setPadding(dp(7),0,dp(4),0);
            info.addView(tv(line.optString("name"),15,NAVY,true));
            info.addView(sub(line.optInt("qty")+" × "+money(line.optLong("price"))));
            row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(tv(money(line.optInt("qty")*line.optLong("price")),14,NAVY,true));items.addView(row);
            CheckBox discount=null;TextView reduction=null;
            if (line.optDouble("productPercent",0)>0) {
                discount=new CheckBox(this);discount.setText("Diskon produk "+NumberFormat.getNumberInstance(new Locale("id","ID")).format(line.optDouble("productPercent"))+"%");discount.setChecked(true);
                LinearLayout discountRow=new LinearLayout(this);discountRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                discountRow.addView(discount,new LinearLayout.LayoutParams(0,-2,1));
                reduction=tv("",13,NAVY,true);discountRow.addView(reduction);items.addView(discountRow);
            }
            productReductions.add(reduction);
            productDiscounts.add(discount);
        }
        addCard(items);
        LinearLayout detail=card();detail.addView(title("Rincian transaksi"));
        type=spinner(detail,"Jenis order",names("types",""));
        customer=spinner(detail,"Pelanggan",names("people","Pelanggan umum"));
        promo=spinner(detail,"Diskon transaksi",names("discounts","Tanpa diskon"));
        if (!isStaff()) operator=spinner(detail,"Petugas",names("staff","Pemilik outlet"));
        else detail.addView(detailRow("Petugas",tv(username(),14,NAVY,true)));
        points=input("Tukar poin (1 poin = Rp100)",InputType.TYPE_CLASS_NUMBER);
        points.setText("0");detail.addView(detailRow("Tukar poin · 1 poin = Rp100",points));
        addCard(detail);
        LinearLayout pay=card();pay.addView(title("Pembayaran"));
        methods=new LinearLayout(this);pay.addView(methods);
        payment=column();pay.addView(payment);
        addCard(pay);
        LinearLayout breakdown=card();
        subtotalValue=amountRow(breakdown,"Subtotal",false);discountValue=amountRow(breakdown,"Diskon",false);
        taxValue=amountRow(breakdown,"Pajak",false);pointsValue=amountRow(breakdown,"Tukar poin",false);addCard(breakdown);
        cash=input("Rp 0",InputType.TYPE_CLASS_NUMBER);
        reference=input("Referensi QRIS",InputType.TYPE_CLASS_TEXT);
        if (state!=null) {
            type.setSelection(state.getInt("type"));customer.setSelection(state.getInt("customer"));promo.setSelection(state.getInt("promo"));
            if (operator!=null) operator.setSelection(state.getInt("operator"));paymentMethod=Math.max(0,Math.min(2,state.getInt("method")));
            points.setText(state.getString("points","0"));cash.setText(state.getString("cash",""));reference.setText(state.getString("ref",""));
            boolean[] enabled=state.getBooleanArray("product_discounts");
            if(enabled!=null) for(int i=0;i<Math.min(enabled.length,productDiscounts.size());i++) if(productDiscounts.get(i)!=null)productDiscounts.get(i).setChecked(enabled[i]);
        }
        for(CheckBox box:productDiscounts) if(box!=null)box.setOnCheckedChangeListener((v,on)->refresh());
        react(customer);react(promo);
        android.text.TextWatcher watcher=new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void onTextChanged(CharSequence s,int a,int b,int c){refresh();}
            public void afterTextChanged(android.text.Editable s){}
        };
        points.addTextChangedListener(watcher);cash.addTextChangedListener(watcher);
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
    private int surface(){return MerchantAppSettings.isDarkMode(this)?0xff17263a:Color.WHITE;}
    private LinearLayout detailRow(String label,android.view.View field){
        LinearLayout outer=column();
        LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(45));
        row.addView(tv(label,13,NAVY,true),new LinearLayout.LayoutParams(0,-2,0.82f));
        row.addView(field,new LinearLayout.LayoutParams(0,-2,1.18f));
        outer.addView(row);android.view.View line=new android.view.View(this);
        line.setBackgroundColor(MerchantAppSettings.isDarkMode(this)?0xff3a4a5f:0xffe0e8f1);
        outer.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));return outer;
    }
    private TextView amountRow(LinearLayout host,String label,boolean strong){
        LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(3),dp(4),dp(3),dp(4));
        row.addView(tv(label,strong?16:13,NAVY,strong),new LinearLayout.LayoutParams(0,-2,1));
        TextView number=tv("",strong?23:14,strong?BLUE:NAVY,strong);row.addView(number);host.addView(row);return number;
    }
    private Spinner spinner(LinearLayout host,String label,String[] values){
        Spinner picker=new Spinner(this);picker.setContentDescription(label);picker.setMinimumHeight(dp(44));
        picker.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values){
            @Override public android.view.View getView(int position,android.view.View recycled,android.view.ViewGroup parent){
                TextView value=tv(getItem(position),14,NAVY,false);value.setGravity(android.view.Gravity.END|android.view.Gravity.CENTER_VERTICAL);
                value.setMinLines(1);value.setMaxLines(3);value.setPadding(dp(3),dp(4),dp(4),dp(4));return value;
            }
        });host.addView(detailRow(label,picker));return picker;
    }
    private void react(Spinner spinner){
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){refresh();}
        });
    }
    private void updateMethod(){
        methods.removeAllViews();
        String[] labels={"Tunai","QRIS","Kasbon"};
        for(int i=0;i<labels.length;i++){
            final int choice=i;
            Button button=i==paymentMethod?btn(labels[i]):outlineBtn(labels[i]);
            button.setBackground(stroke(i==paymentMethod?0xff0756c7:surface(),0xffb8cee6,dp(8)));
            button.setTextColor(i==paymentMethod?Color.WHITE:NAVY);button.setElevation(0);
            button.setPadding(0,0,0,0);button.setMinWidth(0);button.setSelected(i==paymentMethod);
            button.setOnClickListener(v->{if(paymentMethod!=choice){paymentMethod=choice;updateMethod();refresh();}});
            methods.addView(button,new LinearLayout.LayoutParams(0,-2,1));
        }
        if(cash.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)cash.getParent()).removeView(cash);
        if(reference.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)reference.getParent()).removeView(reference);
        payment.removeAllViews();change=null;
        if(paymentMethod==0){
            payment.addView(sub("Uang diterima"));
            LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.addView(cash,new LinearLayout.LayoutParams(0,-2,1));
            Button exact=outlineBtn("Uang pas");exact.setOnClickListener(v->{
                try{cash.setText(Long.toString(quote().optLong("total")));}catch(Exception error){toast("Periksa rincian transaksi.");}
            });
            exact.setMinimumWidth(0);exact.setMinWidth(0);
            row.addView(exact,new LinearLayout.LayoutParams(dp(91),-2));payment.addView(row);
            LinearLayout changeCard=column();changeCard.setPadding(dp(7),dp(2),dp(7),dp(2));
            changeCard.setBackground(round(MerchantAppSettings.isDarkMode(this)?0xff1b3731:0xffe4f5eb,dp(8)));
            change=amountRow(changeCard,"Kembalian",false);payment.addView(changeCard);
        } else if(paymentMethod==1){
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
            line.remove("productPercent");line.remove("photo");line.remove("image");
            line.put("productDiscount",reduction).put("useProductDiscount",option!=null&&option.isChecked());
            finalLines.put(line);
        }
        long discount=productDiscount+Math.round((subtotal-productDiscount)*rate/100);
        long total=subtotal-discount+tax-count*100;
        if(total<0)throw new IllegalArgumentException("Poin melebihi jumlah tagihan.");
        return new JSONObject().put("items",finalLines).put("subtotal",subtotal).put("discount",discount).put("tax",tax)
                .put("redeem",count*100).put("total",total);
    }
    private void refresh(){
        try{
            JSONObject q=quote();long total=q.optLong("total");
            subtotalValue.setText(money(q.optLong("subtotal")));
            discountValue.setText(q.optLong("discount")==0?money(0):"−"+money(q.optLong("discount")));
            taxValue.setText(money(q.optLong("tax")));pointsValue.setText(money(q.optLong("redeem")));
            totalValue.setText(money(total));
            for(int i=0;i<productReductions.size();i++){
                TextView label=productReductions.get(i);
                if(label!=null)label.setText("−"+money(q.optJSONArray("items").optJSONObject(i).optLong("productDiscount")));
            }
            if(change!=null){
                String input=cash.getText().toString().trim();
                long received=input.isEmpty()?0:Long.parseLong(input);
                change.setText(input.isEmpty()?"—":received<total?"Kurang "+money(total-received):money(received-total));
                change.setTextColor(received>=total?0xff19834e:0xffb54b33);
            }
        }catch(Exception invalid){totalValue.setText("—");if(change!=null)change.setText("—");}
    }
    private void submit(){
        try{
            JSONObject sale=quote(),buyer=chosen("people",customer,true);
            String mode=new String[]{"cash","qris","debt"}[paymentMethod];
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
                .put("staffKey",isStaff()?"login:"+username():operator.getSelectedItemPosition()==0?"owner":"staff:"+chosen("staff",operator,true).optString("id"))
                .put("typeId",chosen("types",type,false).optString("id"))
                .put("promoId",chosen("discounts",promo,true).optString("id"))
                .put("type",chosen("types",type,false).optString("name")).put("outlet",data.optString("outlet_name"))
                .put("cash",paid).put("reference","qris".equals(mode)?ref:"");
            setResult(RESULT_OK,new Intent().putExtra(SALE,sale.toString()).putExtra("outlet",data.optString("outlet"))
                    .putExtra("revision",data.optInt("revision")));finish();
        }catch(Exception invalid){toast(invalid instanceof IllegalArgumentException&&!(invalid instanceof NumberFormatException)?invalid.getMessage():"Periksa isian pembayaran.");}
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);
        if(data==null||points==null)return;
        state.putInt("type",type.getSelectedItemPosition());state.putInt("customer",customer.getSelectedItemPosition());
        state.putInt("promo",promo.getSelectedItemPosition());state.putInt("method",paymentMethod);
        if(operator!=null)state.putInt("operator",operator.getSelectedItemPosition());
        state.putString("points",points.getText().toString());state.putString("cash",cash.getText().toString());state.putString("ref",reference.getText().toString());
        boolean[] enabled=new boolean[productDiscounts.size()];
        for(int i=0;i<enabled.length;i++)enabled[i]=productDiscounts.get(i)!=null&&productDiscounts.get(i).isChecked();
        state.putBooleanArray("product_discounts",enabled);
    }
}
