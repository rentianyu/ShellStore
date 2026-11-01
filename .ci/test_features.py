"""本轮两个功能的纯逻辑复刻断言（Kotlin 逻辑等价实现，用来在推 CI 前抓算法错误）。"""

fails = []


def ck(name, cond):
    print(("PASS " if cond else "FAIL ") + name)
    if not cond:
        fails.append(name)


# ---------- 1. 收藏排序：order 降序，同 order 按 savedAt 降序 ----------
def items_in(items, folder=None):
    sel = [i for i in items if i["folder"] == folder]
    return [i["id"] for i in sorted(sel, key=lambda x: (-x["order"], -x["savedAt"]))]


old_data = [
    {"id": "a", "folder": None, "order": 0, "savedAt": 300},
    {"id": "b", "folder": None, "order": 0, "savedAt": 200},
    {"id": "c", "folder": None, "order": 0, "savedAt": 100},
]
ck("老数据（order 全 0）按 savedAt 排", items_in(old_data) == ["a", "b", "c"])

mixed = [
    {"id": "a", "folder": None, "order": 0, "savedAt": 300},
    {"id": "b", "folder": None, "order": 5, "savedAt": 100},
    {"id": "c", "folder": None, "order": 0, "savedAt": 200},
]
ck("手动排过的（order>0）排最前", items_in(mixed) == ["b", "a", "c"])

ck("不同文件夹互不干扰", items_in(
    [{"id": "x", "folder": "f1", "order": 9, "savedAt": 1},
     {"id": "y", "folder": None, "order": 1, "savedAt": 1}]) == ["y"])


# ---------- 2. reorderFavs：位次从大到小赋（第一个最大） ----------
def reorder(items, folder, ordered_ids):
    pos = {fid: len(ordered_ids) - i for i, fid in enumerate(ordered_ids)}
    out = []
    for it in items:
        p = pos.get(it["id"])
        out.append(dict(it, order=p) if (p is not None and it["folder"] == folder) else it)
    return out


ids = ["c", "a", "b"]                    # 用户拖成 c,a,b
src = [{"id": "a", "folder": None, "order": 0, "savedAt": 300},
       {"id": "b", "folder": None, "order": 0, "savedAt": 200},
       {"id": "c", "folder": None, "order": 0, "savedAt": 100}]
after = reorder(src, None, ids)
ck("重排后顺序 = 拖拽结果", items_in(after) == ids)
ck("第一个位次最大", max(i["order"] for i in after) == 3)
ck("位次无重复", len({i["order"] for i in after}) == 3)

# 只重排当前文件夹，别的不动
two = [{"id": "a", "folder": "f1", "order": 0, "savedAt": 1},
       {"id": "b", "folder": "f2", "order": 0, "savedAt": 1}]
after2 = reorder(two, "f1", ["a"])
ck("只动本文件夹的 order", after2[0]["order"] == 1 and after2[1]["order"] == 0)

# 交换到底：两元素互换
two2 = [{"id": "a", "folder": None, "order": 2, "savedAt": 1},
        {"id": "b", "folder": None, "order": 1, "savedAt": 1}]
ck("交换两项", items_in(reorder(two2, None, ["b", "a"])) == ["b", "a"])

# 新收藏插最前：top + 1
def add_fav(items, new_id, folder=None):
    top = max([i["order"] for i in items if i["folder"] == folder], default=0)
    return items + [{"id": new_id, "folder": folder, "order": top + 1, "savedAt": 999}]


ck("新收藏排最前", items_in(add_fav(after, "new"))[0] == "new")
ck("空文件夹新增 order=1", add_fav([], "z")[0]["order"] == 1)


# ---------- 3. highlightSpan：命中区间切分 ----------
def highlight_segments(text, kw):
    kw = kw.strip()
    if not kw or not text:
        return [(text, False)]
    lt, lk = text.lower(), kw.lower()
    out, i = [], 0
    while i < len(text):
        p = lt.find(lk, i)
        if p < 0:
            out.append((text[i:], False))
            break
        if p > i:
            out.append((text[i:p], False))
        end = min(p + len(kw), len(text))
        out.append((text[p:end], True))
        i = end
    return out


ck("单词命中", highlight_segments("git clone", "git") == [("git", True), (" clone", False)])
ck("大小写不敏感", highlight_segments("Git Clone", "git") == [("Git", True), (" Clone", False)])
ck("多次命中", [s for s, h in highlight_segments("abcabc", "abc") if h] == ["abc", "abc"])
ck("无命中整段原样", highlight_segments("hello", "zzz") == [("hello", False)])
ck("空关键词不高亮", highlight_segments("hello", "  ") == [("hello", False)])
ck("拼接后等于原文", "".join(s for s, _ in highlight_segments("pm list packages -3", "list")) == "pm list packages -3")
ck("命中在末尾", highlight_segments("pm list", "list")[-1] == ("list", True))
ck("中文命中", highlight_segments("查看电池状态", "电池") == [("查看", False), ("电池", True), ("状态", False)])


# ---------- 4. 搜索历史：去重（忽略大小写）+ 上限 10 ----------
SEARCH_MAX = 10


def add_history(hist, q):
    kw = q.strip()
    if len(kw) < 2:
        return hist
    return ([kw] + [h for h in hist if h.lower() != kw.lower()])[:SEARCH_MAX]


h = []
h = add_history(h, "git")
h = add_history(h, "pm")
ck("最新的在前", h == ["pm", "git"])
h = add_history(h, "GIT")
ck("大小写去重", h == ["GIT", "pm"])
ck("单字符不入历史", add_history(h, "g") == h)
ck("空白不入历史", add_history(h, "   ") == h)
for i in range(15):
    h = add_history(h, "kw%02d" % i)
ck("历史上限 10", len(h) == SEARCH_MAX and h[0] == "kw14")

print("\nFAILS:", len(fails))
for f in fails:
    print("  -", f)
raise SystemExit(1 if fails else 0)
