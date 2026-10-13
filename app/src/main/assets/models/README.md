# HƯỚNG DẪN TẢI CÁC FILE MODEL AI OFFLINE

Để ứng dụng Tủ Khóa Locker hoạt động offline, bạn cần tải 2 file model dưới đây và đặt vào thư mục `app/src/main/assets/models/`:

---

## 1. Model YuNet Face Detection
- **Tên file yêu cầu**: `face_detection_yunet_2023mar.onnx`
- **Nguồn tải chính thức**: [OpenCV Zoo - YuNet GitHub](https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet)
- **Đường dẫn tải trực tiếp**: `https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx`
- **Mục đích**: Phát hiện vị trí khuôn mặt và trích xuất 5 vị trí mốc đặc trưng (Right eye, Left eye, Nose, Right mouth corner, Left mouth corner) với tốc độ cao real-time.

---

## 2. Model MobileFaceNet Feature Embedding
- **Tên file yêu cầu**: `w600k_mbf.onnx`
- **Nguồn tải chính thức**: [InsightFace Model Zoo - Buffalo_s Package](https://github.com/deepinsight/insightface/tree/master/model_zoo)
- **Mục đích**: Trích xuất vector đặc trưng khuôn mặt 512 chiều (512D Embedding) từ ảnh khuôn mặt 112x112 đã align để so khớp Cosine Similarity.

---

## 3. Cấu trúc thư mục Assets sau khi tải đầy đủ
```
app/src/main/assets/
└── models/
    ├── README.md
    ├── face_detection_yunet_2023mar.onnx
    └── w600k_mbf.onnx
```

> [!NOTE]
> Khi ứng dụng khởi chạy lần đầu tiên, `LockerApp` và `YuNetDetector` / `MobileFaceNetEmbedder` sẽ tự động sao chép các file `.onnx` từ thư mục Assets ra `context.filesDir` để thư viện C++ OpenCV (`FaceDetectorYN`) và ONNX Runtime đọc trực tiếp.
