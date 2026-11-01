#!/usr/bin/env python3
"""不下载整包，读出 APK 的包名 / 版本 / SDK / debuggable / label。

为什么需要它：CI 日志端点常断流、12MB 整包下载又慢，而交付验收真正要看的就是
「包名对不对、versionCode 有没有递增、versionName 对不对、是不是 debug 变体」。
APK 是 ZIP，这些信息全在 AndroidManifest.xml 里，用 HTTP Range 只取尾部
central directory + manifest 条目就够了（实测 12MB 的包只读 137KB）。

零第三方依赖（自带 AXML 二进制解析），本地文件与远端 URL 都支持。

用法:
  python3 apk_peek.py <apk路径|http(s) URL>
  python3 apk_peek.py <url> --json                # 机器可读，便于脚本断言
  python3 apk_peek.py <url> --expect-package com.x.y --min-code 15
  python3 apk_peek.py <url> --strings Shell        # 另从 resources.arsc 的全局字符串池里搜

退出码: 0 正常 / 2 Range 不支持 / 3 找不到 manifest / 4 断言不通过
"""
import argparse
import json
import struct
import sys
import urllib.request
import zipfile
import zlib

UA = "apk-peek/1.0 (android-app-dev)"

# ---------- AXML ----------

RES_STRING_POOL = 0x0001
RES_XML = 0x0003
RES_XML_START_ELEMENT = 0x0102
RES_TABLE = 0x0002


def _u16(b, o):
    return struct.unpack_from("<H", b, o)[0]


def _u32(b, o):
    return struct.unpack_from("<I", b, o)[0]


def _varint8(b, o):
    v = b[o]
    if v & 0x80:
        return ((v & 0x7F) << 8) | b[o + 1], o + 2
    return v, o + 1


def read_string_pool(b, off):
    """返回该 chunk 的字符串列表。off 指向 chunk 头（type=0x0001）。"""
    hsize = _u16(b, off + 2)
    str_count = _u32(b, off + 8)
    flags = _u32(b, off + 16)
    strings_start = _u32(b, off + 20)
    utf8 = bool(flags & 0x100)
    base = off + strings_start
    out = []
    for i in range(str_count):
        p = base + _u32(b, off + hsize + 4 * i)
        if utf8:
            _, p = _varint8(b, p)          # 字符数（UTF-16 计数），跳过
            blen, p = _varint8(b, p)       # 字节长度
            out.append(b[p:p + blen].decode("utf-8", "replace"))
        else:
            n = _u16(b, p)
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | _u16(b, p)
                p += 2
            out.append(b[p:p + n * 2].decode("utf-16-le", "replace"))
    return out


def typed_value(strings, dtype, data):
    if dtype == 0x03:                      # TYPE_STRING
        return strings[data] if data < len(strings) else None
    if dtype == 0x10:                      # TYPE_INT_DEC
        return data if data < 0x80000000 else data - 0x100000000
    if dtype == 0x11:                      # TYPE_INT_HEX
        return "0x%x" % data
    if dtype == 0x12:                      # TYPE_INT_BOOLEAN
        return data != 0
    if dtype == 0x01:                      # TYPE_REFERENCE
        return "@0x%08X" % data
    return data


def parse_axml(b):
    """解析 AndroidManifest.xml，返回 {元素名: {属性名: 值}} 的一层收集结果。"""
    if _u16(b, 0) != RES_XML:
        raise ValueError("不是 AXML（magic=0x%04x）" % _u16(b, 0))
    total = _u32(b, 4)
    i = _u16(b, 2)
    strings = []
    found = {}
    while i < total and i + 8 <= len(b):
        ctype = _u16(b, i)
        csize = _u32(b, i + 4)
        if csize <= 0:
            break
        if ctype == RES_STRING_POOL:
            strings = read_string_pool(b, i)
        elif ctype == RES_XML_START_ELEMENT:
            name = strings[_u32(b, i + 20)] if strings and _u32(b, i + 20) < len(strings) else "?"
            attr_start = _u16(b, i + 24)
            attr_size = _u16(b, i + 26)
            attr_count = _u16(b, i + 28)
            base = i + 16 + attr_start
            attrs = {}
            for k in range(attr_count):
                p = base + k * attr_size
                if p + 20 > len(b):
                    break
                a_name = _u32(b, p + 4)
                if not strings or a_name >= len(strings):
                    continue
                dtype = b[p + 15]
                data = _u32(b, p + 16)
                attrs[strings[a_name]] = typed_value(strings, dtype, data)
            if name not in found:
                found[name] = attrs
        i += csize
    return found


# ---------- 取包（本地 / 远端） ----------

