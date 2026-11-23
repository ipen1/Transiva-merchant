package com.transiva.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import androidx.exifinterface.media.ExifInterface;
import com.google.zxing.integration.android.IntentIntegrator;
import org.json.JSONObject;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MerchantPosProductActivity extends MerchantBaseActivity {
    static final String PRODUCT="pos_product", OUTLET="pos_outlet";
    private static final int PHOTO=819;
    private static final String PREFIX="data:image/jpeg;base64,";
    private final ExecutorService images=Executors.newSingleThreadExecutor();
    private EditText name, barcode, price, stock, tax;
    private ImageView preview;
    private Button save;
    private String photo="", id, outlet;
    private boolean loadingPhoto;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (sessionManager.getToken().isEmpty()) {finish();return;}
        JSONObject original;
        try { original=new JSONObject(getIntent().getStringExtra(PRODUCT)); }
        catch(Exception ignored) {original=new JSONObject();}
        outlet=getIntent().getStringExtra(OUTLET);
        id=original.optString("id",UUID.randomUUID().toString());
        LinearLayout form=new LinearLayout(this);
        LinearLayout footer=new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(12),dp(6),dp(12),dp(8));
        save=btn("Simpan produk");footer.addView(save,new LinearLayout.LayoutParams(-1,-2));
        setContentView(page(form,footer));
        Button back=outlineBtn("‹  Produk & stok");back.setOnClickListener(v->finish());
        form.addView(back);
        form.addView(title(original.has("id")?"Edit produk Kasir":"Tambah produk Kasir"));
        form.addView(sub(getIntent().getStringExtra("outlet_name")));
        preview=new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setContentDescription("Pratinjau foto produk");
        preview.setBackground(round(0xffe9f3ff,dp(12)));preview.setClipToOutline(true);
        LinearLayout.LayoutParams imageSize=new LinearLayout.LayoutParams(dp(112),dp(112));imageSize.topMargin=dp(10);
        form.addView(preview,imageSize);
        LinearLayout photoActions=new LinearLayout(this);
        Button pick=outlineBtn("Pilih foto");
        pick.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),PHOTO));
        photoActions.addView(pick,new LinearLayout.LayoutParams(0,-2,1));
        Button remove=outlineBtn("Hapus foto");remove.setOnClickListener(v->{if (!loadingPhoto) {photo="";showPhoto();}});
        photoActions.addView(remove,new LinearLayout.LayoutParams(0,-2,1));form.addView(photoActions);
        name=field(form,"Nama produk",original.optString("name"),InputType.TYPE_CLASS_TEXT,120);
        barcode=field(form,"Barcode (opsional)",original.optString("barcode"),InputType.TYPE_CLASS_TEXT,80);
        Button scan=outlineBtn("Scan barcode");scan.setOnClickListener(v->MerchantPosScanActivity.launch(this));form.addView(scan);
        price=field(form,"Harga jual (Rp)",original.optString("price"),InputType.TYPE_CLASS_NUMBER,10);
        stock=field(form,"Stok unit",original.has("stock")?Long.toString(original.optLong("stock")):"0",InputType.TYPE_CLASS_NUMBER,6);
        tax=field(form,"Pajak (%)",original.optString("tax","0"),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL,6);
        photo=state==null?original.optString("photo"):state.getString("photo","");
        if (state!=null) {
            name.setText(state.getString("name"));barcode.setText(state.getString("barcode"));
            price.setText(state.getString("price"));stock.setText(state.getString("stock"));tax.setText(state.getString("tax"));
        }
        showPhoto();save.setOnClickListener(v->submit());
    }
    private EditText field(LinearLayout form,String label,String value,int type,int max) {
        form.addView(sub(label));
        EditText field=input(label,type);field.setText(value);field.setSingleLine(true);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
        form.addView(field);return field;
    }
    private static String value(EditText input){return input.getText().toString().trim();}
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("name",value(name));state.putString("barcode",value(barcode));
        state.putString("price",value(price));state.putString("stock",value(stock));state.putString("tax",value(tax));
        state.putString("photo",photo);super.onSaveInstanceState(state);
    }
    private void submit() {
        try {
            String label=value(name);
            if (label.isEmpty()) {name.setError("Nama produk wajib diisi.");return;}
            long selling=Long.parseLong(value(price)), quantity=Long.parseLong(value(stock));
            double rate=Double.parseDouble(value(tax).replace(',','.'));
            if (selling<1||selling>1000000000||quantity<0||quantity>999999||!(rate>=0&&rate<=100)) throw new NumberFormatException();
            JSONObject data=new JSONObject().put("id",id).put("name",label).put("price",selling)
                    .put("stock",quantity).put("tax",Math.round(rate*100)/100.0).put("barcode",value(barcode)).put("photo",photo);
            setResult(RESULT_OK,new Intent().putExtra(PRODUCT,data.toString()).putExtra(OUTLET,outlet));finish();
        } catch(Exception error){toast("Periksa harga, stok, dan pajak (0–100%).");}
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        com.google.zxing.integration.android.IntentResult scan=IntentIntegrator.parseActivityResult(code,result,data);
        if (scan!=null) {if (scan.getContents()!=null) barcode.setText(scan.getContents());return;}
        if (code!=PHOTO||result!=RESULT_OK||data==null||data.getData()==null) return;
        Uri uri=data.getData();loadingPhoto=true;save.setEnabled(false);
        images.execute(()->{
            try {
                String image=readPhoto(uri);
                runOnUiThread(()->{if (!isDestroyed()&&!isFinishing()){photo=image;showPhoto();}});
            } catch(Exception failure){runOnUiThread(()->{if (!isDestroyed()) toast("Foto belum dapat dibaca.");});}
            finally {runOnUiThread(()->{if (!isDestroyed()){loadingPhoto=false;save.setEnabled(true);}});}
        });
    }
    @Override protected void onDestroy(){images.shutdown();super.onDestroy();}
    private void showPhoto() {
        byte[] bytes=photoBytes(photo);
        if (bytes==null) {com.bumptech.glide.Glide.with(this).clear(preview);preview.setImageResource(R.drawable.ic_merchant_package);}
        else com.bumptech.glide.Glide.with(this).load(bytes).centerCrop().into(preview);
    }
    static byte[] photoBytes(String value) {
        if (!value.startsWith(PREFIX)||value.length()>16410) return null;
        try {return android.util.Base64.decode(value.substring(PREFIX.length()),android.util.Base64.NO_WRAP);}
        catch(IllegalArgumentException error) {return null;}
    }
    private String readPhoto(Uri uri) throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try (InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
        if (bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>20000||bounds.outHeight>20000) throw new IOException();
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=1;
        while (Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>720) options.inSampleSize*=2;
        Bitmap bitmap;
        try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
        if (bitmap==null) throw new IOException();
        int rotation=0;boolean flipped=false;
        try(InputStream in=getContentResolver().openInputStream(uri)) {
            if (in!=null) {ExifInterface exif=new ExifInterface(in);rotation=exif.getRotationDegrees();flipped=exif.isFlipped();}
        }catch(IOException ignored){}
        int side=Math.min(bitmap.getWidth(),bitmap.getHeight());
        Matrix transform=new Matrix();
        float ratio=176f/side;transform.setScale(flipped?-ratio:ratio,ratio);transform.postRotate(rotation);
        Bitmap thumbnail=Bitmap.createBitmap(bitmap,(bitmap.getWidth()-side)/2,(bitmap.getHeight()-side)/2,side,side,transform,true);
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            for(int quality=83;quality>=23;quality-=10){
                out.reset();thumbnail.compress(Bitmap.CompressFormat.JPEG,quality,out);
                if(out.size()<=12288) return PREFIX+android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP);
            }
            throw new IOException();
        }finally{if(thumbnail!=bitmap)thumbnail.recycle();bitmap.recycle();}
    }
}
