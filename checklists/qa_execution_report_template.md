# QA Execution Report — Native Android PDA App

Template ghi lại KẾT QUẢ chạy QA cho 1 bản build cụ thể. Copy file này thành
`qa_report_<version>_<date>.md` rồi điền. Checklist gốc: `../docs/09_QA_UAT_CHECKLIST.md`.

## 1. Thông tin bản build

| Mục | Giá trị |
|---|---|
| versionName | |
| versionCode | |
| Flavor / build type | staging / release (mặc định UAT) |
| Commit hash | |
| Signed? | |
| Ngày test | |
| Người test (QA) | |
| Môi trường backend (URL) | staging / prod |
| Loại phát hành | UAT / release nội bộ |

## 2. Thiết bị đã test

| Device | Android | Scanner mode | Network | Build cài | Kết quả |
|---|---|---|---|---|---|
| | | Keyboard/Broadcast/SDK/Camera | Wi-Fi/4G | | PASS/FAIL |
| | | | | | |

## 3. Kết quả theo nhóm (tham chiếu doc 09)

Điền: PASS / FAIL / N/A. Nếu FAIL ghi mã issue ở cột Ghi chú.

| Nhóm | Kết quả | Ghi chú / Issue ID |
|---|---|---|
| 2. Auth & context | | |
| 3. Device license | | |
| 4. Scanner core | | |
| 5. Stock lookup | | |
| 6. GR Receiving | | |
| 7. GI Picking | | |
| 8. PA Put-away/Transfer | | |
| 9. IC Counting | | |
| 10. Network/reliability | | |
| 11. Performance | | |
| 12. UAT kho thật | | |

## 4. Test nghiệp vụ bắt buộc (stock integrity)

| Case | Kết quả | Ghi chú |
|---|---|---|
| GR receive product NONE | | |
| GR receive LOT có expiry | | |
| GR receive SERIAL không trùng | | |
| GI pick đúng allocation | | |
| GI overpick bị chặn | | |
| PA from A→B, tồn chuyển đúng, không âm nguồn | | |
| IC scan serial duplicate bị chặn | | |
| Double tap complete/submit KHÔNG commit trùng | | |
| Tenant/warehouse isolation đúng | | |
| Webapp/PWA thấy đúng dữ liệu native vừa ghi | | |

## 5. Bug phát hiện

| # | Severity (blocker/major/minor) | Module | Mô tả | Repro steps | Issue ID |
|---|---|---|---|---|---|
| 1 | | | | | |

## 6. Lệnh / artifact đã chạy

```txt
./gradlew test                 -> (kết quả, số test pass/fail)
./gradlew assembleStagingRelease (hoặc bundle)
apksigner verify <apk>         -> (signed? signer DN)
Smoke test                     -> (link smoke report nếu có)
```

## 7. Go / No-go

- [ ] Không có bug blocker về stock/permission/auth.
- [ ] Không commit trùng khi double tap / scan nhanh.
- [ ] PDA hardware scanner ổn định trên thiết bị chính.
- [ ] User kho hiểu cách dùng sau hướng dẫn ngắn.

**Kết luận:** GO / NO-GO

**Lý do:**

**Người duyệt:**  __________________   **Ngày:** __________
