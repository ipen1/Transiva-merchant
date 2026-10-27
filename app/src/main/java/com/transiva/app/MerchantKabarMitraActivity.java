package com.transiva.app;

import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.text.InputFilter;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.UUID;

/** Photo stories, publication retry key, active story management and follower paging. */
public class MerchantKabarMitraActivity extends MerchantBaseActivity {
 private KabarMitraUi ui;private LinearLayout content,followers,stories,storyContent,followerContent;private TextView status,count;
 private EditText caption;private ImageView preview;private Button publish,pick,more;private Uri photo;
 private boolean writing=false;private String publishKey=UUID.randomUUID().toString();private int before=0,generation=0;private boolean jumped=false;
 private final Handler h=new Handler(Looper.getMainLooper());private JSONArray active=new JSONArray();private long clock,anchor;
 private final Runnable expiry=new Runnable(){public void run(){if(!isFinishing()){renderStories();h.postDelayed(this,1000);}}};
 @Override protected void onCreate(Bundle b){super.onCreate(b);ui=new KabarMitraUi(this);if(b!=null){String p=b.getString("photo");if(p!=null)photo=Uri.parse(p);publishKey=b.getString("publish_key",publishKey);}build();if(b!=null)caption.setText(b.getString("caption",""));caption.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){publishKey=UUID.randomUUID().toString();}public void afterTextChanged(android.text.Editable e){}});}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);jumped=false;if(content!=null)content.post(()->{if(intent.getBooleanExtra("show_followers",false))jumpFollowers();else showFollowers(false);});}
 @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(photo!=null)b.putString("photo",photo.toString());b.putString("caption",caption.getText().toString());b.putString("publish_key",publishKey);}
 @Override protected void onResume(){super.onResume();load(false);h.removeCallbacks(expiry);h.postDelayed(expiry,1000);}
 @Override protected void onPause(){h.removeCallbacks(expiry);super.onPause();}
 private void build(){
  content=new LinearLayout(this);setContentView(page(content));
  LinearLayout header=row();header.setGravity(android.view.Gravity.CENTER_VERTICAL);
  header.addView(title("Cerita Toko"),new LinearLayout.LayoutParams(0,-2,1));
  count=tv("Pengikut…",12,BLUE,true);count.setMinHeight(dp(48));count.setGravity(android.view.Gravity.CENTER);count.setOnClickListener(v->showFollowers(true));header.addView(count);content.addView(header);
  storyContent=new LinearLayout(this);storyContent.setOrientation(LinearLayout.VERTICAL);content.addView(storyContent);
  LinearLayout composer=ui.box();composer.addView(tv("Bagikan kabar • tampil 24 jam",14,NAVY,true));
  preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setContentDescription("Pratinjau foto cerita");preview.setVisibility(photo==null?View.GONE:View.VISIBLE);composer.addView(preview,new LinearLayout.LayoutParams(-1,dp(112)));if(photo!=null)com.bumptech.glide.Glide.with(this).load(photo).into(preview);
  caption=input("Kabar singkat dari tokomu • opsional",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);caption.setMinLines(1);caption.setMaxLines(3);caption.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});composer.addView(caption);
  LinearLayout actions=actionRow();pick=outlineBtn("Pilih foto");publish=btn("Terbitkan");
  pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,708);});publish.setOnClickListener(v->publish());
  actions.addView(pick,actionLp());actions.addView(publish,actionLp());composer.addView(actions);
  status=tv("",12,MUTED,false);status.setVisibility(View.GONE);status.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence t,int st,int c,int a){}public void onTextChanged(CharSequence t,int st,int b,int c){status.setVisibility(t.length()==0?View.GONE:View.VISIBLE);}public void afterTextChanged(android.text.Editable e){}});content.addView(status,1);storyContent.addView(composer);
  storyContent.addView(label("Cerita aktif"));stories=new LinearLayout(this);stories.setOrientation(LinearLayout.VERTICAL);storyContent.addView(stories);
  followerContent=new LinearLayout(this);followerContent.setOrientation(LinearLayout.VERTICAL);content.addView(followerContent);
  Button back=outlineBtn("← Kembali ke cerita");back.setOnClickListener(v->showFollowers(false));followerContent.addView(back);
  followerContent.addView(label("Pengikut toko"));followers=new LinearLayout(this);followers.setOrientation(LinearLayout.VERTICAL);followerContent.addView(followers);
  more=outlineBtn("Pengikut berikutnya");more.setVisibility(View.GONE);more.setOnClickListener(v->load(true));followerContent.addView(more);
  Button refresh=outlineBtn("↻ Perbarui");refresh.setOnClickListener(v->load(false));content.addView(refresh);
  showFollowers(getIntent().getBooleanExtra("show_followers",false));
 }
 private void showFollowers(boolean value){getIntent().putExtra("show_followers",value);storyContent.setVisibility(value?View.GONE:View.VISIBLE);followerContent.setVisibility(value?View.VISIBLE:View.GONE);content.post(()->content.requestRectangleOnScreen(new android.graphics.Rect(0,0,content.getWidth(),dp(48)),true));}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==708&&result==RESULT_OK&&data!=null&&data.getData()!=null){photo=data.getData();preview.setVisibility(View.VISIBLE);try{getContentResolver().takePersistableUriPermission(photo,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}com.bumptech.glide.Glide.with(this).load(photo).into(preview);publishKey=UUID.randomUUID().toString();status.setText("Foto siap diterbitkan.");}}
 private void load(boolean append){final int req=++generation;int cursor=append?before:0;more.setEnabled(false);java.util.concurrent.Future<?> pending=MerchantNetworkExecutor.executeRead(this,"kabar:"+req,()->{try{JSONObject res=new JSONObject(get(BASE+"merchant_kabar_mitra.php?before_user_id="+cursor));runOnUiThread(()->{if(isFinishing()||isDestroyed()||req!=generation)return;more.setEnabled(true);if(!res.optBoolean("success")){status.setText(res.optString("message","Gagal memuat kabar."));return;}count.setText(res.optInt("follower_count")+" Pengikut");clock=res.optLong("server_time");anchor=SystemClock.elapsedRealtime();active=res.optJSONArray("stories");if(active==null)active=new JSONArray();drawn="";renderStories();if(!append)followers.removeAllViews();JSONArray rows=res.optJSONArray("followers");if(rows!=null)for(int n=0;n<rows.length();n++){JSONObject f=rows.optJSONObject(n);if(f==null)continue;LinearLayout box=ui.box();box.addView(ui.text(f.optString("name","Pelanggan"),15,true));followers.addView(box);}if(followers.getChildCount()==0)followers.addView(ui.text("Belum ada pengikut. Ajak pelanggan mengikuti tokomu di Transfood.",14,false));before=res.optInt("next_before_user_id");more.setVisibility(before>0?View.VISIBLE:View.GONE);if(!jumped&&getIntent().getBooleanExtra("show_followers",false)){jumped=true;followers.post(this::jumpFollowers);}});}catch(Exception e){runOnUiThread(()->{if(req==generation&&!isFinishing()){more.setEnabled(true);status.setText("Koneksi gagal. Tekan Muat ulang.");}});}});if(pending==null){more.setEnabled(true);status.setText("Sistem sedang sibuk. Tekan Muat ulang sebentar lagi.");}}
 private void jumpFollowers(){showFollowers(true);}
 private String drawn="";
 private void renderStories(){
  long now=clock+(SystemClock.elapsedRealtime()-anchor)/1000;
  StringBuilder ids=new StringBuilder();ids.append(now/60).append(':');JSONArray current=new JSONArray();
  for(int i=0;i<active.length();i++){JSONObject item=active.optJSONObject(i);if(item!=null&&item.optLong("expires_at")>now){current.put(item);ids.append(item.optLong("id")).append(',');}}
  String signature=ids.toString();if(signature.equals(drawn)&&stories.getChildCount()>0)return;drawn=signature;stories.removeAllViews();
  if(current.length()==0){stories.addView(card("Belum ada cerita aktif. Bagikan foto menu atau promo hari ini."));return;}
  final JSONArray visible=current;
  for(int i=0;i<current.length();i++){
   JSONObject item=current.optJSONObject(i);LinearLayout box=ui.box();LinearLayout line=new LinearLayout(this);line.setGravity(android.view.Gravity.CENTER_VERTICAL);
   ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(round(SOFT_BLUE,dp(10)));image.setClipToOutline(true);ui.image(image,item.optString("image"));
   LinearLayout.LayoutParams imageLp=new LinearLayout.LayoutParams(dp(52),dp(64));imageLp.setMargins(0,0,dp(10),0);line.addView(image,imageLp);
   LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);TextView title=tv(item.optString("caption").isEmpty()?"Cerita foto":item.optString("caption"),14,TEXT,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(title);
   long minutes=Math.max(1,(item.optLong("expires_at")-now+59)/60);String remaining=minutes>=60?(minutes/60)+" j "+(minutes%60)+" m":minutes+" menit";
   labels.addView(tv("Berakhir dalam "+remaining,12,MUTED,false));line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
   Button options=outlineBtn("⋯");options.setContentDescription("Pilihan cerita");line.addView(options,new LinearLayout.LayoutParams(dp(48),-2));
   options.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Cerita toko").setItems(new String[]{"Lihat cerita","Hapus cerita"},(d,w)->{
    if(w==0)ui.viewer(visible,item.optInt("restaurant_id"),clock+(SystemClock.elapsedRealtime()-anchor)/1000);
    else new android.app.AlertDialog.Builder(this).setTitle("Hapus cerita ini?").setMessage("Cerita ini akan berhenti tampil ke pelanggan.").setNegativeButton("Batal",null).setPositiveButton("Hapus",(x,y)->delete(item.optLong("id"),options)).show();
   }).setNegativeButton("Tutup",null).show());
   image.setOnClickListener(v->ui.viewer(visible,item.optInt("restaurant_id"),clock+(SystemClock.elapsedRealtime()-anchor)/1000));box.addView(line);stories.addView(box);
  }
 }
 private void lock(boolean value){writing=value;publish.setEnabled(!value);pick.setEnabled(!value);caption.setEnabled(!value);publish.setText(value?"Menerbitkan…":"Terbitkan");}
 private void publish(){if(writing)return;if(photo==null){status.setText("Pilih foto story terlebih dahulu.");return;}lock(true);status.setText("Menyiapkan foto…");final Uri selected=photo;final String text=caption.getText().toString().trim(),key=publishKey;boolean accepted=MerchantNetworkExecutor.executeWrite("kabar-publish",()->{try{PreparedImage image=prepareAiResizeToWebp(selected,"story",1600,0,1400000);JSONObject p=new JSONObject();p.put("action","publish");p.put("caption",text);p.put("publish_key",key);JSONObject res=new JSONObject(postFormPrepared(BASE+"merchant_kabar_mitra.php",p,image,"image"));runOnUiThread(()->{if(isFinishing()||isDestroyed())return;lock(false);status.setText(res.optString("message","Gagal menerbitkan story."));if(res.optBoolean("success")){photo=null;preview.setImageDrawable(null);preview.setVisibility(View.GONE);caption.setText("");publishKey=UUID.randomUUID().toString();load(false);}});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){lock(false);status.setText("Koneksi gagal. Tekan Terbitkan untuk mencoba lagi.");}});}});if(!accepted){lock(false);status.setText("Permintaan masih diproses. Coba lagi sebentar.");}}
 private void delete(long id,Button b){b.setEnabled(false);boolean accepted=MerchantNetworkExecutor.executeWrite("story-delete:"+id,()->{try{JSONObject p=new JSONObject();p.put("action","delete");p.put("story_id",id);JSONObject res=new JSONObject(postJson(BASE+"merchant_kabar_mitra.php",p));runOnUiThread(()->{if(isFinishing()||isDestroyed())return;b.setEnabled(true);status.setText(res.optString("message"));if(res.optBoolean("success"))load(false);});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()){b.setEnabled(true);status.setText("Koneksi gagal menghapus story.");}});}});if(!accepted){b.setEnabled(true);status.setText("Permintaan masih diproses. Coba lagi sebentar.");}}
}
