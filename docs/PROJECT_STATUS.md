# CoffeeCheckAI – Trạng thái dự án

Cập nhật: 2026-10-22. Đây là tài liệu nguồn sự thật cho mọi người và mọi AI hỗ trợ dự án. **Đọc file này trước khi nhận xét code.**

## 1. Mục tiêu

App Android kiểm tra chất lượng hạt cà phê xanh bằng AI chạy trên máy (đồ án môn "Chuyên đề thiết kế ứng dụng cho thiết bị di động"):
chụp hoặc chọn ảnh → phát hiện từng hạt → phân loại Fine/Defect → tỷ lệ lỗi, xếp loại, gợi ý → lưu lịch sử.

Package `com.example.coffeecheckai`, minSdk 27, targetSdk 36, Java.
Thư viện: CameraX 1.3.4, OpenCV 4.9.0, TensorFlow Lite 2.14.0, Room 2.6.1, Material 1.14.
`gradle.properties` phải có `android.uniquePackageNames=false` (tránh lỗi trùng namespace của TensorFlow Lite).

## 2. Luồng xử lý một ảnh (`ui/scan/ScanFragment.processImage`)

```
ảnh gốc (camera/thư viện)
  → ImageUtils.checkQuality     OpenCV: cảnh báo ảnh tối/chói/mờ (không chặn)
  → tìm hạt, theo Cài đặt:      BeanDetector (YOLO, mặc định) HOẶC ImageUtils.findAllBeanRects (OpenCV contour)
  → ImageUtils.cropRect         cắt từng hạt, viền 15%
  → CoffeeClassifier            Fine / Defect + độ tin cậy
  → tổng hợp                    số hạt, tỷ lệ lỗi, xếp loại, GoiYHelper.getSuggestion
  → ImageUtils.drawAllBeanBoxes vẽ khung xanh (Fine) / đỏ (Defect)
  → lưu Room (nếu độ tin cậy TB >= ngưỡng trong Cài đặt)
```

### Hai model (đặt trong `app/src/main/assets/`)

| File | Vai trò | Đặc điểm |
|---|---|---|
| `bean_detector.tflite` | Tìm hạt | YOLOv8n, 1 lớp `coffee_bean`. Input NCHW `[1,3,640,640]` float32, pixel/255. Output `[1,5,8400]`. Letterbox nền xám 114. |
| `trash_classifier.tflite` + `trash_labels.txt` | Phân loại hạt | MobileNetV2, 224x224 RGB, pixel/127.5 - 1. Nhãn: `Defect`=0, `Fine`=1. |

Chú ý:
- Tên `trash_*` là tên cũ còn sót từ mẫu của môn học. Bên trong là model cà phê.
- Hai model dùng **cách chuẩn hoá khác nhau**, không dùng chung.
- `.tflite` và `trash_labels.txt` phải thay **cùng lúc** và đúng thứ tự nhãn.
- Ảnh đưa vào YOLO và classifier là ảnh **gốc**. `enhance()` chỉ dùng để TÌM hạt trên đường OpenCV; cắt và phân loại vẫn trên ảnh gốc (model được train trên ảnh gốc).

## 3. Trạng thái từng phần

| Phần | Trạng thái |
|---|---|
| Quét ảnh (camera + thư viện), 3 chế độ quét | Chạy được |
| Phát hiện hạt bằng YOLO | Chạy được, đếm đúng số hạt trên ảnh thử |
| Phát hiện hạt bằng OpenCV contour (Chương 2) | Chạy được, chọn trong Cài đặt; kém hơn YOLO khi có bóng/hạt dính/thiếu sáng |
| Kiểm tra chất lượng ảnh (OpenCV) | Xong: độ sáng TB + phương sai Laplacian, chỉ cảnh báo |
| Phân loại Fine/Defect | **Đang train lại** (xem mục 4) |
| Room (`data/`), lưu mỗi lần quét | Xong |
| Lịch sử (tìm kiếm, lọc, xem chi tiết, vuốt xoá) | Xong |
| Thống kê (tỉ lệ, xu hướng theo ngày) | Xong |
| Cài đặt (ngưỡng tin cậy, chế độ quét) | Xong |
| Chat (`ChatFragment`, `ChatbotHelper`, `KienThucLoiCaPhe`) | **Chưa làm**, chưa chọn hướng (chatbot offline hay API) |
| Xếp loại Tốt/Khá/Trung bình/Cần kiểm tra | Tạm theo tỷ lệ lỗi, cần thay bằng quy tắc trong đề cương |

