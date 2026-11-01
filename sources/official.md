# 官方命令库

@author 小贝塔
@version 2026.11.1
@updated 2026-11-01
@doc https://github.com/rentianyu/ShellStore
@desc Android 常用命令、脚本、URL Scheme 与 Intent 的整理。命令环境为设备上的 sh（adb shell / Root / Termux 均可）。带「需 shell」标记的命令需要 adb 或 Shizuku 授权，带「需 root」的需要已 root 的设备。

## 系统信息

### 设备型号
@risk safe
@need none
@tag 设备 型号
@desc 读取只读属性，不需要任何权限。

```sh
getprop ro.product.model
```

### 品牌与制造商
@risk safe
@need none
@tag 设备 品牌

```sh
getprop ro.product.brand && getprop ro.product.manufacturer
```

### 设备代号
@risk safe
@need none
@tag 设备 代号

```sh
getprop ro.product.device
```

### Android 版本
@risk safe
@need none
@tag 版本 系统

```sh
getprop ro.build.version.release
```

### SDK 版本
@risk safe
@need none
@tag 版本 sdk

```sh
getprop ro.build.version.sdk
```

### 安全补丁级别
@risk safe
@need none
@tag 版本 补丁 安全

```sh
getprop ro.build.version.security_patch
```

### 系统指纹
@risk safe
@need none
@tag 版本 指纹
@desc 厂商、机型、版本、编译号合在一起的唯一标识，刷机、报障时常要。

```sh
getprop ro.build.fingerprint
```

### 编译时间
@risk safe
@need none
@tag 版本 编译

```sh
getprop ro.build.date
```

### CPU 架构
@risk safe
@need none
@tag cpu 架构

```sh
getprop ro.product.cpu.abi
```

### 内核信息
@risk safe
@need none
@tag 内核

```sh
uname -a
```

### 开机时长
@risk safe
@need none
@tag 运行 时长
@desc 第一列是秒数，除以 3600 得到小时。

