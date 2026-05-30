# Android Commands Cheatsheet

Chạy tại folder Android project, ví dụ `app/android`.

## Gradle

```bash
./gradlew clean
./gradlew test
./gradlew assembleDebug
./gradlew assembleRelease
```

## ADB

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb uninstall vn.delfi.xcloudwms
adb logcat | grep Xcloud
```

## Simulate keyboard scan

```bash
adb shell input text "LOC:A1-01"
adb shell input keyevent 66
```

## Clear app data

```bash
adb shell pm clear vn.delfi.xcloudwms
```

## Capture screenshot

```bash
adb exec-out screencap -p > screenshot.png
```