## 4. Quyết định và lịch sử (để khỏi đi lại đường cũ)

1. **Giữ cả hai cách tìm hạt, mặc định YOLO.** Pipeline theo đề cương là CameraX → OpenCV → Room → TFLite nên OpenCV vẫn là một phần chính thức: `enhance`, `checkQuality`, `findAllBeanRects` (contour + watershed), `cropRect`, `drawAllBeanBoxes`. Người dùng chọn YOLO hoặc OpenCV trong Cài đặt. Lý do mặc định YOLO: contour thất bại với bóng, nền có hoa văn, hạt dính nhau, ánh sáng yếu (chính đề cương đã nêu rủi ro này). Câu chuyện cho báo cáo: thử contour OpenCV → đo giới hạn → nâng cấp lên object detection (YOLO). Cần thu số liệu so sánh hai cách trên cùng bộ ảnh test. Không tinh chỉnh thêm Canny/watershed; việc cần làm là đo và ghi lại kết quả.
2. **Lỗi dữ liệu train classifier.** Dataset Roboflow *Green Bean Coffee v10* có nhãn dạng **polygon** (100% dòng nhãn). `crop_beans.py` cũ đọc như khung chữ nhật nên cắt sai. Model phân loại cũ vì vậy kém trên ảnh thật (ảnh 1 hạt luôn ra Defect). Đã viết lại ở `ml_training/rebuild_classifier_colab.py` và đang train lại.
3. **Độ tin cậy softmax thường quá tự tin**: 100% không có nghĩa là đúng.
4. **Cách resize**: Android dùng bilinear nên khi train cũng đặt `interpolation='bilinear'`.
5. **Ngưỡng tin cậy trong Cài đặt**: kết quả dưới ngưỡng vẫn hiển thị nhưng không lưu vào lịch sử.

## 5. Việc còn lại (theo thứ tự)

1. Train xong classifier mới, chép `trash_classifier.tflite` + `trash_labels.txt` vào `assets/`, test lại trên điện thoại.
2. Chụp bộ ảnh test thật (ảnh 1 hạt, vài hạt, nhiều hạt dính, nền sáng/tối, có bóng), đo số hạt và độ đúng, **với cả hai cách tìm hạt (YOLO và OpenCV)** để làm bảng so sánh cho báo cáo.
3. Xử lý xoay ảnh theo EXIF (ảnh camera có thể bị xoay 90° khi hiển thị).
4. Loại hoặc đánh dấu "không chắc" các hạt có độ tin cậy thấp.
5. Quyết định và làm màn Chat.
6. Thay quy tắc xếp loại theo đề cương.

## 6. Quy tắc làm việc khi có nhiều người/AI cùng hỗ trợ

- **Nguồn sự thật** là nhánh `master` (sau khi merge). Mọi thay đổi đi qua nhánh riêng + Pull Request, người giữ dự án là người merge.
- Khi nhờ một AI đọc repo, nói rõ **nhánh và commit** nó nên đọc. Nhánh chưa merge thì AI đọc nhánh mặc định sẽ thấy code cũ.
- Mỗi file chỉ một người/AI sửa tại một thời điểm.
- Không đổi cách tiền xử lý ảnh trong app mà không đổi script train tương ứng trong `ml_training/` (và ngược lại).
- Không sửa tay file `.tflite`/labels. Xuất lại từ script train.
- Thay đổi lớn ghi vào mục nhật ký bên dưới.

## 7. Nhật ký thay đổi

- 2026-10-20: thêm Room, `BeanDetector`, Lịch sử, Thống kê, Cài đặt. Sửa `BeanDetector` cho input NCHW.
- 2026-10-22: bỏ `enhance()` khi dùng YOLO, padding crop 0.15. Phát hiện lỗi cắt polygon, viết lại script train classifier.
- 2026-10-22: giữ OpenCV cùng YOLO: thêm lựa chọn phương pháp tìm hạt trong Cài đặt và bước kiểm tra chất lượng ảnh (`ImageUtils.checkQuality`).
- 2026-10-28: Thống kê có xuất PDF (`report/PdfReportGenerator`, `FileExporter`, FileProvider) và phân bố xếp loại; Cài đặt chia nhóm (ngưỡng YOLO, kiểm tra chất lượng ảnh, quy tắc xếp loại chỉnh được thay cho luật tạm, flash/lưới/rung, tự lưu, tự xóa lịch sử cũ, xuất CSV, xóa lịch sử); thêm mục Chat trên menu (màn chờ, chưa có chức năng). Xếp loại giờ dùng `AppSettings.gradeFor()`.
