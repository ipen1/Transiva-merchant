package com.transiva.app;
import android.net.Uri;
import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import javax.net.ssl.SSLException;

/** Network transport separated from Activity. GET may retry once; writes never auto-retry. */
final class MerchantHttpClient {
    private MerchantHttpClient() {}
    private static HttpURLConnection open(MerchantBaseActivity a,String link,int connect,int read)throws Exception{HttpURLConnection c=MerchantApiClient.open(a,link);c.setConnectTimeout(connect);c.setReadTimeout(read);c.setDoInput(true);return c;}
    private static void auth(MerchantBaseActivity a,HttpURLConnection c){c.setRequestProperty("Accept","application/json");c.setRequestProperty("Cache-Control","no-store");c.setRequestProperty("X-Transiva-App","Android-Merchant");c.setRequestProperty("X-App-Scope","merchant");c.setRequestProperty("X-Android-SDK",String.valueOf(Build.VERSION.SDK_INT));String token="";try{token=a.sessionManager==null?"":a.sessionManager.getToken().trim();}catch(Exception ignored){}if(!token.isEmpty()){c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("X-Device-UUID",DeviceIdentityManager.getInstallationUuid(a));}}
    private static String response(MerchantBaseActivity a,HttpURLConnection c)throws Exception{int code=c.getResponseCode();InputStream is=code>=400?c.getErrorStream():c.getInputStream();String out=read(is);if(code==401){String api="";try{api=new JSONObject(out).optString("code","");}catch(Exception ignored){}if(api.isEmpty()||"UNAUTHORIZED".equalsIgnoreCase(api)||"SESSION_EXPIRED".equalsIgnoreCase(api)||"SESSION_REVOKED".equalsIgnoreCase(api)||"INVALID_TOKEN".equalsIgnoreCase(api)){a.runOnUiThread(a::handleSessionExpired);}}return out;}
    static String get(MerchantBaseActivity a,String link)throws Exception{Exception last=null;for(int attempt=0;attempt<2;attempt++){HttpURLConnection c=null;try{c=open(a,link,10000,15000);auth(a,c);c.setRequestMethod("GET");return response(a,c);}catch(SocketTimeoutException|UnknownHostException|SSLException e){last=e;if(attempt==0)try{Thread.sleep(350L);}catch(InterruptedException ie){Thread.currentThread().interrupt();throw e;}}finally{if(c!=null)c.disconnect();}}throw last==null?new IOException("Koneksi merchant gagal"):last;}
    static String postJson(MerchantBaseActivity a,String link,JSONObject p)throws Exception{return postJson(a,link,p,"");}
    static String postJson(MerchantBaseActivity a,String link,JSONObject p,String idempotencyKey)throws Exception{HttpURLConnection c=null;try{c=open(a,link,10000,15000);auth(a,c);c.setDoOutput(true);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json; charset=UTF-8");if(idempotencyKey!=null&&!idempotencyKey.trim().isEmpty())c.setRequestProperty("Idempotency-Key",idempotencyKey.trim());try(OutputStream os=c.getOutputStream()){os.write(p.toString().getBytes("UTF-8"));os.flush();}return response(a,c);}finally{if(c!=null)c.disconnect();}}
    static String postMultipart(MerchantBaseActivity a, String link, JSONObject fields, Uri uri,
                                String field, String fileName, String mime) throws Exception {
        java.util.Map<String,MerchantBaseActivity.PreparedImage> files = new java.util.LinkedHashMap<>();
        if (uri != null) files.put(field, new MerchantBaseActivity.PreparedImage(uri,mime,fileName,0,0,false));
        return postMultipart(a,link,fields,files);
    }

    static String postMultipart(MerchantBaseActivity a, String link, JSONObject fields,
                                java.util.Map<String,MerchantBaseActivity.PreparedImage> files) throws Exception {
        String boundary = "----Transiva" + java.util.UUID.randomUUID();
        HttpURLConnection connection = open(a,link,15000,45000);
        try {
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
            auth(a,connection);
            try (OutputStream out = connection.getOutputStream()) {
                java.util.Iterator<String> keys = fields.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    write(out,"--"+boundary+"\r\nContent-Disposition: form-data; name=\""+key+"\"\r\n\r\n"+fields.optString(key,"")+"\r\n");
                }
                byte[] buffer = new byte[8192];
                for (java.util.Map.Entry<String,MerchantBaseActivity.PreparedImage> entry : files.entrySet()) {
                    MerchantBaseActivity.PreparedImage image = entry.getValue();
                    write(out,"--"+boundary+"\r\nContent-Disposition: form-data; name=\""+entry.getKey()
                            +"\"; filename=\""+image.fileName+"\"\r\nContent-Type: "+image.mimeType+"\r\n\r\n");
                    try (InputStream input = a.getContentResolver().openInputStream(image.uri)) {
                        if (input == null) throw new IOException("File upload tidak dapat dibaca.");
                        int count;
                        while ((count = input.read(buffer)) != -1) out.write(buffer,0,count);
                    }
                    write(out,"\r\n");
                }
                write(out,"--"+boundary+"--\r\n");
                out.flush();
            }
            return response(a,connection);
        } finally { connection.disconnect(); }
    }

    private static void write(OutputStream os,String s)throws Exception{os.write(s.getBytes("UTF-8"));}
    static String read(InputStream is)throws Exception{if(is==null)return "";try(BufferedReader br=new BufferedReader(new InputStreamReader(is,"UTF-8"))){StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);return sb.toString();}}
}
