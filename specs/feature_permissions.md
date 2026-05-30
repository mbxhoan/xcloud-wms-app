# Feature & Permission Map Draft

Tên permission thật phải audit từ backend/webapp/scanner PWA.

| Native menu | Permission draft | Backend check required | Notes |
|---|---|---|---|
| Stock Lookup | `SCANNER_STOCK_LOOKUP` | Yes | Read-only |
| GR List | `SCANNER_GR_VIEW` | Yes | Assigned/warehouse scoped |
| GR Receive | `SCANNER_GR_RECEIVE` | Yes | Commit stock via API |
| GR Complete | `SCANNER_GR_COMPLETE` | Yes | Manager/keeper tùy rule |
| GI List | `SCANNER_GI_VIEW` | Yes | Assigned/warehouse scoped |
| GI Pick | `SCANNER_GI_PICK` | Yes | Chặn overpick backend |
| GI Complete | `SCANNER_GI_COMPLETE` | Yes | Ship/complete |
| PA Create | `SCANNER_PA_CREATE` | Yes | Start session |
| PA Submit | `SCANNER_PA_SUBMIT` | Yes | Internal transfer commit |
| IC View | `SCANNER_IC_VIEW` | Yes | Assigned/scope |
| IC Count | `SCANNER_IC_COUNT` | Yes | Count detail |
| Device Info | `SCANNER_DEVICE_VIEW` | Optional | Useful for support |
