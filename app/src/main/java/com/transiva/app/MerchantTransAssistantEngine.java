package com.transiva.app;

import java.text.Normalizer;
import java.util.Locale;

public final class MerchantTransAssistantEngine {
    public static final class Reply { public final String text,action,label; Reply(String t,String a,String l){text=t;action=a;label=l;} }
    public Reply answer(String raw){String q=norm(raw);if(q.isEmpty())return r("Tulis kebutuhan Anda, misalnya ‘cek pesanan’, ‘tambah menu’, atau ‘kenapa toko tertutup’.\n","","");
        if(any(q,"halo","hai","pagi","siang","malam"))return r("Halo Merchant! Saya Trans Asisten. Saya bisa membantu pesanan, menu, status toko, keuangan, ulasan, profil, lokasi, dan verifikasi akun.","","");
        if(any(q,"pesanan","order masuk","order baru","cek order","cek pesanan"))return r("Buka Pesanan untuk melihat order aktif. Prioritaskan order baru, konfirmasi kesiapan, lalu ikuti status pengantaran sampai selesai.","ORDERS","Buka Pesanan");
        if(any(q,"tambah menu","menu baru","produk baru","tambah makanan"))return r("Buka Tambah Menu. Isi nama, kategori, harga, stok, deskripsi, dan foto yang jelas agar customer mudah memilih.","ADD_MENU","Tambah Menu");
        if(any(q,"daftar menu","edit menu","hapus menu","stok menu"))return r("Buka Daftar Menu untuk mengubah harga, stok, foto, status aktif, atau menghapus menu yang tidak digunakan.","MENU","Kelola Menu");
        if(any(q,"buka toko","tutup toko","status toko","kenapa toko tutup","terkunci"))return r("Status toko hanya dapat dibuka jika akun merchant sudah terverifikasi. Merchant baru selalu dimulai dalam status Tutup untuk keamanan.","DASHBOARD","Lihat Status Toko");
        if(any(q,"verifikasi","belum verifikasi","akun diverifikasi"))return r("Merchant yang daftar sendiri berstatus Belum Terverifikasi sampai disetujui admin. Selama belum terverifikasi, merchant tidak tampil di customer dan toko tetap terkunci Tutup.","SETTINGS","Buka Akun");
        if(any(q,"lokasi","gps","titik merchant","koordinat","akurasi"))return r("Untuk titik merchant, gunakan Ambil Lokasi Presisi dan tunggu sampai akurasi ideal ≤25 meter. Jika masih di atas 50 meter, pindah ke area terbuka lalu ambil ulang.","PROFILE","Buka Profil Merchant");
        if(any(q,"keuangan","pendapatan","omzet","saldo","uang"))return r("Buka Keuangan untuk melihat ringkasan pendapatan merchant. Dashboard juga menampilkan omzet hari ini sebagai pantauan cepat.","FINANCE","Buka Keuangan");
        if(any(q,"rating","ulasan","review","bintang"))return r("Buka Rating & Ulasan untuk memantau pengalaman customer. Gunakan masukan berulang sebagai prioritas perbaikan layanan dan menu.","REVIEWS","Buka Ulasan");
        if(any(q,"analitik","statistik","performa","laris"))return r("Buka Analitik untuk melihat performa bisnis. Bandingkan tren order, omzet, dan menu untuk menentukan langkah berikutnya.","ANALYTICS","Buka Analitik");
        if(any(q,"jam buka","operasional","jadwal"))return r("Atur jam operasional dari menu Operasional. Pastikan jadwal sesuai waktu merchant benar-benar siap menerima pesanan.","OPERATIONS","Buka Operasional");
        if(any(q,"profil","alamat","nama merchant","kategori merchant"))return r("Buka Profil Merchant untuk mengelola identitas dan informasi restoran. Pastikan lokasi dan data bisnis akurat sebelum dipublikasikan.","PROFILE","Buka Profil Merchant");
        if(any(q,"hapus akun","delete akun","tutup akun"))return r("Penghapusan akun bersifat permanen. Buka Pengaturan, pilih Hapus Akun Permanen, lalu verifikasi password sebelum melanjutkan.","SETTINGS","Buka Pengaturan");
        if(any(q,"chat","driver","hubungi driver"))return r("Chat driver tersedia dari pesanan aktif. Gunakan untuk koordinasi kesiapan makanan atau proses pengambilan.","ORDERS","Lihat Pesanan");
        return r("Saya belum yakin. Coba tanya lebih spesifik seperti ‘cek pesanan’, ‘tambah menu’, ‘atur jam buka’, ‘cek pendapatan’, ‘lokasi merchant’, atau ‘verifikasi akun’.","","");
    }
    private static Reply r(String t,String a,String l){return new Reply(t,a,l);}private static boolean any(String q,String...x){for(String s:x)if(q.contains(norm(s)))return true;return false;}private static String norm(String s){if(s==null)return"";return Normalizer.normalize(s.toLowerCase(new Locale("id","ID")),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();}
}
