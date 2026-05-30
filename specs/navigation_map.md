# Native Android Navigation Map

```txt
Splash
  ├─ Login
  └─ Session Restore
       ├─ Device Verify
       ├─ Warehouse Select
       └─ Home

Home
  ├─ Stock Lookup
  ├─ Goods Receipt
  │    ├─ GR Assigned List
  │    ├─ GR Detail
  │    └─ GR Receive Scan
  ├─ Goods Issue
  │    ├─ GI Assigned List
  │    ├─ GI Detail
  │    └─ GI Picking Scan
  ├─ Put-away
  │    ├─ PA Session Start
  │    ├─ PA Scan Stepper
  │    └─ PA Review Submit
  ├─ Inventory Count
  │    ├─ IC Assigned List
  │    ├─ IC Detail
  │    └─ IC Count Scan
  └─ Settings
       ├─ Warehouse Switch
       ├─ Scanner Test
       ├─ Device Info
       └─ Logout
```

## Deep link/internal navigation

- Scan `GR:<code>` từ home có thể mở GR detail nếu user có quyền.
- Scan `GI:<code>` từ home có thể mở GI detail nếu user có quyền.
- Scan `LOC:<code>` từ home mở stock by location.
- Scan serial từ home mở serial lookup.
