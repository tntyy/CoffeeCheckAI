# ============================================================
# TRAIN MODEL OBJECT DETECTION PHÁT HIỆN HẠT CÀ PHÊ (Colab)
# ============================================================
# Thay cho ImageUtils.findAllBeanRects() (OpenCV thủ công, không ổn định
# với ánh sáng/nền thực tế) bằng 1 model YOLOv8n học thật từ dữ liệu
# Roboflow gốc (đã có bounding box sẵn, không cần gán nhãn lại).
#
# Model chỉ học PHÁT HIỆN VỊ TRÍ hạt cà phê (1 lớp duy nhất, gộp
# Defect+Fine) - việc phân loại Fine/Defect vẫn do CoffeeClassifier.java
# (trash_classifier.tflite) đảm nhiệm như hiện tại, không đổi.
#
# Cách dùng:
# 1. Vào Runtime > Change runtime type > chọn GPU (T4), rồi chạy từng Cell
#    theo thứ tự.
# 2. Cell 2 sẽ hỏi upload file zip - chọn ĐÚNG file zip dataset gốc tải từ
#    Roboflow (cái có train/images, train/labels, valid/..., test/...,
#    data.yaml bên trong) - không phải bản đã crop từng hạt.
# 3. Train xong (Cell 5), Cell 6 xuất ra file .tflite, Cell 7 tải về máy.
# ============================================================

# ------------------------------------------------------------
# CELL 1: Cài đặt
# ------------------------------------------------------------
!pip install -q ultralytics

from ultralytics import YOLO
import os
import shutil
import zipfile
import glob
import yaml

print("Cài đặt xong.")


# ------------------------------------------------------------
# CELL 2: Upload + giải nén dataset Roboflow gốc
# ------------------------------------------------------------
from google.colab import files

print("Chọn file zip dataset Roboflow gốc (có train/valid/test + data.yaml):")
uploaded = files.upload()
ZIP_PATH = list(uploaded.keys())[0]

EXTRACT_PATH = "/content/dataset_raw"
if os.path.exists(EXTRACT_PATH):
    shutil.rmtree(EXTRACT_PATH)
os.makedirs(EXTRACT_PATH)

with zipfile.ZipFile(ZIP_PATH, "r") as zf:
    zf.extractall(EXTRACT_PATH)

# Tự tìm file data.yaml ở bất kỳ cấp thư mục nào (phòng trường hợp zip có
# thêm 1 lớp thư mục con lồng bên trong, như đã từng gặp lúc crop_beans).
yaml_candidates = glob.glob(os.path.join(EXTRACT_PATH, "**", "data.yaml"), recursive=True)
if not yaml_candidates:
    raise FileNotFoundError(
        "Không tìm thấy data.yaml trong file zip. Kiểm tra lại đúng file zip "
        "dataset Roboflow gốc (không phải bản đã crop từng hạt)."
    )

ORIGINAL_YAML = yaml_candidates[0]
DATASET_ROOT = os.path.dirname(ORIGINAL_YAML)
print(f"Tìm thấy dataset tại: {DATASET_ROOT}")
print(f"data.yaml gốc: {ORIGINAL_YAML}")

with open(ORIGINAL_YAML, "r") as f:
    original_cfg = yaml.safe_load(f)
print("Nội dung data.yaml gốc:", original_cfg)


# ------------------------------------------------------------
# CELL 3: Gộp nhãn thành 1 lớp duy nhất "coffee_bean"
# ------------------------------------------------------------
# Dữ liệu gốc có 2 lớp (Defect, Fine) với bounding box riêng từng lớp.
# Model detection mới chỉ cần biết "đây là 1 hạt", không cần phân biệt
# Fine/Defect ở bước này (việc đó CoffeeClassifier đã làm sau khi crop).
# Nên gán lại toàn bộ class id trong file .txt nhãn về 0.

PREPARED_ROOT = "/content/dataset_single_class"
if os.path.exists(PREPARED_ROOT):
    shutil.rmtree(PREPARED_ROOT)
shutil.copytree(DATASET_ROOT, PREPARED_ROOT)

label_files = glob.glob(os.path.join(PREPARED_ROOT, "**", "*.txt"), recursive=True)
# Bỏ qua các file không phải nhãn YOLO (nếu có README.txt... lẫn vào)
label_files = [f for f in label_files if "labels" in f.replace("\\", "/").split("/")]

count_boxes = 0
for lf in label_files:
    with open(lf, "r") as f:
        lines = f.readlines()
    new_lines = []
    for line in lines:
        parts = line.strip().split()
        if len(parts) < 5:
            continue
        coords = list(map(float, parts[1:]))
        if len(parts) == 5:
            # Đã là bounding box: xc yc w h
            xc, yc, bw, bh = coords
        else:
            # Nhãn dạng POLYGON (class x1 y1 x2 y2 ...) -> lấy khung bao
            # nhỏ nhất quanh các đỉnh, đổi về bounding box chuẩn.
            xs = coords[0::2]
            ys = coords[1::2]
            x_min, x_max = min(xs), max(xs)
            y_min, y_max = min(ys), max(ys)
            xc = (x_min + x_max) / 2
            yc = (y_min + y_max) / 2
            bw = x_max - x_min
            bh = y_max - y_min
        # class id gốc -> ép về 0 (1 lớp duy nhất "coffee_bean")
        new_lines.append(f"0 {xc:.6f} {yc:.6f} {bw:.6f} {bh:.6f}")
        count_boxes += 1
    with open(lf, "w") as f:
        f.write("\n".join(new_lines) + ("\n" if new_lines else ""))

