package com.example.coffeecheckai.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface KetQuaKiemTraDao {

    @Insert
    long insert(KetQuaKiemTra ketQua);

    @Delete
    void delete(KetQuaKiemTra ketQua);

    @Query("DELETE FROM ket_qua_kiem_tra")
    void deleteAll();

    @Query("SELECT * FROM ket_qua_kiem_tra ORDER BY thoiGian DESC")
    List<KetQuaKiemTra> getAll();

    @Query("SELECT * FROM ket_qua_kiem_tra WHERE thoiGian BETWEEN :from AND :to ORDER BY thoiGian ASC")
    List<KetQuaKiemTra> getBetween(long from, long to);
}
