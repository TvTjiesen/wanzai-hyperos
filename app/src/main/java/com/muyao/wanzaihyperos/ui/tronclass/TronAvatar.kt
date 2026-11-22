package com.muyao.wanzaihyperos.ui.tronclass

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.net.HzcuRateLimiter
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun avatarUrl(raw: String?): HttpUrl? = raw?.toHttpUrlOrNull()?.takeIf {
    it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.port == 443
}

private object AvatarReader {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private val http by lazy {
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(8, TimeUnit.SECONDS).build()
    }

    suspend fun load(url: HttpUrl): Bitmap? = HzcuRateLimiter.instance.gate("/tron/profile-avatar") {
        withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                if (body.contentLength() > MAX_BYTES || body.contentType()?.type != "image") return@withContext null
                val output = ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_BYTES) return@withContext null
                        output.write(buffer, 0, count)
                    }
                }
                val bytes = output.toByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096 ||
                    bounds.outWidth.toLong() * bounds.outHeight > 16_000_000) return@withContext null
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                while (bounds.outWidth / options.inSampleSize > 256 || bounds.outHeight / options.inSampleSize > 256) {
                    options.inSampleSize *= 2
                }
                ensureActive()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        }
    }
}

/** No image cache or authenticated client; a new owner gets a fresh composition and state. */
@Composable
internal fun TronAvatar(owner: Long, userNo: String, name: String, rawUrl: String?) {
    key(owner, userNo, rawUrl) {
        val bitmap by produceState<Bitmap?>(null, rawUrl) {
            val url = avatarUrl(rawUrl) ?: return@produceState
            value = try {
                AvatarReader.load(url)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            }
        }
        Box(Modifier.size(72.dp).clip(CircleShape).background(MiuixTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) {
            val image = bitmap
            if (image != null) Image(image.asImageBitmap(), "个人头像", Modifier.size(72.dp), contentScale = ContentScale.Crop)
            else Text(name.take(1).ifBlank { "—" }, style = MiuixTheme.textStyles.title1,
                color = MiuixTheme.colorScheme.onSurface)
        }
    }
}
