# 10 — Native Android App Backlog Phases

## Phase 0 — Discovery

| ID | Task | Output | Priority |
|---|---|---|---|
| APP-0001 | Audit scanner PWA routes | navigation map | P0 |
| APP-0002 | Audit scanner PWA APIs | api endpoint matrix | P0 |
| APP-0003 | Audit scanner PWA UI states | parity matrix | P0 |
| APP-0004 | Audit permissions | permission map | P0 |
| APP-0005 | Audit PDA target models | device lab sheet | P0 |

## Phase 1 — Foundation

| ID | Task | Output | Priority |
|---|---|---|---|
| APP-0101 | Create Android Kotlin Compose project | buildable app | P0 |
| APP-0102 | Configure dev/staging/prod | build variants | P0 |
| APP-0103 | Add network layer | API client | P0 |
| APP-0104 | Add navigation shell | app nav | P0 |
| APP-0105 | Add theme matching scanner PWA | design tokens | P1 |
| APP-0106 | Add safe logger | logger | P1 |

## Phase 2 — Auth & Context

| ID | Task | Output | Priority |
|---|---|---|---|
| APP-0201 | Login screen | auth UI | P0 |
| APP-0202 | Secure token storage | security core | P0 |
| APP-0203 | Load profile/tenant | session context | P0 |
| APP-0204 | Warehouse switch | warehouse context | P0 |
| APP-0205 | Permission-based menu | home menu | P0 |
| APP-0206 | Logout/session expiry | auth handling | P0 |

## Phase 3 — Scanner Core

| ID | Task | Output | Priority |
|---|---|---|---|
| APP-0301 | ScannerManager interface | scanner core | P0 |
| APP-0302 | Keyboard wedge adapter | PDA generic support | P0 |
| APP-0303 | Broadcast adapter | PDA support | P0 |
| APP-0304 | Camera fallback | phone/demo support | P1 |
| APP-0305 | Barcode parser | scan classification | P0 |
| APP-0306 | Feedback manager | beep/vibrate | P0 |
| APP-0307 | Scan test screen | hardware validation | P0 |

## Phase 4 — Stock Lookup

| ID | Task | Output | Priority |
|---|---|---|---|
| APP-0401 | Lookup API adapter | stock read | P0 |
| APP-0402 | Scan lookup screen | read-only UX | P0 |
| APP-0403 | Product/location/serial result cards | UI | P0 |
| APP-0404 | Offline/error/retry states | reliability | P0 |

## Phase 5 — PA Put-away

| ID | Task | Output | Priority |
|---|---|---|---|
| APP-0501 | Start PA session | DRAFT create | P0 |
| APP-0502 | PA scan stepper | scan UX | P0 |
| APP-0503 | Add/delete draft lines | draft management | P0 |
| APP-0504 | Submit PA | commit transaction | P0 |
| APP-0505 | Conflict handling | safe submit | P0 |

## Phase 6 — GI Picking

| ID | Task | Output | Priority |
|---|---|---|---|
| APP-0601 | GI assigned list | task list | P0 |
| APP-0602 | GI detail/progress | detail UI | P0 |
| APP-0603 | Picking scan flow | pick UX | P0 |
| APP-0604 | Overpick prevention | validation | P0 |
| APP-0605 | Complete GI | shipment commit | P0 |

## Phase 7 — GR Receiving

| ID | Task | Output | Priority |
|---|---|---|---|
| APP-0701 | GR assigned list | task list | P0 |
| APP-0702 | GR detail/progress | detail UI | P0 |
| APP-0703 | Receive NONE/LOT/SERIAL | receive UX | P0 |
| APP-0704 | Release stock | commit API | P0 |
| APP-0705 | Complete GR | status flow | P0 |

## Phase 8 — IC Counting

| ID | Task | Output | Priority |
|---|---|---|---|
| APP-0801 | IC assigned list | task list | P0 |
| APP-0802 | IC detail/scope | detail UI | P0 |
| APP-0803 | Count scan flow | count UX | P0 |
| APP-0804 | Duplicate serial prevention | validation | P0 |
| APP-0805 | Finish count | status flow | P0 |

## Phase 9 — Device License & Release

| ID | Task | Output | Priority |
|---|---|---|---|
| APP-0901 | Device fingerprint | device core | P0 |
| APP-0902 | Device verify/register | license flow | P0 |
| APP-0903 | App version check | rollout control | P1 |
| APP-0904 | Release signing | signed APK/AAB | P0 |
| APP-0905 | UAT package | install guide | P0 |
