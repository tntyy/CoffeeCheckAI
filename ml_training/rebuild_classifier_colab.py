# ==============================================================================
# TẠO LẠI DỮ LIỆU CẮT HẠT ĐÚNG + TRAIN LẠI MODEL PHÂN LOẠI Fine/Defect (Colab)
# ==============================================================================
# Vì sao phải làm lại:
#   crop_beans.py cũ đọc mỗi dòng nhãn dưới dạng "class xc yc w h" (khung chữ
#   nhật). Nhưng dataset Roboflow này có nhãn dạng POLYGON ("class x1 y1 x2 y2
#   x3 y3 ..."). Script cũ lấy nhầm 2 đỉnh đầu của polygon làm tâm + kích
#   thước, nên ảnh "1 hạt" bị cắt thành vùng lớn, lệch, chứa nhiều hạt. Model
#   học trên dữ liệu đó nên gặp ảnh 1 hạt thật ngoài đời là đoán sai (luôn Defect).
#
# Cách dùng: Runtime > Change runtime type > T4 GPU, rồi chạy lần lượt từng CELL.
# Mỗi khối "===== CELL n =====" là 1 ô code riêng.
# ==============================================================================


# ===== CELL 1: Mount Drive + giải nén dataset Roboflow GỐC =====
from google.colab import drive
drive.mount('/content/drive')

import os, glob, zipfile, shutil

# SỬA 2 dòng này cho đúng vị trí file trên Drive của bạn:
RAW_ZIP = '/content/drive/MyDrive/chuyen_de_thiet_bi_di_dong/Green Bean Coffee.v10i.yolov8.zip'
SAVE_DIR = '/content/drive/MyDrive/chuyen_de_thiet_bi_di_dong/classifier_v2'   # nơi lưu kết quả

RAW_PATH = '/content/dataset_raw'
if os.path.exists(RAW_PATH):
    shutil.rmtree(RAW_PATH)
with zipfile.ZipFile(RAW_ZIP) as z:
    z.extractall(RAW_PATH)

yaml_files = glob.glob(os.path.join(RAW_PATH, '**', 'data.yaml'), recursive=True)
assert yaml_files, 'Không thấy data.yaml trong zip - kiểm tra lại đúng file zip gốc Roboflow'
ROOT = os.path.dirname(yaml_files[0])
print('Dataset gốc tại:', ROOT)

import yaml
with open(yaml_files[0]) as f:
    NAMES = yaml.safe_load(f)['names']
print('Các lớp:', NAMES)   # kỳ vọng ['Defect', 'Fine']
os.makedirs(SAVE_DIR, exist_ok=True)


# ===== CELL 2: KIỂM CHỨNG LỖI - xem bao nhiêu dòng nhãn là polygon =====
from collections import Counter

len_counter = Counter()
for lf in glob.glob(os.path.join(ROOT, 'train', 'labels', '*.txt')):
    with open(lf) as f:
        for line in f:
            n = len(line.split())
            if n:
                len_counter['bbox (5 số)' if n == 5 else f'polygon ({n} số)'] += 1

poly = sum(v for k, v in len_counter.items() if k.startswith('polygon'))
total = sum(len_counter.values())
print(f'Tổng dòng nhãn train: {total}, trong đó polygon: {poly} ({poly / max(total, 1) * 100:.1f}%)')
print('Nếu tỉ lệ polygon cao -> crop_beans.py cũ đã cắt sai, đúng như dự đoán.')


# ===== CELL 3: Cắt hạt ĐÚNG (hỗ trợ cả bbox và polygon) =====
from PIL import Image
import random

PADDING_RATIO = 0.15   # giữ nguyên như lúc train cũ và như CoffeeClassifier/ImageUtils.cropRect
MIN_SIZE = 20
OUT_ROOT = '/content/cropped_v2'
if os.path.exists(OUT_ROOT):
    shutil.rmtree(OUT_ROOT)

def parse_box(parts):
    """Trả về (xc, yc, w, h) chuẩn hoá 0-1 từ 1 dòng nhãn bbox hoặc polygon."""
    vals = list(map(float, parts[1:]))
    if len(vals) == 4:                       # bbox: xc yc w h
        return vals[0], vals[1], vals[2], vals[3]
    xs, ys = vals[0::2], vals[1::2]          # polygon: khung bao nhỏ nhất các đỉnh
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    return (x0 + x1) / 2, (y0 + y1) / 2, x1 - x0, y1 - y0

