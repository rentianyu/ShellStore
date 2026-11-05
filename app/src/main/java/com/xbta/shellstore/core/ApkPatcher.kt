package com.xbta.shellstore.core

import java.io.File
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * APK 二进制改写：改包名 + 替换 assets 文件。
 *
 * 纯 Kotlin、无 android 依赖 —— 这样可以拿真实的模板 APK 在桌面上跑同一份代码做验证
 * （沙箱没有 Android SDK，只有这种方式能证明算法正确）。
 *
 * 只支持**等长**包名替换：`com.xbta.cmd.a0000000` → `com.xbta.cmd.a<7位hex>`，
 * 都是 21 个 ASCII 字符。等长意味着二进制 XML 的字符串池与 arsc 的定长包名区都能原地覆盖，
 * 不用重建任何偏移表——这是整件事里最容易出错的地方，用等长把风险压到最低。
 */
object ApkPatcher {

    /** 模板的 applicationId，长度就是导出包名的长度（21） */
    const val TEMPLATE_PKG = "com.xbta.cmd.a0000000"

    /** resources.arsc 里包名区的固定长度：128 个 UTF-16 字符 */
    private const val PKG_NAME_AREA = 256

    private const val HEX = "0123456789abcdef"

    /**
     * 生成一个与模板等长的新包名。
     *
     * 长度由模板反推，不写死数字 —— 之前把「a + 8 位 hex」当成了 9 位，
     * 结果永远是 22 与模板 21 对不上，导出直接失败。
     * 末段以字母开头是为了满足 Java 包名规范（段不能用数字开头）。
     */
    fun newPackage(rand: Random = Random()): String {
        val head = TEMPLATE_PKG.substringBeforeLast('.') + "."   // com.xbta.cmd.
        val need = TEMPLATE_PKG.length - head.length - 1          // 21 - 13 - 1 = 7
        val sb = StringBuilder(TEMPLATE_PKG.length)
        sb.append(head).append('a')
        repeat(need) { sb.append(HEX[rand.nextInt(HEX.length)]) }
        val pkg = sb.toString()
        check(pkg.length == TEMPLATE_PKG.length) {
            "生成包名长度 ${pkg.length} 与模板 ${TEMPLATE_PKG.length} 不一致：$pkg"
        }
        return pkg
    }

    fun rewrite(
        input: File,
        output: File,
        newPkg: String,
        cmd: String,
        title: String,
        oldPkg: String = TEMPLATE_PKG
    ) {
        require(newPkg.length == oldPkg.length) {
            "包名长度必须与模板一致（${oldPkg.length}），当前 ${newPkg.length}"
        }
        var manifestHits = 0
        var arscOffset = -1L
        val counting = CountingStream(output.outputStream().buffered())
        ZipFile(input).use { zin ->
            ZipOutputStream(counting).use { zout ->
                for (entry in zin.entries()) {
                    var bytes = zin.getInputStream(entry).readBytes()
                    when (entry.name) {
                        "AndroidManifest.xml" -> {
                            val r = patchManifestPackage(bytes, oldPkg, newPkg)
                            bytes = r.first
                            manifestHits = r.second
                        }
                        "resources.arsc" -> bytes = patchArscPackage(bytes, newPkg)
                        "assets/cmd.txt" -> bytes = cmd.trim().toByteArray(Charsets.UTF_8)
                        "assets/title.txt" -> bytes = title.take(60).trim().toByteArray(Charsets.UTF_8)
                    }
                    val ne = ZipEntry(entry.name)
                    ne.time = entry.time
                    val nameLen = entry.name.toByteArray(Charsets.UTF_8).size
                    if (entry.method == ZipEntry.STORED) {
                        ne.method = ZipEntry.STORED
                        ne.size = bytes.size.toLong()
                        ne.compressedSize = bytes.size.toLong()
                        ne.crc = CRC32().apply { update(bytes) }.value
                        // 重建 zip 会打乱数据偏移：Android 11+ 要求 resources.arsc 未压缩且
                        // 4 字节对齐（.so 要求 4096），否则直接拒装。这里用 extra 字段补齐，
                        // 算法与 zipalign 一致（extra 至少放得下 4 字节头）。
                        val align = if (entry.name.endsWith(".so")) 4096 else 4
                        alignExtra(counting.count + 30L + nameLen, align)?.let { ne.setExtra(it) }
                    } else {
                        ne.method = ZipEntry.DEFLATED
                    }
                    if (entry.name == "resources.arsc") {
                        arscOffset = counting.count + 30L + nameLen +
                            (ne.extra?.size ?: 0)
                    }
                    zout.putNextEntry(ne)
                    zout.write(bytes)
                    zout.closeEntry()
                }
            }
        }
        // 没替换到就说明模板结构变了，继续下去会导出一个「包名没改」的 APK，
        // 装第二个会直接覆盖第一个，比失败更难排查
        check(manifestHits > 0) { "模板 manifest 里没找到包名 $oldPkg，无法改写" }
        if (arscOffset >= 0) {
            check(arscOffset % 4 == 0L) { "resources.arsc 未对齐（偏移 $arscOffset），导出后会装不上" }
        }
    }

