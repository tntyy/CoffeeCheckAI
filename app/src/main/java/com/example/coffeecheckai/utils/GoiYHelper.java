package com.example.coffeecheckai.utils;
/**
 * Sinh gợi ý xử lý dựa theo kết quả kiểm tra 1 mẻ hạt cà phê.
 * Dùng ngay trên card kết quả ở ScanFragment, không cần AI/internet.
 */
public class GoiYHelper {

    /**
     * @param defectRate  tỉ lệ lỗi theo PHẦN TRĂM (0 - 100), khớp với cách
     *                     ScanFragment tính: defectCount * 100f / total
     * @param fineCount   số hạt Fine (đạt)
     * @param defectCount số hạt Defect (lỗi)
     */
    public static String getSuggestion(float defectRate, int fineCount, int defectCount) {

        int total = fineCount + defectCount;

        if (total == 0) {
            return "Không phát hiện được hạt nào trong ảnh. Hãy chụp lại với ánh sáng tốt hơn, " +
                    "đặt các hạt cà phê tách rời nhau, tránh chồng lấn.";
        }

        if (defectRate == 0) {
            return "Mẫu đạt chất lượng tốt, không phát hiện hạt lỗi. " +
                    "Có thể đóng gói/xuất bán trực tiếp mà không cần sàng lọc thêm.";
        }

        if (defectRate <= 10) {
            return "Tỉ lệ lỗi " + formatPercent(defectRate) + "% ở mức khá. " +
                    "Nên loại riêng " + defectCount + " hạt lỗi trước khi đóng gói để nâng chất lượng.";
        }

        if (defectRate <= 25) {
            return "Tỉ lệ lỗi " + formatPercent(defectRate) + "% ở mức trung bình. " +
                    "Nên sàng lọc kỹ, loại bỏ " + defectCount + " hạt lỗi trước khi sử dụng/đóng gói.";
        }

        return "Tỉ lệ lỗi khá cao (" + formatPercent(defectRate) + "%). " +
                "Nên kiểm tra lại quy trình phơi/sơ chế, và phân loại kỹ trước khi dùng " +
                "hoặc chỉ dùng cho mục đích thấp cấp hơn.";
    }

    private static String formatPercent(float value) {
        return String.format("%.1f", value);
    }
}