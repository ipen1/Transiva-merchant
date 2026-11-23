package com.transiva.app;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.usb.*;
import android.os.Build;
import android.print.PrintManager;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MerchantPosPrinters {
    static final int BLUETOOTH_PERMISSION=728;
    private static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final MerchantPosActivity host;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private BroadcastReceiver receiver;
    private List<String> permissionLines;
    private boolean printing;
    MerchantPosPrinters(MerchantPosActivity host){this.host=host;}

    void choose(List<String> lines){
        if(printing || receiver!=null){host.toast("Pencetakan masih diproses.");return;}
        new AlertDialog.Builder(host).setTitle("Cetak struk")
                .setItems(new String[]{"USB · ESC/POS","Bluetooth · ESC/POS","Wi-Fi / LAN · ESC/POS","Cetak Android / Simpan PDF"},
                        (dialog,which)->{
                            if(which==0)usb(lines);
                            else if(which==1)bluetooth(lines);
                            else if(which==2)network(lines);
                            else {
                                PrintManager manager=host.getSystemService(PrintManager.class);
                                if(manager!=null) manager.print("Struk Transiva",new MerchantPosPrintAdapter(host,lines),null);
                            }
                        }).show();
    }
    private interface Send {void run(byte[] data)throws Exception;}
    private void send(List<String> lines,Send output){
        if(printing)return;printing=true;
        worker.execute(()->{
            String status;
            try {output.run(escpos(lines));status="Data struk dikirim ke printer.";}
            catch(Exception failure){status="Gagal mengirim struk. Periksa printer dan koneksi.";}
            String feedback=status;
            host.runOnUiThread(()->{printing=false;if(!host.isDestroyed()&&!host.isFinishing())host.toast(feedback);});
        });
    }
    private byte[] escpos(List<String> lines)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        bytes.write(new byte[]{27,64});
        for(int i=0;i<lines.size();i++){
            boolean bold=i==0||lines.get(i).startsWith("Total ");
            bytes.write(new byte[]{27,69,(byte)(bold?1:0)});
            String line=lines.get(i).replace("×","x").replace("·","-")
                    .replaceAll("[\\p{Cntrl}\\p{Cf}]"," ").replaceAll("[^\\x20-\\x7E]","?");
            if(line.isEmpty())bytes.write(10);
            for(int offset=0;offset<line.length();offset+=32)
                bytes.write((line.substring(offset,Math.min(offset+32,line.length()))+"\n").getBytes(StandardCharsets.US_ASCII));
        }
        bytes.write(new byte[]{27,69,0,10,10,10});return bytes.toByteArray();
    }
    private void bluetooth(List<String> lines){
        BluetoothManager manager=host.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null){host.toast("Bluetooth tidak tersedia.");return;}
        if(Build.VERSION.SDK_INT>=31&&host.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            permissionLines=lines;host.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},BLUETOOTH_PERMISSION);return;
        }
        if(!adapter.isEnabled()){host.toast("Aktifkan Bluetooth dan pasangkan printer di Pengaturan.");return;}
        List<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
        if(devices.isEmpty()){host.toast("Pasangkan printer Bluetooth di Pengaturan Android.");return;}
        String[] labels=new String[devices.size()];
        for(int i=0;i<labels.length;i++)labels[i]=devices.get(i).getName()+" · "+devices.get(i).getAddress();
        new AlertDialog.Builder(host).setTitle("Printer Bluetooth terpasang").setItems(labels,(d,i)->send(lines,bytes->{
            try(BluetoothSocket socket=devices.get(i).createRfcommSocketToServiceRecord(SPP)){
                Runnable timeout=()->{try{socket.close();}catch(IOException ignored){}};
                main.postDelayed(timeout,12000);
                try{socket.connect();OutputStream out=socket.getOutputStream();out.write(bytes);out.flush();}
                finally{main.removeCallbacks(timeout);}
            }
        })).show();
    }
    void permission(boolean granted){
        List<String> lines=permissionLines;permissionLines=null;
        if(granted&&lines!=null) bluetooth(lines);
        else if(!granted)host.toast("Izin Bluetooth diperlukan untuk mencetak.");
    }
    private static final class UsbTarget {
        final UsbDevice device;final UsbInterface port;final UsbEndpoint endpoint;
        UsbTarget(UsbDevice d,UsbInterface p,UsbEndpoint e){device=d;port=p;endpoint=e;}
    }
    private void usb(List<String> lines){
        UsbManager manager=host.getSystemService(UsbManager.class);
        if(manager==null){host.toast("USB tidak tersedia.");return;}
        List<UsbTarget> devices=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(UsbDevice device:manager.getDeviceList().values()) for(int i=0;i<device.getInterfaceCount();i++){
            UsbInterface port=device.getInterface(i);
            if(port.getInterfaceClass()!=UsbConstants.USB_CLASS_PRINTER&&port.getInterfaceClass()!=UsbConstants.USB_CLASS_VENDOR_SPEC)continue;
            for(int j=0;j<port.getEndpointCount();j++){
                UsbEndpoint endpoint=port.getEndpoint(j);
                if(endpoint.getDirection()!=UsbConstants.USB_DIR_OUT||endpoint.getType()!=UsbConstants.USB_ENDPOINT_XFER_BULK)continue;
                devices.add(new UsbTarget(device,port,endpoint));labels.add(device.getDeviceName()+" · USB "+port.getId());break;
            }
        }
        if(devices.isEmpty()){host.toast("Hubungkan printer ESC/POS dengan kabel USB OTG.");return;}
        new AlertDialog.Builder(host).setTitle("Pilih printer USB").setItems(labels.toArray(new String[0]),(d,i)->{
            UsbTarget target=devices.get(i);
            if(manager.hasPermission(target.device)){usbWrite(manager,target,lines);return;}
            String event=host.getPackageName()+".POS_USB_"+UUID.randomUUID();
            receiver=new BroadcastReceiver(){
                @Override public void onReceive(Context context,Intent intent){
                    if(!event.equals(intent.getAction()))return;
                    clear();
                    if(manager.hasPermission(target.device))usbWrite(manager,target,lines);
                    else host.toast("Izin printer USB ditolak.");
                }
            };
            if(Build.VERSION.SDK_INT>=33)host.registerReceiver(receiver,new IntentFilter(event),Context.RECEIVER_NOT_EXPORTED);
            else host.registerReceiver(receiver,new IntentFilter(event));
            Intent permission=new Intent(event).setPackage(host.getPackageName());
            int flags=PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_IMMUTABLE;
            try{manager.requestPermission(target.device,PendingIntent.getBroadcast(host,0,permission,flags));}
            catch(RuntimeException error){clear();host.toast("Izin printer USB belum dapat diminta.");}
        }).show();
    }
    private void usbWrite(UsbManager manager,UsbTarget target,List<String> lines){
        send(lines,bytes->{
            UsbDeviceConnection connection=manager.openDevice(target.device);
            if(connection==null)throw new IOException();
            try{
                if(!connection.claimInterface(target.port,true))throw new IOException();
                try{
                    int offset=0;
                    while(offset<bytes.length){
                        int wrote=connection.bulkTransfer(target.endpoint,bytes,offset,Math.min(4096,bytes.length-offset),3000);
                        if(wrote<=0)throw new IOException();offset+=wrote;
                    }
                }finally{connection.releaseInterface(target.port);}
            }finally{connection.close();}
        });
    }
    private void network(List<String> lines){
        LinearLayout inputs=new LinearLayout(host);inputs.setOrientation(LinearLayout.VERTICAL);inputs.setPadding(host.dp(18),0,host.dp(18),0);
        EditText address=host.input("IP atau nama printer",InputType.TYPE_CLASS_TEXT);
        EditText port=host.input("Port printer",InputType.TYPE_CLASS_NUMBER);port.setText("9100");
        address.setTextColor(0xff0b1530);port.setTextColor(0xff0b1530);
        inputs.addView(address);inputs.addView(port);
        AlertDialog dialog=new AlertDialog.Builder(host).setTitle("Printer Wi-Fi / LAN").setView(inputs)
                .setNegativeButton("Batal",null).setPositiveButton("Cetak",null).create();
        dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String printer=address.getText().toString().trim();
            int number;
            try{number=Integer.parseInt(port.getText().toString().trim());}
            catch(NumberFormatException error){number=0;}
            if(!printer.matches("[A-Za-z0-9][A-Za-z0-9.:-]{0,252}")||number<1||number>65535){host.toast("Isi alamat dan port printer yang valid.");return;}
            final int targetPort=number;
            dialog.dismiss();send(lines,bytes->{
                try(Socket socket=new Socket()){
                    socket.connect(new InetSocketAddress(printer,targetPort),5000);
                    OutputStream out=socket.getOutputStream();out.write(bytes);out.flush();
                }
            });
        });
    }
    void close(){clear();worker.shutdown();}
    private void clear(){if(receiver!=null){host.unregisterReceiver(receiver);receiver=null;}}
}
