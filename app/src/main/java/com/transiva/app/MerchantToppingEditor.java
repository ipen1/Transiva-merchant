package com.transiva.app;

import android.content.Intent;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.text.TextUtils;
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
    private Button photoButton, saveButton, resetPhotoButton;
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

    private LinearLayout page(String title, LinearLayout footer) {
        root=new LinearLayout(host);
        host.setContentView(host.page(root,footer));
        root.addView(host.editorHeader(title,this::back));
        return root;
    }

    void show() {
        opened=true;
        draft=null;
        refreshPrice=null;
        Button add=host.editorButton("＋ Tambah topping",true);
        add.setEnabled(toppings.size()<LIMIT);
        add.setOnClickListener(v->edit(-1));
        LinearLayout screen=page("Topping Menu",host.editorFooter(add));
        TextView counter=host.tv(toppings.size()+" dari "+LIMIT+" pilihan · Satu topping per porsi",12,host.MUTED,true);
        counter.setPadding(host.dp(2),0,0,host.dp(8));
        screen.addView(counter);
        if (toppings.isEmpty()) screen.addView(host.card("Belum ada topping. Tambahkan pilihan untuk menu ini."));
        for (int i=0;i<toppings.size();i++) {
            final int index=i;
            Topping topping=toppings.get(i);
            LinearLayout card=host.editorSection();
            LinearLayout row=new LinearLayout(host);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(host.dp(62));
            ImageView thumbnail=new ImageView(host);
            thumbnail.setBackground(host.round(Color.parseColor("#EAF4FF"),host.dp(10)));
            thumbnail.setClipToOutline(true);
            row.addView(thumbnail,new LinearLayout.LayoutParams(host.dp(52),host.dp(52)));
            Glide.with(host).load(photoFor(topping)).centerCrop().into(thumbnail);

            LinearLayout info=new LinearLayout(host);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(host.dp(10),0,host.dp(6),0);
            TextView name=host.tv(topping.name,14,host.NAVY,true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(name);
            if (!topping.description.isEmpty()) {
                TextView detail=host.tv(topping.description,11,host.MUTED,false);
                detail.setMaxLines(1);
                detail.setEllipsize(TextUtils.TruncateAt.END);
                info.addView(detail);
            }
            info.addView(host.tv(host.rupiah(customerPrice.applyAsLong(topping.price))+" · Harga pelanggan",11,host.BLUE,true));
            row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(host.tv("›",20,host.BLUE,true));
            card.addView(row);
            card.setFocusable(true);
            card.setContentDescription("Edit topping "+topping.name+", "+host.rupiah(customerPrice.applyAsLong(topping.price)));
            card.setOnClickListener(v->edit(index));
            screen.addView(card);
        }
    }

    private void edit(int index) {
        editingIndex=index;
        draft=index<0?new Topping():new Topping(toppings.get(index));
        renderDraft();
    }

    private void renderDraft() {
        final int index=editingIndex;
        saveButton=host.editorButton("Simpan topping",true);
        LinearLayout footer=host.editorFooter(saveButton);
        TextView error=host.tv("",12,Color.parseColor("#B42318"),true);
        error.setVisibility(View.GONE);
        footer.addView(error,0);
        LinearLayout screen=page(index<0?"Tambah Topping":"Edit Topping",footer);

        LinearLayout photoCard=host.editorSection();
        LinearLayout photoRow=new LinearLayout(host);
        photoRow.setGravity(Gravity.CENTER_VERTICAL);
        photo=new ImageView(host);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setBackground(host.round(Color.parseColor("#EAF4FF"),host.dp(12)));
        photo.setClipToOutline(true);
        photoRow.addView(photo,new LinearLayout.LayoutParams(host.dp(76),host.dp(76)));
        LinearLayout photoActions=new LinearLayout(host);
        photoActions.setOrientation(LinearLayout.VERTICAL);
        photoActions.setPadding(host.dp(9),0,0,0);
        photoActions.addView(host.tv("Foto topping",13,host.NAVY,true));
        photoStatus=host.tv("",11,host.MUTED,false);
        photoActions.addView(photoStatus);
        photoButton=host.editorButton("Ganti foto",false);
        photoButton.setOnClickListener(v->pickPhoto());
        photoActions.addView(photoButton);
        photoRow.addView(photoActions,new LinearLayout.LayoutParams(0,-2,1));
        photoCard.addView(photoRow);
        resetPhotoButton=host.editorButton("Pakai foto menu utama",false);
        resetPhotoButton.setOnClickListener(v->{
            if (photoLoading) return;
            draft.image="";
            draft.prepared=null;
            showPhoto();
        });
        photoCard.addView(resetPhotoButton);
        screen.addView(photoCard);
        showPhoto();

        LinearLayout fields=host.editorSection();
        fields.addView(host.editorLabel("Nama topping"));
        EditText name=host.editorField("Contoh: Keju",InputType.TYPE_CLASS_TEXT);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        name.setText(draft.name);
        fields.addView(name);
        fields.addView(host.editorLabel("Deskripsi · opsional"));
        EditText description=host.editorField("Contoh: Keju parut",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});
        description.setText(draft.description);
        description.setMaxLines(3);
        fields.addView(description);
        fields.addView(host.editorLabel("Harga toko untuk satu porsi (Rp)"));
        EditText price=host.editorField("Harga menu + topping, contoh: 10000",InputType.TYPE_CLASS_NUMBER);
        if (draft.price>0) price.setText(String.valueOf(draft.price));
        fields.addView(price);
        TextView preview=host.tv("",13,host.BLUE,true);
        preview.setPadding(0,host.dp(6),0,host.dp(6));
        fields.addView(preview);
        screen.addView(fields);
        Runnable update=()->{
            draft.name=name.getText().toString().trim();
            draft.description=description.getText().toString().trim();
            draft.price=parsePrice(price.getText().toString());
            preview.setText(draft.price>0?"Harga pelanggan  "+host.rupiah(customerPrice.applyAsLong(draft.price)):"Harga pelanggan dihitung otomatis.");
            error.setVisibility(View.GONE);
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

        saveButton.setOnClickListener(v->{
            if (photoLoading) return;
            long base=host.baseToppingPrice();
            if (draft.name.isEmpty() || draft.price<=0 || draft.price<base || draft.price>100000000) {
                error.setVisibility(View.VISIBLE);
                error.setText("Isi nama dan harga satu porsi. Harga minimal "+host.rupiah(Math.max(1,base))+".");
                return;
            }
            for (int i=0;i<toppings.size();i++) {
                if (i!=editingIndex && toppings.get(i).name.equalsIgnoreCase(draft.name)) {
                    error.setVisibility(View.VISIBLE);
                    error.setText("Nama topping sudah dipakai.");
                    return;
                }
            }
            if (editingIndex<0) toppings.add(draft); else toppings.set(editingIndex,draft);
            dirty=true;
            show();
        });
        if (index>=0) {
            Button delete=host.editorButton("Hapus topping",false);
            delete.setTextColor(Color.parseColor("#B42318"));
            delete.setOnClickListener(v->{
                if (photoLoading) return;
                new AlertDialog.Builder(host).setTitle("Hapus topping?")
                        .setMessage(draft.name+" akan dihapus saat menu disimpan.")
                        .setNegativeButton("Batal",null).setPositiveButton("Hapus",(dialog,which)->{
                            toppings.remove(index);
                            dirty=true;
                            show();
                        }).show();
            });
            screen.addView(delete);
        }
    }

    private Object photoFor(Topping topping) {
        if (topping.prepared!=null) return topping.prepared.uri;
        if (!topping.image.isEmpty()) return imageUrl(topping.image);
        return mainPhoto.get();
    }

    private void showPhoto() {
        Glide.with(host).load(photoFor(draft)).centerCrop().into(photo);
        photoStatus.setText(draft.prepared!=null?"Foto siap · "+host.humanBytes(draft.prepared.finalBytes)
                : draft.image.isEmpty()?"Foto menu utama":"Foto khusus");
        resetPhotoButton.setVisibility(draft.prepared!=null || !draft.image.isEmpty()?View.VISIBLE:View.GONE);
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
