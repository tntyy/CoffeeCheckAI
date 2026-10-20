package com.example.coffeecheckai.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Một lần quét (một mẻ hạt, một ảnh). Mỗi ảnh có nhiều hạt nên lưu số lượng
 * từng loại thay vì một nhãn duy nhất.
 */
@Entity(tableName = "ket_qua_kiem_tra")
public class KetQuaKiemTra {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String duongDanAnh;   // ảnh kết quả (đã vẽ khung) lưu trong bộ nhớ app
    public int tongHat;
    public int soFine;
    public int soDefect;
    public float tyLeLoi;        // phần trăm 0 - 100
    public String xepLoai;       // Tốt / Khá / Trung bình / Cần kiểm tra
    public float doTinCayTB;     // độ tin cậy trung bình của model phân loại, 0 - 1
    public long thoiGian;        // System.currentTimeMillis()
    public String ghiChu;        // gợi ý xử lý đã hiển thị lúc quét
}
