# Demo: xem đơn ứng tuyển kèm thông tin ứng viên

```text
GET application-service:8083/api/v1/applications/{id}
  -> đọc application DB
  -> CandidateDetailsGateway [Circuit Breaker: candidateDetails]
  -> CandidateClient (OpenFeign)
  -> GET candidate-service:8082/api/v1/candidates/{candidateId}
  -> trả thông tin đơn + candidate hoặc fallback candidate=null
```

Flow này chỉ cần application-service và candidate-service. `jobId` là tham chiếu
lấy từ đơn trong DB, không gọi job-service. Flow POST tạo đơn có sẵn vẫn có nghiệp vụ riêng.

## Chạy

Java 21, Maven, PostgreSQL với hai DB `application_sdb`, `candidate_sdb`.
Cấu hình datasource bằng `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD` tương ứng từng service.

Chạy hai main class trong IntelliJ với active profile `cb-demo`, hoặc từ `ats_parrent`
chạy ở hai terminal:

```powershell
mvn -f services/candidate-service/pom.xml spring-boot:run "-Dspring-boot.run.profiles=cb-demo"
```

```powershell
mvn -f services/application-service/pom.xml spring-boot:run "-Dspring-boot.run.profiles=cb-demo"
```

Profile tắt Eureka. Sau khi schema được tạo:
1. Chạy `candidate-seed.sql` trong candidate DB và lấy candidate_id.
2. Điền ID đó vào biến `demo_candidate_id` trong `application-seed.sql`, chạy ở application DB.
3. Điền application_id được trả về vào `requests.http` (IntelliJ HTTP Client/Postman).

Controller tạo đơn nằm ở `fu.ats.api.rest.ApplicationController`, chuyển
`CreateApplicationRequest` sang `ApplicationRequest` trước khi gọi service.
POST và GET application đều nhận các header `X-User-ID`, `X-User-Email`, `X-User-Name`.

## Response và lỗi

- Bình thường: HTTP 200, `candidateInfoAvailable=true`, `candidate` chứa id, fullName, status...
- Kết nối lỗi, timeout, HTTP 5xx hoặc OPEN: HTTP 200, thông tin đơn giữ nguyên,
  `candidateInfoAvailable=false`, `candidate=null`, message thông tin ứng viên tạm thời không khả dụng.
- Candidate không tồn tại: candidate-service trả 404, response tổng hợp có message
  ứng viên không còn tồn tại. Exception này không tính vào circuit.
- Application không tồn tại: HTTP 404 trước khi gọi candidate-service.
- Candidate trả 4xx khác: trả lỗi HTTP tương ứng, không che bằng fallback sự cố.

Circuit dùng `executeSupplier()`, chỉ đo lời gọi HTTP. Fallback thực hiện sau khi
ghi nhận kết quả. CandidateClient tắt Retry; timeout kết nối 1s, đọc 5s.

## Chia 3 người: 15 cấu hình

YAML: `services/application-service/src/main/resources/application.yml`.
Thay YAML cần restart application-service; reset circuit không nạp lại cấu hình.

### Người 1 — Khi nào ngắt?

`slidingWindowType`, `slidingWindowSize`, `minimumNumberOfCalls`,
`failureRateThreshold`, `recordExceptions`.

- Mode ERROR, reset circuit, gọi GET application 4 lần: CLOSED (chưa đủ 5 mẫu).
- Lần 5: 100% lỗi >= 50%, OPEN. Lần 6 ngay sau đó: bị chặn, receivedCalls ở candidate không tăng.
- Biến thể: reset, NORMAL 2 lần + ERROR 3 lần => 60% lỗi, OPEN.
- TIME_BASED: đổi type, size=5, restart; 4 request thành công, chờ >5s rồi thêm 1 request.
  Kết quả cũ hết hạn, chỉ còn 1 mẫu, chưa đủ minimumNumberOfCalls.
- `recordExceptions` ghi nhận `RetryableException` khi mất kết nối/timeout.

### Người 2 — Khi nào nối lại?

