package com.transiva.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public final class MerchantPosActivity extends MerchantBaseActivity {
    private static final int INK=0xff0b1530, ACCENT=0xff0756c7, PAGE=0xfff2f6fc, LINE=0xffd6dfeb;
    private static final int IMPORT=718, EXPORT=719, QR=720, PRODUCT_EDITOR=721, CHECKOUT=722, LIMIT=300000;
    private static final String[] BOOK_LISTS={"products","sales","people","staff","attendance","materials","expenses","payments","surveys","discounts","types"};
    private static final String[][] SECTIONS={{"Kasir","home","sale","history","cash"},{"Produk","products","materials","discounts","types"},
            {"Tim & Pelanggan","staff","attendance","people","surveys"},{"Outlet","report","outlets","qris","plan"}};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String,Integer> cart=new LinkedHashMap<>();
    private final NumberFormat numbers=NumberFormat.getIntegerInstance(new Locale("id","ID"));
    private FrameLayout shell, overlay;
    private LinearLayout body, content, dock;
    private ScrollView scroll;
    private JSONArray branches=new JSONArray(), catalog=new JSONArray();
    private JSONObject book, plan, pending;
    private Runnable afterSave;
    private String outlet="", screen="home", receipt="", query="", importKind="", exportText="";
    private int revision, historyLimit=50, catalogLimit=50;
    private boolean busy;
    private View checkoutBar;
    private String saleFilter="Semua";
    private boolean csvExpanded;
    private Intent deferredProduct, deferredCheckout;
    private final MerchantPosPrinters printers=new MerchantPosPrinters(this);
    private String deferredBarcode;
    private LinearLayout saleResults;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (sessionManager.getToken().isEmpty()) { finish(); return; }
        if (state!=null) {
            outlet=state.getString("pos_outlet","");
            screen=state.getString("pos_screen","home");receipt=state.getString("pos_receipt","");
            query=state.getString("pos_query","");saleFilter=state.getString("pos_filter","Semua");
            Bundle savedCart=state.getBundle("pos_cart");
            if (savedCart!=null) for (String id:savedCart.keySet()) cart.put(id,savedCart.getInt(id));
            exportText=state.getString("pos_export","");
            importKind=state.getString("pos_import","");
        }
        shell=new FrameLayout(this);
        shell.setBackgroundColor(PAGE);
        setContentView(shell);
        ViewCompat.setOnApplyWindowInsetsListener(shell,(view,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            Insets ime=insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));
            boolean keyboard=insets.isVisible(WindowInsetsCompat.Type.ime());
            if (dock!=null) dock.setVisibility(keyboard?View.GONE:View.VISIBLE);
            if (checkoutBar!=null) checkoutBar.setVisibility(keyboard?View.GONE:View.VISIBLE);
            return insets;
        });
        MerchantWindowInsets.requestWhenAttached(shell);
        load(true);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("pos_outlet",outlet);
        state.putString("pos_screen",screen);state.putString("pos_receipt",receipt);
        state.putString("pos_query",query);state.putString("pos_filter",saleFilter);
        if (cart.size()<=300) {
            Bundle savedCart=new Bundle();
            for (Map.Entry<String,Integer> entry:cart.entrySet()) savedCart.putInt(entry.getKey(),entry.getValue());
            state.putBundle("pos_cart",savedCart);
        }
        state.putString("pos_export",exportText);
        state.putString("pos_import",importKind);
        super.onSaveInstanceState(state);
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (shell!=null) shell.post(()->{if (alive()) render();});
    }
    @Override protected void onDestroy() { printers.close(); worker.shutdown(); super.onDestroy(); }
    private boolean alive() { return !isDestroyed() && !isFinishing(); }

    private void load(boolean refreshOutlets) {
        if (busy || pending!=null) return;
        busy=true;
        render();
        final String chosen=outlet.isEmpty()?branchId():outlet;
        final JSONArray known=branches;
        worker.execute(()->{
            try {
                JSONArray available=known;
                if (refreshOutlets || available.length()==0)
                    available=checked(get(BASE+"merchant_branches.php")).getJSONArray("branches");
                if (available.length()==0) throw new IOException("Outlet belum tersedia.");
                JSONObject match=find(available,"id",chosen);
                String selected=(match==null?available.getJSONObject(0):match).getString("id");
                JSONObject result=checked(get(endpoint(selected)));
                if (!result.has("catalog")) throw new IOException("Pasang pembaruan server untuk sinkronisasi menu.");
                JSONArray updated=available;
                runOnUiThread(()->{
                    if (!alive()) return;
                    branches=updated;
                    if (!outlet.isEmpty()&&!outlet.equals(selected)) cart.clear();
                    outlet=selected;
                    book=normalize(result.optJSONObject("book"));
                    plan=result.optJSONObject("plan");
                    catalog=array(result,"catalog");
                    revision=result.optInt("revision");
                    busy=false;
                    cart.entrySet().removeIf(entry->!canSell(product(book,entry.getKey()),entry.getValue()));
                    render();
                    Intent nextProduct=deferredProduct;deferredProduct=null;
                    if (nextProduct!=null) receiveProduct(nextProduct);
                    Intent nextCheckout=deferredCheckout;deferredCheckout=null;
                    if (nextCheckout!=null) receiveCheckout(nextCheckout);
                    String nextBarcode=deferredBarcode;deferredBarcode=null;
                    if (nextBarcode!=null) scanResult(nextBarcode,true);
                });
            } catch (Exception failure) {
                runOnUiThread(()->{
                    if (!alive()) return;
                    busy=false; book=null;
                    render();
                    content.addView(text(message(failure,"Kasir belum dapat dimuat."),14,INK,false));
                    place(content,action("Coba lagi",0,()->load(true),true),10);
                });
            }
        });
    }
    private String endpoint(String id) { return BASE+"merchant_pos.php?outlet="+Uri.encode(id); }
    private JSONObject checked(String raw) throws Exception {
        JSONObject result=new JSONObject(raw);
        if (!result.optBoolean("success")) throw new IOException(result.optString("message","Permintaan gagal."));
        return result;
    }
    private interface Edit { void apply(JSONObject target) throws Exception; }
    private void commit(Edit edit,Runnable success) {
        if (busy || pending!=null || book==null) return;
        try {
            JSONObject next=new JSONObject(book.toString());
            edit.apply(next);
            JSONObject payload=new JSONObject().put("revision",revision).put("request",UUID.randomUUID().toString()).put("book",next);
            if (payload.toString().getBytes(StandardCharsets.UTF_8).length>1950000) throw new IllegalArgumentException("Kapasitas data outlet tercapai.");
            pending=payload;
            afterSave=success;
            save();
        } catch (Exception error) { alert("Kasir",message(error,"Data belum bisa disimpan.")); }
    }
    private void save() {
        if (pending==null || busy) return;
        JSONObject sent=pending;
        String selected=outlet;
        busy=true;
        render();
        worker.execute(()->{
            try {
                JSONObject result=new JSONObject(postJson(endpoint(selected),sent));
                runOnUiThread(()->{
                    if (!alive()) return;
                    busy=false;
                    String code=result.optString("code");
                    if (!result.optBoolean("success")) {
                        if (code.isEmpty() && result.optString("message").contains("belum dapat terhubung")) { render(); return; }
                        pending=null; afterSave=null;
                        toast(result.optString("message","Data ditolak server."));
                        if ("POS_CONFLICT".equals(code)||"POS_CATALOG_CHANGED".equals(code)||"PREMIUM_REQUIRED".equals(code)) {
                            cart.clear(); load(false);
                        } else render();
                        return;
                    }
                    book=sent.optJSONObject("book");
                    revision=result.optInt("revision");
                    pending=null;
                    Runnable done=afterSave;
                    afterSave=null;
                    if (done!=null) done.run();
                    render();
                    toast("Data tersimpan.");
                });
            } catch (Exception failure) {
                runOnUiThread(()->{ if (alive()) { busy=false; render(); } });
            }
        });
    }
    private static JSONObject normalize(JSONObject source) {
        JSONObject result=source==null?new JSONObject():source;
        for (String key:BOOK_LISTS) if (result.optJSONArray(key)==null) put(result,key,new JSONArray());
        if (array(result,"types").length()==0) {
            array(result,"types").put(record("here","Makan di tempat")).put(record("take","Bawa pulang"));
        }
        return result;
    }
    private static void put(JSONObject obj,String key,Object value) {
        try { obj.put(key,value); } catch (org.json.JSONException e) { throw new IllegalArgumentException(e); }
    }
    private static JSONObject record(String id,String name) {
        JSONObject row=new JSONObject(); put(row,"id",id); put(row,"name",name); return row;
    }
    private static JSONArray array(JSONObject obj,String key) {
        JSONArray rows=obj==null?null:obj.optJSONArray(key); return rows==null?new JSONArray():rows;
    }
    private static List<JSONObject> rows(JSONArray list) {
        List<JSONObject> records=new ArrayList<>();
        for (int i=0;i<list.length();i++) if (list.optJSONObject(i)!=null) records.add(list.optJSONObject(i));
        return records;
    }
    private static JSONObject find(JSONArray rows,String key,String value) {
        for (int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);
            if (row!=null && value.equals(row.optString(key))) return row;
        }
        return null;
    }
    private static String label(JSONObject row,String fallback) { return row==null?fallback:row.optString("name",fallback); }
    private static String message(Exception error,String fallback) {
        return error instanceof IOException || error instanceof IllegalArgumentException
                ? (error.getMessage()==null?fallback:error.getMessage()):fallback;
    }
    private String rup(long value) { return "Rp "+numbers.format(value); }
    private String day(long timestamp) { return format("yyyy-MM-dd",timestamp); }
    private String format(String style,long timestamp) {
        SimpleDateFormat f=new SimpleDateFormat(style,new Locale("id","ID"));
        f.setTimeZone(TimeZone.getTimeZone("Asia/Makassar")); return f.format(new Date(timestamp));
    }
    private String outletName() { return label(find(branches,"id",outlet),"Outlet aktif"); }
    private LinearLayout vertical() { LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view; }
    private LinearLayout horizontal() { LinearLayout view=new LinearLayout(this);view.setGravity(Gravity.CENTER_VERTICAL);return view; }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=tv(value,size,color,bold);t.setTextColor(color);t.setIncludeFontPadding(false);return t;
    }
    private void pad(View view,int h,int v) { view.setPadding(dp(h),dp(v),dp(h),dp(v)); }
    private void place(LinearLayout host,View child,int gap) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(gap);host.addView(child,lp);
    }
    private void weight(LinearLayout host,View child,int margin) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.setMarginStart(dp(margin));host.addView(child,lp);
    }
    private LinearLayout card() {
        LinearLayout v=vertical();v.setBackground(stroke(Color.WHITE,LINE,dp(9)));pad(v,11,9);return v;
    }
    private View action(String label,int resource,Runnable click,boolean primary) {
        return action(label,resource,click,primary?ACCENT:Color.WHITE,primary?Color.WHITE:INK,primary?ACCENT:LINE,44);
    }
    private View action(String label,int resource,Runnable click,int bg,int color,int border,int height) {
        LinearLayout row=horizontal();row.setMinimumHeight(dp(height));pad(row,9,4);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2572a6fc),stroke(bg,border,dp(8)),null));
        if (resource!=0) row.addView(icon(resource,color),new LinearLayout.LayoutParams(dp(19),dp(19)));
        if (!label.isEmpty()) {
            TextView t=text(label,14,color,true);
            if (resource!=0) t.setPadding(dp(8),0,0,0);
            row.addView(t,new LinearLayout.LayoutParams(-1,-2));
        }
        row.setOnClickListener(v->click.run());row.setFocusable(true);
        ViewCompat.setAccessibilityDelegate(row,new androidx.core.view.AccessibilityDelegateCompat(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,androidx.core.view.accessibility.AccessibilityNodeInfoCompat info){
                super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());
            }
        });
        return row;
    }
    private ImageView icon(int resource,int color) {
        ImageView image=new ImageView(this);image.setImageResource(resource);image.setImageTintList(ColorStateList.valueOf(color));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return image;
    }
    private int glyph(String key) {
        switch(key) {
            case "home": return R.drawable.ic_merchant_home;
            case "sale": case "plan": return R.drawable.ic_merchant_receipt;
            case "history": case "attendance": return R.drawable.ic_merchant_activity;
            case "cash": case "qris": return R.drawable.ic_merchant_wallet;
            case "products": case "materials": return R.drawable.ic_merchant_package;
            case "discounts": case "types": return R.drawable.ic_merchant_tag;
            case "staff": return R.drawable.ic_merchant_profile;
            case "people": return R.drawable.ic_merchant_followers;
            case "surveys": return R.drawable.ic_merchant_reviews;
            case "report": return R.drawable.ic_merchant_analytics;
            default: return R.drawable.ic_merchant_store;
        }
    }
    private String titleOf(String key) {
        switch(key) {
            case "home":return "Ringkasan"; case "sale":return "Transaksi"; case "history":return "Riwayat transaksi";
            case "cash":return "Rekap kas"; case "products":return "Produk & stok"; case "materials":return "Bahan baku";
            case "discounts":return "Diskon"; case "types":return "Tipe order"; case "staff":return "Pegawai";
            case "attendance":return "Absensi"; case "people":return "Pelanggan"; case "surveys":return "Survei pelanggan";
            case "report":return "Laporan penjualan"; case "outlets":return "Outlet"; case "qris":return "QRIS outlet";
            case "plan":return "Langganan Kasir"; default:return "Struk";
        }
    }
    private void go(String next) {
        if (busy || pending!=null || book==null) return;
        closeDrawer();screen=next;
        if ("sale".equals(next) && cart.isEmpty()) load(false); else render();
    }
    private void render() {
        shell.removeAllViews();overlay=null;checkoutBar=null;saleResults=null;
        body=vertical();
        int max=dp(600);
        FrameLayout.LayoutParams frame=new FrameLayout.LayoutParams(Math.min(shell.getWidth()>0?shell.getWidth()-shell.getPaddingLeft()-shell.getPaddingRight():dp(screenWidthDp()),max),-1,Gravity.CENTER_HORIZONTAL);
        shell.addView(body,frame);
        LinearLayout top=horizontal();
        top.setBackgroundColor("sale".equals(screen)?INK:Color.WHITE);pad(top,10,3);
        int topColor="sale".equals(screen)?INK:Color.WHITE, foreground="sale".equals(screen)?Color.WHITE:INK;
        View menu=action("",R.drawable.ic_merchant_menu,this::drawer,topColor,foreground,topColor,44);
        menu.setContentDescription("Buka menu");top.addView(menu,new LinearLayout.LayoutParams(dp(44),-2));
        weight(top,text("Kasir Transiva",20,foreground,true),4);
        top.addView(action("‹  Merchant",0,this::exit,topColor,"sale".equals(screen)?Color.WHITE:ACCENT,0xff8ab1eb,44));
        body.addView(top);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=vertical();pad(content,12,10);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        if (book!=null) {
            LinearLayout outletCard=horizontal();
            outletCard.setBackground(stroke(Color.WHITE,LINE,dp(9)));pad(outletCard,11,7);
            outletCard.addView(icon(glyph("outlets"),ACCENT),new LinearLayout.LayoutParams(dp(25),dp(25)));
            LinearLayout labels=vertical();
            labels.addView(text(outletName(),16,INK,true));place(labels,text("●  Outlet aktif",12,0xff157155,false),3);
            weight(outletCard,labels,10);
            if (!isStaff()) outletCard.addView(text("⌄",18,INK,true));
            outletCard.setMinimumHeight(dp(52));
            if (!isStaff()) {outletCard.setOnClickListener(v->chooseOutlet());outletCard.setFocusable(true);}
            content.addView(outletCard);
        }
        if (busy || pending!=null) {
            place(content,text(busy?"Memproses data Kasir…":"Status penyimpanan belum pasti",16,INK,true),12);
            if (!busy) place(content,action("Periksa penyimpanan",0,this::save,true),8);
        } else if (book!=null) {
            if (!"staff".equals(screen)) place(content,text(titleOf(screen),22,INK,true),13);
            renderScreen();
        }
        if (checkoutBar!=null) body.addView(checkoutBar);
        dock=horizontal();dock.setBackgroundColor(Color.WHITE);
        for (String key:new String[]{"home","sale","history"}) {
            boolean selected=key.equals(screen);
            LinearLayout tab=vertical();tab.setGravity(Gravity.CENTER_HORIZONTAL);tab.setMinimumHeight(dp(52));
            View marker=new View(this);marker.setBackgroundColor(selected?ACCENT:Color.TRANSPARENT);
            tab.addView(marker,new LinearLayout.LayoutParams(dp(72),dp(2)));
            ImageView glyph=icon(glyph(key),selected?ACCENT:INK);
            LinearLayout.LayoutParams iconLayout=new LinearLayout.LayoutParams(dp(19),dp(19));iconLayout.topMargin=dp(5);tab.addView(glyph,iconLayout);
            place(tab,center(text("history".equals(key)?"Riwayat":titleOf(key),12,selected?ACCENT:INK,selected)),3);
            tab.setOnClickListener(v->go(key));tab.setFocusable(true);tab.setSelected(selected);
            weight(dock,tab,0);
        }
        body.addView(dock);
        ViewCompat.requestApplyInsets(shell);
    }
    private TextView center(TextView text) { text.setGravity(Gravity.CENTER);return text; }
    private void drawer() {
        if (busy || pending!=null || book==null || overlay!=null) return;
        overlay=new FrameLayout(this);overlay.setBackgroundColor(0xb60c2246);
        overlay.setOnClickListener(v->closeDrawer());shell.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout panel=vertical();panel.setBackgroundColor(Color.WHITE);panel.setClickable(true);
        int available=shell.getWidth()-shell.getPaddingLeft()-shell.getPaddingRight();
        overlay.addView(panel,new FrameLayout.LayoutParams(Math.min(dp(390),(int)(available*.79f)),-1,Gravity.START));
        LinearLayout heading=horizontal();pad(heading,13,6);
        LinearLayout identity=vertical();identity.addView(text("Kasir Transiva",21,INK,true));place(identity,text(outletName(),14,INK,false),3);
        weight(heading,identity,0);
        View close=action("×",0,this::closeDrawer,Color.WHITE,INK,Color.WHITE,44);
        close.setContentDescription("Tutup menu");heading.addView(close,new LinearLayout.LayoutParams(dp(42),-2));panel.addView(heading);
        ScrollView list=new ScrollView(this);panel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout links=vertical();pad(links,10,3);list.addView(links);
        for (String[] section:SECTIONS) {
            View separator=new View(this);separator.setBackgroundColor(LINE);links.addView(separator,new LinearLayout.LayoutParams(-1,dp(1)));
            TextView caption=text(section[0].toUpperCase(new Locale("id","ID")),11,0xff52617a,true);pad(caption,6,6);links.addView(caption);
            for (int i=1;i<section.length;i++) {
                String key=section[i];boolean active=screen.equals(key);
                View entry=action(titleOf(key),glyph(key),()->go(key),active?0xffdceaff:Color.WHITE,active?ACCENT:INK,
                        active?0xffdceaff:Color.WHITE,44);
                entry.setSelected(active);links.addView(entry);
            }
        }
        body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        close.requestFocus();
    }
    private void closeDrawer() {
        if (overlay==null) return;
        shell.removeView(overlay);overlay=null;body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
    private void chooseOutlet() {
        if (isStaff() || busy || pending!=null || branches.length()==0) return;
        List<JSONObject> available=rows(branches);
        String[] names=available.stream().map(item->label(item,"Outlet")).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("Pilih outlet").setItems(names,(dialog,index)->{
            String selected=available.get(index).optString("id");
            if (selected.equals(outlet)) return;
            Runnable switchOutlet=()->{cart.clear();outlet=selected;screen="home";query="";load(false);};
            if (cart.isEmpty()) switchOutlet.run();
            else confirm("Pindah outlet?","Keranjang saat ini akan dikosongkan.",switchOutlet);
        }).show();
    }
    private void confirm(String title,String detail,Runnable run) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(detail).setNegativeButton("Batal",null)
                .setPositiveButton("Lanjut",(d,w)->run.run()).show();
    }
    private void exit() {
        if (busy || pending!=null) { toast("Selesaikan penyimpanan terlebih dahulu.");return; }
        if (cart.isEmpty()) finish(); else confirm("Keluar dari Kasir?","Keranjang saat ini akan dikosongkan.",this::finish);
    }
    @Override public void onBackPressed() {
        if (overlay!=null) closeDrawer();
        else if (busy || pending!=null) toast("Selesaikan penyimpanan terlebih dahulu.");
        else if ("receipt".equals(screen)) go("history");
        else if (!"home".equals(screen) && book!=null) go("home");
        else exit();
    }

    private void renderScreen() {
        switch(screen) {
            case "home": home();break;
            case "sale": sale();break;
            case "history": history();break;
            case "receipt": receipt();break;
            case "products": products();break;
            case "cash": cash();break;
            case "report": report();break;
            case "attendance": attendance();break;
            case "staff": employees();break;
            case "outlets": outlets();break;
            case "qris": qris();break;
            case "plan": plan();break;
            case "surveys": surveys();break;
            default: entities(screen);
        }
    }
    private List<JSONObject> productsList() {
        List<JSONObject> products=rows(catalog);products.addAll(rows(array(book,"products")));return products;
    }
    private long sum(JSONArray list,String field,String datePrefix) {
        long total=0;
        for (JSONObject item:rows(list))
            if (datePrefix.isEmpty()||day(item.optLong("at")).startsWith(datePrefix)) total+=item.optLong(field);
        return total;
    }
    private JSONArray reportSales() {
        JSONArray sales=new JSONArray();
        for (JSONObject sale:rows(array(book,"sales"))) {
            String date=day(sale.optLong("at"));
            if (plan==null || date.compareTo(plan.optString("starts_on"))>=0 && date.compareTo(plan.optString("ends_on"))<=0) sales.put(sale);
        }
        return sales;
    }
    private long due(JSONObject sale,JSONObject data) {
        if (!"debt".equals(sale.optString("method"))) return 0;
        long amount=sale.optLong("total");
        for (JSONObject payment:rows(array(data,"payments"))) if (sale.optString("id").equals(payment.optString("sale"))) amount-=payment.optLong("amount");
        return Math.max(0,amount);
    }
    private void home() {
        String now=day(System.currentTimeMillis());
        JSONArray sales=reportSales();
        LinearLayout hero=vertical();pad(hero,14,11);
        GradientDrawable blue=new GradientDrawable(GradientDrawable.Orientation.BL_TR,new int[]{0xff061d50,0xff063a9d,0xff0873ec});
        blue.setCornerRadius(dp(12));hero.setBackground(blue);
        hero.addView(text("Penjualan hari ini",15,Color.WHITE,true));
        place(hero,text(rup(sum(sales,"total",now)),32,Color.WHITE,true),4);
        View start=action("Mulai transaksi",glyph("sale"),()->go("sale"),Color.WHITE,ACCENT,Color.WHITE,44);
        ((LinearLayout)start).setGravity(Gravity.CENTER);
        ((LinearLayout)start).getChildAt(1).setLayoutParams(new LinearLayout.LayoutParams(-2,-2));
        place(hero,start,10);
        place(content,hero,12);
        long debt=0, low=0;
        for (JSONObject sale:rows(array(book,"sales"))) debt+=due(sale,book);
        for (JSONObject product:productsList()) if (!product.isNull("stock") && product.optLong("stock")<=5) low++;
        LinearLayout stats=horizontal();
        weight(stats,metric("Penjualan bulan ini",rup(sum(sales,"total",now.substring(0,7))),glyph("cash")),0);
        weight(stats,metric("Kasbon belum lunas",rup(debt),glyph("sale")),7);
        place(content,stats,9);
        LinearLayout stock=(LinearLayout)action("Stok rendah",glyph("products"),()->go("products"),Color.WHITE,INK,LINE,43);
        stock.getChildAt(1).setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        stock.addView(text(low+" produk  ›",13,INK,false));place(content,stock,7);
        place(content,text("Kelola outlet",19,INK,true),13);
        String[] keys={"products","cash","report","people","staff","outlets"};
        for (int i=0;i<keys.length;i+=2) {
            LinearLayout row=horizontal();
            tile(row,keys[i],0);tile(row,keys[i+1],7);place(content,row,6);
        }
    }
    private void tile(LinearLayout row,String key,int gap) {
        View tile=action(titleOf(key),glyph(key),()->go(key),Color.WHITE,INK,LINE,44);
        ((ImageView)((LinearLayout)tile).getChildAt(0)).setImageTintList(ColorStateList.valueOf(ACCENT));
        weight(row,tile,gap);
    }
    private View metric(String key,String amount,int glyph) {
        LinearLayout line=horizontal();pad(line,8,8);line.setMinimumHeight(dp(53));
        line.setBackground(stroke(Color.WHITE,LINE,dp(8)));
        if (glyph!=0) line.addView(icon(glyph,ACCENT),new LinearLayout.LayoutParams(dp(19),dp(19)));
        LinearLayout info=vertical();info.addView(text(key,11,INK,false));place(info,text(amount,15,INK,true),3);
        weight(line,info,glyph==0?0:7);return line;
    }
    private void item(LinearLayout target,String title,String subtitle,View control) {
        LinearLayout row=card();row.addView(text(title,15,INK,true));
        if (!subtitle.isEmpty()) place(row,text(subtitle,13,0xff43526a,false),3);
        if (control!=null) place(row,control,7);
        place(target,row,7);
    }
    private JSONObject product(JSONObject data,String id) {
        JSONObject linked=find(catalog,"id",id);return linked!=null?linked:find(array(data,"products"),"id",id);
    }
    private boolean canSell(JSONObject item,int qty) {
        return item!=null && item.optString("reason").isEmpty() && (item.isNull("stock") || item.optInt("stock")>=qty);
    }
    private String stock(JSONObject product) {
        return product.isNull("stock")?"Stok tidak dilacak":"Stok "+product.optLong("stock");
    }
    private void add(String id,int delta) {
        int amount=cart.getOrDefault(id,0)+delta;
        if (delta>0 && !canSell(product(book,id),amount)) {toast("Stok tidak mencukupi atau menu nonaktif.");return;}
        if (amount<=0) cart.remove(id); else cart.put(id,amount);
        if (saleResults==null || checkoutBar==null) {render();return;}
        fillSale(saleResults);
        int footerPosition=body.indexOfChild(checkoutBar);
        body.removeView(checkoutBar);checkoutBar=checkoutPanel();body.addView(checkoutBar,footerPosition);
        ViewCompat.requestApplyInsets(shell);
    }
    private void sale() {
        LinearLayout controls=horizontal();
        EditText search=input("Cari menu atau barcode",InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);search.setText(query);weight(controls,search,0);
        View scan=action("Scan",R.drawable.ic_merchant_search,this::scan,Color.WHITE,ACCENT,ACCENT,44);
        LinearLayout.LayoutParams scanParams=new LinearLayout.LayoutParams(-2,-2);scanParams.setMarginStart(dp(7));
        controls.addView(scan,scanParams);place(content,controls,8);
        LinearLayout filters=horizontal();
        LinearLayout results=vertical();saleResults=results;
        place(content,filters,6);content.addView(results);
        showSaleFilters(filters,results);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence value,int start,int count,int after){}
            public void onTextChanged(CharSequence value,int start,int before,int count){query=value.toString();catalogLimit=50;fillSale(results);}
            public void afterTextChanged(android.text.Editable value){}
        });
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((view,id,event)->{
            if (id==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {scanResult(search.getText().toString().trim(),false);return true;}
            return false;
        });
        fillSale(results);
        checkoutBar=checkoutPanel();
    }
    private void showSaleFilters(LinearLayout filters,LinearLayout results) {
        filters.removeAllViews();
        for (String label:new String[]{"Semua","Menu Merchant","Produk Kasir"}) {
            boolean selected=label.equals(saleFilter);
            View button=action(label,0,()->{saleFilter=label;catalogLimit=50;showSaleFilters(filters,results);fillSale(results);},
                    selected?ACCENT:Color.WHITE,selected?Color.WHITE:INK,selected?ACCENT:LINE,44);
            button.setSelected(selected);weight(filters,button,filters.getChildCount()==0?0:5);
        }
    }
    private View checkoutPanel() {
        LinearLayout panel=card();panel.setElevation(dp(4));
        int qty=0;long price=0;
        ArrayList<String> lines=new ArrayList<>();
        for (Map.Entry<String,Integer> entry:cart.entrySet()) {
            JSONObject product=product(book,entry.getKey());int amount=entry.getValue();
            if (product==null) continue;
            qty+=amount;
            long base=product.optLong("price")*amount;
            long net=base-Math.round(base*product.optDouble("posDiscount",0)/100);
            price+=net+Math.round(net*product.optDouble("tax",0)/100);
            lines.add(label(product,"Produk")+" × "+amount);
        }
        LinearLayout summary=horizontal();pad(summary,3,2);
        summary.setMinimumHeight(dp(36));
        summary.addView(icon(R.drawable.ic_merchant_bag,ACCENT),new LinearLayout.LayoutParams(dp(21),dp(21)));
        weight(summary,text("Keranjang · "+qty+" produk",14,INK,true),7);
        summary.addView(text(rup(price),16,INK,true));
        if (!lines.isEmpty()) {
            summary.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Keranjang")
                    .setItems(lines.toArray(new String[0]),null).setPositiveButton("Tutup",null).show());
            summary.setFocusable(true);
        }
        panel.addView(summary);
        View pay=action("Lanjut bayar",0,this::checkout,true);
        LinearLayout button=(LinearLayout)pay;button.setGravity(Gravity.CENTER);
        button.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(-2,-2));
        pay.setEnabled(qty>0);pay.setAlpha(qty>0?1f:.48f);place(panel,pay,4);
        return panel;
    }
    private void fillSale(LinearLayout results) {
        results.removeAllViews();
        int count=0;
        String filter=query.trim().toLowerCase(Locale.ROOT);
        for (JSONObject row:productsList()) {
            String id=row.optString("id");boolean linked=id.startsWith("menu:");
            if (("Menu Merchant".equals(saleFilter)&&!linked)||("Produk Kasir".equals(saleFilter)&&linked)) continue;
            String metadata=(label(row,"")+" "+row.optString("barcode")+" "+row.optString("category")).toLowerCase(Locale.ROOT);
            if (!metadata.contains(filter)) continue;
            if (++count>catalogLimit) {
                place(results,action("Tampilkan lebih banyak",0,()->{catalogLimit+=50;fillSale(results);},false),6);break;
            }
            LinearLayout wrapper=card();LinearLayout line=productLine(row,linked,false);
            int qty=cart.getOrDefault(id,0);
            if (qty>0 || canSell(row,1)) {
                LinearLayout countBox=horizontal();
                if (qty>0) {
                    countBox.addView(quantityButton("−","Kurangi "+label(row,"produk"),()->add(id,-1),true));
                    TextView countLabel=text(Integer.toString(qty),13,INK,true);pad(countLabel,4,0);countBox.addView(countLabel);
                }
                countBox.addView(quantityButton("+","Tambah "+label(row,"produk"),()->add(id,1),canSell(row,qty+1)));
                line.addView(countBox);
            }
            wrapper.addView(line);place(results,wrapper,7);
        }
        if (count==0) place(results,text("Produk belum ditemukan.",13,INK,false),9);
    }
    private View quantityButton(String symbol,String description,Runnable click,boolean enabled) {
        View result=action(symbol,0,click,Color.WHITE,ACCENT,LINE,44);
        result.setLayoutParams(new LinearLayout.LayoutParams(dp(44),-2));result.setContentDescription(description);
        result.setEnabled(enabled);result.setAlpha(enabled?1f:.48f);return result;
    }
    private Spinner select(LinearLayout form,String title,List<JSONObject> options) {
        TextView caption=text(title,13,INK,true);place(form,caption,8);
        Spinner picker=new Spinner(this);picker.setMinimumHeight(dp(44));picker.setContentDescription(title);
        String[] names=options.stream().map(row->label(row,"")).toArray(String[]::new);
        picker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));form.addView(picker);
        return picker;
    }
    private JSONObject selected(List<JSONObject> options,Spinner picker) {
        return options.get(Math.max(0,picker.getSelectedItemPosition()));
    }
    private LinearLayout formBody() { LinearLayout form=vertical();pad(form,16,8);return form; }
    private ScrollView formScroll(LinearLayout form) { ScrollView wrapper=new ScrollView(this);wrapper.addView(form);return wrapper; }
    private interface Submit { void apply() throws Exception; }
    private void ask(String title,LinearLayout inputs,String submit,Submit handler) {
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(formScroll(inputs))
                .setNegativeButton("Batal",null).setPositiveButton(submit,null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try { handler.apply();dialog.dismiss(); }
            catch (Exception error) {toast(message(error,"Data belum valid."));}
        });
    }
    private EditText field(LinearLayout parent,String label,String value,int type) {
        TextView caption=text(label,13,INK,true);place(parent,caption,8);
        EditText entry=input(label,type);entry.setText(value);entry.setSingleLine(true);entry.setSaveEnabled(false);parent.addView(entry);return entry;
    }
    private String entered(EditText field) { return field.getText().toString().trim(); }
    private String required(EditText field) {
        String value=entered(field);
        if (value.isEmpty() || value.length()>120) throw new IllegalArgumentException("Isi teks dengan panjang 1–120 karakter.");
        return value;
    }
    private long integer(EditText field,long minimum,long maximum) {
        try {long value=Long.parseLong(entered(field));if (value>=minimum && value<=maximum) return value;}
        catch(NumberFormatException ignored){}
        throw new IllegalArgumentException("Isi jumlah "+minimum+" sampai "+maximum+".");
    }
    private double decimal(EditText field,double maximum) {
        try {double value=Double.parseDouble(entered(field).replace(',','.'));if (value>=0 && value<=maximum) return value;}
        catch(NumberFormatException ignored){}
        throw new IllegalArgumentException("Isi angka dari 0 sampai "+maximum+".");
    }
    private void checkout() {
        if (cart.isEmpty()||busy||pending!=null) return;
        try {
            JSONArray lines=new JSONArray();
            for (Map.Entry<String,Integer> entry:cart.entrySet()) {
                JSONObject product=product(book,entry.getKey());
                if (!canSell(product,entry.getValue())) throw new IllegalArgumentException("Ketersediaan produk berubah.");
                lines.put(record(entry.getKey(),label(product,"Produk")).put("qty",entry.getValue())
                        .put("price",product.optLong("price")).put("tax",product.optDouble("tax",0))
                        .put("productPercent",product.optDouble("posDiscount",0)));
            }
            JSONObject data=new JSONObject().put("items",lines).put("outlet",outlet).put("revision",revision)
                    .put("outlet_name",outletName()).put("qr",book.optString("qrImage"));
            for (String key:new String[]{"types","people","discounts","staff"}) data.put(key,array(book,key));
            String payload=data.toString();
            if(payload.getBytes(StandardCharsets.UTF_8).length>550000)
                throw new IllegalArgumentException("Data checkout terlalu besar untuk dibuka. Kurangi jumlah produk pada keranjang.");
            startActivityForResult(new Intent(this,MerchantPosCheckoutActivity.class)
                    .putExtra(MerchantPosCheckoutActivity.DATA,payload),CHECKOUT);
        } catch(Exception failure){toast(message(failure,"Checkout belum dapat dibuka."));}
    }
    private void receiveCheckout(Intent result) {
        if (busy||book==null){deferredCheckout=result;return;}
        if (pending!=null||!outlet.equals(result.getStringExtra("outlet"))||revision!=result.getIntExtra("revision",-1)){
            toast("Data outlet berubah. Periksa keranjang sebelum melanjutkan.");return;
        }
        try {
            JSONObject sale=new JSONObject(result.getStringExtra(MerchantPosCheckoutActivity.SALE));
            commit(next->applySale(next,sale,sale.optLong("redeem")/100),()->{
                for (JSONObject line:rows(array(sale,"items"))) {
                    JSONObject linked=find(catalog,"id",line.optString("id"));
                    if (linked==null || linked.isNull("stock")) continue;
                    int stock=linked.optInt("stock")-line.optInt("qty");put(linked,"stock",stock);
                    if (stock==0) put(linked,"reason","Stok habis");
                }
                cart.clear();receipt=sale.optString("id");screen="receipt";
            });
        }catch(Exception failure){toast("Rincian checkout belum dapat disimpan.");}
    }
    private void applySale(JSONObject next,JSONObject sale,long points) {
        if (!sale.optString("reference").isEmpty()) {
            for (JSONObject older:rows(array(next,"sales"))) if (sale.optString("reference").equalsIgnoreCase(older.optString("reference")))
                throw new IllegalArgumentException("Referensi QRIS sudah tercatat.");
        }
        Map<String,Long> used=new LinkedHashMap<>();
        for (JSONObject line:rows(array(sale,"items"))) {
            String id=line.optString("id");int qty=line.optInt("qty");
            JSONObject local=find(array(next,"products"),"id",id);
            if (local==null) continue;
            if (!canSell(local,qty)) throw new IllegalArgumentException("Stok produk tidak cukup.");
            put(local,"stock",local.optInt("stock")-qty);
            JSONObject recipe=local.optJSONObject("recipe");
            if (recipe==null) continue;
            for (Iterator<String> keys=recipe.keys();keys.hasNext();) {
                String key=keys.next();long amount=Math.round(recipe.optDouble(key,0)*1000)*qty;
                used.put(key,used.getOrDefault(key,0L)+amount);
            }
        }
        for (Map.Entry<String,Long> use:used.entrySet()) {
            JSONObject material=find(array(next,"materials"),"id",use.getKey());
            long available=material==null?0:Math.round(material.optDouble("stock",0)*1000);
            if (material==null || available<use.getValue()) throw new IllegalArgumentException("Stok bahan "+label(material,"resep")+" tidak cukup.");
            put(material,"stock",(available-use.getValue())/1000.0);
        }
        JSONObject customer=find(array(next,"people"),"id",sale.optString("customer"));
        if (customer!=null) {
            long earned="debt".equals(sale.optString("method"))?0:sale.optLong("total")/10000;
            put(customer,"points",customer.optLong("points")-points+earned);
        }
        array(next,"sales").put(sale);
    }

    private void history() {
        List<JSONObject> list=rows(array(book,"sales"));Collections.reverse(list);
        if (list.isEmpty()) place(content,text("Belum ada transaksi.",14,INK,false),8);
        for (int i=0;i<Math.min(historyLimit,list.size());i++) {
            JSONObject sale=list.get(i);long outstanding=due(sale,book);
            item(content,rup(sale.optLong("total")),format("dd MMM yyyy HH:mm",sale.optLong("at"))+" · "+sale.optString("customerName")
                    +" · "+sale.optString("method").toUpperCase(Locale.ROOT)+(outstanding>0?" · Sisa "+rup(outstanding):""),
                    action("Struk",glyph("sale"),()->{receipt=sale.optString("id");go("receipt");},false));
        }
        if (list.size()>historyLimit) place(content,action("Lihat transaksi sebelumnya",0,()->{historyLimit+=50;render();},false),8);
    }
    private List<String> receiptLines(JSONObject sale) {
        List<String> lines=new ArrayList<>();
        lines.add("TRANSIVA · "+sale.optString("outlet"));lines.add("No. "+sale.optString("id"));
        lines.add(format("dd MMM yyyy HH:mm",sale.optLong("at")));lines.add(sale.optString("customerName")+" · "+sale.optString("type"));
        lines.add("Petugas "+sale.optString("staff"));lines.add(" ");
        for (JSONObject line:rows(array(sale,"items"))) {
            lines.add(line.optInt("qty")+" × "+label(line,"Produk")+"   "+rup(line.optInt("qty")*line.optLong("price")));
            if (line.optLong("productDiscount")>0) lines.add("  Diskon produk -"+rup(line.optLong("productDiscount")));
        }
        lines.add("Subtotal "+rup(sale.optLong("subtotal")));lines.add("Diskon "+rup(sale.optLong("discount")));
        lines.add("Pajak "+rup(sale.optLong("tax")));lines.add("Tukar poin "+rup(sale.optLong("redeem")));lines.add("Total "+rup(sale.optLong("total")));
        lines.add("Metode "+sale.optString("method").toUpperCase(Locale.ROOT));
        if ("cash".equals(sale.optString("method"))) {
            lines.add("Uang diterima "+rup(sale.optLong("cash")));lines.add("Kembalian "+rup(sale.optLong("cash")-sale.optLong("total")));
        }
        if ("debt".equals(sale.optString("method"))) lines.add("Sisa kasbon "+rup(due(sale,book)));
        if (!sale.optString("reference").isEmpty()) lines.add("Ref. QRIS "+sale.optString("reference"));
        return lines;
    }
    private void receipt() {
        JSONObject sale=find(array(book,"sales"),"id",receipt);
        if (sale==null) return;
        LinearLayout paper=card(), hero=horizontal(), summary=vertical();
        hero.setBackground(round(0xffe7f1ff,dp(10)));pad(hero,12,10);
        summary.addView(text("Total pembayaran",13,0xff43526a,false));
        place(summary,text(rup(sale.optLong("total")),28,INK,true),4);
        weight(hero,summary,0);
        String mode=sale.optString("method");
        TextView method=text("cash".equals(mode)?"Tunai":"debt".equals(mode)?"Kasbon":"QRIS",13,Color.WHITE,true);
        pad(method,9,5);method.setBackground(round(ACCENT,dp(20)));hero.addView(method);
        paper.addView(hero);
        place(paper,text(sale.optString("outlet"),18,INK,true),10);
        place(paper,text(format("dd MMM yyyy · HH:mm",sale.optLong("at")),13,INK,false),4);
        String id=sale.optString("id");
        String shortId=id.length()>15?id.substring(0,8)+"…"+id.substring(id.length()-5):id;
        LinearLayout identity=horizontal();
        weight(identity,text("No. "+shortId.toUpperCase(Locale.ROOT),12,0xff43526a,false),0);
        identity.addView(action("Salin",0,()->{
            android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);
            if(clipboard!=null){clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Nomor struk",id));toast("Nomor struk disalin.");}
        },Color.WHITE,ACCENT,LINE,44));
        place(paper,identity,1);
        place(paper,text(sale.optString("customerName")+" · "+sale.optString("type"),13,INK,false),3);
        place(paper,text("Petugas: "+sale.optString("staff"),13,INK,false),3);
        divider(paper);
        for (JSONObject line:rows(array(sale,"items"))) {
            long gross=line.optInt("qty")*line.optLong("price"), off=line.optLong("productDiscount");
            receiptRow(paper,label(line,"Produk"),rup(gross),true);
            place(paper,text(line.optInt("qty")+" × "+rup(line.optLong("price")),12,0xff43526a,false),2);
            if(off>0)receiptRow(paper,"Diskon produk","−"+rup(off),false);
        }
        divider(paper);
        receiptRow(paper,"Subtotal",rup(sale.optLong("subtotal")),false);
        long discount=sale.optLong("discount"), tax=sale.optLong("tax"), points=sale.optLong("redeem");
        if(discount==0&&tax==0&&points==0) receiptRow(paper,"Diskon, pajak & poin",rup(0),false);
        else {
            if(discount>0)receiptRow(paper,"Total diskon","−"+rup(discount),false);
            if(tax>0)receiptRow(paper,"Pajak",rup(tax),false);
            if(points>0)receiptRow(paper,"Tukar poin","−"+rup(points),false);
        }
        divider(paper);
        if("cash".equals(mode)){
            receiptRow(paper,"Uang diterima",rup(sale.optLong("cash")),false);
            receiptRow(paper,"Kembalian",rup(sale.optLong("cash")-sale.optLong("total")),true);
        } else if("debt".equals(mode)) receiptRow(paper,"Sisa kasbon",rup(due(sale,book)),true);
        else if(!sale.optString("reference").isEmpty()) receiptRow(paper,"Ref. QRIS",sale.optString("reference"),false);
        place(content,paper,8);
        place(content,action("Cetak struk",glyph("sale"),()->printers.choose(receiptLines(sale)),true),8);
        if (due(sale,book)>0) place(content,action("Catat pembayaran kasbon",0,()->payDebt(sale),false),7);
    }
    private void receiptRow(LinearLayout target,String label,String value,boolean bold){
        LinearLayout row=horizontal();
        weight(row,text(label,13,INK,bold),0);
        TextView amount=text(value,13,INK,bold);amount.setGravity(Gravity.END);
        amount.setMaxWidth(dp(140));row.addView(amount);
        place(target,row,5);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] result){
        super.onRequestPermissionsResult(request,permissions,result);
        if(request==MerchantPosPrinters.BLUETOOTH_PERMISSION)
            printers.permission(result.length>0&&result[0]==android.content.pm.PackageManager.PERMISSION_GRANTED);
    }
    private void payDebt(JSONObject sale) {
        long outstanding=due(sale,book);
        LinearLayout form=formBody();
        EditText amount=field(form,"Jumlah pembayaran tunai (Rp)",Long.toString(outstanding),InputType.TYPE_CLASS_NUMBER);
        ask("Pelunasan kasbon",form,"Simpan",()->{
            long payment=integer(amount,1,outstanding);
            commit(next->{
                long balance=due(sale,next);
                if (payment>balance) throw new IllegalArgumentException("Pembayaran melebihi sisa kasbon.");
                array(next,"payments").put(new JSONObject().put("id",UUID.randomUUID().toString())
                        .put("at",System.currentTimeMillis()).put("sale",sale.optString("id")).put("amount",payment));
                JSONObject customer=find(array(next,"people"),"id",sale.optString("customer"));
                if (customer!=null && payment==balance) put(customer,"points",customer.optLong("points")+sale.optLong("total")/10000);
            },null);
        });
    }
    private void products() {
        LinearLayout merchant=card(), header=horizontal(), info=vertical();
        info.addView(text("Menu Merchant",17,INK,true));place(info,text("Sinkron dari Merchant",12,0xff43526a,false),3);
        weight(header,info,0);
        header.addView(action("Segarkan",R.drawable.ic_merchant_refresh,()->load(false),Color.WHITE,ACCENT,ACCENT,44));
        merchant.addView(header);
        addProductRows(merchant,rows(catalog),true);
        place(content,merchant,8);
        LinearLayout local=card(), localHeader=horizontal();
        weight(localHeader,text("Produk khusus Kasir",17,INK,true),0);
        localHeader.addView(action("Tambah",R.drawable.ic_merchant_plus,()->openProduct(null),true));
        local.addView(localHeader);
        place(local,action(csvExpanded?"Kelola CSV  ⌃":"Kelola CSV  ⌄",glyph("sale"),()->{csvExpanded=!csvExpanded;render();},
                0xffeaf3ff,INK,0xffeaf3ff,44),7);
        if (csvExpanded) {
            place(local,action("Unduh template CSV",0,()->export("template-produk.csv",headings("products"),Collections.emptyList()),false),5);
            place(local,action("Impor CSV",0,()->{importKind="products";pickFile(IMPORT,"*/*");},false),5);
            place(local,action("Ekspor produk CSV",0,this::exportProducts,false),5);
        }
        addProductRows(local,rows(array(book,"products")),false);place(content,local,9);
    }
    private void exportProducts() {
        List<String[]> data=new ArrayList<>();
        for (JSONObject product:rows(array(book,"products"))) data.add(new String[]{label(product,""),product.optString("barcode"),
                product.optString("price"),Long.toString(product.optLong("stock")),product.optString("tax")});
        export("produk-kasir.csv",headings("products"),data);
    }
    private void divider(LinearLayout parent) {
        View line=new View(this);line.setBackgroundColor(LINE);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(1));params.topMargin=dp(7);
        parent.addView(line,params);
    }
    private void addProductRows(LinearLayout group,List<JSONObject> records,boolean linked) {
        if (records.isEmpty()) place(group,text(linked?"Belum ada menu Merchant.":"Belum ada produk khusus Kasir.",13,INK,false),8);
        int count=0;
        for (JSONObject product:records) {
            if (++count>catalogLimit) {
                place(group,action("Tampilkan lebih banyak",0,()->{catalogLimit+=50;render();},false),7);break;
            }
            divider(group);place(group,productLine(product,linked,true),8);
        }
    }
    private LinearLayout productLine(JSONObject product,boolean linked,boolean manage) {
        LinearLayout line=horizontal();line.setGravity(Gravity.TOP);
        ImageView image=icon(linked?R.drawable.ic_merchant_bag:R.drawable.ic_merchant_package,ACCENT);
        image.setBackground(round(0xffe8f3ff,dp(8)));image.setClipToOutline(true);image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int size=manage?60:51;
        line.addView(image,new LinearLayout.LayoutParams(dp(size),dp(size)));
        byte[] imageBytes=MerchantPosProductActivity.photoBytes(product.optString("photo"));
        String remote=product.optString("image");
        Object source=imageBytes;
        if (linked && remote.startsWith("https://transiva.my.id/")) source=remote;
        if (source!=null) {
            image.setImageTintList(null);
            com.bumptech.glide.Glide.with(this).load(source).centerCrop()
                .placeholder(linked?R.drawable.ic_merchant_bag:R.drawable.ic_merchant_package)
                .error(linked?R.drawable.ic_merchant_bag:R.drawable.ic_merchant_package).into(image);
        }
        LinearLayout details=vertical();
        weight(line,details,8);
        details.addView(text(label(product,"Produk"),15,INK,true));
        place(details,text(rup(product.optLong("price")),14,ACCENT,true),3);
        boolean inStock=!product.isNull("stock")&&product.optLong("stock")>0;
        place(details,text(stock(product),12,inStock?0xff167844:0xff43526a,false),3);
        String reason=product.optString("reason");
        if (!reason.isEmpty()) {
            TextView status=text(reason,11,0xff875208,true);pad(status,6,3);
            status.setBackground(round(0xffffe6b4,dp(6)));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.topMargin=dp(4);details.addView(status,params);
        }
        String expiry=product.optString("expiresOn");
        if (!expiry.isEmpty()){
            boolean expired=expiry.compareTo(day(System.currentTimeMillis()))<0;
            place(details,text((expired?"Kedaluwarsa · ":"Kedaluwarsa ")+expiry,12,expired?0xffa33224:0xff43526a,expired),3);
        }
        if(product.optDouble("posDiscount",0)>0)
            place(details,text("Diskon Kasir "+NumberFormat.getNumberInstance(new Locale("id","ID")).format(product.optDouble("posDiscount"))+"%",12,ACCENT,true),3);
        if (manage&&!linked) {
            place(details,text("Pajak "+NumberFormat.getNumberInstance(new Locale("id","ID")).format(product.optDouble("tax",0))+"%",12,0xff43526a,false),3);
            LinearLayout actions=horizontal();
            weight(actions,action("Edit",0,()->openProduct(product),Color.WHITE,ACCENT,ACCENT,44),0);
            weight(actions,action("Resep",0,()->recipe(product),Color.WHITE,ACCENT,ACCENT,44),6);
            place(details,actions,5);
        }
        return line;
    }
    private void openProduct(JSONObject product) {
        Intent intent=new Intent(this,MerchantPosProductActivity.class)
                .putExtra(MerchantPosProductActivity.PRODUCT,product==null?"{}":product.toString())
                .putExtra(MerchantPosProductActivity.OUTLET,outlet).putExtra("outlet_name",outletName());
        startActivityForResult(intent,PRODUCT_EDITOR);
    }
    private void receiveProduct(Intent data) {
        if (busy || book==null) {deferredProduct=data;return;}
        if (!outlet.equals(data.getStringExtra(MerchantPosProductActivity.OUTLET))) return;
        try {
            JSONObject values=new JSONObject(data.getStringExtra(MerchantPosProductActivity.PRODUCT));
            String id=values.getString("id");
            uniqueBarcode(book,values.optString("barcode"),id);
            commit(next->{
                uniqueBarcode(next,values.optString("barcode"),id);
                JSONObject existing=find(array(next,"products"),"id",id);
                if (existing==null) array(next,"products").put(values);
                else for (Iterator<String> keys=values.keys();keys.hasNext();) {
                    String key=keys.next();put(existing,key,values.opt(key));
                }
            },null);
        } catch(Exception problem) {
            toast(message(problem,"Produk belum dapat disimpan."));
            try {openProduct(new JSONObject(data.getStringExtra(MerchantPosProductActivity.PRODUCT)));}
            catch(Exception ignored){}
        }
    }
    private String singular(String key) {
        switch(key) {
            case "products":return "produk";case "people":return "pelanggan";case "staff":return "pegawai";
            case "materials":return "bahan baku";case "discounts":return "diskon";default:return "tipe order";
        }
    }
    private void entities(String key) {
        if ("types".equals(key)||"discounts".equals(key)){compactOptions(key);return;}
        if ("people".equals(key)) controls(key);
        place(content,action("Tambah "+singular(key),R.drawable.ic_merchant_plus,()->editEntity(key,null),true),8);
        for (JSONObject row:rows(array(book,key))) {
            String detail;
            switch(key) {
                case "people": detail=row.optString("phone")+" · "+row.optLong("points")+" poin · Diskon "+row.optDouble("member",0)+"%";break;
                case "staff": detail=row.optString("role")+" · "+row.optString("phone");break;
                case "materials": detail="Stok "+row.optDouble("stock",0)+" "+row.optString("unit");break;
                case "discounts": detail=row.optDouble("percent",0)+"%";break;
                default: detail="";
            }
            item(content,label(row,""),detail,action("Edit",0,()->editEntity(key,row),false));
        }
    }
    private void compactOptions(String key){
        boolean discount="discounts".equals(key);
        LinearLayout heading=horizontal();
        weight(heading,text(discount?"Diskon transaksi":"Pilihan saat checkout",15,INK,true),0);
        heading.addView(action("Tambah",R.drawable.ic_merchant_plus,()->editEntity(key,null),true));
        place(content,heading,8);
        LinearLayout group=card();
        List<JSONObject> options=rows(array(book,key));
        if(options.isEmpty())group.addView(text("Belum ada "+singular(key)+".",13,INK,false));
        for(JSONObject option:options){
            if(group.getChildCount()>0)divider(group);
            String pct=NumberFormat.getNumberInstance(new Locale("id","ID")).format(option.optDouble("percent",0));
            LinearLayout row=horizontal();row.setMinimumHeight(dp(53));
            row.addView(icon(glyph(key),ACCENT),new LinearLayout.LayoutParams(dp(24),dp(24)));
            LinearLayout labels=vertical();labels.addView(text(label(option,""),15,INK,true));
            if(discount)place(labels,text(pct+"% potongan",13,ACCENT,true),3);
            weight(row,labels,9);
            row.addView(action("Edit",0,()->editEntity(key,option),Color.WHITE,ACCENT,LINE,44));
            place(group,row,5);
        }
        place(content,group,8);
    }
    private void editEntity(String key,JSONObject original) {
        JSONObject old=original==null?new JSONObject():original;
        LinearLayout form=formBody();Map<String,EditText> fields=new LinkedHashMap<>();
        fields.put("name",field(form,"Nama "+singular(key),old.optString("name"),InputType.TYPE_CLASS_TEXT));
        int integer=InputType.TYPE_CLASS_NUMBER, decimal=integer|InputType.TYPE_NUMBER_FLAG_DECIMAL;
        switch(key) {
            case "people":
                fields.put("phone",field(form,"Nomor telepon (opsional)",old.optString("phone"),InputType.TYPE_CLASS_PHONE));
                fields.put("member",field(form,"Diskon anggota (%)",old.optString("member","0"),decimal));break;
            case "staff":
                fields.put("role",field(form,"Jabatan",old.optString("role"),InputType.TYPE_CLASS_TEXT));
                fields.put("phone",field(form,"Nomor telepon (opsional)",old.optString("phone"),InputType.TYPE_CLASS_PHONE));break;
            case "materials":
                fields.put("unit",field(form,"Satuan (misalnya gram)",old.optString("unit"),InputType.TYPE_CLASS_TEXT));
                fields.put("stock",field(form,"Stok bahan",old.optString("stock","0"),decimal));break;
            case "discounts":
                fields.put("percent",field(form,"Potongan (%)",old.optString("percent","0"),decimal));break;
            default:
        }
        ask((original==null?"Tambah ":"Edit ")+singular(key),form,"Simpan",()->{
            String id=old.optString("id",UUID.randomUUID().toString());
            JSONObject values=record(id,required(fields.get("name")));
            for (Map.Entry<String,EditText> entry:fields.entrySet()) {
                String name=entry.getKey();EditText input=entry.getValue();
                switch(name) {
                    case "name":break;
                    case "price":put(values,name,integer(input,1,1000000000));break;
                    case "stock":put(values,name,"products".equals(key)?integer(input,0,999999):decimal(input,999999));break;
                    case "tax":case "member":case "percent":put(values,name,decimal(input,100));break;
                    case "barcode":case "phone":
                        String value=entered(input);
                        if (value.length()>("barcode".equals(name)?80:40)) throw new IllegalArgumentException("Isian terlalu panjang.");
                        put(values,name,value);break;
                    default:put(values,name,required(input));
                }
            }
            commit(next->{
                if ("products".equals(key)) uniqueBarcode(next,values.optString("barcode"),id);
                JSONObject target=find(array(next,key),"id",id);
                if (target==null) array(next,key).put(values);
                else for (Iterator<String> keys=values.keys();keys.hasNext();) {
                    String name=keys.next();put(target,name,values.opt(name));
                }
            },null);
        });
    }
    private void uniqueBarcode(JSONObject next,String barcode,String id) {
        if (barcode.isEmpty()) return;
        for (JSONObject p:rows(array(next,"products"))) if (!id.equals(p.optString("id")) && barcode.equals(p.optString("barcode")))
            throw new IllegalArgumentException("Barcode sudah digunakan.");
    }
    private void recipe(JSONObject product) {
        List<JSONObject> materials=rows(array(book,"materials"));
        if (materials.isEmpty()) {toast("Tambahkan bahan baku dahulu.");return;}
        LinearLayout form=formBody();Map<String,EditText> entries=new LinkedHashMap<>();
        JSONObject old=product.optJSONObject("recipe");
        for (JSONObject material:materials) {
            String id=material.optString("id");entries.put(id,field(form,label(material,"")+" ("+material.optString("unit")+")",
                    old==null?"0":old.optString(id,"0"),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL));
        }
        ask("Resep "+label(product,"produk"),form,"Simpan",()->{
            JSONObject recipe=new JSONObject();
            for (Map.Entry<String,EditText> row:entries.entrySet()) {
                double qty=Math.round(decimal(row.getValue(),100000)*1000)/1000.0;
                if (qty>0) put(recipe,row.getKey(),qty);
            }
            commit(next->put(find(array(next,"products"),"id",product.optString("id")),"recipe",recipe),null);
        });
    }
    private void cash() {
        String date=day(System.currentTimeMillis());long cash=0;
        for (JSONObject sale:rows(array(book,"sales"))) if ("cash".equals(sale.optString("method"))&&date.equals(day(sale.optLong("at")))) cash+=sale.optLong("total");
        long settlements=sum(array(book,"payments"),"amount",date), expenses=sum(array(book,"expenses"),"amount",date);
        JSONObject opening=book.optJSONObject("cashOpening");
        long initial=opening==null?0:opening.optLong(date);
        place(content,metric("Perkiraan saldo laci",rup(initial+cash+settlements-expenses),glyph("cash")),8);
        place(content,metric("Penjualan tunai",rup(cash),0),7);place(content,metric("Pelunasan kasbon tunai",rup(settlements),0),7);
        place(content,metric("Pengeluaran hari ini",rup(expenses),0),7);
        place(content,action("Modal awal · "+rup(initial),0,()->{
            LinearLayout form=formBody();EditText amount=field(form,"Modal kas (Rp)",Long.toString(initial),InputType.TYPE_CLASS_NUMBER);
            ask("Modal awal",form,"Simpan",()->{
                long value=integer(amount,0,1000000000);
                commit(next->{
                    JSONObject hours=next.optJSONObject("cashOpening");
                    if (hours==null) {hours=new JSONObject();put(next,"cashOpening",hours);}
                    put(hours,date,value);
                },null);
            });
        },false),7);
        place(content,action("Catat pengeluaran",0,()->{
            LinearLayout form=formBody();EditText name=field(form,"Keperluan","",InputType.TYPE_CLASS_TEXT);
            EditText amount=field(form,"Jumlah (Rp)","",InputType.TYPE_CLASS_NUMBER);
            ask("Pengeluaran tunai",form,"Simpan",()->{
                JSONObject row=record(UUID.randomUUID().toString(),required(name))
                        .put("amount",integer(amount,1,1000000000)).put("at",System.currentTimeMillis());
                commit(next->array(next,"expenses").put(row),null);
            });
        },true),7);
        List<JSONObject> records=rows(array(book,"expenses"));Collections.reverse(records);
        for (JSONObject expense:records.subList(0,Math.min(60,records.size())))
            item(content,label(expense,""),format("dd MMM HH:mm",expense.optLong("at"))+" · "+rup(expense.optLong("amount")),null);
    }
    private String reportMonth="";
    private void report() {
        if (reportMonth.isEmpty()) reportMonth=day(System.currentTimeMillis()).substring(0,7);
        place(content,action("Bulan laporan · "+reportMonth,0,()->{
            LinearLayout form=formBody();EditText month=field(form,"Bulan (YYYY-MM)",reportMonth,InputType.TYPE_CLASS_TEXT);
            ask("Periode laporan",form,"Terapkan",()->{
                String value=entered(month);
                if (!value.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw new IllegalArgumentException("Gunakan format YYYY-MM.");
                reportMonth=value;render();
            });
        },false),7);
        JSONArray sales=reportSales();
        Map<String,Long> daily=new TreeMap<>();List<String[]> export=new ArrayList<>();
        long tax=0, discount=0;
        for (JSONObject sale:rows(sales)) {
            String day=day(sale.optLong("at"));
            if (!day.startsWith(reportMonth)) continue;
            long total=sale.optLong("total");daily.put(day,daily.getOrDefault(day,0L)+total);tax+=sale.optLong("tax");discount+=sale.optLong("discount");
            export.add(new String[]{day,sale.optString("id"),sale.optString("method"),sale.optString("subtotal"),sale.optString("discount"),sale.optString("tax"),sale.optString("redeem"),sale.optString("total")});
        }
        place(content,metric("Total penjualan · "+export.size()+" transaksi",rup(sum(sales,"total",reportMonth)),glyph("report")),7);
        place(content,metric("Pajak produk",rup(tax),0),7);place(content,metric("Diskon",rup(discount),0),7);
        place(content,text("Tren harian",17,INK,true),12);
        long peak=Math.max(1,daily.values().stream().mapToLong(Long::longValue).max().orElse(0));
        for (Map.Entry<String,Long> entry:daily.entrySet()) {
            place(content,text(entry.getKey()+"   "+rup(entry.getValue()),13,INK,false),6);
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            bar.setMax(1000);bar.setProgress((int)Math.round(1000.0*entry.getValue()/peak));place(content,bar,2);
        }
        place(content,action("Ekspor laporan CSV",0,()->export("laporan-penjualan.csv",
                new String[]{"tanggal","id","metode","subtotal","diskon","pajak","poin_rp","total"},export),false),8);
    }
    private void outlets() {
        for (JSONObject branch:rows(branches)) item(content,label(branch,"Outlet"),
                outlet.equals(branch.optString("id"))?"Outlet aktif":"Outlet Merchant",null);
        if (!isStaff()) {
            place(content,action("Pilih outlet",glyph("outlets"),this::chooseOutlet,true),8);
            place(content,action("Kelola cabang Merchant",0,()->open(MerchantBranchesActivity.class),false),7);
        }
        place(content,action("Segarkan outlet",0,()->load(true),false),7);
    }
    private void plan() {
        String until=plan==null?"":plan.optString("ends_on");
        item(content,"Premium Merchant",until.isEmpty()?"Hubungi pengelola Transiva untuk informasi paket.":"Aktif sampai "+until,null);
    }
    private LinearLayout personRow(String person,String subtitle,int statusColor,View control) {
        LinearLayout row=horizontal();row.setMinimumHeight(dp(52));
        String symbol=person.isEmpty()?"?":new String(Character.toChars(person.codePointAt(0))).toUpperCase(Locale.ROOT);
        TextView initial=center(text(symbol,17,INK,true));initial.setBackground(round(0xffd5e8ff,dp(25)));
        row.addView(initial,new LinearLayout.LayoutParams(dp(36),dp(36)));
        LinearLayout details=vertical();details.addView(text(person,15,INK,true));
        if (!subtitle.isEmpty()) place(details,text(subtitle,12,statusColor,false),3);
        weight(row,details,8);
        if (control!=null) row.addView(control);
        return row;
    }
    private String maskPhone(String phone) {
        String digits=phone.replaceAll("[^0-9]","");
        if (digits.length()>=9) return digits.substring(0,4)+" •••• "+digits.substring(digits.length()-4);
        return digits.isEmpty()?"":"••••";
    }
    private void employees() {
        List<JSONObject> people=rows(array(book,"staff"));
        LinearLayout heading=horizontal(), description=vertical();
        description.addView(text("Pegawai",22,INK,true));
        place(description,text(people.size()+" pegawai",13,0xff43526a,false),3);
        weight(heading,description,0);
        heading.addView(action("Tambah",R.drawable.ic_merchant_plus,()->editEntity("staff",null),true));
        place(content,heading,13);
        LinearLayout group=card();
        if (people.isEmpty()) group.addView(text("Belum ada pegawai.",13,INK,false));
        for (JSONObject person:people) {
            if (group.getChildCount()>0) divider(group);
            String role=person.optString("role"), phone=maskPhone(person.optString("phone"));
            String subtitle=phone.isEmpty()?role:role.isEmpty()?phone:role+"\n"+phone;
            place(group,personRow(label(person,"Pegawai"),subtitle,0xff43526a,
                    action("Edit",0,()->editEntity("staff",person),Color.WHITE,ACCENT,ACCENT,44)),4);
        }
        place(content,group,10);
        place(content,action("Buka absensi",glyph("attendance"),()->go("attendance"),Color.WHITE,ACCENT,LINE,44),10);
    }
    private void attendance() {
        List<JSONObject> team=rows(array(book,"staff"));
        List<JSONObject> shifts=rows(array(book,"attendance"));Collections.reverse(shifts);
        Map<String,JSONObject> active=new LinkedHashMap<>();
        for (JSONObject item:shifts) if (item.isNull("end")) active.putIfAbsent(item.optString("staff"),item);
        long now=System.currentTimeMillis();
        place(content,text(format("dd MMMM yyyy",now),13,0xff43526a,false),3);
        int working=0;for (JSONObject person:team) if (active.containsKey(person.optString("id"))) working++;
        LinearLayout totals=horizontal();
        weight(totals,metric("● Sedang bekerja",Integer.toString(working),0),0);
        weight(totals,metric("● Di luar shift",Integer.toString(team.size()-working),0),6);
        place(content,totals,8);place(content,text("Status shift",17,INK,true),11);
        if (team.isEmpty()) place(content,action("Kelola pegawai",0,()->go("staff"),false),7);
        else {
            LinearLayout group=card();
            for (JSONObject person:team) {
                if (group.getChildCount()>0) divider(group);
                String id=person.optString("id");
                JSONObject shift=active.get(id);
                String status=shift==null?"Di luar shift":"Masuk "+attendanceTime(shift.optLong("at"),now);
                View mark=action(shift==null?"Catat masuk":"Catat pulang",0,()->commit(next->{
                    long timestamp=System.currentTimeMillis();
                    if (shift==null) array(next,"attendance").put(record(UUID.randomUUID().toString(),label(person,"Pegawai"))
                            .put("staff",id).put("at",timestamp));
                    else put(find(array(next,"attendance"),"id",shift.optString("id")),"end",timestamp);
                },null),shift==null?ACCENT:Color.WHITE,shift==null?Color.WHITE:ACCENT,ACCENT,44);
                place(group,personRow(label(person,"Pegawai"),status,shift==null?0xff43526a:0xff167844,mark),4);
            }
            place(content,group,7);
        }
        place(content,text("Riwayat absensi",17,INK,true),12);
        String previous="";int shown=0;
        for (JSONObject shift:shifts) {
            if (shift.isNull("end")) continue;
            if (++shown>60) break;
            long start=shift.optLong("at"), end=shift.optLong("end");
            String date=day(start);
            if (!date.equals(previous)) {place(content,text(format("dd MMMM yyyy",start),13,0xff43526a,false),6);previous=date;}
            TextView done=text("Selesai",11,0xff167844,true);pad(done,6,4);done.setBackground(round(0xffe0f5e9,dp(6)));
            LinearLayout line=card();line.addView(personRow(label(shift,"Pegawai"),
                    "Masuk "+format("HH.mm",start)+" · Pulang "+attendanceTime(end,start),0xff43526a,done));
            place(content,line,6);
        }
        if (shown==0) place(content,text("Belum ada shift yang selesai.",13,0xff43526a,false),6);
    }
    private String attendanceTime(long timestamp,long reference) {
        return format(day(timestamp).equals(day(reference))?"HH.mm":"dd MMM HH.mm",timestamp);
    }
    private void surveys() {
        place(content,action("Catat survei",0,()->{
            LinearLayout form=formBody();List<JSONObject> people=rows(array(book,"people"));people.add(0,record("","Anonim"));
            Spinner customer=select(form,"Pelanggan",people);
            EditText score=field(form,"Nilai 1–5","5",InputType.TYPE_CLASS_NUMBER), note=field(form,"Masukan (opsional)","",InputType.TYPE_CLASS_TEXT);
            ask("Survei pelanggan",form,"Simpan",()->{
                JSONObject person=selected(people,customer);String detail=entered(note);
                if (detail.length()>350) throw new IllegalArgumentException("Masukan maksimal 350 karakter.");
                JSONObject row=record(UUID.randomUUID().toString(),label(person,"Anonim")).put("customer",person.optString("id"))
                        .put("note",detail).put("score",integer(score,1,5)).put("at",System.currentTimeMillis());
                commit(next->array(next,"surveys").put(row),null);
            });
        },true),7);
        List<JSONObject> results=rows(array(book,"surveys"));Collections.reverse(results);
        if (!results.isEmpty()) place(content,metric("Rata-rata kepuasan",
                String.format(new Locale("id","ID"),"%.1f / 5",results.stream().mapToInt(r->r.optInt("score")).average().orElse(0)),0),7);
        for (JSONObject row:results) item(content,label(row,"Anonim")+" · "+row.optInt("score")+"/5",row.optString("note"),null);
    }
    private void qris() {
        place(content,text("QRIS statis resmi milik outlet. Dana dikonfirmasi manual saat transaksi.",13,INK,false),8);
        showQr(content);
        if (!isStaff()) {
            place(content,action("Pasang gambar QRIS",0,()->pickFile(QR,"image/*"),true),8);
            if (book.has("qrImage")) place(content,action("Hapus gambar QRIS",0,()->confirm("Hapus QRIS?","Gambar QRIS outlet akan dihapus.",()->commit(next->next.remove("qrImage"),null)),false),7);
        }
    }
    private void showQr(LinearLayout parent) {
        String image=book.optString("qrImage");
        if (!image.startsWith("data:image/jpeg;base64,")) return;
        try {
            byte[] bytes=android.util.Base64.decode(image.substring(23),android.util.Base64.DEFAULT);
            android.graphics.Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
            if (bitmap==null) return;
            ImageView view=new ImageView(this);view.setImageBitmap(bitmap);view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.setContentDescription("QRIS outlet "+outletName());parent.addView(view,new LinearLayout.LayoutParams(-1,dp(180)));
        } catch (IllegalArgumentException ignored) {}
    }

    private void scan() {
        MerchantPosScanActivity.launch(this);
    }
    private void scanResult(String value,boolean notify) {
        if (!"sale".equals(screen)||book==null||busy||pending!=null) return;
        for (JSONObject product:productsList()) if (!value.isEmpty()&&value.equals(product.optString("barcode"))) {
            add(product.optString("id"),1);return;
        }
        if (notify) toast("Barcode belum terdaftar.");
    }
    private String[] headings(String kind) {
        return "products".equals(kind)?new String[]{"nama","barcode","harga","stok","pajak"}
                :new String[]{"nama","telepon","diskon_anggota"};
    }
    private void controls(String kind) {
        LinearLayout row=horizontal();
        weight(row,action("Template CSV",0,()->export("template-"+kind+".csv",headings(kind),Collections.emptyList()),false),0);
        weight(row,action("Impor CSV",0,()->{importKind=kind;pickFile(IMPORT,"*/*");},false),6);
        place(content,row,7);
        if ("products".equals(kind)) place(content,action("Ekspor produk CSV",0,()->{
            List<String[]> data=new ArrayList<>();
            for (JSONObject product:rows(array(book,"products"))) data.add(new String[]{label(product,""),product.optString("barcode"),
                    product.optString("price"),Long.toString(product.optLong("stock")),product.optString("tax")});
            export("produk-kasir.csv",headings(kind),data);
        },false),6);
    }
    private void pickFile(int request,String mime) {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,request);
    }
    private void export(String filename,String[] columns,List<String[]> data) {
        StringBuilder csv=new StringBuilder();
        appendCsv(csv,columns);
        for (String[] line:data) appendCsv(csv,line);
        if (csv.toString().getBytes(StandardCharsets.UTF_8).length>LIMIT) {toast("File melebihi batas ekspor.");return;}
        exportText=csv.toString();
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,filename);startActivityForResult(intent,EXPORT);
    }
    private void appendCsv(StringBuilder csv,String[] fields) {
        for (int i=0;i<fields.length;i++) {
            if (i>0) csv.append(',');
            String value=fields[i];
            if (value.matches("(?s)^(\\s*[=+\\-@]|[\\t\\r\\n]).*")) value="'"+value;
            csv.append('"').append(value.replace("\"","\"\"")).append('"');
        }
        csv.append("\r\n");
    }
    private List<List<String>> parseCsv(String text) {
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();
        if (text.startsWith("\ufeff")) text=text.substring(1);
        boolean quoted=false,closed=false;
        for (int i=0;i<text.length();i++) {
            char ch=text.charAt(i);
            if (quoted) {
                if (ch=='"' && i+1<text.length() && text.charAt(i+1)=='"') { field.append('"');i++; }
                else if (ch=='"') {quoted=false;closed=true;}
                else field.append(ch);
            } else if (ch=='"' && field.length()==0 && !closed) quoted=true;
            else if (ch==',' || ch=='\n' || ch=='\r') {
                row.add(field.toString().trim());field.setLength(0);closed=false;
                if (ch!=',') {
                    if (ch=='\r' && i+1<text.length() && text.charAt(i+1)=='\n') i++;
                    if (row.stream().anyMatch(v->!v.isEmpty())) rows.add(row);
                    row=new ArrayList<>();
                }
            } else if (closed || ch=='"') throw new IllegalArgumentException("Tanda kutip CSV tidak valid.");
            else field.append(ch);
        }
        if (quoted) throw new IllegalArgumentException("Tanda kutip CSV belum ditutup.");
        row.add(field.toString().trim());
        if (row.stream().anyMatch(v->!v.isEmpty())) rows.add(row);
        return rows;
    }
    private double csvValue(String value,long min,long max,boolean fraction) {
        if (!value.matches(fraction?"[0-9]+(\\.[0-9]{1,2})?":"[0-9]+")) throw new IllegalArgumentException("Format angka tidak valid.");
        double number=Double.parseDouble(value);
        if (number<min || number>max) throw new IllegalArgumentException("Angka berada di luar batas.");
        return number;
    }
    private void importCsv(String kind,String text) {
        if (book==null || busy || pending!=null) return;
        try {
            List<List<String>> records=parseCsv(text);
            if (records.size()<2 || records.size()>301 || !records.remove(0).equals(Arrays.asList(headings(kind))))
                throw new IllegalArgumentException("Gunakan template CSV Kasir dengan 1–300 baris.");
            confirm("Impor "+records.size()+" baris?","Data baru akan ditambahkan ke "+outletName()+".",()->commit(next->{
                int line=1;
                for (List<String> source:records) {
                    line++;
                    try {
                        if (source.size()!=headings(kind).length || source.get(0).isEmpty() || source.get(0).length()>120)
                            throw new IllegalArgumentException("Kolom atau nama tidak valid.");
                        JSONObject row=record(UUID.randomUUID().toString(),source.get(0));
                        String code=source.get(1);
                        if ("products".equals(kind)) {
                            if (code.length()>80) throw new IllegalArgumentException("Barcode terlalu panjang.");
                            uniqueBarcode(next,code,"");
                            row.put("barcode",code).put("price",(long)csvValue(source.get(2),1,1000000000,false))
                                    .put("stock",(int)csvValue(source.get(3),0,999999,false)).put("tax",csvValue(source.get(4),0,100,true));
                        } else {
                            if (code.length()>40) throw new IllegalArgumentException("Nomor terlalu panjang.");
                            row.put("phone",code).put("member",csvValue(source.get(2),0,100,true)).put("points",0);
                        }
                        array(next,kind).put(row);
                    } catch (IllegalArgumentException error) {throw new IllegalArgumentException("Baris "+line+" · "+error.getMessage());}
                }
            },null));
        } catch (IllegalArgumentException error) {toast(error.getMessage());}
    }
    @Override protected void onActivityResult(int code,int status,Intent data) {
        super.onActivityResult(code,status,data);
        IntentResult scan=IntentIntegrator.parseActivityResult(code,status,data);
        if (scan!=null) {
            if (scan.getContents()!=null) {
                if (busy||book==null) deferredBarcode=scan.getContents();
                else scanResult(scan.getContents(),true);
            }
            return;
        }
        if (status!=RESULT_OK || data==null || !alive()) return;
        if (code==PRODUCT_EDITOR) {receiveProduct(data);return;}
        if (code==CHECKOUT) {receiveCheckout(data);return;}
        if (data.getData()==null) return;
        if (code!=IMPORT && code!=QR && code!=EXPORT) return;
        Uri uri=data.getData();String kind=importKind,csv=exportText,selected=outlet;
        if (code==EXPORT) exportText="";
        worker.execute(()->{
            try {
                if (code==EXPORT) {
                    try (OutputStream output=getContentResolver().openOutputStream(uri)) {
                        if (output==null) throw new IOException("File tidak dapat ditulis.");
                        output.write(csv.getBytes(StandardCharsets.UTF_8));
                    }
                    runOnUiThread(()->{if (alive()) toast("CSV tersimpan.");});
                } else {
                    String result=code==QR?readQr(uri):readCsv(uri);
                    runOnUiThread(()->{
                        if (!alive() || !outlet.equals(selected)) return;
                        if (code==QR && !isStaff()) commit(next->put(next,"qrImage",result),null);
                        else if (code==IMPORT && ("products".equals(kind)||"people".equals(kind))) importCsv(kind,result);
                    });
                }
            } catch (Exception error) {runOnUiThread(()->{if (alive()) toast("File belum dapat diproses.");});}
        });
    }
    private String readCsv(Uri uri) throws IOException {
        try (InputStream input=getContentResolver().openInputStream(uri);ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            if (input==null) throw new IOException("File tidak dapat dibuka.");
            byte[] buffer=new byte[8192];int length;
            while ((length=input.read(buffer))!=-1) {
                if (bytes.size()+length>LIMIT) throw new IOException("CSV terlalu besar.");
                bytes.write(buffer,0,length);
            }
            return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private String readQr(Uri uri) throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try (InputStream in=getContentResolver().openInputStream(uri)) {BitmapFactory.decodeStream(in,null,bounds);}
        if (bounds.outHeight<120||bounds.outWidth<120||bounds.outHeight>10000||bounds.outWidth>10000) throw new IOException("Ukuran QRIS tidak sesuai.");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
        while (Math.max(bounds.outHeight,bounds.outWidth)/options.inSampleSize>1200) options.inSampleSize*=2;
        android.graphics.Bitmap bitmap;
        try (InputStream in=getContentResolver().openInputStream(uri)) {bitmap=BitmapFactory.decodeStream(in,null,options);}
        if (bitmap==null) throw new IOException("Gambar tidak valid.");
        try (ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,bytes)||bytes.size()>LIMIT) throw new IOException("Gambar terlalu besar.");
            return "data:image/jpeg;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
        } finally {bitmap.recycle();}
    }
}
