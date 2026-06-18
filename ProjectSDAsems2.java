import java.io.*;
import java.util.*;

public class ProjectSDAsems2 {

    //  MODEL
    static class Skripsi {
        int id;
        String judul, abstrak;
        int tahun;
        Skripsi(int id, String judul, String abstrak, int tahun) {
            this.id = id; this.judul = judul; this.abstrak = abstrak; this.tahun = tahun;
        }
    }

    static class HasilCari {
        Skripsi skripsi;
        double skor;
        HasilCari(Skripsi skripsi, double skor) { this.skripsi = skripsi; this.skor = skor; }
    }

  
    //  DATA GLOBAL
    static ArrayList<Skripsi> daftarSkripsi = new ArrayList<>();
    static HashMap<String, ArrayList<int[]>> invertedIndex = new HashMap<>();  // kata -> [idSkripsi, frekuensi]
    static HashMap<Integer, HashSet<String>> kataAbstrak = new HashMap<>();     // idSkripsi -> himpunan kata abstrak
    static HashMap<Integer, Skripsi> petaSkripsi = new HashMap<>();             // idSkripsi -> objek (akses O(1))
    static HashMap<Integer, ArrayList<double[]>> graph = new HashMap<>();       // idSkripsi -> [idTetangga, bobot]
    static HashMap<String, String[]> kamusSinonim = new HashMap<>();
    static HashMap<String, String> petaNormalSinonim = new HashMap<>();   // sinonim -> kata baku (untuk abstrak)
    static Set<String> kataNegasi = new HashSet<>(Arrays.asList("tidak", "tanpa", "bukan"));
    static final double THRESHOLD = 0.3;

    static Scanner scanner = new Scanner(System.in);

    //  MAIN
    public static void main(String[] args) {
        cetakBanner();

        muatDataCSV("data_skripsi.csv");
        if (daftarSkripsi.isEmpty()) {
            System.out.println("[!] Data kosong. Pastikan file data_skripsi.csv ada di folder yang sama.");
            return;
        }
        bangunKamusSinonim();
        bangunNormalSinonim();
        bangunIndex();          // bangun inverted index
        siapkanKataAbstrak();   // siapkan himpunan kata abstrak
        bangunGraph();          // BuildSimilarityGraph

        System.out.println("Sistem siap! Total skripsi dimuat: " + daftarSkripsi.size());

        boolean jalan = true;
        while (jalan) {
            cetakMenu();
            System.out.print("Pilih menu: ");
            String pilihan = scanner.nextLine().trim();
            switch (pilihan) {
                case "1" -> menuPencarian();
                case "2" -> menuSemuaSkripsi();
                case "3" -> jalan = false;
                default  -> System.out.println("  [!] Pilihan tidak valid.\n");
            }
        }
        System.out.println("\nProgram selesai.");
    }

    //  MEMBANGUN INVERTED INDEX  --  O(N x K)
    static void bangunIndex() {
        for (Skripsi s : daftarSkripsi) {
            HashMap<String, Integer> frekuensi = new HashMap<>();
            for (String kata : pecahKata(s.judul)) frekuensi.merge(kata, 1, Integer::sum);
            for (Map.Entry<String, Integer> e : frekuensi.entrySet()) {
                invertedIndex.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                             .add(new int[]{ s.id, e.getValue() });
            }
        }
    }

    static void siapkanKataAbstrak() {
        for (Skripsi s : daftarSkripsi) kataAbstrak.put(s.id, pecahKataDenganNegasiSinonim(s.abstrak));
    }

