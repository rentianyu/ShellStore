## 调试

### 带 adb shell 前缀
```sh
adb shell pm list packages -3
```

### 带设备号
```sh
adb -s emulator-5554 shell dumpsys battery
```

### 带提示符
```sh
$ pm list packages -s
```

### 主机端命令不剥离
```sh
adb install app.apk
```

### su 包裹
```sh
su -c 'pm list packages'
```
