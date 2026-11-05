## 自动化脚本

### 收到短信自动转发到微信
@risk danger
@need shell
@param key = 机器人key | 改成你自己的

```sh
time=$(date "+%Y-%m-%d %H:%M:%S")
hh="\n\n"
sms="$(content query --uri content://sms/inbox --projection address:body --sort "_id DESC"|sed '/Row: 1/,$d')"
qqq="${time}${hh}${sms}"
curl 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key={key}' \
-H 'Content-Type: application/json' \
-d '
{
"msgtype": "text",
"text": {
"content": "'"$qqq"'"
}
}'
```

### 特殊符号密集
```sh
echo "quote:\" and hash:# and colon: end" | sed 's/[{}|]/X/g'
```
