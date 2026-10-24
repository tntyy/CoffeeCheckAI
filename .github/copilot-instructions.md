# Hướng dẫn cho trợ lý AI trong repo CoffeeCheckAI

Trước khi nhận xét hay sửa code, đọc `docs/PROJECT_STATUS.md` (kiến trúc, trạng thái, quyết định đã chốt, việc còn lại).

Điểm dễ hiểu sai:
- Pipeline đề cương: CameraX → OpenCV → Room → TFLite. **OpenCV là phần chính thức, đừng đề xuất bỏ.** App có **hai cách tìm hạt**, chọn trong Cài đặt: YOLO (`ml/BeanDetector.java`, mặc định) và OpenCV contour (`ImageUtils.findAllBeanRects`). OpenCV còn làm `enhance`, `checkQuality` (cảnh báo ảnh tối/mờ), cắt và vẽ khung.
- Contour kém hơn YOLO trên ảnh thật (bóng, hạt dính, thiếu sáng) và báo cáo sẽ so sánh hai cách. **Đừng đề xuất tinh chỉnh Canny/watershed**, chỉ giúp đo và so sánh.
- `trash_classifier.tflite` là model cà phê (tên `trash_` là tên cũ còn sót).
- Hai model có chuẩn hoá khác nhau (detector `/255`, classifier `/127.5 - 1`). Không sửa chung.
- Đừng đề xuất refactor lớn (tách nhiều lớp) hay kế hoạch nhiều tuần: dự án là đồ án, ưu tiên độ đúng của phân loại và hoàn thiện chức năng.
- Chỉ nhận xét dựa trên code thật trong repo ở nhánh được chỉ định. Nếu không chắc nhánh nào, hỏi lại. Không suy đoán file chưa đọc.
- Script train nằm ở `ml_training/` (chạy trên Colab).
