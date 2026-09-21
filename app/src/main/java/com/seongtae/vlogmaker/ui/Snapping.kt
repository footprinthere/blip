package com.seongtae.vlogmaker.ui

import kotlin.math.abs

object Snapping {
    /** targets 중 value 에 threshold 이내로 가까운 값. 없으면 null. */
    fun nearest(value: Float, targets: List<Float>, threshold: Float): Float? =
        targets.minByOrNull { abs(it - value) }?.takeIf { abs(it - value) <= threshold }
}