counts = {}
sizes = []
for split in ['train', 'valid', 'test']:
    img_dir = os.path.join(ROOT, split, 'images')
    lbl_dir = os.path.join(ROOT, split, 'labels')
    if not os.path.isdir(img_dir):
        continue
    for name in NAMES:
        os.makedirs(os.path.join(OUT_ROOT, split, name), exist_ok=True)
    for lf in glob.glob(os.path.join(lbl_dir, '*.txt')):
        stem = os.path.splitext(os.path.basename(lf))[0]
        cand = [p for p in glob.glob(os.path.join(img_dir, stem + '.*'))]
        if not cand:
            continue
        img = Image.open(cand[0]).convert('RGB')
        W, H = img.size
        with open(lf) as f:
            lines = [l.split() for l in f if l.strip()]
        for i, parts in enumerate(lines):
            if len(parts) < 5:
                continue
            cls = int(parts[0])
            if cls >= len(NAMES):
                continue
            xc, yc, bw, bh = parse_box(parts)
            bw_px, bh_px = bw * W, bh * H
            cx, cy = xc * W, yc * H
            x1 = max(0, int(cx - bw_px / 2 - bw_px * PADDING_RATIO))
            y1 = max(0, int(cy - bh_px / 2 - bh_px * PADDING_RATIO))
            x2 = min(W, int(cx + bw_px / 2 + bw_px * PADDING_RATIO))
            y2 = min(H, int(cy + bh_px / 2 + bh_px * PADDING_RATIO))
            if (x2 - x1) < MIN_SIZE or (y2 - y1) < MIN_SIZE:
                continue
            crop = img.crop((x1, y1, x2, y2))
            crop.save(os.path.join(OUT_ROOT, split, NAMES[cls], f'{stem}_{i}.jpg'), quality=95)
            counts[(split, NAMES[cls])] = counts.get((split, NAMES[cls]), 0) + 1
            if split == 'train':
                sizes.append((x2 - x1, y2 - y1))

for k in sorted(counts):
    print(k, counts[k])
import numpy as np
sz = np.array(sizes)
print(f'Kích thước crop train: trung vị {np.median(sz[:, 0]):.0f} x {np.median(sz[:, 1]):.0f} px '
      f'(nhỏ nhất {sz.min():.0f}, lớn nhất {sz.max():.0f})')


# ===== CELL 4: XEM MẮT các crop mới (mỗi crop phải là ĐÚNG 1 hạt) =====
import matplotlib.pyplot as plt

fig, axes = plt.subplots(2, 8, figsize=(20, 6))
for r, name in enumerate(NAMES[:2]):
    files = glob.glob(os.path.join(OUT_ROOT, 'train', name, '*.jpg'))
    for c, fp in enumerate(random.sample(files, min(8, len(files)))):
        axes[r][c].imshow(Image.open(fp))
        axes[r][c].set_title(name)
        axes[r][c].axis('off')
plt.show()
print('Nếu mỗi ô là 1 hạt rõ ràng, ở giữa khung -> OK, chạy tiếp. Nếu vẫn thấy cả đám hạt thì dừng lại báo mình.')


# ===== CELL 5: Chuẩn bị dữ liệu train (có augmentation mạnh hơn) =====
import tensorflow as tf
import cv2
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.layers import GlobalAveragePooling2D, Dense, Dropout
from tensorflow.keras.models import Model
from tensorflow.keras.optimizers import Adam

IMG_SIZE = 224
BATCH_SIZE = 32
STEPS = 350   # ~11.000 ảnh/epoch (đủ cho bài toán 2 lớp), giúp mỗi epoch chỉ vài phút
print('GPU:', tf.config.list_physical_devices('GPU') or 'KHÔNG CÓ GPU -> đổi Runtime sang T4 GPU rồi chạy lại!')

def train_preprocess(img):
    """Làm xấu ngẫu nhiên (mờ, giảm độ phân giải) để model quen với ảnh điện thoại
    khác ảnh dataset, rồi chuẩn hoá y hệt app: pixel/127.5 - 1."""
    img = img.astype('float32')
    if random.random() < 0.3:
        img = cv2.GaussianBlur(img, (0, 0), random.uniform(0.5, 1.5))
    if random.random() < 0.3:
        s = random.randint(40, 150)
        img = cv2.resize(cv2.resize(img, (s, s), interpolation=cv2.INTER_LINEAR),
                         (IMG_SIZE, IMG_SIZE), interpolation=cv2.INTER_LINEAR)
    return preprocess_input(img)

train_datagen = ImageDataGenerator(
    preprocessing_function=train_preprocess,
    rotation_range=30,
    width_shift_range=0.1,
    height_shift_range=0.1,
    zoom_range=0.2,
    horizontal_flip=True,
    vertical_flip=True,
    brightness_range=[0.6, 1.3],
    channel_shift_range=20.0,
)
eval_datagen = ImageDataGenerator(preprocessing_function=preprocess_input)

