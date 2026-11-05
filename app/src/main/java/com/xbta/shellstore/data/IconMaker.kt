package com.xbta.shellstore.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * 生成的图标：文字图标（名称首字 + 动作类型配色），或用用户选的图。
 *
 * 尺寸按 Android 的五档密度来（48/72/96/144/192），导出时分别替换模板里
 * `res/mipmap-<density>-v4/ic_launcher.png` 这些条目。
 */
object IconMaker {

    /** 五档密度对应的像素边长 */
    val DENSITY_PX = listOf(48, 72, 96, 144, 192)

    private fun colorsOf(type: String): Pair<Int, Int> = when (type) {
        "url" -> 0xFF2F80ED.toInt() to 0xFF1749A0.toInt()
        "activity" -> 0xFF7B61FF.toInt() to 0xFF4A32B8.toInt()
        "broadcast" -> 0xFFFF8A3D.toInt() to 0xFFB84C13.toInt()
        "service" -> 0xFF00B8A9.toInt() to 0xFF007A70.toInt()
        else -> 0xFF13B55C.toInt() to 0xFF0A6B37.toInt()      // shell
    }

    /** 文字图标：圆角方 + 渐变底 + 居中的字 */
    fun renderText(text: String, type: String, px: Int): ByteArray {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (top, bottom) = colorsOf(type)

        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, px.toFloat(), top, bottom, Shader.TileMode.CLAMP
            )
        }
        val r = px * 0.22f
        c.drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), r, r, body)

        val glyph = text.trim().let { if (it.isEmpty()) "S" else it.take(1) }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = px * 0.54f
        }
        val fm = tp.fontMetrics
        val cy = px / 2f - (fm.ascent + fm.descent) / 2f
        c.drawText(glyph, px / 2f, cy, tp)

        return toPng(bmp)
    }

    /** 用户选的图：居中裁成正方形再缩放，避免变形 */
    fun renderPhoto(src: Bitmap, px: Int): ByteArray {
        val side = minOf(src.width, src.height)
        val left = (src.width - side) / 2
        val top = (src.height - side) / 2
        val square = Bitmap.createBitmap(src, left, top, side, side)
        val scaled = Bitmap.createScaledBitmap(square, px, px, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        if (scaled !== square) scaled.recycle()
        if (square !== src) square.recycle()
        return out.toByteArray()
    }

    /**
     * 生成五档密度的图标。
     * @param photo 用户选的图；为 null 时按文字生成
     */
    fun allSizes(text: String, type: String, photo: Bitmap?): Map<Int, ByteArray> {
        val map = LinkedHashMap<Int, ByteArray>()
        for (px in DENSITY_PX) {
            map[px] = if (photo != null) renderPhoto(photo, px) else renderText(text, type, px)
        }
        return map
    }

    /** 预览用：单张 */
    fun preview(text: String, type: String, photo: Bitmap?, px: Int = 192): ByteArray =
        if (photo != null) renderPhoto(photo, px) else renderText(text, type, px)

    private fun toPng(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
