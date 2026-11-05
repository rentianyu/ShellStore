package com.xbta.shellstore.core

import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * APK（本质是 zip）结构完整性检查。
 *
 * 只用 JDK 的 zip 实现，不碰 android.*，所以能在桌面用 kotlinc 编译验证。
 * 判据分三步，任何一步不过都说明「包不完整/不是 APK」：
 *   1. 体积在合理区间（太小＝断流，太大＝拿到别的东西）
 *   2. 文件尾部有 ZIP 结束记录（EOCD）—— 断流截断的包几乎都过不了这关
 *   3. 逐个条目完整读一遍（ZipFile 会顺带校验 CRC），且必须含 AndroidManifest.xml
 *
 * 为什么不能只看文件头 PK\x03\x04：断流时头部完好、长度也可能上 MB，
 * 只看头会把半个包当成功，用户装的时候才报「解析包时出现问题」。
 */
object ZipCheck {

    private const val MIN_SIZE = 1_000_000L
    private const val MAX_SIZE = 300L * 1024 * 1024

    /**
     * 通过返回 null，失败返回可读原因。
     * @param minSize 体积下限（主 App 更新包用默认 1MB；导出的小 APK 传 1KB）
     */
    fun verify(f: File, minSize: Long = MIN_SIZE): String? {
        if (!f.exists()) return "文件不存在"
        if (!f.isFile) return "不是文件"
        val len = f.length()
        if (len < minSize) return "文件只有 ${len} 字节，下载不完整"
        if (len > MAX_SIZE) return "文件异常偏大（${len / 1024 / 1024}MB）"

        if (!hasEndRecord(f)) return "缺少 ZIP 结束记录，文件被截断"

        return runCatching {
            ZipFile(f).use { z ->
                val entries = z.entries()
                var files = 0
                var hasManifest = false
                var bad: String? = null
                val buf = ByteArray(64 * 1024)
                val crc = CRC32()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.name == "AndroidManifest.xml") hasManifest = true
                    if (e.isDirectory) continue
                    files++
                    crc.reset()
                    var written = 0L
                    z.getInputStream(e).use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            crc.update(buf, 0, n)
                            written += n
                        }
                    }
                    // 自己算 CRC 与条目声明值比对。不能指望 ZipFile 在流末尾自动校验，
                    // 实测 JDK 读尽一个被破坏的条目也可能不抛异常（会静默返回坏数据）。
                    if (e.crc >= 0 && crc.value != e.crc && bad == null) {
                        bad = "条目 ${e.name} 校验和不符（数据损坏）"
                    }
                    if (e.size >= 0 && written != e.size && bad == null) {
                        bad = "条目 ${e.name} 长度不符（$written/${e.size}）"
                    }
                }
                when {
                    files == 0 -> "压缩包内没有任何文件"
                    !hasManifest -> "不是 APK：缺少 AndroidManifest.xml"
                    bad != null -> "压缩包损坏：$bad"
                    else -> null
                }
            }
        }.getOrElse { e ->
            "压缩包损坏：${e.javaClass.simpleName}" + (e.message?.let { "（$it）" } ?: "")
        }
    }

    /** 末尾 64KB 内找 PK\x05\x06（ZIP 结束记录签名） */
    private fun hasEndRecord(f: File): Boolean {
        val tailLen = minOf(f.length(), 65536L).toInt()
        if (tailLen < 22) return false
        val tail = ByteArray(tailLen)
        val read = runCatching {
            RandomAccessFile(f, "r").use { r ->
                r.seek(f.length() - tailLen)
                r.readFully(tail)
                true
            }
        }.getOrDefault(false)
        if (!read) return false
        for (i in tail.size - 4 downTo 0) {
            if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4B.toByte() &&
                tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()
            ) return true
        }
        return false
    }
}
