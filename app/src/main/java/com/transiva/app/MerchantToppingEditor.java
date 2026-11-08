package com.transiva.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

final class MerchantToppingEditor {
    static final int PICK_PHOTO = 802;
    private static final int LIMIT = 8;
    private final MerchantAddMenuActivity host;
    private final Supplier<Object> mainPhoto;
    private final LongUnaryOperator customerPrice;
    private final ArrayList<Topping> toppings = new ArrayList<>();
    private Topping draft, photoTarget;
    private int editingIndex = -1;
    private boolean opened, dirty, photoLoading;
    private LinearLayout root;
    private ImageView photo;
    private TextView photoStatus;
    private Button photoButton, saveButton;
    private Runnable refreshPrice;

    MerchantToppingEditor(MerchantAddMenuActivity host, Supplier<Object> mainPhoto,
                          LongUnaryOperator customerPrice, String options) {
        this.host=host; this.mainPhoto=mainPhoto; this.customerPrice=customerPrice;
        try {
            JSONArray groups = new JSONArray(options);
            for (int i=0;i<groups.length();i++) {
                JSONObject group=groups.optJSONObject(i);
                if (group==null || !"bundle_topping".equals(group.optString("type"))) continue;
                JSONArray items=group.optJSONArray("items");
                if (items==null) continue;
                for (int j=0;j<items.length();j++) {
                    JSONObject item=items.optJSONObject(j);
                    if (item==null) continue;
                    Topping topping=new Topping();
                    topping.name=item.optString("name");
                    topping.description=item.optString("description");
                    topping.price=item.optLong("bundle_price");
                    topping.image=item.optString("image","");
                    toppings.add(topping);
                }
            }
        } catch (Exception ignored) {}
    }

    int size() { return toppings.size(); }
    boolean isOpen() { return opened; }
    boolean isDirty() { return dirty; }

    private LinearLayout page(String title) {
        root = new LinearLayout(host);
        host.setContentView(host.page(root));
        root.addView(host.title(title));
        return root;
    }

    void show() {
        opened = true;
        draft = null;
        refreshPrice = null;
        LinearLayout screen = page("Topping Menu");
        screen.addView(host.sub("Satu pilihan topping per porsi. Isi harga lengkap menu dengan topping, lalu Transiva menghitung harga pelanggan."));
        for (int i=0;i<toppings.size();i++) {
            final int index=i;
            Topping topping=toppings.get(i);
            Button option=host.outlineBtn(topping.name+" · "+host.rupiah(customerPrice.applyAsLong(topping.price)));
            option.setOnClickListener(v->edit(index));
            screen.addView(option);
        }
        Button add=host.btn("Tambah topping");
        add.setEnabled(toppings.size()<LIMIT);
        add.setOnClickListener(v->edit(-1));
        screen.addView(add);
        Button back=host.outlineBtn("Kembali ke menu");
        back.setOnClickListener(v->back());
        screen.addView(back);
    }

    private void edit(int index) {
        editingIndex=index;
        draft=index<0?new Topping():new Topping(toppings.get(index));
        renderDraft();
    }

