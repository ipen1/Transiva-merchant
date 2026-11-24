package com.transiva.app;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A single in-flight mutation for this signed-in cashier, excluded from cloud backup. */
final class MerchantPosPending {
    private final AtomicFile file;

    MerchantPosPending(Context context,String userId,String username,boolean staff) {
        String account=UUID.nameUUIDFromBytes((userId+":"+staff+":"+username)
                .getBytes(StandardCharsets.UTF_8)).toString();
        file=new AtomicFile(new File(context.getNoBackupFilesDir(),"pos-pending-"+account));
    }

    JSONObject read() throws IOException,JSONException {
        try {
            String value=new String(file.readFully(),StandardCharsets.UTF_8);
            return value.isEmpty()?null:new JSONObject(value);
        } catch (FileNotFoundException absent) {
            if (file.getBaseFile().exists()) throw absent;
            return null;
        }
    }

    void write(JSONObject value) throws IOException {
        store(value.toString());
    }

    void clear() throws IOException {
        store("");
    }

    private void store(String value) throws IOException {
        FileOutputStream stream=file.startWrite();
        try {
            stream.write(value.getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
            file.finishWrite(stream);
        } catch (IOException failure) {
            file.failWrite(stream);
            throw failure;
        }
    }
}
