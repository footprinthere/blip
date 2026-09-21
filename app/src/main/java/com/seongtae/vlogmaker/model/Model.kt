package com.seongtae.vlogmaker.model

import android.graphics.RectF
import android.net.Uri
import java.util.UUID

const val SYSTEM_FONT = "system"

enum class MediaType { PHOTO, VIDEO }

data class MediaItem(
    val uri: Uri,
    val type: MediaType,
    val width: Int,
    val height: Int,
    val durationMs: Long = 0L,
    val frameRate: Float = 30f,
    val frameCount: Int = 0,
)

data class Slot(val media: MediaItem? = null, val trimStartMs: Long = 0L)

private fun grid(cols: Int, rows: Int): List<RectF> = buildList {
    for (r in 0 until rows) for (c in 0 until cols) {
        add(RectF(c / cols.toFloat(), r / rows.toFloat(), (c + 1) / cols.toFloat(), (r + 1) / rows.toFloat()))
    }
}

enum class LayoutTemplate(val label: String, val cells: List<RectF>) {
    SINGLE("한 칸", grid(1, 1)),
    ROWS_2("상하 2", grid(1, 2)),
    ROWS_3("상하 3", grid(1, 3)),
    ROWS_4("상하 4", grid(1, 4)),
    COLS_2("좌우 2", grid(2, 1)),
    GRID_2X2("2×2", grid(2, 2)),
    GRID_2X3("2×3", grid(2, 3)),
    GRID_3X2("3×2", grid(3, 2));

    val slotCount: Int get() = cells.size
}

data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val template: LayoutTemplate = LayoutTemplate.SINGLE,
    val slots: List<Slot> = List(template.slotCount) { Slot() },
    val units: Int = 1,
)

data class Caption(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val startScene: Int = 0,
    val endScene: Int = 0,
    val x: Float = 0.5f,
    val y: Float = 0.85f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val fontId: String = SYSTEM_FONT,
    val size: Float = 0.045f,
)

data class Project(
    val unitMs: Long = 1000L,
    val scenes: List<Scene> = emptyList(),
    val captions: List<Caption> = emptyList(),
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
) {
    val aspectRatio: Float get() = width.toFloat() / height
    val totalMs: Long get() = scenes.sumOf { it.units } * unitMs
    fun sceneDurationMs(scene: Scene): Long = scene.units * unitMs
    fun captionsAt(sceneIndex: Int): List<Caption> = captions.filter { sceneIndex in it.startScene..it.endScene }
}

val UNIT_OPTIONS_MS = listOf(500L, 1000L, 1500L, 2000L, 3000L)

fun formatSec(ms: Long): String =
    if (ms % 1000L == 0L) "${ms / 1000}초" else "%.1f초".format(ms / 1000f)

fun formatClock(ms: Long): String {
    val totalSec = ms / 1000
    val tenth = (ms % 1000) / 100
    return "%d:%02d.%d".format(totalSec / 60, totalSec % 60, tenth)
}
