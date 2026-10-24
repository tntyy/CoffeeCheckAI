"""
Script cắt từng hạt cà phê ra thành ảnh riêng dựa theo bounding box (định dạng YOLO),
chia sẵn vào 2 thư mục Fine/Defect, giữ nguyên cấu trúc train/valid/test.

Kết quả sau khi chạy (thư mục 'cropped_dataset' được tạo mới, không đụng vào dữ liệu gốc):

cropped_dataset/
├── train/
│   ├── Fine/     (ảnh các hạt Fine đã cắt riêng)
│   └── Defect/   (ảnh các hạt Defect đã cắt riêng)
├── valid/
│   ├── Fine/
│   └── Defect/
└── test/
    ├── Fine/
    └── Defect/

Cấu trúc này dùng được TRỰC TIẾP với Keras flow_from_directory() khi train trên Colab.

Cách chạy:
1. Cài thư viện Pillow (nếu chưa có): pip install pillow
2. Copy file này vào đúng thư mục gốc chứa data.yaml, train/, valid/, test/
   (cùng thư mục với count_classes.py bạn đã chạy trước đó).
3. Mở Terminal tại đó, chạy: py crop_beans.py
4. Đợi chạy xong (có thể mất vài phút vì hơn 33.000 ảnh nhỏ), kết quả nằm
   trong thư mục 'cropped_dataset' mới được tạo.
"""

import os
import sys
import traceback
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except AttributeError:
    pass

try:
    from PIL import Image
except ImportError:
    print("Chưa cài thư viện Pillow. Chạy lệnh sau rồi thử lại:")
    print("  py -m pip install pillow")
    sys.exit(1)


def read_class_names(yaml_path="data.yaml"):
    names = []
    if not os.path.exists(yaml_path):
        print(f"Không tìm thấy {yaml_path} — đặt script cùng thư mục với data.yaml")
        return names
    with open(yaml_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line.startswith("names:"):
                content = line.split(":", 1)[1].strip().strip("[]")
                names = [n.strip().strip("'").strip('"') for n in content.split(",")]
                break
    return names


def crop_split(split_name, names, out_root, padding_ratio=0.15, min_size=20):
    images_dir = Path(split_name) / "images"
    labels_dir = Path(split_name) / "labels"

    if not images_dir.exists() or not labels_dir.exists():
        print(f"  [{split_name}] Thiếu thư mục images/ hoặc labels/ — bỏ qua")
        return {name: 0 for name in names}

    # Tạo thư mục output cho từng lớp
    out_dirs = {}
    for name in names:
        d = Path(out_root) / split_name / name
        d.mkdir(parents=True, exist_ok=True)
        out_dirs[name] = d

    counts = {name: 0 for name in names}
    skipped = 0
    image_files = list(images_dir.glob("*.jpg")) + list(images_dir.glob("*.png")) + list(images_dir.glob("*.jpeg"))

    for img_path in image_files:
        label_path = labels_dir / (img_path.stem + ".txt")
        if not label_path.exists():
            continue

        try:
            img = Image.open(img_path).convert("RGB")
        except Exception:
            skipped += 1
            continue

        W, H = img.size

        with open(label_path, "r", encoding="utf-8") as f:
            lines = [l.strip() for l in f if l.strip()]

        for i, line in enumerate(lines):
            parts = line.split()
            if len(parts) < 5:
                continue
            class_id = int(parts[0])
            xc, yc, bw, bh = map(float, parts[1:5])  # tọa độ chuẩn hóa (0-1), tâm + rộng/cao

            if class_id >= len(names):
                continue
            label = names[class_id]

            # Chuyển tọa độ chuẩn hóa (YOLO) sang pixel thật
            box_w = bw * W
            box_h = bh * H
            cx = xc * W
            cy = yc * H

            # Thêm padding quanh hạt (giúp model thấy được viền/ngữ cảnh xung quanh)
            pad_w = box_w * padding_ratio
            pad_h = box_h * padding_ratio

            x1 = max(0, int(cx - box_w / 2 - pad_w))
            y1 = max(0, int(cy - box_h / 2 - pad_h))
            x2 = min(W, int(cx + box_w / 2 + pad_w))
            y2 = min(H, int(cy + box_h / 2 + pad_h))

            if (x2 - x1) < min_size or (y2 - y1) < min_size:
                skipped += 1
                continue

            cropped = img.crop((x1, y1, x2, y2))
            out_name = f"{img_path.stem}_{i}.jpg"
            cropped.save(out_dirs[label] / out_name, quality=95)
            counts[label] += 1

    if skipped:
        print(f"  [{split_name}] Bỏ qua {skipped} hạt (ảnh lỗi hoặc box quá nhỏ)")

    return counts


def main():
    print(f"Đang chạy script tại thư mục: {os.getcwd()}\n", flush=True)

    names = read_class_names("data.yaml")
    if not names:
        print("Không đọc được tên lớp từ data.yaml — dừng lại.")
        return
    print(f"Danh sách lớp: {names}\n")

    out_root = "cropped_dataset"
    splits = ["train", "valid", "test"]
    grand_total = {name: 0 for name in names}

    for split in splits:
        if not os.path.exists(split):
            print(f"Bỏ qua '{split}' (không tìm thấy thư mục)")
            continue
        print(f"=== Đang xử lý {split.upper()} ===")
        counts = crop_split(split, names, out_root)
        for name in names:
            print(f"  {name}: {counts[name]} ảnh đã cắt")
            grand_total[name] += counts[name]
        print()

    print("=== TỔNG CỘNG ===")
    for name in names:
        print(f"  {name}: {grand_total[name]} ảnh")
    print(f"\nKết quả lưu tại thư mục: {os.path.abspath(out_root)}")
    print("Cấu trúc này dùng trực tiếp được với Keras flow_from_directory() khi train trên Colab.")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("\n=== CÓ LỖI XẢY RA, chi tiết bên dưới ===")
        traceback.print_exc()
    print("\n--- Đã chạy xong ---")
    input("Nhấn Enter để đóng cửa sổ...")
