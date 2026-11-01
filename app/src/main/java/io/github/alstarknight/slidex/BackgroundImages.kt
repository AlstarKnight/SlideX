package io.github.alstarknight.slidex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pictures chosen as page backgrounds. They are copied into the app's files, no larger than the screen, so a page
 * keeps its background when the original picture is moved or deleted.
 */
object BackgroundImages {
    private const val DIR = "backgrounds"
    private const val JPEG_QUALITY = 90

    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun cached(name: String): ImageBitmap? = cache.get(name)

    /** The saved picture called [name], or null when it is missing or cannot be read. */
    fun load(context: Context, name: String): ImageBitmap? {
        cache.get(name)?.let { return it }
        val bitmap = BitmapFactory.decodeFile(file(context, name).path) ?: return null
        return bitmap.asImageBitmap().also { cache.put(name, it) }
    }

    /** Copies the picture at [uri] into the app's files; returns its new name, or null when it cannot be read. */
    fun import(context: Context, uri: Uri): String? {
        val maxSide = context.resources.displayMetrics.run { max(widthPixels, heightPixels) }
        val bitmap = try {
            decode(context, uri, maxSide)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } ?: return null
        // JPEG keeps photos small; pictures with see-through parts stay PNG so those parts stay see-through.
        val png = bitmap.hasAlpha()
        val name = UUID.randomUUID().toString() + if (png) ".png" else ".jpg"
        val file = file(context, name)
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use {
                bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            }
            cache.put(name, bitmap.asImageBitmap())
            name
        } catch (e: IOException) {
            file.delete()
            null
        }
    }

    /** Deletes the saved pictures that no page in [configs] uses any more. */
    fun deleteUnused(context: Context, configs: List<PanelConfig>) {
        val used = configs.flatMap { it.backgrounds() }.toSet()
        File(context.filesDir, DIR).listFiles()?.forEach { file ->
            if (file.name !in used) {
                file.delete()
                cache.remove(file.name)
            }
        }
    }

    /** Keeps the picture called [name] from an imported config, unless one by that name is already kept. */
    fun save(context: Context, name: String, data: InputStream) {
        val file = file(context, name)
        if (file.exists()) return
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { data.copyTo(it) }
    }

    // Only the last part of the name is used, so a name from an imported config cannot point outside the folder.
    fun file(context: Context, name: String) = File(File(context.filesDir, DIR), File(name).name)

    private fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = maxSide.toFloat() / max(info.size.width, info.size.height)
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
                // A software bitmap, so it can be compressed into the file.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
}

/** The saved picture called [name], loaded off the main thread; null until it is loaded or when it is missing. */
@Composable
fun rememberBackground(name: String): ImageBitmap? {
    val context = LocalContext.current
    val picture by produceState(BackgroundImages.cached(name), name) {
        value = BackgroundImages.cached(name) ?: withContext(Dispatchers.IO) { BackgroundImages.load(context, name) }
    }
    return picture
}

/** Fills the area with a page's [color], or with its [picture] laid out by [fit], [opacity] times as opaque. */
fun DrawScope.drawPageBackground(color: Color, picture: ImageBitmap?, fit: BackgroundFit, opacity: Float) {
    val area = IntSize(size.width.roundToInt(), size.height.roundToInt())
    if (picture == null) {
        drawRect(color, alpha = opacity)
        return
    }
    when (fit) {
        BackgroundFit.CROP -> {
            val crop = centerCrop(picture.width, picture.height, size.width, size.height)
            drawImage(
                picture,
                srcOffset = IntOffset(crop.left, crop.top),
                srcSize = IntSize(crop.width, crop.height),
                dstSize = area,
                alpha = opacity,
            )
        }
        BackgroundFit.FILL_WIDTH -> {
            val height = (picture.height.toFloat() * area.width / picture.width).roundToInt()
            drawImage(picture, dstSize = IntSize(area.width, height), alpha = opacity)
            if (height < area.height) drawRect(color, topLeft = Offset(0f, height.toFloat()), alpha = opacity)
        }
        BackgroundFit.STRETCH -> drawImage(picture, dstSize = area, alpha = opacity)
    }
}
