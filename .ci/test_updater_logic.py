import re

DEV = re.compile(r"""ShellStore-([0-9][0-9.]*)-debug\.apk(?=["'<\s]|$)""")

def parts(v):
    v = v.strip().lstrip("vV")
    out = []
    for s in re.split(r"[.\-+]", v):
        num = ""
        for ch in s:
            if ch.isdigit():
                num += ch
            else:
                break
        out.append(int(num) if num else 0)
    return out

def is_newer(remote, local):
    a, b = parts(remote), parts(local)
    for i in range(max(len(a), len(b))):
        x = a[i] if i < len(a) else 0
        y = b[i] if i < len(b) else 0
        if x != y:
            return x > y
    return False

fails = []
def ck(name, cond):
    print(("PASS " if cond else "FAIL ") + name)
    if not cond:
        fails.append(name)

# 1) asset 名解析
for html, want in [
    ('href="/o/r/releases/download/dev-latest/ShellStore-0.12.0-debug.apk"', "0.12.0"),
    ('"name":"ShellStore-0.12.10-debug.apk"', "0.12.10"),
    ('ShellStore-1.0.0-debug.apk', "1.0.0"),
]:
    m = DEV.search(html)
    ck("parse %-12s -> %s" % (want, m.group(1) if m else None), m is not None and m.group(1) == want)

# 2) 不该匹配的
for html in [
    "app-debug.apk",
    "ShellStore-debug.apk",
    "ShellStore-v0.12.0-debug.apk",
    "ShellStore-0.12.0-release.apk",
    "ShellStore-0.12.0-debug.apk.bak",
]:
    ck("no-match %s" % html, DEV.search(html) is None)

# 3) 版本比较：debug 版自身 / 升级 / 降级
ck("same 0.12.0 vs 0.12.0(debug pkg)", not is_newer("0.12.0", "0.12.0"))
ck("same 0.12.0 vs 0.12.0-debug", not is_newer("0.12.0", "0.12.0-debug"))
ck("upgrade 0.12.1 > 0.12.0", is_newer("0.12.1", "0.12.0"))
ck("upgrade 0.13.0 > 0.12.9", is_newer("0.13.0", "0.12.9"))
ck("downgrade 0.11.0 < 0.12.0", not is_newer("0.11.0", "0.12.0"))
ck("numeric 0.12.10 > 0.12.9", is_newer("0.12.10", "0.12.9"))
ck("tag with v: v0.12.1 > 0.12.0", is_newer("v0.12.1", "0.12.0"))

# 4) 下载缓存文件名带版本 -> dev-latest 每次升级不会命中旧文件
def dest(version):
    return "update-%s.apk" % version
ck("dest changes with version", dest("0.12.0") != dest("0.12.1"))
ck("dest stable for release tag", dest("0.11.0") == dest("0.11.0"))

print("\nFAILS:", len(fails))
for f in fails:
    print("  -", f)
