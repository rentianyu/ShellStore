package com.xbta.shellstore.core

import java.io.File
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
 * 只支持**等长**包名替换：`com.xbta.cmd.a0000000` → `com.xbta.cmd.a<8位hex>`，
 * 都是 21 个 ASCII 字符。等长意味着二进制 XML 的字符串池与 arsc 的定长包名区都能原地覆盖，
 * 不用重建任何偏移表——这是整件事里最容易出错的地方，用等长把风险压到最低。
 */
object ApkPatcher {

    private const val TEMPLATE_PKG = "com.xbta.cmd.a0000000"

    /** resources.arsc 里包名区的固定长度：128 个 UTF-16 字符 */
    private const val PKG_NAME_AREA = 256

    fun rewrite(
        input: File,
        output: File,
        newPkg: String,
        cmd: String,
        title: String
    ) {
        require(newPkg.length == TEMPLATE_PKG.length) {
            "包名长度必须与模板一致（${TEMPLATE_PKG.length}），当前 ${newPkg.length}"
        }
        ZipFile(input).use { zin ->
            ZipOutputStream(output.outputStream().buffered()).use { zout ->
                for (entry in zin.entries()) {
                    var bytes = zin.getInputStream(entry).readBytes()
                    when (entry.name) {
                        "AndroidManifest.xml" -> bytes = patchManifestPackage(bytes, newPkg)
                        "resources.arsc" -> bytes = patchArscPackage(bytes, newPkg)
                        "assets/cmd.txt" -> bytes = cmd.trim().toByteArray(Charsets.UTF_8)
                        "assets/title.txt" -> bytes = title.take(60).toByteArray(Charsets.UTF_8)
                    }
                    val ne = ZipEntry(entry.name)
                    ne.time = entry.time
                    if (entry.method == ZipEntry.STORED) {
                        ne.method = ZipEntry.STORED
                        ne.size = bytes.size.toLong()
                        ne.compressedSize = bytes.size.toLong()
                        ne.crc = CRC32().apply { update(bytes) }.value
                    } else {
                        ne.method = ZipEntry.DEFLATED
                    }
                    zout.putNextEntry(ne)
                    zout.write(bytes)
                    zout.closeEntry()
                }
            }
        }
    }

    /** 二进制 XML（AXML）的字符串池里替换包名，等长原地覆盖 */
    fun patchManifestPackage(data: ByteArray, newPkg: String): ByteArray {
        val buf = data.copyOf()
        var pos = u16(buf, 2)          // 文件头 headerSize
        while (pos + 8 <= buf.size) {
            val type = u16(buf, pos)
            val size = i32(buf, pos + 4)
            if (type == 0x0001) {      // RES_STRING_POOL_TYPE
                patchStringPool(buf, pos, u16(buf, pos + 2), newPkg)
                return buf
            }
            if (size <= 0) break
            pos += size
        }
        return buf
    }

    private fun patchStringPool(buf: ByteArray, chunkStart: Int, headerSize: Int, newPkg: String) {
        val stringCount = i32(buf, chunkStart + 8)
        val flags = i32(buf, chunkStart + 16)
        val stringsStart = i32(buf, chunkStart + 20)
        val isUtf8 = (flags and 0x100) != 0     // FLAG_UTF8
        val base = chunkStart + stringsStart
        for (i in 0 until stringCount) {
            val off = i32(buf, chunkStart + headerSize + i * 4)
            val p = base + off
            if (p < 0 || p >= buf.size) continue
            if (isUtf8) {
                var idx = p
                var len = buf[idx].toInt() and 0xFF
                if (len and 0x80 != 0) {
                    len = ((len and 0x7F) shl 8) or (buf[idx + 1].toInt() and 0xFF)
                    idx += 1
                }
                val start = idx + 1
                if (start + len > buf.size) continue
                if (String(buf, start, len, Charsets.UTF_8) == TEMPLATE_PKG) {
                    val n = newPkg.toByteArray(Charsets.UTF_8)
                    System.arraycopy(n, 0, buf, start, minOf(n.size, len))
                    return
                }
            } else {
                val len = u16(buf, p)
                val start = p + 2
                if (start + len * 2 > buf.size) continue
                if (String(buf, start, len * 2, Charsets.UTF_16LE) == TEMPLATE_PKG) {
                    val n = newPkg.toByteArray(Charsets.UTF_16LE)
                    System.arraycopy(n, 0, buf, start, minOf(n.size, len * 2))
                    return
                }
            }
        }
    }

    /** resources.arsc 的 package chunk 里替换包名（128 个 UTF-16 字符定长区） */
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
                    System.arraycopy(bytes, 0, buf, nameOff, bytes.size)
                    for (i in nameOff + bytes.size until nameOff + PKG_NAME_AREA) buf[i] = 0
                }
                return buf
            }
            if (size <= 0) break
            pos += size
        }
        return buf
    }

    /** 从 arsc 里读回包名，仅用于验证 */
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

    private fun u16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun i32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
}