print(f"Đã gộp nhãn cho {len(label_files)} file, tổng {count_boxes} bounding box -> 1 lớp 'coffee_bean'.")

# Ghi lại data.yaml mới, đường dẫn trỏ đúng vào bản đã copy ở Colab
new_yaml_path = os.path.join(PREPARED_ROOT, "data.yaml")
new_cfg = {
    "train": os.path.join(PREPARED_ROOT, "train", "images"),
    "val": os.path.join(PREPARED_ROOT, "valid", "images"),
    "test": os.path.join(PREPARED_ROOT, "test", "images"),
    "nc": 1,
    "names": ["coffee_bean"],
}
with open(new_yaml_path, "w") as f:
    yaml.safe_dump(new_cfg, f)

print("data.yaml mới:", new_cfg)


# ------------------------------------------------------------
# CELL 4: Kiểm tra nhanh 1 ảnh mẫu có vẽ đúng bounding box không
# ------------------------------------------------------------
import cv2
import matplotlib.pyplot as plt

sample_images = glob.glob(os.path.join(PREPARED_ROOT, "train", "images", "*"))[:1]
if sample_images:
    img_path = sample_images[0]
    label_path = img_path.replace("images", "labels").rsplit(".", 1)[0] + ".txt"

    img = cv2.imread(img_path)
    h, w = img.shape[:2]

    if os.path.exists(label_path):
        with open(label_path) as f:
            for line in f:
                parts = line.strip().split()
                if len(parts) < 5:
                    continue
                _, xc, yc, bw, bh = map(float, parts)
                x1 = int((xc - bw / 2) * w)
                y1 = int((yc - bh / 2) * h)
                x2 = int((xc + bw / 2) * w)
                y2 = int((yc + bh / 2) * h)
                cv2.rectangle(img, (x1, y1), (x2, y2), (0, 0, 255), 2)

    plt.figure(figsize=(8, 8))
    plt.imshow(cv2.cvtColor(img, cv2.COLOR_BGR2RGB))
    plt.title("Kiểm tra nhãn mẫu - mỗi hạt phải có 1 khung đỏ")
    plt.axis("off")
    plt.show()
else:
    print("Không tìm thấy ảnh mẫu để kiểm tra - kiểm tra lại cấu trúc thư mục.")


# ------------------------------------------------------------
# CELL 5: Train YOLOv8n
# ------------------------------------------------------------
# yolov8n = bản nhỏ nhất (~6MB gốc), phù hợp chạy trên điện thoại.
# epochs=60 là mốc khởi điểm hợp lý cho vài nghìn ảnh - nếu thấy
# loss/mAP vẫn đang cải thiện tốt lúc kết thúc, có thể tăng lên.
model = YOLO("yolov8n.pt")

results = model.train(
    data=new_yaml_path,
    epochs=60,
    imgsz=640,
    batch=16,
    patience=15,  # dừng sớm nếu không cải thiện sau 15 epoch
    project="/content/runs",
    name="bean_detector",
)

print("Train xong. Kết quả lưu tại /content/runs/bean_detector")


# ------------------------------------------------------------
# CELL 6: Đánh giá nhanh + xem thử ảnh dự đoán
# ------------------------------------------------------------
best_model_path = "/content/runs/bean_detector/weights/best.pt"
best_model = YOLO(best_model_path)

metrics = best_model.val(data=new_yaml_path)
print("mAP50:", metrics.box.map50)
print("mAP50-95:", metrics.box.map)

# Thử dự đoán trên vài ảnh test, xem khung vẽ ra có đúng không
test_images = glob.glob(os.path.join(PREPARED_ROOT, "test", "images", "*"))[:4]
pred_results = best_model.predict(test_images, conf=0.4, save=False)

fig, axes = plt.subplots(1, len(pred_results), figsize=(6 * len(pred_results), 6))
if len(pred_results) == 1:
    axes = [axes]
for ax, r in zip(axes, pred_results):
    im = r.plot()  # ảnh đã vẽ sẵn bounding box dự đoán
    ax.imshow(cv2.cvtColor(im, cv2.COLOR_BGR2RGB))
    ax.set_title(f"{len(r.boxes)} hạt phát hiện được")
    ax.axis("off")
plt.show()


# ------------------------------------------------------------
# CELL 7: Xuất ra TFLite + tải về máy
# ------------------------------------------------------------
best_model.export(format="tflite", imgsz=640)

tflite_path = best_model_path.replace("best.pt", "best_saved_model/best_float32.tflite")
if not os.path.exists(tflite_path):
    # Tuỳ phiên bản ultralytics, tên file xuất ra có thể khác chút -> tự tìm
    candidates = glob.glob("/content/runs/bean_detector/weights/**/*.tflite", recursive=True)
    if candidates:
        tflite_path = candidates[0]

print("File .tflite:", tflite_path)

FINAL_NAME = "bean_detector.tflite"
shutil.copy(tflite_path, f"/content/{FINAL_NAME}")
files.download(f"/content/{FINAL_NAME}")

print(f"Đã tải {FINAL_NAME} về máy. Đưa file này vào app/src/main/assets/ "
      f"trong project Android (bên cạnh trash_classifier.tflite).")