```sh
cat /proc/uptime
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

### 序列号
@risk safe
@need shell
@tag 设备 序列号
@desc Android 10 起普通应用读不到真实序列号，返回 unknown 属正常。

```sh
getprop ro.serialno
```

### 查看全部属性
@risk safe
@need none
@tag 属性 全部

```sh
getprop | head -n 120
```

## 文件与存储

### 分区占用
@risk safe
@need none
@tag 存储 分区

```sh
df -h
```

### 指定目录大小
@risk safe
@need shell
@tag 存储 目录 大小
@param dir=目录|/sdcard/Download

```sh
du -sh {dir}
```

### 列出目录
@risk safe
@need shell
@tag 文件 列表
@param dir=目录|/sdcard

```sh
ls -al {dir}
```

### 按名称找文件
@risk safe
@need shell
@tag 文件 查找
@param name=文件名关键词|*.apk
@param dir=搜索起点|/sdcard

```sh
find {dir} -name "{name}" -type f 2>/dev/null | head -n 50
```

### 找大文件
@risk safe
@need root
@tag 文件 大文件 清理
@desc 扫 /data 下大于 100MB 的文件，按体积排序。整盘扫描比较慢。

```sh
find /data -type f -size +100M 2>/dev/null | head -n 30
```

### 看文件开头几行
@risk safe
@need none
@tag 文件 查看
@param file=文件路径|/proc/version

```sh
head -n 20 {file}
```

### 统计行数
@risk safe
@need none
@tag 文件 统计
@param file=文件路径|/proc/cpuinfo

```sh
wc -l {file}
```

### 压缩目录
@risk caution
@need shell
@tag 压缩 tar
@param src=源目录|/sdcard/DCIM
@param dst=输出文件|/sdcard/backup.tar.gz
@desc Android 自带的是 toybox tar，支持 gzip。

```sh
tar -czf {dst} -C {src} .
```

### 解压 tar.gz
@risk caution
@need shell
@tag 解压 tar
@param file=压缩包|/sdcard/backup.tar.gz
@param dir=解压到|/sdcard/restore
@desc 目录要先建好：mkdir -p 目标目录。

```sh
mkdir -p {dir} && tar -xzf {file} -C {dir}
```

### 清理应用缓存目录
@risk caution
@need root
@tag 清理 缓存
@desc 只删 cache 子目录，不动数据目录。删之前建议先强制停止对应应用。

```sh
find /data/data -maxdepth 2 -type d -name cache -exec rm -rf {} + 2>/dev/null
```

### 查看挂载点
@risk safe
@need none
@tag 存储 挂载

```sh
mount | grep -E " /data | /system | /storage "
```

## 网络与连接

### 查看 IP 地址
@risk safe
@need shell
@tag 网络 ip

```sh
ip -4 addr show | grep inet
```

### 查看路由表
@risk safe
@need shell
@tag 网络 路由 网关

```sh
ip route
```

### DNS 服务器
@risk safe
@need none
@tag 网络 dns

```sh
getprop | grep -i "net.dns"
```

### 测连通性
@risk safe
@need shell
@tag 网络 ping
@param host=目标地址|114.114.114.114

```sh
ping -c 4 {host}
```

### 域名解析
@risk safe
@need shell
@tag 网络 dns 解析
@param host=域名|www.baidu.com

```sh
nslookup {host}
```

### ARP 邻居表
@risk safe
@need shell
@tag 网络 arp

```sh
ip neigh
```

### WiFi 连接信息
@risk safe
@need shell
@tag 网络 wifi
@desc 输出里 SSID、频率、信号强度（RSSI）、IP 都在。

```sh
dumpsys wifi | grep -E "SSID|mWifiInfo|Frequency|RSSI" | head -n 10
```

### WiFi 扫描结果
@risk safe
@need shell
@tag 网络 wifi 扫描

```sh
cmd wifi list-scan-results
```

### 流量统计
@risk safe
@need none
@tag 网络 流量

```sh
cat /proc/net/dev
```

### 开关 WiFi
@risk caution
@need root
@tag 网络 wifi 开关
@param state=开关|disable
@desc state 填 enable 或 disable。

```sh
svc wifi {state}
```

### 开关移动数据
@risk caution
@need root
@tag 网络 数据 开关
@param state=开关|disable

```sh
svc data {state}
```

### 飞行模式
@risk caution
@need shell
@tag 网络 飞行模式
@param state=开关|enable
@desc enable 打开飞行模式，disable 关闭。

```sh
cmd connectivity airplane-mode {state}
```

## 应用管理

### 列出第三方应用
@risk safe
@need shell
@tag 应用 列表

```sh
pm list packages -3
```

### 列出系统应用
@risk safe
@need shell
@tag 应用 系统

```sh
pm list packages -s
```

### 搜索包名
@risk safe
@need shell
@tag 应用 搜索
@param kw=关键词|wechat

```sh
pm list packages | grep -i {kw}
```

### 应用详情
@risk safe
@need shell
@tag 应用 详情
@param pkg=包名|com.tencent.mm

```sh
dumpsys package {pkg} | head -n 40
```

### 查看应用版本
@risk safe
@need shell
@tag 应用 版本
@param pkg=包名|com.tencent.mm

```sh
dumpsys package {pkg} | grep -E "versionName|versionCode" | head -n 4
```

### 当前前台界面
@risk safe
@need shell
@tag 应用 前台 activity

```sh
dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -n 3
```

### 强制停止应用
@risk caution
@need shell
@tag 应用 停止
@param pkg=包名|com.tencent.mm

```sh
am force-stop {pkg}
```

### 启动应用
@risk safe
@need shell
@tag 应用 启动
@param pkg=包名|com.tencent.mm

```sh
monkey -p {pkg} -c android.intent.category.LAUNCHER 1
```

### 清除应用数据
@risk danger
@need shell
@tag 应用 清除 数据
@param pkg=包名|com.example.app
@desc 等价于设置里「清除数据」，聊天记录、登录态全部丢失且不可恢复。

```sh
pm clear {pkg}
```

### 卸载应用
@risk danger
@need shell
@tag 应用 卸载
@param pkg=包名|com.example.app

```sh
pm uninstall {pkg}
```

### 停用系统应用
@risk caution
@need shell
@tag 应用 停用
@param pkg=包名|com.example.bloat
@desc 比卸载安全，随时可 pm enable 恢复。停用错系统组件可能影响开机。

```sh
pm disable-user --user 0 {pkg}
```

### 恢复被停用的应用
@risk safe
@need shell
@tag 应用 启用
@param pkg=包名|com.example.bloat

```sh
pm enable {pkg}
```

### 安装 APK
@risk caution
@need shell
@tag 应用 安装
@param file=APK 路径|/sdcard/Download/app.apk

```sh
pm install -r {file}
```

### 列出设备上所有包名对应启动项
@risk safe
@need shell
@tag 应用 activity 列表

```sh
cmd package resolve-activity --brief com.tencent.mm | tail -n 1
```

## 权限与安全

### 查看应用的运行时权限
@risk safe
@need shell
@tag 权限 授予
@param pkg=包名|com.tencent.mm

```sh
dumpsys package {pkg} | grep -E "granted=(true|false)" | head -n 30
```

### 授予运行时权限
@risk caution
@need shell
@tag 权限 授予
@param pkg=包名|com.example.app
@param perm=权限|android.permission.CAMERA
@desc 应用需在清单里声明过该权限，否则报错。

```sh
pm grant {pkg} {perm}
```

### 撤销运行时权限
@risk caution
@need shell
@tag 权限 撤销
@param pkg=包名|com.example.app
@param perm=权限|android.permission.CAMERA

```sh
pm revoke {pkg} {perm}
```

### 列出危险权限
@risk safe
@need shell
@tag 权限 危险

```sh
pm list permissions -g -d
```

### 检查 Root
@risk safe
@need none
@tag root 检测

```sh
which su; id
```

### SELinux 状态
@risk safe
@need none
@tag 安全 selinux

```sh
getenforce
```

### 用户证书列表
@risk safe
@need root
@tag 证书 安全

```sh
ls -l /system/etc/security/cacerts | head -n 30
```

### 电池优化白名单
@risk safe
@need shell
@tag 电池 白名单 后台

```sh
dumpsys deviceidle whitelist
```

### 开发者选项是否开启
@risk safe
@need shell
@tag 设置 开发者

```sh
settings get global development_settings_enabled
```

### 查看已注册账户类型
@risk safe
@need shell
@tag 账户 安全

```sh
dumpsys account | grep -E "Account \{|type=" | head -n 20
```

## 性能与内存

### 内存概况
@risk safe
@need none
@tag 内存

```sh
head -n 8 /proc/meminfo
```

### 内存用量汇总
@risk safe
@need none
@tag 内存 使用率
@desc MemAvailable 才是真正可用的量，别只看 MemFree。

```sh
awk '/MemTotal|MemFree|MemAvailable/ {print $1, int($2/1024)" MB"}' /proc/meminfo
```

### CPU 占用排行
@risk safe
@need shell
@tag cpu 占用

```sh
top -n 1 -b -o %CPU | head -n 20
```

### 进程列表
@risk safe
@need shell
@tag 进程

```sh
ps -A -o PID,RSS,NAME | head -n 40
```

### 单个应用内存
@risk safe
@need shell
@tag 内存 应用
@param pkg=包名|com.tencent.mm

```sh
dumpsys meminfo {pkg}
```

### 全局内存分布
@risk safe
@need shell
@tag 内存 汇总

```sh
dumpsys meminfo | head -n 30
```

### 电池状态
@risk safe
@need shell
@tag 电池

```sh
dumpsys battery
```

### 电池耗电统计
@risk safe
@need shell
@tag 电池 耗电
@desc 输出很长，只取前 60 行。

```sh
dumpsys batterystats --charged | head -n 60
```

### CPU 温度
@risk safe
@need none
@tag 温度 cpu
@desc 数值单位是毫摄氏度，除以 1000 才是摄氏度。

```sh
cat /sys/class/thermal/thermal_zone0/temp
```

### 动画缩放
@risk safe
@need shell
@tag 设置 动画

```sh
settings get global window_animation_scale; settings get global transition_animation_scale; settings get global animator_duration_scale
```

### 关闭动画加速
@risk caution
@need shell
@tag 设置 动画 加速
@desc 三个缩放都设为 0，系统过渡会瞬间完成，主观更快。恢复时把 0 改回 1。

```sh
settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0
```

## 日志与排查

### 最近日志
@risk safe
@need shell
@tag 日志 logcat

```sh
logcat -d -t 200
```

### 按标签过滤
@risk safe
@need shell
@tag 日志 过滤
@param tag=标签|ActivityManager

```sh
logcat -d -s {tag} -t 200
```

### 只看错误日志
@risk safe
@need shell
@tag 日志 错误

```sh
logcat -d "*:E" -t 200
```

### 清空日志缓冲
@risk caution
@need shell
@tag 日志 清空
@desc 清空后旧日志不可恢复，排查问题前先导出。

```sh
logcat -c
```

### 崩溃墓碑
@risk safe
@need root
@tag 崩溃 tombstone

```sh
ls -lt /data/tombstones | head -n 10
```

### ANR 记录
@risk safe
@need root
@tag anr 崩溃

```sh
ls -lt /data/anr | head -n 10
```

### 内核日志尾部
@risk safe
@need root
@tag 内核 dmesg

```sh
dmesg | tail -n 60
```

### 所有 dumpsys 服务
@risk safe
@need shell
@tag dumpsys 服务

```sh
dumpsys -l
```

### 系统崩溃摘要
@risk safe
@need shell
@tag 崩溃 dropbox

```sh
dumpsys dropbox --print | tail -n 120
```

### 实时日志
@risk safe
@need shell
@tag 日志 实时
@desc 会一直输出，需要手动停止（执行框里点关闭不会中断进程，可用 kill 处理）。

```sh
logcat -v time
```

## 快捷跳转

### 拨号
@type urlscheme
@risk safe
@need none
@tag 电话 拨号

```text
tel:10086
```

### 发送短信
@type urlscheme
@risk safe
@need none
@tag 短信

```text
smsto:10086
```

### 打开网页
@type urlscheme
@risk safe
@need none
@tag 浏览器 网页

```text
https://xbta.cc
```

### 写邮件
@type urlscheme
@risk safe
@need none
@tag 邮件

```text
mailto:hello@example.com
```

### 地图查看坐标
@type urlscheme
@risk safe
@need none
@tag 地图 位置

```text
geo:36.1993,117.0876?q=36.1993,117.0876
```

### 应用市场详情页
@type urlscheme
@risk safe
@need none
@tag 市场 应用
@param pkg=包名|com.tencent.mm

```text
market://details?id={pkg}
```

### 微信扫一扫
@type urlscheme
@risk safe
@need none
@tag 微信 扫码

```text
weixin://scanqrcode
```

### 支付宝扫一扫
@type urlscheme
@risk safe
@need none
@tag 支付宝 扫码

```text
alipays://platformapi/startapp?appId=10000011
```

### 打开淘宝
@type urlscheme
@risk safe
@need none
@tag 淘宝

```text
taobao://
```

### 打开高德地图
@type urlscheme
@risk safe
@need none
@tag 高德 地图

```text
androidamap://viewMap?sourceApplication=ShellStore
```

### 系统分享
@type intent
@risk safe
@need none
@tag 分享
@desc 拉起分享面板，分享一段文本。

@action android.intent.action.SEND
@mime text/plain
@extra android.intent.extra.TEXT:string=来自 Shell 商店的分享

### 打开网页（Intent 版）
@type intent
@risk safe
@need none
@tag 浏览器
@desc 与 urlscheme 等价，但可以额外带包名指定浏览器。

@action android.intent.action.VIEW
@data https://xbta.cc
@category android.intent.category.BROWSABLE

## 系统设置页

### 设置首页
@type activity
@risk safe
@need none
@tag 设置

@action android.settings.SETTINGS

### WiFi 设置
@type activity
@risk safe
@need none
@tag 设置 wifi

@action android.settings.WIFI_SETTINGS

### 蓝牙设置
@type activity
@risk safe
@need none
@tag 设置 蓝牙

@action android.settings.BLUETOOTH_SETTINGS

### 应用列表
@type activity
@risk safe
@need none
@tag 设置 应用

@action android.settings.APPLICATION_SETTINGS

### 应用信息页
@type activity
@risk safe
@need none
@tag 设置 应用 详情
@desc 带 package: 前缀的 data，多数 ROM 都能正确落到该应用的信息页。

@action android.settings.APPLICATION_DETAILS_SETTINGS
@data package:com.tencent.mm

### 通知设置
@type activity
@risk safe
@need none
@tag 设置 通知

@action android.settings.NOTIFICATION_SETTINGS

### 电池设置
@type activity
@risk safe
@need none
@tag 设置 电池

@action android.settings.BATTERY_SAVER_SETTINGS

### 电池优化白名单设置
@type activity
@risk safe
@need none
@tag 设置 电池 后台

@action android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS

### 显示设置
@type activity
@risk safe
@need none
@tag 设置 显示

@action android.settings.DISPLAY_SETTINGS

### 声音设置
@type activity
@risk safe
@need none
@tag 设置 声音

@action android.settings.SOUND_SETTINGS

### 存储设置
@type activity
@risk safe
@need none
@tag 设置 存储

@action android.settings.INTERNAL_STORAGE_SETTINGS

### 位置信息
@type activity
@risk safe
@need none
@tag 设置 位置

@action android.settings.LOCATION_SOURCE_SETTINGS

### 安全设置
@type activity
@risk safe
@need none
@tag 设置 安全

@action android.settings.SECURITY_SETTINGS

### 开发者选项
@type activity
@risk safe
@need none
@tag 设置 开发者

@action android.settings.APPLICATION_DEVELOPMENT_SETTINGS

### 无障碍设置
@type activity
@risk safe
@need none
@tag 设置 无障碍

@action android.settings.ACCESSIBILITY_SETTINGS

### 输入法设置
@type activity
@risk safe
@need none
@tag 设置 输入法

@action android.settings.INPUT_METHOD_SETTINGS

### 默认应用设置
@type activity
@risk safe
@need none
@tag 设置 默认应用

@action android.settings.MANAGE_DEFAULT_APPS_SETTINGS

### 应用使用情况
@type activity
@risk safe
@need none
@tag 设置 使用统计

@action android.settings.USAGE_ACCESS_SETTINGS

## 广播与系统操作

### 媒体库扫描单个文件
@type broadcast
@risk safe
@need shell
@tag 广播 媒体 扫描
@desc 发广播让媒体库重新索引指定文件，新下载的图片在相册里不显示时用。

@action android.intent.action.MEDIA_SCANNER_SCAN_FILE
@data file:///sdcard/DCIM/Camera/1.jpg

### 通知耳机按键事件
@type broadcast
@risk caution
@need shell
@tag 广播 媒体
@desc 模拟一次媒体按键，部分播放器会响应。keycode 85 是播放/暂停。

@action android.intent.action.MEDIA_BUTTON
@extra android.intent.extra.KEY_EVENT:int=85

### 触发媒体重扫全盘
@risk caution
@need shell
@tag 媒体 扫描 重扫
@desc 通知系统挂载状态变化，媒体库会重扫存储。耗时较长。

```sh
am broadcast -a android.intent.action.MEDIA_MOUNTED -d file:///sdcard
```

### 结束所有后台进程
@risk caution
@need shell
@tag 后台 清理 进程

```sh
am kill-all
```

### 结束指定应用后台
@risk caution
@need shell
@tag 后台 应用
@param pkg=包名|com.example.app

```sh
am kill {pkg}
```

### 重启应用
@risk caution
@need shell
@tag 应用 重启
@param pkg=包名|com.tencent.mm

```sh
am force-stop {pkg}; monkey -p {pkg} -c android.intent.category.LAUNCHER 1
```

### 重启 SystemUI
@risk caution
@need root
@tag 重启 界面
@desc 状态栏、通知栏卡死时用。不重启手机，界面会闪一下恢复。

```sh
pkill -f com.android.systemui
```

### 重启手机
@risk danger
@need root
@tag 重启 设备

```sh
reboot
```

### 关机
@risk danger
@need root
@tag 关机 设备

```sh
reboot -p
```

### 进入 Recovery
@risk danger
@need root
@tag 重启 recovery

```sh
reboot recovery
```

### 屏幕录制
@risk caution
@need shell
@tag 录屏 screenrecord
@param file=输出文件|/sdcard/record.mp4
@desc 默认 180 秒上限，Ctrl+C 或超时结束。Android 14 起部分机型需要显式授权。

```sh
screenrecord --time-limit 30 {file}
```

### 截图
@risk safe
@need shell
@tag 截图
@param file=输出文件|/sdcard/shot.png

```sh
screencap -p {file}
```

### 模拟电源键
@risk caution
@need shell
@tag input 按键 电源

```sh
input keyevent 26
```

### 模拟音量加
@risk safe
@need shell
@tag input 按键 音量

```sh
input keyevent 24
```

### 返回键
@risk safe
@need shell
@tag input 按键 返回

```sh
input keyevent 4
```

### 文本输入
@risk caution
@need shell
@tag input 文本
@param text=要输入的文字|hello
@desc 不支持中文，中文需要借助输入法或 adb 剪贴板方案。

```sh
input text {text}
```