class RemoteZip:
    """只支持单条目按需 Range 读取的极简 ZIP 读取器。"""

    def __init__(self, url, timeout=40):
        self.url = url
        self.timeout = timeout
        self.tail_start = self.total = 0
        self.entries = {}
        self._load_cd()

    def _fetch(self, rng):
        req = urllib.request.Request(self.url, headers={
            "User-Agent": UA, "Range": rng, "Accept-Encoding": "identity"})
        with urllib.request.urlopen(req, timeout=self.timeout) as r:
            return r.read(), r.headers

    def _load_cd(self):
        _, h = self._fetch("bytes=0-0")
        cr = h.get("Content-Range") or ""
        if "/" not in cr:
            raise RuntimeError("服务端不支持 Range（Content-Range 缺失）")
        self.total = int(cr.rsplit("/", 1)[1])
        self.tail_start = max(0, self.total - 131072)
        # 注意：suffix 形式（bytes=-N）很多反代返回 501，必须用绝对范围
        tail, _ = self._fetch("bytes=%d-%d" % (self.tail_start, self.total - 1))
        idx = tail.rfind(b"PK\x05\x06")
        if idx < 0:
            raise RuntimeError("尾部找不到 EOCD")
        cd_size, cd_off = struct.unpack_from("<II", tail, idx + 12)
        if cd_off >= self.tail_start:
            cd = tail[cd_off - self.tail_start:]
        else:
            cd, _ = self._fetch("bytes=%d-%d" % (cd_off, cd_off + cd_size - 1))
        i = 0
        while i < len(cd) - 4 and cd[i:i + 4] == b"PK\x01\x02":
            (_s, _vm, _vn, _fl, method, _mt, _md, _crc, csize, usize,
             nlen, elen, clen, _dk, _ia, _ea, lho) = struct.unpack_from(
                "<IHHHHHHIIIHHHHHII", cd, i)
            name = cd[i + 46:i + 46 + nlen].decode("utf-8", "replace")
            self.entries[name] = dict(method=method, csize=csize, usize=usize, lho=lho)
            i += 46 + nlen + elen + clen

    def read(self, name, limit=None):
        e = self.entries.get(name)
        if not e:
            return None
        csize = e["csize"] if limit is None else min(e["csize"], limit)
        chunk, _ = self._fetch("bytes=%d-%d" % (e["lho"], e["lho"] + 30 + 1024 + csize))
        nlen, elen = struct.unpack_from("<HH", chunk, 26)
        raw = chunk[30 + nlen + elen: 30 + nlen + elen + csize]
        if e["method"] == 8:
            d = zlib.decompressobj(-15)
            return d.decompress(raw)
        return raw


def arsc_strings(data, max_bytes=8_000_000):
    """返回 (字符串列表, 是否因读取范围不足而截断)"""
    if not data:
        return [], False
    data = data[:max_bytes]
    if _u16(data, 0) != RES_TABLE:
        return [], False
    i = _u16(data, 2)
    total = _u32(data, 4)
    while i < total and i + 8 <= len(data):
        ctype = _u16(data, i)
        csize = _u32(data, i + 4)
        if csize <= 0:
            break
        if ctype == RES_STRING_POOL:
            if i + csize > len(data):
                return [], True
            return read_string_pool(data, i), False
        i += csize
    return [], False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("target")
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--expect-package")
    ap.add_argument("--min-code", type=int)
    ap.add_argument("--strings", metavar="PATTERN", help="从 resources.arsc 全局字符串池里搜")
    ap.add_argument("--strings-full", action="store_true",
                    help="--strings 时读 1MB 压缩数据（默认 256KB，包大时字符串池可能超出）")
    a = ap.parse_args()

    local = None if a.target.startswith("http") else a.target
    remote = None if local else a.target
    if local:
        manifest = zipfile.ZipFile(local).read("AndroidManifest.xml")
        arsc = zipfile.ZipFile(local).read("resources.arsc") if "resources.arsc" in zipfile.ZipFile(local).namelist() else None
    else:
        rz = RemoteZip(remote)
        manifest = rz.read("AndroidManifest.xml")
        arsc = rz.read("resources.arsc", limit=(1048576 if a.strings_full else 262144)) if a.strings else None

    if not manifest:
        print("找不到 AndroidManifest.xml", file=sys.stderr)
        return 3
    tree = parse_axml(manifest)
    man = tree.get("manifest", {})
    sdk = tree.get("uses-sdk", {})
    app = tree.get("application", {})

    info = {
        "package": man.get("package"),
        "versionCode": man.get("versionCode"),
        "versionName": man.get("versionName"),
        "compileSdkVersion": man.get("compileSdkVersion"),
        "minSdkVersion": sdk.get("minSdkVersion"),
        "targetSdkVersion": sdk.get("targetSdkVersion"),
        "debuggable": app.get("debuggable"),
        "label": app.get("label"),
        "applicationName": app.get("name"),
    }
    if a.strings:
        strs, trunc = arsc_strings(arsc)
        if trunc and not local and not a.strings_full:
            # 256KB 解压后仍不够覆盖字符串池时，自动加到 1MB 重试一次
            arsc = rz.read("resources.arsc", limit=1048576)
            strs, trunc = arsc_strings(arsc)
        if trunc:
            print("（字符串池超出已读范围，拿到的结果可能不全）", file=sys.stderr)
        info["strings"] = [x for x in strs if a.strings.lower() in x.lower()][:20]

    if a.json:
        print(json.dumps(info, ensure_ascii=False, indent=1))
    else:
        for k, v in info.items():
            if k == "strings":
                print("%-18s %s" % (k, v))
            else:
                print("%-18s %s" % (k, v))

    bad = []
    if a.expect_package and info["package"] != a.expect_package:
        bad.append("package %r != %r" % (info["package"], a.expect_package))
    if a.min_code is not None and (info["versionCode"] or 0) < a.min_code:
        bad.append("versionCode %r < %d" % (info["versionCode"], a.min_code))
    if bad:
        for b in bad:
            print("ASSERT FAIL: " + b, file=sys.stderr)
        return 4
    if a.expect_package or a.min_code is not None:
        print("ASSERT OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