    /** 计算让数据区落到 alignment 边界的 extra 字段；已在边界上则返回 null */
    private fun alignExtra(offsetWithoutExtra: Long, alignment: Int): ByteArray? {
        val r = ((alignment - (offsetWithoutExtra % alignment).toInt()) % alignment)
        if (r == 0) return null
        // extra 至少要能放下 4 字节头，不足就补满一整个 alignment（不改变对齐结果）
        val pad = if (r < 4) r + alignment else r
        val b = ByteArray(pad)
        b[0] = 0x35            // Android ZIP alignment extra field id = 0xd935
        b[1] = 0xD9.toByte()
        val sz = pad - 4
        b[2] = (sz and 0xFF).toByte()
        b[3] = ((sz shr 8) and 0xFF).toByte()
        return b
    }

    /** 统计已写入字节数，用来算当前条目在 zip 里的起始偏移 */
    private class CountingStream(inner: java.io.OutputStream) : java.io.FilterOutputStream(inner) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    /**
     * 二进制 XML（AXML）的字符串池里替换包名，等长原地覆盖。
     * @return (改写后的字节, 命中次数)
     */
    fun patchManifestPackage(data: ByteArray, oldPkg: String, newPkg: String): Pair<ByteArray, Int> {
        val buf = data.copyOf()
        var pos = u16(buf, 2)          // 文件头 headerSize
        while (pos + 8 <= buf.size) {
            val type = u16(buf, pos)
            val size = i32(buf, pos + 4)
            if (type == 0x0001) {      // RES_STRING_POOL_TYPE
                return buf to patchStringPool(buf, pos, u16(buf, pos + 2), oldPkg, newPkg)
            }
            if (size <= 0) break
            pos += size
        }
        return buf to 0
    }

    /** 字符串池里把所有等于 oldPkg 的条目原地覆盖成 newPkg，返回命中数 */
    private fun patchStringPool(
        buf: ByteArray,
        chunkStart: Int,
        headerSize: Int,
        oldPkg: String,
        newPkg: String
    ): Int {
        if (oldPkg.length != newPkg.length) return 0
        val stringCount = i32(buf, chunkStart + 8)
        val flags = i32(buf, chunkStart + 16)
        val stringsStart = i32(buf, chunkStart + 20)
        val isUtf8 = (flags and 0x100) != 0     // FLAG_UTF8
        val base = chunkStart + stringsStart
        var hits = 0
        for (i in 0 until stringCount) {
            val off = i32(buf, chunkStart + headerSize + i * 4)
            val p = base + off
            if (p < 0 || p >= buf.size) continue
            if (isUtf8) {
                // UTF-8 串结构：[utf16长度][utf8长度][字节][0]，两个长度都是 1~2 字节变长
                var idx = p
                var skip = buf[idx].toInt() and 0xFF
                idx++
                if (skip and 0x80 != 0) idx++
                var len = buf[idx].toInt() and 0xFF
                idx++
                if (len and 0x80 != 0) {
                    len = ((len and 0x7F) shl 8) or (buf[idx].toInt() and 0xFF)
                    idx++
                }
                val start = idx
                if (start + len > buf.size) continue
                if (String(buf, start, len, Charsets.UTF_8) == oldPkg) {
                    val n = newPkg.toByteArray(Charsets.UTF_8)
                    if (n.size == len) {
                        System.arraycopy(n, 0, buf, start, len)
                        hits++
                    }
                }
            } else {
                var len = u16(buf, p)
                var start = p + 2
                if (len and 0x8000 != 0) {          // 长串：高 16 位另存
                    len = ((len and 0x7FFF) shl 16) or u16(buf, p + 2)
                    start = p + 4
                }
                if (start + len * 2 > buf.size) continue
                if (String(buf, start, len * 2, Charsets.UTF_16LE) == oldPkg) {
                    val n = newPkg.toByteArray(Charsets.UTF_16LE)
                    if (n.size == len * 2) {
                        System.arraycopy(n, 0, buf, start, n.size)
                        hits++
                    }
                }
            }
        }
        return hits
    }

