## 跳转

### 打开 WiFi 设置
@type activity
@action android.settings.WIFI_SETTINGS
@flag NEW_TASK

### 分享文本
@type intent
@action android.intent.action.SEND
@mime text/plain
@extra text:string=你好
@extra force:bool=true
@extra count:int=3

### 无组件无 action
@type broadcast

## 其它

### 微信扫一扫
@type urlscheme
@package com.tencent.mm

```
weixin://scanqrcode
```