# interpolation='bilinear' khớp Bitmap.createScaledBitmap(filter=true) bên Android
# (mặc định của Keras là 'nearest' -> lệch so với app).
common = dict(target_size=(IMG_SIZE, IMG_SIZE), batch_size=BATCH_SIZE,
              class_mode='categorical', interpolation='bilinear')
train_gen = train_datagen.flow_from_directory(os.path.join(OUT_ROOT, 'train'), shuffle=True, **common)
valid_gen = eval_datagen.flow_from_directory(os.path.join(OUT_ROOT, 'valid'), shuffle=False, **common)
test_gen = eval_datagen.flow_from_directory(os.path.join(OUT_ROOT, 'test'), shuffle=False, **common)

print('Thứ tự lớp:', train_gen.class_indices)   # phải là {'Defect': 0, 'Fine': 1}

# Cân bằng lớp tự động theo số lượng thực tế
cls_counts = np.bincount(train_gen.classes)
class_weight = {i: len(train_gen.classes) / (len(cls_counts) * c) for i, c in enumerate(cls_counts)}
print('Số ảnh mỗi lớp:', cls_counts, '-> class_weight:', class_weight)


# ===== CELL 6: Xây model + train giai đoạn 1 =====
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint

base_model = MobileNetV2(input_shape=(IMG_SIZE, IMG_SIZE, 3), include_top=False, weights='imagenet')
base_model.trainable = False
x = GlobalAveragePooling2D()(base_model.output)
x = Dense(128, activation='relu')(x)
x = Dropout(0.3)(x)
out = Dense(2, activation='softmax')(x)
model = Model(base_model.input, out)

model.compile(optimizer=Adam(1e-3), loss='categorical_crossentropy', metrics=['accuracy'])
model.fit(train_gen, validation_data=valid_gen, epochs=8, steps_per_epoch=STEPS, class_weight=class_weight,
          workers=4, use_multiprocessing=True,
          callbacks=[EarlyStopping(monitor='val_accuracy', patience=4, restore_best_weights=True)])


# ===== CELL 7: Fine-tune giai đoạn 2 =====
base_model.trainable = True
for layer in base_model.layers[:len(base_model.layers) - 30]:
    layer.trainable = False

model.compile(optimizer=Adam(1e-5), loss='categorical_crossentropy', metrics=['accuracy'])
model.fit(train_gen, validation_data=valid_gen, epochs=6, steps_per_epoch=STEPS, class_weight=class_weight,
          workers=4, use_multiprocessing=True,
          callbacks=[EarlyStopping(monitor='val_accuracy', patience=4, restore_best_weights=True)])


# ===== CELL 8: Đánh giá trên tập test =====
from sklearn.metrics import classification_report, confusion_matrix

loss, acc = model.evaluate(test_gen)
print(f'\nĐộ chính xác TEST: {acc * 100:.2f}%')
test_gen.reset()
probs = model.predict(test_gen)
y_pred = np.argmax(probs, axis=1)
names_sorted = [k for k, _ in sorted(test_gen.class_indices.items(), key=lambda kv: kv[1])]
print(classification_report(test_gen.classes, y_pred, target_names=names_sorted))
print('Confusion matrix:\n', confusion_matrix(test_gen.classes, y_pred))

# Phân bố độ tin cậy: nếu gần như toàn >99% thì model "tự tin quá mức"
conf = probs.max(axis=1)
print(f'Độ tin cậy: trung vị {np.median(conf):.3f}, % mẫu > 0.99: {(conf > 0.99).mean() * 100:.1f}%')


# ===== CELL 9: Xuất TFLite + labels, lưu vào Drive và tải về =====
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()
with open('trash_classifier.tflite', 'wb') as f:
    f.write(tflite_model)

with open('trash_labels.txt', 'w', encoding='utf-8') as f:
    for label, idx in sorted(train_gen.class_indices.items(), key=lambda kv: kv[1]):
        f.write(label + '\n')
print('Thứ tự labels:', open('trash_labels.txt').read().split())

shutil.copy('trash_classifier.tflite', SAVE_DIR)
shutil.copy('trash_labels.txt', SAVE_DIR)
print('Đã lưu vào Drive:', SAVE_DIR)

from google.colab import files
files.download('trash_classifier.tflite')
files.download('trash_labels.txt')
print('Chép 2 file này đè vào app/src/main/assets/ (cùng tên), rebuild app là xong. '
      'Không cần sửa code Java.')
