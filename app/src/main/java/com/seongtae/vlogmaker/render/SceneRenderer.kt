package com.seongtae.vlogmaker.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.seongtae.vlogmaker.model.Caption
import com.seongtae.vlogmaker.model.Scene
import com.seongtae.vlogmaker.model.Slot
import kotlin.math.max

/** 슬롯에 그릴 프레임을 돌려준다. 사진이면 이미지, 영상이면 해당 시점 프레임, 빈 슬롯이면 null. */
fun interface FrameSource {
    fun frame(slotIndex: Int, slot: Slot, sceneTimeMs: Long): Bitmap?
}

/** 미리보기와 내보내기가 함께 쓰는 장면 그리기 코드. 좌표는 모두 0~1 비율이라 어느 크기 캔버스에도 그릴 수 있다. */
object SceneRenderer {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val placeholderFill = Paint().apply { color = 0xFF111111.toInt() }
    private val placeholderLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF444444.toInt(); style = Paint.Style.STROKE }

    fun drawScene(
        canvas: Canvas, w: Int, h: Int, scene: Scene, captions: List<Caption>,
        frames: FrameSource, sceneTimeMs: Long, typefaces: (String) -> Typeface, placeholders: Boolean,
    ) {
        drawMedia(canvas, w, h, scene, frames, sceneTimeMs, placeholders)
        drawCaptions(canvas, w, h, captions, typefaces)
    }

    fun drawMedia(canvas: Canvas, w: Int, h: Int, scene: Scene, frames: FrameSource, sceneTimeMs: Long, placeholders: Boolean) {
        canvas.drawColor(Color.BLACK)
        scene.template.cells.forEachIndexed { i, cell ->
            val dst = RectF(cell.left * w, cell.top * h, cell.right * w, cell.bottom * h)
            val slot = scene.slots.getOrNull(i)
            val bmp = slot?.let { frames.frame(i, it, sceneTimeMs) }
            if (bmp != null && !bmp.isRecycled) drawCenterCrop(canvas, bmp, dst)
            else if (placeholders) drawPlaceholder(canvas, dst)
        }
    }

    private fun drawCenterCrop(canvas: Canvas, bmp: Bitmap, dst: RectF) {
        val scale = max(dst.width() / bmp.width, dst.height() / bmp.height)
        val sw = dst.width() / scale
        val sh = dst.height() / scale
        val sx = (bmp.width - sw) / 2f
        val sy = (bmp.height - sh) / 2f
        val src = Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt())
        canvas.drawBitmap(bmp, src, dst, bitmapPaint)
    }

    private fun drawPlaceholder(canvas: Canvas, dst: RectF) {
        canvas.drawRect(dst, placeholderFill)
        val r = minOf(dst.width(), dst.height()) * 0.08f
        placeholderLine.strokeWidth = r * 0.15f
        val cx = dst.centerX()
        val cy = dst.centerY()
        canvas.drawLine(cx - r, cy, cx + r, cy, placeholderLine)
        canvas.drawLine(cx, cy - r, cx, cy + r, placeholderLine)
        placeholderLine.strokeWidth = max(1f, dst.width() * 0.002f)
        canvas.drawRect(dst, placeholderLine)
    }

    fun drawCaptions(canvas: Canvas, w: Int, h: Int, captions: List<Caption>, typefaces: (String) -> Typeface) {
        for (c in captions) drawCaption(canvas, w, h, c, typefaces(c.fontId))
    }

    private fun layoutFor(w: Int, caption: Caption, typeface: Typeface): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = caption.color
            textSize = caption.size * w
            this.typeface = typeface
            setShadowLayer(w * 0.004f, 0f, w * 0.002f, 0x99000000.toInt())
        }
        val maxWidth = (w * 0.9f).toInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(caption.text, 0, caption.text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
    }

    fun drawCaption(canvas: Canvas, w: Int, h: Int, caption: Caption, typeface: Typeface) {
        if (caption.text.isBlank()) return
        val layout = layoutFor(w, caption, typeface)
        val left = caption.x * w - layout.width / 2f
        val top = caption.y * h - layout.height / 2f
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }

    /** 자막이 실제로 차지하는 영역(픽셀). 편집 화면에서 테두리 표시용. */
    fun captionBounds(w: Int, h: Int, caption: Caption, typeface: Typeface): RectF {
        val layout = layoutFor(w, caption, typeface)
        var lineMax = 0f
        for (i in 0 until layout.lineCount) lineMax = max(lineMax, layout.getLineWidth(i))
        val cx = caption.x * w
        val cy = caption.y * h
        val halfW = max(lineMax, w * 0.05f) / 2f
        val halfH = max(layout.height.toFloat(), caption.size * w) / 2f
        return RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
    }
}
