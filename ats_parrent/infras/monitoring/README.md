# Prometheus và Grafana cho ATS

Circuit Breaker `candidateDetails` nằm trong application-service (8083), bảo vệ lời gọi
sang candidate-service (8082). Prometheus đọc `/actuator/prometheus` mỗi 2 giây.

## Khởi động

1. Mở Docker Desktop (Linux containers).
2. Reload Maven sau khi thêm `micrometer-registry-prometheus`.
3. Chạy application-service và candidate-service trong IntelliJ, cấu hình PostgreSQL tương ứng.
   Có thể dùng profile `cb-demo` cho các chức năng giả lập lỗi/chậm.
4. Mở <http://localhost:8083/actuator/prometheus>: phải thấy metrics dạng text.
5. Từ thư mục `ats_parrent`, chạy:

```powershell
docker compose -f infras/monitoring/compose.yml up -d
```

Mở <http://localhost:9090/targets>, target `ats-application-service` phải **UP**.
Mở <http://localhost:9090> để chạy các query bên dưới.

Grafana: <http://localhost:3002/d/ats-candidate-details>.
Port 3002 tránh xung đột với ứng dụng khác ở 3001.
Đăng nhập mặc định cho demo local: `admin` / `ats-demo-local`.
Đổi mật khẩu bằng `GRAFANA_ADMIN_PASSWORD` trước lần khởi tạo đầu tiên;
không dùng mật khẩu demo cho môi trường public.
Datasource và dashboard được provision tự động, không cần import thủ công.

### Chia phần quan sát Grafana cho 3 người

| Người | Kịch bản | Panel cần chỉ ra |
|---|---|---|
| 1 | ERROR, reset, 5 lần gọi để OPEN, lần 6 bị chặn | Circuit states, Failure rate, Recorded calls, Blocked calls |
| 2 | NORMAL, đợi HALF_OPEN, 2 probe để CLOSED; reset rồi NOT_FOUND | Circuit states, Buffered calls (404 không thêm mẫu) |
| 3 | SLOW 3000ms, reset, 5 lần gọi tuần tự | Slow call rate tăng 100%, failed không tăng, circuit OPEN |

Grafana refresh mỗi 5s, Prometheus scrape mỗi 2s nên đồ thị có độ trễ.
Đặt time range Last 15 minutes; gửi 2 probe cách nhau khoảng 3s để dễ thấy HALF_OPEN,
nhưng phải hoàn thành trong giới hạn HALF_OPEN 10s. Trạng thái quá ngắn có thể không được scrape.
Counter tích lũy không nhất thiết về 0 khi reset circuit; so sánh trước/sau.
Panel UP kiểm tra application-service có được scrape, không phản ánh candidate-service.

Nếu port 9090 đang được Prometheus của project demo sử dụng, chọn port 9091:

```powershell
$env:PROMETHEUS_PORT = "9091"
docker compose -f infras/monitoring/compose.yml up -d
```

Khi đó mở <http://localhost:9091>. Giữ cùng biến môi trường khi quản lý stack.

## Query cho buổi demo

```promql
# 1 = scrape thành công; 0 = không lấy được metrics
up{job="ats-application-service"}

# Trạng thái hiện tại: xem label state của series có giá trị 1
resilience4j_circuitbreaker_state{application="application-service",name="candidateDetails"} == 1

# Tỷ lệ lỗi (%); -1 nghĩa là chưa đủ mẫu
resilience4j_circuitbreaker_failure_rate{application="application-service",name="candidateDetails"}

# Số lời gọi tích lũy, phân theo label kind
resilience4j_circuitbreaker_calls_seconds_count{application="application-service",name="candidateDetails"}

# Số lời gọi bị circuit chặn
resilience4j_circuitbreaker_not_permitted_calls_total{application="application-service",name="candidateDetails"}

# Tỷ lệ slow call (%)
resilience4j_circuitbreaker_slow_call_rate{application="application-service",name="candidateDetails"}

# Số mẫu trong cửa sổ thống kê
resilience4j_circuitbreaker_buffered_calls{application="application-service",name="candidateDetails"}
```

Để vẽ lịch sử trạng thái, bỏ `== 1` ở query state và chọn tab Graph.

## Tạo dữ liệu metrics

Dùng dữ liệu và request trong [hướng dẫn Circuit Breaker](../../docs/circuit-breaker-demo/README.md).

```http
GET http://localhost:8083/api/v1/applications/1
X-User-ID: 42
X-User-Email: demo@example.com
X-User-Name: Demo User
```

Thay `1` bằng ID đơn tồn tại. Nếu đơn không tồn tại, application-service trả 404
trước khi gọi candidate-service nên không ghi nhận lời gọi vào circuit.

- Bình thường: có thông tin ứng viên, circuit CLOSED.
- Reset circuit rồi tắt candidate-service hoặc chọn mode ERROR: gọi 5 lần để OPEN.
- Gọi ngay lần 6: fallback nhanh, not-permitted tăng.
- Phục hồi candidate-service: sau thời gian OPEN 10 giây, HALF_OPEN cho 2 lời gọi thử.
  Gửi hai lời gọi thành công trong khoảng HALF_OPEN 10 giây để về CLOSED.
- Prometheus lấy mẫu mỗi 2 giây. Chờ khoảng 3 giây giữa hai probe để dễ thấy HALF_OPEN.
- Fallback HTTP 200 vẫn có thể đi kèm failed call trong circuit vì lỗi gốc đã được ghi nhận.

## Quản lý và chẩn đoán

```powershell
docker compose -f infras/monitoring/compose.yml ps
docker compose -f infras/monitoring/compose.yml logs --tail 50 prometheus
docker compose -f infras/monitoring/compose.yml exec prometheus promtool check config /etc/prometheus/prometheus.yml
docker compose -f infras/monitoring/compose.yml restart prometheus
docker compose -f infras/monitoring/compose.yml down
```

Restart Prometheus sau khi thay file scrape config. `down` giữ volume dữ liệu.

- Target DOWN: kiểm tra service 8083 và `host.docker.internal:8083` từ Docker Desktop.
- Endpoint 404: Reload Maven, restart ứng dụng; cả default và `cb-demo` phải expose `prometheus`.
- Health 503 khi circuit OPEN không đồng nghĩa metrics lỗi: kiểm tra `/actuator/prometheus`.
- Nếu sau này chạy application-service trong Docker, đổi target thành tên service Docker
  trên cùng network thay vì `host.docker.internal`.

Kiểm tra build application-service: `mvn -f services/application-service/pom.xml clean test`
(chạy từ thư mục `ats_parrent`).