    /**
     * resources.arsc 的 package chunk 里替换包名（128 个 UTF-16 字符定长区）。
     * 模板没有自有资源时 arsc 里没有 package chunk，这时原样返回即可。
     */
    fun patchArscPackage(data: ByteArray, newPkg: String): ByteArray {
        val buf = data.copyOf()
        var pos = u16(buf, 2)          // RES_TABLE headerSize（通常 12）
        while (pos + 8 <= buf.size) {
            val type = u16(buf, pos)
            val size = i32(buf, pos + 4)
            if (type == 0x0200) {      // RES_TABLE_PACKAGE_TYPE
                val nameOff = pos + 12 // id(4) 之后就是包名区
                if (nameOff + PKG_NAME_AREA <= buf.size) {
                    val bytes = newPkg.toByteArray(Charsets.UTF_16LE)
                    System.arraycopy(bytes, 0, buf, nameOff, minOf(bytes.size, PKG_NAME_AREA))
                    for (i in nameOff + bytes.size until nameOff + PKG_NAME_AREA) buf[i] = 0
                }
                return buf
            }
            if (size <= 0) break
            pos += size
        }
        return buf
    }

    /** 从 arsc 里读回包名，仅用于验证（模板无 package chunk 时返回空串） */
    fun readArscPackage(data: ByteArray): String {
        var pos = u16(data, 2)
        while (pos + 8 <= data.size) {
            val type = u16(data, pos)
            val size = i32(data, pos + 4)
            if (type == 0x0200) {
                val nameOff = pos + 12
                var end = nameOff
                // UTF-16LE：两个字节都为 0 才是结束（只判低字节会把 'a'(0x61,0x00) 当终止）
                while (end < nameOff + PKG_NAME_AREA - 1 &&
                    (data[end].toInt() != 0 || data[end + 1].toInt() != 0)
                ) end += 2
                return String(data, nameOff, end - nameOff, Charsets.UTF_16LE)
            }
            if (size <= 0) break
            pos += size
        }
        return ""
    }

    /** 从二进制 manifest 的字符串池里挑出包名，仅用于验证 */
    fun readManifestPackage(data: ByteArray, expectPrefix: String = "com.xbta.cmd."): String {
        var pos = u16(data, 2)
        while (pos + 8 <= data.size) {
            val type = u16(data, pos)
            val size = i32(data, pos + 4)
            if (type == 0x0001) {
                val headerSize = u16(data, pos + 2)
                val count = i32(data, pos + 8)
                val flags = i32(data, pos + 16)
                val stringsStart = i32(data, pos + 20)
                val base = pos + stringsStart
                val isUtf8 = (flags and 0x100) != 0
                for (i in 0 until count) {
                    val off = i32(data, pos + headerSize + i * 4)
                    val p = base + off
                    if (p < 0 || p >= data.size) continue
                    val s = if (isUtf8) {
                        var idx = p
                        var skip = data[idx].toInt() and 0xFF; idx++
                        if (skip and 0x80 != 0) idx++
                        var len = data[idx].toInt() and 0xFF; idx++
                        if (len and 0x80 != 0) {
                            len = ((len and 0x7F) shl 8) or (data[idx].toInt() and 0xFF); idx++
                        }
                        if (idx + len > data.size) continue
                        String(data, idx, len, Charsets.UTF_8)
                    } else {
                        var len = u16(data, p)
                        var start = p + 2
                        if (len and 0x8000 != 0) {
                            len = ((len and 0x7FFF) shl 16) or u16(data, p + 2); start = p + 4
                        }
                        if (start + len * 2 > data.size) continue
                        String(data, start, len * 2, Charsets.UTF_16LE)
                    }
                    // 只要「以 com.xbta.cmd. 开头且没有更深的点」的那一条，即纯包名
                    if (s.startsWith(expectPrefix) && s.removePrefix(expectPrefix).none { it == '.' }) return s
                }
                return ""
            }
            if (size <= 0) break
            pos += size
        }
        return ""
    }

    private fun u16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun i32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
}