`waitDurationInOpenState`, `automaticTransitionFromOpenToHalfOpenEnabled`,
`permittedNumberOfCallsInHalfOpenState`, `maxWaitDurationInHalfOpenState`, `ignoreExceptions`.

- Tiếp nối OPEN, đổi candidate sang NORMAL. Sau 10s tự HALF_OPEN, 2 probe thành công => CLOSED.
- Giữ ERROR: 2 probe lỗi => OPEN.
- HALF_OPEN không nhận đủ kết quả trong 10s => OPEN.
- Đổi automaticTransition=false: hết 10s vẫn OPEN tới khi có lời gọi tiếp theo.
- Reset, NOT_FOUND, gọi 6 lần: CLOSED, bufferedCalls=0; 4xx bị ignore không tính thành công/thất bại.

### Người 3 — Chậm và quan sát

`slowCallDurationThreshold`, `slowCallRateThreshold`, `registerHealthIndicator`,
`eventConsumerBufferSize`, `recordFailurePredicate`.

- Reset, SLOW 3000ms, gọi 5 lần tuần tự: cả 5 thành công, 100% slow call => OPEN.
- Lần 5 vẫn có candidate; lần 6 trả fallback nhanh.
- Slow threshold 2s chỉ phân loại, không cắt request. Muốn timeout dùng SLOW 7000ms (> read timeout 5s).
- Xem health, events, metrics. Buffer 20 sự kiện khác cửa sổ thống kê 10 lời gọi.
- `recordFailurePredicate` ghi nhận FeignException HTTP >=500 (503 trong mode ERROR).
  Predicate kết hợp OR với recordExceptions, ignoreExceptions được ưu tiên.

## Quan sát

### Grafana dashboard

Import file [dashboard.json](dashboard.json) bằng **Grafana → Dashboards → New → Import → Upload dashboard JSON file**.
Chọn datasource Prometheus đang scrape application-service, rồi nhấn Import.
Dashboard dùng panel Stat/Time series hiện đại, lọc đúng `application-service` và
circuit `candidateDetails`; chọn instance `host.docker.internal:8083` trên dashboard.
Không cần đổi tên datasource thành `Prometheus`. File này không có các panel Retry/Bulkhead
vì flow hiện tại không sử dụng hai cơ chế đó.

- Người 1: Failure Rate, Buffered calls, CircuitBreaker States, Blocked calls.
- Người 2: HALF_OPEN/CLOSED, lịch sử trạng thái và Buffered calls khi ignore 404.
- Người 3: Slow Call Rate, Average downstream call duration, Recorded downstream calls.

Refresh 5s, time range 15 phút. Prometheus scrape 2s nên có thể bỏ lỡ trạng thái quá ngắn.
Dashboard import này độc lập với dashboard được provision trong stack monitoring.

- `GET :8083/api/v1/circuit-breakers/candidateDetails`: state và counters.
- `POST :8083/api/v1/circuit-breakers/candidateDetails/resets`: reset trước kịch bản độc lập.
- `GET/PUT :8082/api/v1/services/candidate-service/behavior`: xem/đổi mode.
- Các API điều khiển chỉ có ở profile `cb-demo`.
- `GET :8083/actuator/circuitbreakerevents/candidateDetails`: sự kiện.
- `GET :8083/actuator/prometheus`: metrics; [hướng dẫn Prometheus](../../infras/monitoring/README.md).
- Health có thể 503 khi OPEN; endpoint metrics vẫn hoạt động.
- Tỷ lệ -1 nghĩa là chưa đủ mẫu. Reset không nhất thiết xóa lịch sử event.

## Kiểm tra

`ApplicationDetailCircuitBreakerTest` nạp YAML thật, kiểm tra 503/timeout/404,
OPEN/chặn gọi/phục hồi, slow success, response HTTP và xác nhận không gọi JobClient.
`CandidateDemoFilterTest` kiểm tra filter lỗi/phục hồi chỉ áp dụng GET candidate.

```powershell
mvn -f services/application-service/pom.xml "-Dtest=ApplicationDetailCircuitBreakerTest" test
mvn -f services/candidate-service/pom.xml test
```

Chạy toàn bộ test application-service bằng `mvn -f services/application-service/pom.xml clean test`.
