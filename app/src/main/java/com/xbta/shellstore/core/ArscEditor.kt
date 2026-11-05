package com.xbta.shellstore.core

/**
 * resources.arsc 里包名的读写。
 *
 * 包名存在 RES_TABLE_PACKAGE_TYPE（0x0200）chunk 头部的「128 个 UTF-16 字符定长区」
 * （第 12 字节起，共 256 字节），所以**任意长度**的包名都能原地写入 + 余下清零，
 * 不需要等长替换，也不影响 chunk 内其它内容的偏移。
 *
 * 模板若没有任何自有资源，arsc 里就没有 package chunk，这时原样返回即可
 * （包名只由 manifest 决定）。
 */
object ArscEditor {

    private const val TYPE_TABLE = 0x0002
    private const val TYPE_PACKAGE = 0x0200
    private const val NAME_AREA = 256

    fun setPackage(data: ByteArray, newPkg: String): ByteArray {
        val out = data.copyOf()
        if (out.size < 12) return out
        var pos = u16(out, 2)                     // RES_TABLE headerSize（通常 12）
        while (pos + 8 <= out.size) {
            val type = u16(out, pos)
            val size = i32(out, pos + 4)
            if (size <= 0 || pos + size > out.size) break
            if (type == TYPE_PACKAGE) {
                val nameOff = pos + 12
                val bytes = newPkg.toByteArray(Charsets.UTF_16LE)
                val n = minOf(bytes.size, NAME_AREA - 2)
                System.arraycopy(bytes, 0, out, nameOff, n)
                for (i in nameOff + n until nameOff + NAME_AREA) out[i] = 0
                return out
            }
            pos += size
        }
        return out
    }

    /** 读回 arsc 里的包名（没有 package chunk 时返回空串） */
    fun readPackage(data: ByteArray): String {
        if (data.size < 12) return ""
        var pos = u16(data, 2)
        while (pos + 8 <= data.size) {
            val type = u16(data, pos)
            val size = i32(data, pos + 4)
            if (size <= 0 || pos + size > data.size) break
            if (type == TYPE_PACKAGE) {
                val nameOff = pos + 12
                var end = nameOff
                while (end < nameOff + NAME_AREA - 1 &&
                    (data[end].toInt() != 0 || data[end + 1].toInt() != 0)
                ) end += 2
                return String(data, nameOff, end - nameOff, Charsets.UTF_16LE)
            }
            pos += size
        }
        return ""
    }

    /** 顶层 chunk 类型（调试用） */
    fun chunkTypes(data: ByteArray): List<Int> {
        val list = mutableListOf<Int>()
        if (data.size < 12 || u16(data, 0) != TYPE_TABLE) return list
        var pos = u16(data, 2)
        while (pos + 8 <= data.size) {
            val type = u16(data, pos)
            val size = i32(data, pos + 4)
            if (size <= 0 || pos + size > data.size) break
            list.add(type)
            pos += size
        }
        return list
    }

    private fun u16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun i32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
}
