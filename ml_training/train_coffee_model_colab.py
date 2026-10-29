# ==============================================================================
# HƯỚNG DẪN: Mỗi khối "===== CELL n =====" bên dưới tương ứng với 1 ô code
# riêng trong Google Colab. Copy đúng nội dung giữa 2 dòng ===== của từng
# CELL, dán vào 1 ô code mới trong Colab (Insert > Code cell), rồi Run lần
# lượt từ Cell 1 đến Cell 8, theo đúng thứ tự.
# ==============================================================================


# ===== CELL 1: Mount Google Drive và giải nén dataset =====
from google.colab import drive
drive.mount('/content/drive')

import zipfile
import os

# Sửa lại đường dẫn này cho đúng vị trí file zip bạn đã tải lên Drive
ZIP_PATH = '/content/drive/MyDrive/CoffeeCheckAI/cropped_dataset.zip'
EXTRACT_PATH = '/content/dataset'

with zipfile.ZipFile(ZIP_PATH, 'r') as z:
    z.extractall(EXTRACT_PATH)

# Nếu file zip chứa nguyên thư mục cha "cropped_dataset" bên trong (thường
# gặp khi nén cả thư mục), cần trỏ thêm 1 cấp vào đúng chỗ chứa train/valid/test
if os.path.isdir(os.path.join(EXTRACT_PATH, 'cropped_dataset')):
    EXTRACT_PATH = os.path.join(EXTRACT_PATH, 'cropped_dataset')

# Kiểm tra cấu trúc sau khi giải nén (nên thấy train/valid/test)
for root, dirs, files in os.walk(EXTRACT_PATH):
    level = root.replace(EXTRACT_PATH, '').count(os.sep)
    if level <= 2:
        print('  ' * level + os.path.basename(root) + '/')
# ================================================================


# ===== CELL 2: Cài đặt / import thư viện =====
import tensorflow as tf
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.layers import GlobalAveragePooling2D, Dense, Dropout
from tensorflow.keras.models import Model
from tensorflow.keras.optimizers import Adam

print("TensorFlow version:", tf.__version__)
print("GPU:", tf.config.list_physical_devices('GPU'))
# ================================================================


# ===== CELL 3: Chuẩn bị dữ liệu (ImageDataGenerator) =====
# QUAN TRỌNG: dùng preprocess_input của MobileNetV2 (đưa pixel về khoảng
# -1..1), khớp CHÍNH XÁC với cách CoffeeClassifier.java xử lý ảnh phía
# Android (buffer.putFloat(pixel / 127.5f - 1f)) — không đổi công thức
# này, nếu không model sẽ dự đoán sai khi chạy trên điện thoại.

IMG_SIZE = 224
BATCH_SIZE = 32

# Data augmentation nhẹ cho tập train — dataset đã cân bằng tốt (51%/49%)
# nên không cần class_weight, chỉ cần augmentation cơ bản để model tổng
# quát hoá tốt hơn (chống overfitting)
train_datagen = ImageDataGenerator(
    preprocessing_function=preprocess_input,
    rotation_range=20,
    width_shift_range=0.1,
    height_shift_range=0.1,
    zoom_range=0.15,
    horizontal_flip=True,
    brightness_range=[0.8, 1.2],
)

# Tập valid/test KHÔNG augmentation, chỉ tiền xử lý
valid_test_datagen = ImageDataGenerator(preprocessing_function=preprocess_input)

train_gen = train_datagen.flow_from_directory(
    os.path.join(EXTRACT_PATH, 'train'),
    target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE,
    class_mode='categorical',
    shuffle=True,
)

valid_gen = valid_test_datagen.flow_from_directory(
    os.path.join(EXTRACT_PATH, 'valid'),
    target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE,
    class_mode='categorical',
    shuffle=False,
)

test_gen = valid_test_datagen.flow_from_directory(
    os.path.join(EXTRACT_PATH, 'test'),
    target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE,
    class_mode='categorical',
    shuffle=False,
)

# In ra thứ tự lớp — QUAN TRỌNG: phải ghi lại đúng thứ tự này vào
# labels.txt sau này (Keras xếp theo thứ tự alphabet: Defect=0, Fine=1)
print("Thứ tự lớp (class_indices):", train_gen.class_indices)
# ================================================================


# ===== CELL 4: Xây dựng model MobileNetV2 (Transfer Learning) =====
base_model = MobileNetV2(
    input_shape=(IMG_SIZE, IMG_SIZE, 3),
    include_top=False,
    weights='imagenet',
)
base_model.trainable = False  # đóng băng toàn bộ base ở giai đoạn đầu

