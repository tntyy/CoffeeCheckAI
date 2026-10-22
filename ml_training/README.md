# ml_training – script huấn luyện (chạy trên Google Colab, không thuộc app)

| Script | Việc | Trạng thái |
|---|---|---|
| `train_bean_detector_colab_v1.py` | Train YOLOv8n phát hiện hạt (1 lớp), xuất `bean_detector.tflite`. Tự đổi nhãn polygon sang khung chữ nhật. | **Đang dùng** |
| `rebuild_classifier_colab.py` | Cắt hạt đúng (hỗ trợ polygon) từ dataset gốc, train MobileNetV2 Fine/Defect, xuất `trash_classifier.tflite` + `trash_labels.txt`. | **Đang dùng** |
| `crop_beans.py` | Cắt hạt kiểu cũ. **Sai** với nhãn polygon. | Lỗi thời, đừng dùng |
| `train_coffee_model_colab.py` | Train classifier kiểu cũ trên dữ liệu cắt sai. | Lỗi thời, đừng dùng |
| `count_classes.py` | Đếm số khung theo lớp trong dataset. | Tiện ích |

Dataset: Roboflow *Green Bean Coffee*, v10, định dạng YOLOv8 (`revskyyy/green-bean-coffee`, CC BY 4.0). Không đưa dataset vào repo.

Thứ tự khi cần làm lại: detector và classifier độc lập nhau. Sau khi train, chép file `.tflite` (và `trash_labels.txt` nếu đổi classifier) vào `app/src/main/assets/` cùng lúc.
