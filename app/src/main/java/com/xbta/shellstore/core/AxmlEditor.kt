package com.xbta.shellstore.core

import java.io.ByteArrayOutputStream

/**
 * 改写二进制 AndroidManifest.xml 里的字符串。
 *
 * 模板 APK 的 manifest 里预埋了几个标记：包名 `com.xbta.cmd.tpl`、应用名占位符
 * `SSTPL_LABEL_PLACEHOLDER_000000`、Shizuku provider 的 authorities `com.xbta.cmd.tpl.shizuku`。
 * 导出时把它们换成用户要的值。
 *
 * 做法是**只重建「字符串池」chunk**，节点流原样保留 —— 节点里存的是字符串索引，
 * 池内条目的数量与顺序都不变，索引自然还对得上；字符串本身变长变短都无所谓。
 * 这是「支持任意长度名称/包名」的关键：等长替换那套做不到自定义名称。
 *
 * 重建时清掉 SORTED 标志（重排后不再有序），只保留 UTF8 位。
 */
object AxmlEditor {

    private const val TYPE_STRING_POOL = 0x0001
    private const val UTF8_FLAG = 0x100

    /** 按 map 里的「旧值 → 新值」替换字符串池内容，返回新的 manifest 字节 */
    fun replaceStrings(input: ByteArray, map: Map<String, String>): ByteArray {
        if (map.isEmpty()) return input
        var pos = u16(input, 2)
        var poolStart = -1
        var poolSize = 0
        while (pos + 8 <= input.size) {
            val type = u16(input, pos)
            val size = i32(input, pos + 4)
            if (size <= 0) break
            if (type == TYPE_STRING_POOL) {
                poolStart = pos
                poolSize = size
                break
            }
            pos += size
        }
        require(poolStart >= 0) { "AXML 解析失败：未找到字符串池" }

        val strings = readPoolStrings(input, poolStart, poolSize)
        var hits = 0
        for (i in strings.indices) {
            val nv = map[strings[i]]
            if (nv != null && nv != strings[i]) {
                strings[i] = nv
                hits++
            }
        }
        if (hits == 0) return input          // 没有命中就原样返回，由调用方判断

        // 重新生成字符串数据区（每个串：长度前缀 + 内容 + 结束符）
        val headerSize = u16(input, poolStart + 2)
        val styleCount = i32(input, poolStart + 12)
        val flags = i32(input, poolStart + 16)
        val stringsStart = i32(input, poolStart + 20)
        val stylesStart = i32(input, poolStart + 24)
        val utf8 = (flags and UTF8_FLAG) != 0

        val data = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        for (i in strings.indices) {
            offsets[i] = data.size()
            if (utf8) writeUtf8(data, strings[i]) else writeUtf16(data, strings[i])
        }
        while (data.size() % 4 != 0) data.write(0)
        val stringsBytes = data.toByteArray()

        val stylesBytes = if (styleCount > 0 && stylesStart > 0 && stylesStart < poolSize) {
            input.copyOfRange(poolStart + stylesStart, poolStart + poolSize)
        } else {
            ByteArray(0)
        }

        val newPool = ByteArrayOutputStream()
        val newHeader = 28
        val newStringsStart = newHeader + 4 * strings.size + 4 * styleCount
        val newStylesStart = if (stylesBytes.isEmpty()) 0 else newStringsStart + stringsBytes.size
        val newSize = newStringsStart + stringsBytes.size + stylesBytes.size
        writeShort(newPool, TYPE_STRING_POOL)
        writeShort(newPool, newHeader)
        writeInt(newPool, newSize)
        writeInt(newPool, strings.size)
        writeInt(newPool, styleCount)
        writeInt(newPool, if (utf8) UTF8_FLAG else 0)      // 清掉 SORTED
        writeInt(newPool, newStringsStart)
        writeInt(newPool, newStylesStart)
        for (o in offsets) writeInt(newPool, o)
        if (styleCount > 0) {
            for (i in 0 until styleCount) {
                writeInt(newPool, i32(input, poolStart + headerSize + strings.size * 4 + i * 4))
            }
        }
        newPool.write(stringsBytes)
        newPool.write(stylesBytes)
        val poolBytes = newPool.toByteArray()

        val out = ByteArrayOutputStream()
        out.write(input, 0, poolStart)
        out.write(poolBytes)
        out.write(input, poolStart + poolSize, input.size - (poolStart + poolSize))
        val result = out.toByteArray()
        // 文件头的总长度要跟着改
        val total = result.size
        result[4] = (total and 0xff).toByte()
        result[5] = ((total ushr 8) and 0xff).toByte()
        result[6] = ((total ushr 16) and 0xff).toByte()
        result[7] = ((total ushr 24) and 0xff).toByte()
        return result
    }

