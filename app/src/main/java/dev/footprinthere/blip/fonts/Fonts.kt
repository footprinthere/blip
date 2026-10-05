package dev.footprinthere.blip.fonts

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.HandlerThread
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import dev.footprinthere.blip.R
import dev.footprinthere.blip.model.SYSTEM_FONT
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Google Fonts 제공자(Google Play 서비스)에서 글꼴을 내려받아 캐시한다. 실패하면 시스템 기본 글꼴을 쓴다. */
object Fonts {
    /** Compose 가 읽으면 글꼴이 로드될 때 자동으로 다시 그려진다. */
    val typefaces = mutableStateMapOf<String, Typeface>()
    val failed = mutableStateMapOf<String, Boolean>()

    private val handler: Handler by lazy {
        Handler(HandlerThread("fonts").apply { start() }.looper)
    }

    fun get(id: String): Typeface = typefaces[id] ?: Typeface.DEFAULT

    fun isReady(id: String): Boolean = id == SYSTEM_FONT || typefaces.containsKey(id)

    suspend fun load(context: Context, id: String): Typeface {
        if (id == SYSTEM_FONT) return Typeface.DEFAULT
        typefaces[id]?.let { return it }
        val tf = request(context.applicationContext, id)
        if (tf != null) typefaces[id] = tf else failed[id] = true
        return tf ?: Typeface.DEFAULT
    }

    private suspend fun request(context: Context, family: String): Typeface? = suspendCancellableCoroutine { cont ->
        val request = FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            "name=$family&besteffort=true",
            R.array.com_google_android_gms_fonts_certs,
        )
        val callback = object : FontsContractCompat.FontRequestCallback() {
            override fun onTypefaceRetrieved(typeface: Typeface) {
                if (cont.isActive) cont.resume(typeface)
            }

            override fun onTypefaceRequestFailed(reason: Int) {
                if (cont.isActive) cont.resume(null)
            }
        }
        runCatching { FontsContractCompat.requestFont(context, request, callback, handler) }
            .onFailure { if (cont.isActive) cont.resume(null) }
    }
}
