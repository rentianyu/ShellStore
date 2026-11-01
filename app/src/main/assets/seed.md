# 内置命令集

@author 小贝塔
@version 2026.11.1
@updated 2026-11-01

## 设备信息

### 设备型号
@risk safe
@need shell
@tag 设备 型号

```sh
getprop ro.product.model
```

### 品牌
@risk safe
@need shell
@tag 设备 品牌

```sh
getprop ro.product.brand
```

### Android 版本
@risk safe
@need shell
@tag 版本 系统

```sh
getprop ro.build.version.release
```

### SDK 版本
@risk safe
@need shell
@tag 版本 系统

```sh
getprop ro.build.version.sdk
```

### 内核版本
@risk safe
@need shell
@tag 内核 系统

```sh
uname -a
```

### 屏幕分辨率
@risk safe
@need shell
@tag 屏幕 分辨率

```sh
wm size
```

### 屏幕密度
@risk safe
@need shell
@tag 屏幕 密度

```sh
wm density
```

### 开机时长
@risk safe
@need shell
@tag 运行时间

```sh
uptime
```

### 设备序列号
@risk safe
@need shell
@tag 设备 序列号

```sh
getprop ro.serialno
```

### 全部系统属性
@risk safe
@need shell
@tag 属性 调试
@desc 输出所有 getprop 属性，内容很长，建议配合 grep 过滤。

```sh
getprop
```

## 应用管理

### 列出第三方应用
@risk safe
@need shell
@tag pm 包名
@param filter = 范围 | -3
  -3 只列第三方，-s 只列系统，-e 只列已启用，-d 只列已停用。
@example pm list packages -3

```sh
pm list packages {filter}
```

### 列出系统应用
@risk safe
@need shell
@tag pm 系统应用

```sh
pm list packages -s
```

### 列出已停用应用
@risk safe
@need shell
@tag pm 停用

```sh
pm list packages -d
```

### 查应用版本号
@risk safe
@need shell
@tag 版本 应用
@param pkg = 包名 | com.tencent.mm
  dumpsys 输出很长，建议配合 grep 使用。
@example dumpsys package com.tencent.mm | grep versionName

```sh
dumpsys package {pkg} | grep versionName
```

### 查应用安装时间
@risk safe
@need shell
@tag 应用 时间
@param pkg = 包名 | com.tencent.mm

```sh
dumpsys package {pkg} | grep firstInstallTime
```

### 查应用 UID
@risk safe
@need shell
@tag 应用 uid
@param pkg = 包名 | com.tencent.mm

```sh
dumpsys package {pkg} | grep userId=
```

## 系统状态

### 磁盘使用情况
@risk safe
@need shell
@tag 存储 磁盘

```sh
df -h
```

### 电池状态
@risk safe
@need shell
@tag 电池 电量

```sh
dumpsys battery
```

### WiFi 详细信息
@risk safe
@need shell
@tag 网络 wifi
@desc 不同 Android 版本字段名有差异，找不到就改 grep 关键词。

```sh
dumpsys wifi | grep -i mWifiInfo
```

### 查看 IP 地址
@risk safe
@need shell
@tag 网络 ip

```sh
ip addr show wlan0
```

### 查看路由表
@risk safe
@need shell
@tag 网络 路由

```sh
ip route
```

### 内存占用
@risk safe
@need shell
@tag 内存

```sh
cat /proc/meminfo | head -8
```

### CPU 信息
@risk safe
@need shell
@tag cpu 硬件

```sh
cat /proc/cpuinfo | grep -E "Hardware|Processor|model name"
```

### 当前时区
@risk safe
@need shell
@tag 时区 系统

```sh
getprop persist.sys.timezone
```

## 设置跳转

### 打开 WiFi 设置
@type activity
@risk safe
@need none
@tag 设置 wifi
@desc 用 action 而不是 component 启动，不同厂商 ROM 都能落到各自的 WiFi 页面。

@action android.settings.WIFI_SETTINGS

### 打开蓝牙设置
@type activity
@risk safe
@need none
@tag 设置 蓝牙

@action android.settings.BLUETOOTH_SETTINGS

### 打开应用列表
@type activity
@risk safe
@need none
@tag 设置 应用

@action android.settings.APPLICATION_SETTINGS

### 打开开发者选项
@type activity
@risk safe
@need none
@tag 设置 开发

@action android.settings.APPLICATION_DEVELOPMENT_SETTINGS

### 打开电池优化
@type activity
@risk safe
@need none
@tag 设置 电池

@action android.settings.BATTERY_SAVER_SETTINGS

### 打开显示设置
@type activity
@risk safe
@need none
@tag 设置 显示

@action android.settings.DISPLAY_SETTINGS
