package com.example.coffeecheckai.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ket_qua_kiem_tra")
public class KetQuaKiemTra {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String duongDanAnh;      // đường dẫn ảnh đã lưu trong bộ nhớ máy
    public String ketQua;           // "Defect" hoặc "Fine"
    public float doTinCay;          // độ tin cậy AI, ví dụ 0.92
    public long thoiGianKiemTra;    // timestamp (System.currentTimeMillis())
    public String ghiChu;           // ghi chú thêm, có thể để trống
}
