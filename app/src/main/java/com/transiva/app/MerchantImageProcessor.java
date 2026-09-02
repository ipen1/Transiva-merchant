package com.transiva.app;

import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import androidx.exifinterface.media.ExifInterface;
import org.json.JSONObject;
import java.io.*;
import java.util.Locale;

/** Image decode/resize/WebP work kept out of Activity to reduce lifecycle/UI complexity. */
final class MerchantImageProcessor {
    private MerchantImageProcessor() {}
    static MerchantBaseActivity.PreparedImage prepare(MerchantBaseActivity a, Uri source, String prefix, int maxDimension, long optimizeAboveBytes, long targetBytes) throws Exception {
        if(source==null)return null; long originalBytes=contentLength(a,source); String originalMime=safeImageMime(a.getContentResolver().getType(source)); String originalName=displayName(a,source,prefix+"_image");
        if(originalBytes>0&&originalBytes<=optimizeAboveBytes){String ext=ext(originalMime);if(!originalName.toLowerCase(Locale.US).endsWith(ext))originalName=prefix+"_"+System.currentTimeMillis()+ext;return new MerchantBaseActivity.PreparedImage(source,originalMime,originalName,originalBytes,originalBytes,false);}
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream in=a.getContentResolver().openInputStream(source)){BitmapFactory.decodeStream(in,null,bounds);}if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IllegalArgumentException("File gambar tidak dapat dibaca.");
        int sample=1,biggest=Math.max(bounds.outWidth,bounds.outHeight);while(biggest/sample>Math.max(maxDimension*2,1600))sample*=2;BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=Math.max(1,sample);opts.inPreferredConfig=Bitmap.Config.ARGB_8888;Bitmap bitmap;try(InputStream in=a.getContentResolver().openInputStream(source)){bitmap=BitmapFactory.decodeStream(in,null,opts);}if(bitmap==null)throw new IllegalArgumentException("Gambar gagal didekode.");
        int rot=rotation(a,source);if(rot!=0){Matrix m=new Matrix();m.postRotate(rot);Bitmap r=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(r!=bitmap)bitmap.recycle();bitmap=r;}
        int max=Math.max(bitmap.getWidth(),bitmap.getHeight());if(max>maxDimension){float sc=maxDimension/(float)max;Bitmap r=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*sc)),Math.max(1,Math.round(bitmap.getHeight()*sc)),true);if(r!=bitmap)bitmap.recycle();bitmap=r;}
        ByteArrayOutputStream out=new ByteArrayOutputStream();int q=78;Bitmap.CompressFormat fmt=Build.VERSION.SDK_INT>=Build.VERSION_CODES.R?Bitmap.CompressFormat.WEBP_LOSSY:Bitmap.CompressFormat.WEBP;while(true){out.reset();if(!bitmap.compress(fmt,q,out)){bitmap.recycle();throw new IOException("Gagal membuat WebP.");}if(out.size()<=targetBytes||q<=52)break;q-=6;}bitmap.recycle();
        File dir=new File(a.getCacheDir(),"ai_resize_webp");if(!dir.exists()&&!dir.mkdirs()&&!dir.isDirectory())throw new IOException("Cache AI Resize tidak dapat dibuat.");File file=new File(dir,prefix+"_"+System.currentTimeMillis()+".webp");try(FileOutputStream fos=new FileOutputStream(file)){out.writeTo(fos);fos.flush();}long finalBytes=file.length();return new MerchantBaseActivity.PreparedImage(Uri.fromFile(file),"image/webp",file.getName(),originalBytes>0?originalBytes:finalBytes,finalBytes,true);
    }
    private static long contentLength(MerchantBaseActivity a,Uri u){try(AssetFileDescriptor afd=a.getContentResolver().openAssetFileDescriptor(u,"r")){if(afd!=null&&afd.getLength()>=0)return afd.getLength();}catch(Exception ignored){}return -1;}
    private static String displayName(MerchantBaseActivity a,Uri u,String fallback){if(u!=null&&"content".equalsIgnoreCase(u.getScheme()))try(android.database.Cursor c=a.getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String n=c.getString(0);if(n!=null&&!n.trim().isEmpty())return n.trim();}}catch(Exception ignored){}String path=u==null?"":u.getLastPathSegment();return path!=null&&!path.trim().isEmpty()?new File(path).getName():fallback;}
    private static String safeImageMime(String m){m=m==null?"":m.trim().toLowerCase(Locale.US);return ("image/png".equals(m)||"image/webp".equals(m)||"image/jpeg".equals(m))?m:"image/jpeg";}
    private static String ext(String m){if("image/png".equalsIgnoreCase(m))return ".png";if("image/webp".equalsIgnoreCase(m))return ".webp";return ".jpg";}
    private static int rotation(MerchantBaseActivity a,Uri u){try(InputStream in=a.getContentResolver().openInputStream(u)){if(in==null)return 0;ExifInterface e=new ExifInterface(in);int o=e.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);if(o==ExifInterface.ORIENTATION_ROTATE_90)return 90;if(o==ExifInterface.ORIENTATION_ROTATE_180)return 180;if(o==ExifInterface.ORIENTATION_ROTATE_270)return 270;}catch(Exception ignored){}return 0;}
}
