package com.transiva.app;

import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.util.Locale;

/** Shared local image optimizer. Story work is bounded and completed before upload. */
final class MerchantImageProcessor {
    static final int STORY_EDGE=1080;
    static final long STORY_TARGET_BYTES=220*1024L;
    private MerchantImageProcessor() {}
    static MerchantBaseActivity.PreparedImage prepare(MerchantBaseActivity a,Uri source,String prefix,int maxDimension,long optimizeAboveBytes,long targetBytes)throws Exception{
        return prepareInternal(a,source,prefix,maxDimension,optimizeAboveBytes,targetBytes,false);
    }
    static MerchantBaseActivity.PreparedImage prepareStory(MerchantBaseActivity a,Uri source)throws Exception{
        return prepareInternal(a,source,"story",STORY_EDGE,0,STORY_TARGET_BYTES,true);
    }
    static int[] fit(int width,int height,int edge){
        if(width<=0||height<=0||edge<=0)throw new IllegalArgumentException("Ukuran foto tidak valid.");
        double scale=Math.min(1d,edge/(double)Math.max(width,height));return new int[]{Math.max(1,(int)Math.round(width*scale)),Math.max(1,(int)Math.round(height*scale))};
    }
    private static MerchantBaseActivity.PreparedImage prepareInternal(MerchantBaseActivity a,Uri source,String prefix,int maxDimension,long optimizeAboveBytes,long targetBytes,boolean story)throws Exception{
        if(source==null)return null;
        long originalBytes=contentLength(a,source);String mime=safeImageMime(a.getContentResolver().getType(source));String name=displayName(a,source,prefix+"_image");
        if(!story&&originalBytes>0&&originalBytes<=optimizeAboveBytes){String ext=ext(mime);if(!name.toLowerCase(Locale.US).endsWith(ext))name=prefix+"_"+System.currentTimeMillis()+ext;return new MerchantBaseActivity.PreparedImage(source,mime,name,originalBytes,originalBytes,false);}
        if(story&&originalBytes>30L*1024*1024)throw new IOException("Foto terlalu besar. Pilih foto maksimal 30 MB.");
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream in=a.getContentResolver().openInputStream(source)){BitmapFactory.decodeStream(in,null,bounds);}
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("File gambar tidak dapat dibaca.");
        if(story&&(long)bounds.outWidth*bounds.outHeight>120000000L)throw new IOException("Resolusi foto terlalu besar. Pilih foto lain.");
        int sample=1,biggest=Math.max(bounds.outWidth,bounds.outHeight);while(biggest/sample>Math.max(maxDimension*2,1600))sample*=2;
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=Math.max(1,sample);opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap=null;
        try{
            try(InputStream in=a.getContentResolver().openInputStream(source)){bitmap=BitmapFactory.decodeStream(in,null,opts);}
            if(bitmap==null)throw new IOException("Gambar gagal dibaca.");
            Matrix matrix=new Matrix();
            if(story){int orientation=orientation(a,source);switch(orientation){
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:matrix.setScale(-1,1);break;
                case ExifInterface.ORIENTATION_ROTATE_180:matrix.setRotate(180);break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:matrix.setRotate(180);matrix.postScale(-1,1);break;
                case ExifInterface.ORIENTATION_TRANSPOSE:matrix.setRotate(90);matrix.postScale(-1,1);break;
                case ExifInterface.ORIENTATION_ROTATE_90:matrix.setRotate(90);break;
                case ExifInterface.ORIENTATION_TRANSVERSE:matrix.setRotate(-90);matrix.postScale(-1,1);break;
                case ExifInterface.ORIENTATION_ROTATE_270:matrix.setRotate(-90);break;
            }}else{int rot=rotation(a,source);if(rot!=0)matrix.postRotate(rot);}
            if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
            int[] size=fit(bitmap.getWidth(),bitmap.getHeight(),maxDimension);
            if(size[0]!=bitmap.getWidth()||size[1]!=bitmap.getHeight()){Bitmap resized=Bitmap.createScaledBitmap(bitmap,size[0],size[1],true);if(resized!=bitmap)bitmap.recycle();bitmap=resized;}
            ByteArrayOutputStream output=new ByteArrayOutputStream();Bitmap.CompressFormat format=Build.VERSION.SDK_INT>=30?Bitmap.CompressFormat.WEBP_LOSSY:Bitmap.CompressFormat.WEBP;
            if(story){
                for(int pass=0;;pass++){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Optimasi dibatalkan.");
                    for(int quality=82;;quality=Math.max(58,quality-6)){output.reset();if(!bitmap.compress(format,quality,output))throw new IOException("Foto gagal dioptimalkan.");if(output.size()<=targetBytes||quality==58)break;}
                    if(output.size()<=targetBytes)break;
                    int edge=(int)Math.floor(Math.max(bitmap.getWidth(),bitmap.getHeight())*.82);
                    if(pass>=8||edge<400)throw new IOException("Foto belum bisa dibuat ringan. Coba foto lain.");
                    size=fit(bitmap.getWidth(),bitmap.getHeight(),edge);Bitmap smaller=Bitmap.createScaledBitmap(bitmap,size[0],size[1],true);if(smaller!=bitmap)bitmap.recycle();bitmap=smaller;
                }
            }else{
                int quality=78;while(true){output.reset();if(!bitmap.compress(format,quality,output))throw new IOException("Gagal membuat WebP.");if(output.size()<=targetBytes||quality<=52)break;quality-=6;}
            }
            File dir=new File(a.getCacheDir(),"ai_resize_webp");if(!dir.exists()&&!dir.mkdirs()&&!dir.isDirectory())throw new IOException("Cache foto belum dapat dibuat.");
            if(story){File[] stale=dir.listFiles();if(stale!=null)for(File file:stale)if(file.getName().startsWith("story_")&&System.currentTimeMillis()-file.lastModified()>48L*60*60*1000)file.delete();}
            File file=File.createTempFile(prefix+"_",".webp",dir);
            try(FileOutputStream out=new FileOutputStream(file)){output.writeTo(out);}catch(Exception error){file.delete();throw error;}
            return new MerchantBaseActivity.PreparedImage(Uri.fromFile(file),"image/webp",file.getName(),originalBytes>0?originalBytes:file.length(),file.length(),true);
        }catch(OutOfMemoryError lowMemory){throw new IOException("Memori belum cukup. Tutup aplikasi lain atau pilih foto lebih kecil.");}
        finally{if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();}
    }
    private static long contentLength(MerchantBaseActivity a,Uri u){try(AssetFileDescriptor afd=a.getContentResolver().openAssetFileDescriptor(u,"r")){if(afd!=null&&afd.getLength()>=0)return afd.getLength();}catch(Exception ignored){}return -1;}
    private static String displayName(MerchantBaseActivity a,Uri u,String fallback){if(u!=null&&"content".equalsIgnoreCase(u.getScheme()))try(android.database.Cursor c=a.getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String n=c.getString(0);if(n!=null&&!n.trim().isEmpty())return n.trim();}}catch(Exception ignored){}String path=u==null?"":u.getLastPathSegment();return path!=null&&!path.trim().isEmpty()?new File(path).getName():fallback;}
    private static String safeImageMime(String m){m=m==null?"":m.trim().toLowerCase(Locale.US);return ("image/png".equals(m)||"image/webp".equals(m)||"image/jpeg".equals(m))?m:"image/jpeg";}
    private static String ext(String m){if("image/png".equalsIgnoreCase(m))return ".png";if("image/webp".equalsIgnoreCase(m))return ".webp";return ".jpg";}
    private static int orientation(MerchantBaseActivity a,Uri u){try(InputStream in=a.getContentResolver().openInputStream(u)){if(in!=null)return new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(Exception ignored){}return ExifInterface.ORIENTATION_NORMAL;}
    private static int rotation(MerchantBaseActivity a,Uri u){int o=orientation(a,u);if(o==ExifInterface.ORIENTATION_ROTATE_90)return 90;if(o==ExifInterface.ORIENTATION_ROTATE_180)return 180;if(o==ExifInterface.ORIENTATION_ROTATE_270)return 270;return 0;}
}
