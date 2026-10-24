"""
Script đếm số lượng bounding box (Fine/Defect) trong dataset YOLO
tải từ Roboflow (định dạng train/valid/test, mỗi thư mục có images/ và labels/).

Cách chạy:
1. Copy file này vào đúng thư mục gốc chứa 3 thư mục train/valid/test
   (cùng cấp với data.yaml).
2. Mở Terminal/Command Prompt tại đó, chạy: python count_classes.py
3. Không cần cài thêm thư viện gì (chỉ dùng thư viện chuẩn của Python).
"""

import os
import sys
import traceback
from pathlib import Path
from collections import Counter

# Sửa lỗi encoding tiếng Việt trên Windows PowerShell/CMD (mặc định dùng cp1252,
# không đọc được ký tự Unicode có dấu, khiến script crash âm thầm khi print()).
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except AttributeError:
    pass  # Python cũ hơn 3.7 không có reconfigure(), bỏ qua an toàn

# Đọc class names từ data.yaml (đơn giản, không cần cài PyYAML)
def read_class_names(yaml_path="data.yaml"):
    names = []
    if not os.path.exists(yaml_path):
        print(f"Không tìm thấy {yaml_path} — hãy đặt script này cùng thư mục với data.yaml")
        return names
    with open(yaml_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line.startswith("names:"):
                # Dạng: names: ['Defect', 'Fine']
                content = line.split(":", 1)[1].strip()
                content = content.strip("[]")
                names = [n.strip().strip("'").strip('"') for n in content.split(",")]
                break
    return names


def count_split(split_name):
    labels_dir = Path(split_name) / "labels"
    images_dir = Path(split_name) / "images"

    if not labels_dir.exists():
        print(f"  [{split_name}] Không tìm thấy thư mục labels/ — kiểm tra lại cấu trúc")
        return Counter(), 0

    box_counter = Counter()          # đếm số bounding box mỗi lớp
    image_class_counter = Counter()  # đếm số ẢNH có chứa ít nhất 1 box của lớp đó
    total_images = 0

    for label_file in labels_dir.glob("*.txt"):
        total_images += 1
        classes_in_this_image = set()
        with open(label_file, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                class_id = int(line.split()[0])
                box_counter[class_id] += 1
                classes_in_this_image.add(class_id)
        for cid in classes_in_this_image:
            image_class_counter[cid] += 1

    return box_counter, image_class_counter, total_images


def main():
    names = read_class_names("data.yaml")
    if names:
        print(f"Danh sách lớp (theo data.yaml): {names}\n")
    else:
        print("Không đọc được tên lớp từ data.yaml, sẽ hiển thị theo class_id số.\n")

    splits = ["train", "valid", "test"]
    grand_total_boxes = Counter()
    grand_total_images = Counter()
    grand_total_files = 0

    for split in splits:
        if not os.path.exists(split):
            print(f"Bỏ qua '{split}' (không tìm thấy thư mục)")
            continue

        print(f"=== {split.upper()} ===")
        box_counter, image_class_counter, total_files = count_split(split)
        grand_total_files += total_files

        for cid in sorted(set(list(box_counter.keys()) + list(image_class_counter.keys()))):
            label = names[cid] if names and cid < len(names) else f"class_{cid}"
            print(f"  {label}: {box_counter[cid]} bounding box, xuất hiện trong {image_class_counter[cid]} ảnh")
            grand_total_boxes[cid] += box_counter[cid]
            grand_total_images[cid] += image_class_counter[cid]

        print(f"  Tổng số file ảnh có label trong '{split}': {total_files}\n")

    print("=== TỔNG CỘNG (train + valid + test) ===")
    for cid in sorted(grand_total_boxes.keys()):
        label = names[cid] if names and cid < len(names) else f"class_{cid}"
        print(f"  {label}: {grand_total_boxes[cid]} bounding box, xuất hiện trong {grand_total_images[cid]} ảnh")
    print(f"  Tổng số file ảnh (label) toàn bộ dataset: {grand_total_files}")

    # Tính tỉ lệ lệch lớp (dựa trên số bounding box - phù hợp hơn vì đây là dữ liệu
    # sẽ dùng để cắt thành ảnh training cho classification)
    total_boxes = sum(grand_total_boxes.values())
    if total_boxes > 0:
        print("\n  Tỉ lệ theo số bounding box (dùng để đánh giá cân bằng lớp khi cắt ảnh train):")
        for cid in sorted(grand_total_boxes.keys()):
            label = names[cid] if names and cid < len(names) else f"class_{cid}"
            pct = grand_total_boxes[cid] / total_boxes * 100
            print(f"    {label}: {pct:.1f}%")


if __name__ == "__main__":
    print(f"Đang chạy script tại thư mục: {os.getcwd()}\n", flush=True)
    try:
        main()
    except Exception:
        print("\n=== CÓ LỖI XẢY RA, chi tiết bên dưới ===")
        traceback.print_exc()
    print("\n--- Đã chạy xong ---")
    input("Nhấn Enter để đóng cửa sổ...")