    //  SearchArticle: Pencarian + Skor (negasi & sinonim)
    //  Kompleksitas: O(Q x S x P)
    static ArrayList<HasilCari> searchArticle(String keyword) {
        HashMap<Integer, Double> skor = new HashMap<>();
        boolean negasi = false;

        for (String kata : pecahKata(keyword)) {
            if (kataNegasi.contains(kata)) { negasi = true; continue; }   // deteksi negasi

            ArrayList<String> daftarKata = new ArrayList<>();             // perluas sinonim
            daftarKata.add(kata);
            if (kamusSinonim.containsKey(kata)) daftarKata.addAll(Arrays.asList(kamusSinonim.get(kata)));

            for (String k : daftarKata) {
                ArrayList<int[]> postingan = invertedIndex.get(k);       // ambil dari inverted index
                if (postingan == null) continue;
                for (int[] e : postingan) {
                    double tambahan = negasi ? -e[1] : e[1];             // negasi: kurangi, biasa: tambah
                    skor.merge(e[0], tambahan, Double::sum);
                }
            }
            negasi = false;
        }

        ArrayList<HasilCari> hasil = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : skor.entrySet()) {
            if (e.getValue() > 0) hasil.add(new HasilCari(petaSkripsi.get(e.getKey()), e.getValue()));
        }
        return hasil;
    }

    //  MERGE SORT  --  O(M log M)
    //  byTahun = false -> urut skor relevansi (MergeSortRelevansi)
    //  byTahun = true  -> urut tahun terbaru   (SortByYear / MergeSortYear)
    static void mergeSort(ArrayList<HasilCari> list, int kiri, int kanan, boolean byTahun) {
        if (kiri >= kanan) return;
        int tengah = (kiri + kanan) / 2;
        mergeSort(list, kiri, tengah, byTahun);
        mergeSort(list, tengah + 1, kanan, byTahun);
        merge(list, kiri, tengah, kanan, byTahun);
    }

    static void merge(ArrayList<HasilCari> list, int kiri, int tengah, int kanan, boolean byTahun) {
        ArrayList<HasilCari> temp = new ArrayList<>();
        int i = kiri, j = tengah + 1;
        while (i <= tengah && j <= kanan) {
            if (lebihUtama(list.get(i), list.get(j), byTahun)) temp.add(list.get(i++));
            else                                               temp.add(list.get(j++));
        }
        while (i <= tengah) temp.add(list.get(i++));
        while (j <= kanan)  temp.add(list.get(j++));
        for (int k = kiri; k <= kanan; k++) list.set(k, temp.get(k - kiri));
    }

    // menentukan elemen mana yang didahulukan
    static boolean lebihUtama(HasilCari a, HasilCari b, boolean byTahun) {
        if (byTahun) return a.skripsi.tahun >= b.skripsi.tahun;   // tahun terbaru dulu
        if (a.skor != b.skor) return a.skor > b.skor;            // skor terbesar dulu
        return a.skripsi.tahun >= b.skripsi.tahun;               // jika skor sama, tahun terbaru dulu
    }

    //  BuildSimilarityGraph  --  O(N^2 x A)
    //  membangun graph kemiripan antar seluruh skripsi
    static void bangunGraph() {
        for (Skripsi s : daftarSkripsi) graph.put(s.id, new ArrayList<>());
        for (int i = 0; i < daftarSkripsi.size(); i++) {
            for (int j = i + 1; j < daftarSkripsi.size(); j++) {
                Skripsi a = daftarSkripsi.get(i), b = daftarSkripsi.get(j);
                double sim = jaccard(kataAbstrak.get(a.id), kataAbstrak.get(b.id));
                if (sim >= THRESHOLD) {                       // hubungkan jika cukup mirip
                    graph.get(a.id).add(new double[]{ b.id, sim });
                    graph.get(b.id).add(new double[]{ a.id, sim });
                }
            }
        }
    }

    // JaccardSimilarity dua himpunan kata  --  O(A)
    static double jaccard(HashSet<String> a, HashSet<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0;
        HashSet<String> kecil = a.size() <= b.size() ? a : b;   // telusuri himpunan terkecil
        HashSet<String> besar = a.size() <= b.size() ? b : a;
        int irisan = 0;
        for (String kata : kecil) if (besar.contains(kata)) irisan++;
        int gabungan = a.size() + b.size() - irisan;
        return gabungan == 0 ? 0 : (double) irisan / gabungan;
    }


    //  RecommendArticle (BFS)  --  O(V + E)
    //  menelusuri graph maksimal 2 lapis dari skripsi yang dibuka
    static ArrayList<HasilCari> recommendArticle(int startId) {
        Queue<int[]> queue = new LinkedList<>();   // [idSkripsi, level]
        Set<Integer> visited = new HashSet<>();
        ArrayList<HasilCari> rekomendasi = new ArrayList<>();

        queue.add(new int[]{ startId, 0 });
        visited.add(startId);

        while (!queue.isEmpty()) {
            int[] now = queue.poll();
            int id = now[0], level = now[1];
            if (level >= 2) continue;              // batas maksimal 2 lapis
            for (double[] edge : graph.get(id)) {
                int tetangga = (int) edge[0];
                double bobot = edge[1];
                if (!visited.contains(tetangga)) {
                    visited.add(tetangga);
                    rekomendasi.add(new HasilCari(petaSkripsi.get(tetangga), bobot));
                    queue.add(new int[]{ tetangga, level + 1 });
                }
            }
        }
        mergeSort(rekomendasi, 0, rekomendasi.size() - 1, false);   // urut kemiripan tertinggi

        ArrayList<HasilCari> top5 = new ArrayList<>();
        for (int i = 0; i < Math.min(5, rekomendasi.size()); i++) top5.add(rekomendasi.get(i));
        return top5;
    }

    //  MENU 1: Pencarian
    static void menuPencarian() {
        System.out.print("\nMasukkan kata kunci: ");
        String query = scanner.nextLine().trim();
        if (query.isEmpty()) { System.out.println("  [!] Kata kunci tidak boleh kosong.\n"); return; }

        ArrayList<HasilCari> hasil = searchArticle(query);
        if (hasil.isEmpty()) {
            System.out.println("  Tidak ada skripsi yang cocok dengan \"" + query + "\".\n");
            return;
        }
        mergeSort(hasil, 0, hasil.size() - 1, false);   // urut relevansi (skor; jika sama, tahun terbaru)

        System.out.println("\n  Hasil pencarian untuk \"" + query + "\"");
        System.out.println("  (urut berdasarkan relevansi; bila skor sama, tahun terbaru didahulukan)");
        System.out.println("  " + "-".repeat(60));
        int tampil = Math.min(10, hasil.size());
        for (int i = 0; i < tampil; i++) {
            HasilCari h = hasil.get(i);
            System.out.printf("  %2d. [ID %d] %s (%d) | skor: %.1f%n",
                    i + 1, h.skripsi.id, h.skripsi.judul, h.skripsi.tahun, h.skor);
        }

        pilihUntukDibuka();
    }

    
    //  MENU 2: Tampilkan semua skripsi
    static void menuSemuaSkripsi() {
        System.out.println("\n  Daftar seluruh skripsi (" + daftarSkripsi.size() + "):");
        System.out.println("  " + "-".repeat(60));
        for (Skripsi s : daftarSkripsi) {
            System.out.printf("  [ID %d] %s (%d)%n", s.id, s.judul, s.tahun);
        }
        pilihUntukDibuka();
    }


    //  Pilih Skripsi -> detail + rekomendasi.
    //  Rekomendasi dapat terus dibuka sampai memilih kembali ke menu.
    static void pilihUntukDibuka() {
        System.out.print("\nMasukkan ID skripsi untuk dibuka (-1 = kembali ke menu): ");
        String input = scanner.nextLine().trim();
        int id;
        try { id = Integer.parseInt(input); } catch (NumberFormatException e) {
            System.out.println("  [!] Input tidak valid.\n"); return;
        }
        if (id == -1) return;
        Skripsi s = petaSkripsi.get(id);
        if (s == null) { System.out.println("  [!] ID tidak ditemukan.\n"); return; }

        while (s != null) {
            tampilkanDetail(s);
            ArrayList<HasilCari> rekomendasi = recommendArticle(s.id);

            System.out.println("\n  --- Rekomendasi Skripsi Serupa ---");
            if (rekomendasi.isEmpty()) {
                System.out.println("  Belum ada skripsi serupa yang ditemukan.\n");
                return;
            }
            for (int i = 0; i < rekomendasi.size(); i++) {
                HasilCari h = rekomendasi.get(i);
                System.out.printf("  %d. [ID %d] %s (%d) | kemiripan: %.0f%%%n",
                        i + 1, h.skripsi.id, h.skripsi.judul, h.skripsi.tahun, h.skor * 100);
            }

            System.out.print("\n  Masukkan ID skripsi rekomendasi untuk dibuka (-1 = kembali ke menu): ");
            String in = scanner.nextLine().trim();
            int pilih;
            try { pilih = Integer.parseInt(in); } catch (NumberFormatException e) {
                System.out.println("  [!] Input tidak valid."); continue;
            }
            if (pilih == -1) { System.out.println(); return; }

            Skripsi tujuan = null;                          // pastikan ada di daftar rekomendasi
            for (HasilCari h : rekomendasi) if (h.skripsi.id == pilih) { tujuan = h.skripsi; break; }
            if (tujuan == null) System.out.println("  [!] ID tidak ada di daftar rekomendasi.");
            else s = tujuan;                                // pindah ke skripsi rekomendasi
        }
    }

    static void tampilkanDetail(Skripsi s) {
        System.out.println("\n  ============================================================");
        System.out.println("  DETAIL SKRIPSI");
        System.out.println("  ============================================================");
        System.out.println("  ID    : " + s.id);
        System.out.println("  Judul : " + s.judul);
        System.out.println("  Tahun : " + s.tahun);
        System.out.println("  Abstrak:");
        cetakAbstrak(s.abstrak);
    }

    //  BACA DATA DARI CSV  (pemisah titik koma)  format: id;judul;abstrak;tahun
    static void muatDataCSV(String namaFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(namaFile))) {
            String baris;
            boolean header = true;
            while ((baris = br.readLine()) != null) {
                if (header) { header = false; continue; }
                if (baris.trim().isEmpty()) continue;
                String[] kolom = baris.split(";", 4);
                if (kolom.length < 4) continue;
                int id = Integer.parseInt(kolom[0].trim());
                Skripsi s = new Skripsi(id, kolom[1].trim(), kolom[2].trim(), Integer.parseInt(kolom[3].trim()));
                daftarSkripsi.add(s);
                petaSkripsi.put(id, s);
            }
        } catch (IOException e) {
            System.out.println("[!] Gagal membaca file: " + e.getMessage());
        }
    }

    
    //  FUNGSI BANTU
    static ArrayList<String> pecahKata(String teks) {
        ArrayList<String> hasil = new ArrayList<>();
        if (teks == null) return hasil;
        for (String kata : teks.toLowerCase().split("[^a-z0-9]+")) {
            if (kata.length() > 2) hasil.add(kata);
        }
        return hasil;
    }

    // Bangun peta sinonim -> kata baku.
    // Kata baku = key pada kamusSinonim. Semua sinonimnya dipetakan ke key tsb.
    static void bangunNormalSinonim() {
        for (Map.Entry<String, String[]> e : kamusSinonim.entrySet()) {
            String baku = e.getKey();
            petaNormalSinonim.put(baku, baku);             // kata baku -> dirinya sendiri
            for (String sin : e.getValue()) {
                // hanya petakan jika belum ada, agar tidak saling menimpa
                petaNormalSinonim.putIfAbsent(sin, baku);
            }
        }
    }

    // Pecah abstrak menjadi himpunan kata, dengan dua pemrosesan:
    //  - Sinonim   : kata dinormalkan ke bentuk bakunya (forecasting -> prediksi)
    //  - Negasi    : kata yang didahului tidak/tanpa/bukan diberi awalan "neg_"
    //                sehingga "tanpa klasifikasi" tidak dianggap sama dengan "klasifikasi"
    static HashSet<String> pecahKataDenganNegasiSinonim(String teks) {
        HashSet<String> hasil = new HashSet<>();
        if (teks == null) return hasil;
        boolean negasi = false;
        for (String kata : teks.toLowerCase().split("[^a-z0-9]+")) {
            if (kata.length() <= 2) continue;
            if (kataNegasi.contains(kata)) { negasi = true; continue; }   // tandai negasi untuk kata berikutnya
            String baku = petaNormalSinonim.getOrDefault(kata, kata);     // normalisasi sinonim
            hasil.add(negasi ? "neg_" + baku : baku);                     // beri tanda jika negasi
            negasi = false;
        }
        return hasil;
    }

    static void cetakAbstrak(String abstrak) {
        String[] kata = abstrak.split(" ");
        StringBuilder baris = new StringBuilder("  ");
        int hitung = 0;
        for (String k : kata) {
            baris.append(k).append(" ");
            if (++hitung % 12 == 0) { System.out.println(baris); baris = new StringBuilder("  "); }
        }
        if (baris.length() > 2) System.out.println(baris);
    }

    static void bangunKamusSinonim() {
        kamusSinonim.put("prediksi", new String[]{"forecasting", "prediction", "peramalan", "prakiraan"});
        kamusSinonim.put("peramalan", new String[]{"prediksi", "forecasting", "prakiraan"});
        kamusSinonim.put("klasifikasi", new String[]{"classification", "kategorisasi", "penggolongan"});
        kamusSinonim.put("kategorisasi", new String[]{"klasifikasi", "penggolongan"});
        kamusSinonim.put("pengelompokan", new String[]{"clustering", "klasterisasi", "segmentasi"});
        kamusSinonim.put("clustering", new String[]{"pengelompokan", "klasterisasi", "segmentasi"});
        kamusSinonim.put("segmentasi", new String[]{"pengelompokan", "clustering"});
        kamusSinonim.put("gambar", new String[]{"citra", "image", "foto"});
        kamusSinonim.put("citra", new String[]{"gambar", "image", "foto"});
        kamusSinonim.put("teks", new String[]{"text", "tulisan", "dokumen"});
        kamusSinonim.put("dokumen", new String[]{"teks", "berkas", "file"});
        kamusSinonim.put("jaringan", new String[]{"network", "graf", "graph"});
        kamusSinonim.put("graf", new String[]{"graph", "jaringan", "network"});
        kamusSinonim.put("deteksi", new String[]{"detection", "pendeteksian", "identifikasi", "pengenalan"});
        kamusSinonim.put("pengenalan", new String[]{"deteksi", "identifikasi", "recognition"});
        kamusSinonim.put("identifikasi", new String[]{"deteksi", "pengenalan", "pendeteksian"});
        kamusSinonim.put("analisis", new String[]{"analysis", "analisa", "penganalisisan"});
        kamusSinonim.put("rekomendasi", new String[]{"recommendation", "saran", "anjuran"});
        kamusSinonim.put("sentimen", new String[]{"sentiment", "opini", "pendapat"});
        kamusSinonim.put("opini", new String[]{"sentimen", "pendapat", "tanggapan"});
        kamusSinonim.put("optimasi", new String[]{"optimization", "pengoptimalan", "optimalisasi"});
        kamusSinonim.put("ulasan", new String[]{"review", "tinjauan", "komentar"});
        kamusSinonim.put("review", new String[]{"ulasan", "tinjauan"});
        kamusSinonim.put("penjadwalan", new String[]{"scheduling"});
        kamusSinonim.put("rute", new String[]{"jalur", "lintasan", "path"});
        kamusSinonim.put("jalur", new String[]{"rute", "lintasan"});
        kamusSinonim.put("saham", new String[]{"stock", "efek"});
        kamusSinonim.put("cuaca", new String[]{"iklim", "weather"});
        kamusSinonim.put("mahasiswa", new String[]{"pelajar", "siswa"});
        kamusSinonim.put("akurasi", new String[]{"accuracy", "ketepatan"});
    }

    static void cetakBanner() {
        System.out.println("=".repeat(62));
        System.out.println("   SISTEM PENCARIAN SKRIPSI UNS");
        System.out.println("   Fakultas Teknologi Informasi dan Sains Data");
        System.out.println("=".repeat(62));
    }

    static void cetakMenu() {
        System.out.println("\n--------------------- MENU ---------------------");
        System.out.println("  [1] Cari skripsi");
        System.out.println("  [2] Tampilkan semua skripsi");
        System.out.println("  [3] Keluar");
        System.out.println("------------------------------------------------");
    }
}