    /** 读出字符串池里的所有字符串（验证用） */
    fun readStrings(input: ByteArray): List<String> {
        var pos = u16(input, 2)
        while (pos + 8 <= input.size) {
            val type = u16(input, pos)
            val size = i32(input, pos + 4)
            if (size <= 0) break
            if (type == TYPE_STRING_POOL) return readPoolStrings(input, pos, size)
            pos += size
        }
        return emptyList()
    }

    /** 从字符串池里挑出「包名」那一条（以 prefix 开头且不再有更深的点） */
    fun findPackage(manifest: ByteArray, prefix: String = "com.xbta.cmd."): String? =
        readStrings(manifest).firstOrNull {
            it.startsWith(prefix) && it.removePrefix(prefix).none { c -> c == '.' }
        }

    // ---------------- 内部 ----------------

    private fun readPoolStrings(input: ByteArray, poolStart: Int, poolSize: Int): MutableList<String> {
        val headerSize = u16(input, poolStart + 2)
        val stringCount = i32(input, poolStart + 8)
        val flags = i32(input, poolStart + 16)
        val stringsStart = i32(input, poolStart + 20)
        val utf8 = (flags and UTF8_FLAG) != 0
        require(stringCount in 0..200000) { "AXML 解析失败：字符串数量异常 $stringCount" }
        val out = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val off = i32(input, poolStart + headerSize + i * 4)
            val p = poolStart + stringsStart + off
            if (p < 0 || p >= input.size) {
                out.add("")
                continue
            }
            out.add(if (utf8) readUtf8(input, p) else readUtf16(input, p))
        }
        return out
    }

    private fun readUtf8(d: ByteArray, start: Int): String {
        var p = start
        val b0 = d[p].toInt() and 0xff
        if (b0 and 0x80 != 0) p += 2 else p += 1          // utf16 长度（跳过）
        var len8: Int
        val b1 = d[p].toInt() and 0xff
        if (b1 and 0x80 != 0) {
            len8 = ((b1 and 0x7f) shl 8) or (d[p + 1].toInt() and 0xff)
            p += 2
        } else {
            len8 = b1
            p += 1
        }
        if (len8 < 0 || p + len8 > d.size) return ""
        return String(d, p, len8, Charsets.UTF_8)
    }

    private fun readUtf16(d: ByteArray, start: Int): String {
        var len = u16(d, start)
        var p = start + 2
        if (len and 0x8000 != 0) {
            len = ((len and 0x7FFF) shl 16) or u16(d, start + 2)
            p = start + 4
        }
        if (len < 0 || p + len * 2 > d.size) return ""
        return String(d, p, len * 2, Charsets.UTF_16LE)
    }

    private fun writeUtf8(o: ByteArrayOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        writeLen8(o, s.length)
        writeLen8(o, b.size)
        o.write(b)
        o.write(0)
    }

    private fun writeUtf16(o: ByteArrayOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_16LE)
        val len = b.size / 2
        if (len > 0x7FFF) {
            writeShort(o, (len ushr 16) or 0x8000)
            writeShort(o, len and 0xFFFF)
        } else {
            writeShort(o, len)
        }
        o.write(b)
        writeShort(o, 0)
    }

    private fun writeLen8(o: ByteArrayOutputStream, len: Int) {
        if (len > 0x7f) {
            o.write(((len ushr 8) and 0x7f) or 0x80)
            o.write(len and 0xff)
        } else {
            o.write(len)
        }
    }

    private fun u16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun i32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun writeShort(o: ByteArrayOutputStream, v: Int) {
        o.write(v and 0xff)
        o.write((v ushr 8) and 0xff)
    }

    private fun writeInt(o: ByteArrayOutputStream, v: Int) {
        o.write(v and 0xff)
        o.write((v ushr 8) and 0xff)
        o.write((v ushr 16) and 0xff)
        o.write((v ushr 24) and 0xff)
    }
}