x = base_model.output
x = GlobalAveragePooling2D()(x)
x = Dense(128, activation='relu')(x)
x = Dropout(0.3)(x)
output = Dense(2, activation='softmax')(x)  # 2 lớp: Defect, Fine

model = Model(inputs=base_model.input, outputs=output)

model.compile(
    optimizer=Adam(learning_rate=1e-3),
    loss='categorical_crossentropy',
    metrics=['accuracy'],
)

model.summary()
# ================================================================


# ===== CELL 5: Train giai đoạn 1 (chỉ train phần đầu, đóng băng MobileNetV2) =====
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint

callbacks = [
    EarlyStopping(monitor='val_accuracy', patience=4, restore_best_weights=True),
    ModelCheckpoint('best_model_stage1.keras', monitor='val_accuracy', save_best_only=True),
]

history_stage1 = model.fit(
    train_gen,
    validation_data=valid_gen,
    epochs=15,
    callbacks=callbacks,
)
# ================================================================


# ===== CELL 6: Train giai đoạn 2 (fine-tune — mở khóa 1 phần MobileNetV2) =====
# Sau khi phần đầu đã học ổn, mở khóa các lớp cuối của MobileNetV2 để
# tinh chỉnh sâu hơn, với learning rate thấp hơn nhiều để tránh phá vỡ
# trọng số đã học từ ImageNet.

base_model.trainable = True

# Chỉ mở khóa 30 lớp cuối, giữ đóng băng các lớp đầu (feature cơ bản)
fine_tune_at = len(base_model.layers) - 30
for layer in base_model.layers[:fine_tune_at]:
    layer.trainable = False

model.compile(
    optimizer=Adam(learning_rate=1e-5),  # learning rate rất thấp khi fine-tune
    loss='categorical_crossentropy',
    metrics=['accuracy'],
)

callbacks_stage2 = [
    EarlyStopping(monitor='val_accuracy', patience=4, restore_best_weights=True),
    ModelCheckpoint('best_model_stage2.keras', monitor='val_accuracy', save_best_only=True),
]

history_stage2 = model.fit(
    train_gen,
    validation_data=valid_gen,
    epochs=10,
    callbacks=callbacks_stage2,
)
# ================================================================


# ===== CELL 7: Đánh giá trên tập test =====
test_loss, test_acc = model.evaluate(test_gen)
print(f"\nĐộ chính xác trên tập TEST: {test_acc*100:.2f}%")
print(f"Loss trên tập TEST: {test_loss:.4f}")

# Xem thêm confusion matrix để biết model nhầm lẫn Fine/Defect thế nào
import numpy as np
from sklearn.metrics import classification_report, confusion_matrix

test_gen.reset()
preds = model.predict(test_gen)
y_pred = np.argmax(preds, axis=1)
y_true = test_gen.classes

class_names = list(test_gen.class_indices.keys())
print("\nBáo cáo chi tiết:")
print(classification_report(y_true, y_pred, target_names=class_names))
print("Confusion matrix:")
print(confusion_matrix(y_true, y_pred))
# ================================================================


# ===== CELL 8: Convert sang TFLite + xuất labels.txt + tải về máy =====
converter = tf.lite.TFLiteConverter.from_keras_model(model)
# Không quantize để giữ độ chính xác cao nhất, phù hợp cho đồ án
# (nếu muốn file nhẹ hơn, có thể bật converter.optimizations sau này)
tflite_model = converter.convert()

with open('trash_classifier.tflite', 'wb') as f:
    # Đặt tên trùng với biến file trong TrashClassifier.java để đỡ phải sửa code Java,
    # nếu muốn đổi tên khác (vd coffee_classifier.tflite) nhớ sửa lại đúng tên
    # trong hàm loadModelFile() bên Android tương ứng.
    f.write(tflite_model)

# Ghi labels.txt ĐÚNG THEO THỨ TỰ class_indices (rất quan trọng, sai thứ tự
# sẽ khiến app hiển thị nhầm Fine thành Defect và ngược lại)
class_indices = train_gen.class_indices  # ví dụ {'Defect': 0, 'Fine': 1}
sorted_labels = sorted(class_indices.items(), key=lambda x: x[1])
with open('trash_labels.txt', 'w', encoding='utf-8') as f:
    for label, idx in sorted_labels:
        f.write(label + '\n')

print("Thứ tự labels.txt:", [label for label, idx in sorted_labels])

# Tải 2 file này về máy để bỏ vào thư mục assets/ của Android Studio
from google.colab import files
files.download('trash_classifier.tflite')
files.download('trash_labels.txt')
# ================================================================
