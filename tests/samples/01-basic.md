# 测试源
@author 小贝塔
@version 2026.11.1

## 应用管理

### 列出第三方应用
@risk safe
@need shell
@tag pm 包名
@param filter = 范围 | -3
  -3 只列第三方，-s 只列系统
@example pm list packages -3

```sh
pm list packages -3
```

### 强制停止应用
@risk caution
@need shell
@param pkg = 包名 | com.tencent.mm

```sh
am force-stop {pkg}
```