    private void renderDraft() {
        final int index=editingIndex;
        LinearLayout screen=page(index<0?"Tambah Topping":"Edit Topping");
        photo=new ImageView(host);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        screen.addView(photo,new LinearLayout.LayoutParams(-1,host.dp(168)));
        photoStatus=host.sub("");
        screen.addView(photoStatus);
        photoButton=host.outlineBtn("Pilih foto berbeda");
        photoButton.setOnClickListener(v->pickPhoto());
        screen.addView(photoButton);
        Button useDefault=host.outlineBtn("Gunakan foto menu utama");
        useDefault.setOnClickListener(v->{
            if (photoLoading) return;
            draft.image="";
            draft.prepared=null;
            showPhoto();
        });
        screen.addView(useDefault);
        showPhoto();

        EditText name=host.input("Nama topping · contoh Oreo", InputType.TYPE_CLASS_TEXT);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        name.setText(draft.name);
        screen.addView(name);
        EditText description=host.input("Deskripsi · contoh taburan Oreo renyah", InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});
        description.setText(draft.description);
        screen.addView(description);
        EditText price=host.input("Harga jual satu porsi dengan topping (Rp)", InputType.TYPE_CLASS_NUMBER);
        if (draft.price>0) price.setText(String.valueOf(draft.price));
        screen.addView(price);
        TextView preview=host.card("");
        screen.addView(preview);
        Runnable update=()->{
            draft.name=name.getText().toString().trim();
            draft.description=description.getText().toString().trim();
            draft.price=parsePrice(price.getText().toString());
            preview.setText(draft.price>0?"Harga pelanggan · "+host.rupiah(customerPrice.applyAsLong(draft.price)):"Harga pelanggan muncul setelah harga diisi.");
        };
        TextWatcher watcher=new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { update.run(); }
            @Override public void afterTextChanged(Editable e) {}
        };
        name.addTextChangedListener(watcher);
        description.addTextChangedListener(watcher);
        price.addTextChangedListener(watcher);
        refreshPrice=update;
        update.run();

        TextView error=host.sub("");
        screen.addView(error);
        saveButton=host.btn("Simpan topping");
        saveButton.setOnClickListener(v->{
            if (photoLoading) return;
            long base=host.baseToppingPrice();
            if (draft.name.isEmpty() || draft.price<=0 || draft.price<base || draft.price>100000000) {
                error.setText("Isi nama dan harga satu porsi. Harga minimal "+host.rupiah(Math.max(1,base))+".");
                return;
            }
            for (int i=0;i<toppings.size();i++) {
                if (i!=editingIndex && toppings.get(i).name.equalsIgnoreCase(draft.name)) {
                    error.setText("Nama topping sudah dipakai.");
                    return;
                }
            }
            if (editingIndex<0) toppings.add(draft); else toppings.set(editingIndex,draft);
            dirty=true;
            show();
        });
        screen.addView(saveButton);
        if (index>=0) {
            Button delete=host.outlineBtn("Hapus topping ini");
            delete.setOnClickListener(v->{
                if (photoLoading) return;
                toppings.remove(index);
                dirty=true;
                show();
            });
            screen.addView(delete);
        }
        Button back=host.outlineBtn("Batal / kembali");
        back.setOnClickListener(v->back());
        screen.addView(back);
    }

    private void showPhoto() {
        Object source=mainPhoto.get();
        if (!draft.image.isEmpty()) source=imageUrl(draft.image);
        if (draft.prepared!=null) source=draft.prepared.uri;
        Glide.with(host).load(source).centerCrop().into(photo);
        photoStatus.setText(draft.prepared!=null?"Foto topping siap · "+host.humanBytes(draft.prepared.finalBytes)
                : draft.image.isEmpty()?"Menggunakan foto menu utama":"Menggunakan foto topping");
    }

    private void pickPhoto() {
        if (photoLoading) return;
        photoTarget=draft;
        Intent intent=new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
        host.startActivityForResult(Intent.createChooser(intent,"Pilih foto topping"),PICK_PHOTO);
    }

    void receivePhoto(Uri uri) {
        Topping target=photoTarget;
        photoTarget=null;
        if (uri==null || target==null || target!=draft) return;
        photoLoading=true;
        saveButton.setEnabled(false);
        photoButton.setEnabled(false);
        photoStatus.setText("AI Resize sedang menyiapkan foto…");
        if (!MerchantTaskExecutor.execute(()->{
            MerchantBaseActivity.PreparedImage result=null;
            try { result=host.prepareAiResizeToWebp(uri,"topping",900,150*1024L,110*1024L); }
            catch (Exception ignored) {}
            MerchantBaseActivity.PreparedImage prepared=result;
            host.runOnUiThread(()->{
                if (host.isDestroyed() || draft!=target) return;
                photoLoading=false;
                saveButton.setEnabled(true);
                photoButton.setEnabled(true);
                if (prepared==null) photoStatus.setText("Foto belum bisa diproses. Pilih foto lain.");
                else {
                    target.prepared=prepared;
                    showPhoto();
                }
            });
        })) {
            photoLoading=false;
            saveButton.setEnabled(true);
            photoButton.setEnabled(true);
            photoStatus.setText("Antrean foto penuh. Coba lagi.");
        }
    }

    void onRulesLoaded() {
        if (!opened) return;
        if (draft==null) show();
        else if (refreshPrice!=null) refreshPrice.run();
    }

    void back() {
        if (photoLoading) return;
        if (draft!=null) show();
        else {
            opened=false;
            host.returnFromToppings();
        }
    }

    Bundle saveState() {
        Bundle out=new Bundle();
        ArrayList<Bundle> entries=new ArrayList<>();
        for (Topping item:toppings) entries.add(item.toBundle());
        out.putParcelableArrayList("items",entries);
        out.putBoolean("dirty",dirty);
        out.putBoolean("opened",opened);
        out.putInt("index",editingIndex);
        out.putBoolean("picking_photo",photoTarget!=null);
        if (draft!=null) out.putBundle("draft",draft.toBundle());
        return out;
    }

    @SuppressWarnings("deprecation")
    void restoreState(Bundle state) {
        if (state==null) return;
        ArrayList<Bundle> entries=state.getParcelableArrayList("items");
        if (entries!=null) {
            toppings.clear();
            for (Bundle entry:entries) toppings.add(Topping.fromBundle(entry));
        }
        dirty=state.getBoolean("dirty");
        if (!state.getBoolean("opened")) return;
        Bundle item=state.getBundle("draft");
        if (item==null) { show(); return; }
        opened=true;
        editingIndex=state.getInt("index",-1);
        draft=Topping.fromBundle(item);
        if (state.getBoolean("picking_photo")) photoTarget=draft;
        renderDraft();
    }

    JSONArray request(long base, Map<String,MerchantBaseActivity.PreparedImage> files) throws Exception {
        JSONArray out=new JSONArray();
        HashSet<String> names=new HashSet<>();
        for (int i=0;i<toppings.size();i++) {
            Topping topping=toppings.get(i);
            if (topping.name.isEmpty() || !names.add(topping.name.toLowerCase(Locale.ROOT)) || topping.price<base)
                throw new IllegalArgumentException("Periksa topping. Harga satu porsi dengan topping minimal "+host.rupiah(base)+".");
            out.put(new JSONObject().put("name",topping.name).put("description",topping.description)
                    .put("bundle_price",topping.price).put("image",topping.image));
            if (topping.prepared!=null) files.put("topping_"+i,topping.prepared);
        }
        return out;
    }

    static Object imageUrl(String value) {
        if (value.isEmpty()) return null;
        if (value.startsWith("https://")) return value;
        if (value.startsWith("server/") || value.startsWith("assets/")) return "https://transiva.my.id/"+value;
        if (value.startsWith("/")) return "https://transiva.my.id"+value;
        return MerchantBaseActivity.BASE+value;
    }

    private static long parsePrice(String value) {
        try { return Long.parseLong(value.trim()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    static Bundle packPhoto(MerchantBaseActivity.PreparedImage photo) {
        Bundle state=new Bundle();
        state.putString("uri",photo.uri.toString());state.putString("mime",photo.mimeType);
        state.putString("file",photo.fileName);state.putLong("original",photo.originalBytes);
        state.putLong("final",photo.finalBytes);state.putBoolean("transformed",photo.transformed);
        return state;
    }

    static MerchantBaseActivity.PreparedImage unpackPhoto(Bundle state) {
        if (state==null) return null;
        return new MerchantBaseActivity.PreparedImage(Uri.parse(state.getString("uri","")),
                state.getString("mime"),state.getString("file"),state.getLong("original"),
                state.getLong("final"),state.getBoolean("transformed"));
    }

    private static final class Topping {
        String name="",description="",image="";
        long price;
        MerchantBaseActivity.PreparedImage prepared;
        Bundle toBundle() {
            Bundle out=new Bundle();
            out.putString("name",name);out.putString("description",description);
            out.putString("image",image);out.putLong("price",price);
            if (prepared!=null) out.putBundle("photo",packPhoto(prepared));
            return out;
        }
        static Topping fromBundle(Bundle source) {
            Topping item=new Topping();
            item.name=source.getString("name","");item.description=source.getString("description","");
            item.image=source.getString("image","");item.price=source.getLong("price");
            item.prepared=unpackPhoto(source.getBundle("photo"));
            return item;
        }
        Topping() {}
        Topping(Topping other) {
            name=other.name; description=other.description; image=other.image;
            price=other.price; prepared=other.prepared;
        }
    }
}
