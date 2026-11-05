package com.xbta.shellstore.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipFile

/** 一个 zip 条目（APK 就是 zip） */
class ApkEntry(val name: String, val method: Int, val data: ByteArray)

/**
 * 极简 zip 读写。
 *
 * 读用 `ZipFile`；写自己实现，好处是能对**不压缩**的条目做对齐 ——
 * Android 11+ 要求 `resources.arsc` 未压缩且 4 字节对齐（`.so` 要 4096），
 * 而 `ZipOutputStream` 无法精确控制每条目的数据偏移。
 *
 * 顺带支持按名字增删条目（导出时要丢掉模板里的 v1 签名文件与自适应图标 XML）。
 */
object ApkKit {

    private const val LOCAL_SIG = 0x04034b50L
    private const val CENTRAL_SIG = 0x02014b50L
    private const val EOCD_SIG = 0x06054b50L

    fun read(file: File): MutableList<ApkEntry> {
        val list = mutableListOf<ApkEntry>()
        ZipFile(file).use { zf ->
            val en = zf.entries()
            while (en.hasMoreElements()) {
                val e = en.nextElement()
                if (e.isDirectory) continue
                val bytes = zf.getInputStream(e).use { it.readBytes() }
                list.add(ApkEntry(e.name, e.method, bytes))
            }
        }
        return list
    }

    fun write(entries: List<ApkEntry>, out: File) {
        val fos = out.outputStream().buffered()
        val cd = ByteArrayOutputStream()
        var offset = 0L
        val now = System.currentTimeMillis()
        val time = dosTime(now)
        val date = dosDate(now)

        for (e in entries) {
            val nameBytes = e.name.toByteArray(Charsets.UTF_8)
            val crc = crc32(e.data)
            val stored = e.method == 0
            val payload: ByteArray = if (stored) e.data else deflate(e.data)
            val method = if (stored) 0 else 8

            // 对齐：只对不压缩条目有意义（压缩后偏移本来就不可控）
            var extra = ByteArray(0)
            if (method == 0) {
                val align = if (e.name.endsWith(".so")) 4096 else 4
                val base = offset + 30 + nameBytes.size
                val need = ((align - (base % align)) % align).toInt()
                if (need != 0) {
                    // extra 至少要放得下 4 字节头（[0x35 0xD9][size:2]）
                    val extraLen = if (need < 4) need + align else need
                    extra = ByteArray(extraLen)
                    extra[0] = 0x35
                    extra[1] = 0xD9.toByte()
                    extra[2] = ((extraLen - 4) and 0xFF).toByte()
                    extra[3] = (((extraLen - 4) ushr 8) and 0xFF).toByte()
                }
            }

            val localOffset = offset
            val lh = ByteArrayOutputStream()
            writeInt(lh, LOCAL_SIG)
            writeShort(lh, 20)          // version needed
            writeShort(lh, 0)           // flags
            writeShort(lh, method)
            writeShort(lh, time)
            writeShort(lh, date)
            writeInt(lh, crc.toLong())
            writeInt(lh, payload.size.toLong())
            writeInt(lh, e.data.size.toLong())
            writeShort(lh, nameBytes.size)
            writeShort(lh, extra.size)
            lh.write(nameBytes)
            lh.write(extra)
            val lhBytes = lh.toByteArray()
            fos.write(lhBytes)
            fos.write(payload)
            offset += lhBytes.size.toLong() + payload.size

            writeInt(cd, CENTRAL_SIG)
            writeShort(cd, 20)          // version made by
            writeShort(cd, 20)          // version needed
            writeShort(cd, 0)
            writeShort(cd, method)
            writeShort(cd, time)
            writeShort(cd, date)
            writeInt(cd, crc.toLong())
            writeInt(cd, payload.size.toLong())
            writeInt(cd, e.data.size.toLong())
            writeShort(cd, nameBytes.size)
            writeShort(cd, 0)           // extra len（central 里不带对齐 padding）
            writeShort(cd, 0)           // comment len
            writeShort(cd, 0)           // disk start
            writeShort(cd, 0)           // internal attrs
            writeInt(cd, 0)             // external attrs
            writeInt(cd, localOffset)
            cd.write(nameBytes)
        }

        val cdBytes = cd.toByteArray()
        fos.write(cdBytes)
        val eocd = ByteArrayOutputStream()
        writeInt(eocd, EOCD_SIG)
        writeShort(eocd, 0)
        writeShort(eocd, 0)
        writeShort(eocd, entries.size)
        writeShort(eocd, entries.size)
        writeInt(eocd, cdBytes.size.toLong())
        writeInt(eocd, offset)
        writeShort(eocd, 0)
        fos.write(eocd.toByteArray())
        fos.flush()
        fos.close()
    }

    /** 读回所有条目名（验证用） */
    fun names(file: File): List<String> = ZipFile(file).use { zf ->
        zf.entries().toList().filter { !it.isDirectory }.map { it.name }
    }

    /** 读回某个条目的内容（验证用） */
    fun entry(file: File, name: String): ByteArray? = ZipFile(file).use { zf ->
        val e = zf.getEntry(name) ?: return@use null
        zf.getInputStream(e).use { it.readBytes() }
    }

    fun crc32(b: ByteArray): Long {
        val c = CRC32()
        c.update(b)
        return c.value
    }

    fun deflate(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        d.setInput(data)
        d.finish()
        val out = ByteArrayOutputStream(data.size / 2 + 64)
        val buf = ByteArray(8192)
        while (!d.finished()) {
            val n = d.deflate(buf)
            if (n > 0) out.write(buf, 0, n) else break
        }
        d.end()
        return out.toByteArray()
    }

    private fun dosTime(t: Long): Int {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = t
        return (c.get(java.util.Calendar.HOUR_OF_DAY) shl 11) or
            (c.get(java.util.Calendar.MINUTE) shl 5) or
            (c.get(java.util.Calendar.SECOND) / 2)
    }

    private fun dosDate(t: Long): Int {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = t
        return ((c.get(java.util.Calendar.YEAR) - 1980) shl 9) or
            ((c.get(java.util.Calendar.MONTH) + 1) shl 5) or
            c.get(java.util.Calendar.DAY_OF_MONTH)
    }

    private fun writeShort(o: ByteArrayOutputStream, v: Int) {
        o.write(v and 0xff)
        o.write((v ushr 8) and 0xff)
    }

    private fun writeInt(o: ByteArrayOutputStream, v: Long) {
        o.write((v and 0xff).toInt())
        o.write(((v ushr 8) and 0xff).toInt())
        o.write(((v ushr 16) and 0xff).toInt())
        o.write(((v ushr 24) and 0xff).toInt())
    }
}
