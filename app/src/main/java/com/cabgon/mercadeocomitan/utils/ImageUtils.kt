package com.cabgon.mercadeocomitan.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Utilidades para optimizar y validar imágenes antes de subirlas
 */
object ImageUtils {

    private const val MAX_WIDTH = 2048
    private const val MAX_HEIGHT = 2048
    private const val JPEG_QUALITY = 85

    /**
     * Comprime una imagen URI y retorna un ByteArray optimizado
     * @param context Contexto de la aplicación
     * @param uri URI de la imagen
     * @param maxSizeBytes Tamaño máximo en bytes (default 2MB)
     * @return ByteArray de la imagen comprimida, o null si falla
     */
    fun compressImage(
        context: Context,
        uri: Uri,
        maxSizeBytes: Long = 2 * 1024 * 1024
    ): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Decodificar solo dimensiones primero
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calcular factor de escala
            var scaleFactor = 1
            val width = options.outWidth
            val height = options.outHeight

            if (width > MAX_WIDTH || height > MAX_HEIGHT) {
                scaleFactor = kotlin.math.max(
                    width / MAX_WIDTH,
                    height / MAX_HEIGHT
                )
            }

            // Decodificar la imagen escalada
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scaleFactor
            }

            val inputStream2 = context.contentResolver.openInputStream(uri) ?: return null
            var bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
            inputStream2.close()

            if (bitmap == null) return null

            // Corregir orientación según EXIF
            bitmap = correctImageOrientation(context, uri, bitmap)

            // Comprimir a JPEG
            var quality = JPEG_QUALITY
            var compressed: ByteArray

            do {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                compressed = outputStream.toByteArray()
                outputStream.close()

                if (compressed.size <= maxSizeBytes || quality <= 50) break
                quality -= 10
            } while (quality > 0)

            bitmap.recycle()
            compressed

        } catch (e: Exception) {
            android.util.Log.e("ImageUtils", "Error comprimiendo imagen", e)
            null
        }
    }

    /**
     * Valida las dimensiones de una imagen
     * @return Pair<width, height> o null si no es válida
     */
    fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (options.outWidth > 0 && options.outHeight > 0) {
                Pair(options.outWidth, options.outHeight)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageUtils", "Error obteniendo dimensiones", e)
            null
        }
    }

    /**
     * Valida que la imagen tenga dimensiones razonables
     */
    fun isValidImageSize(context: Context, uri: Uri): Boolean {
        val dimensions = getImageDimensions(context, uri) ?: return false
        val (width, height) = dimensions

        // Dimensiones mínimas: 200x200, máximas: 8192x8192
        return width in 200..8192 && height in 200..8192
    }

    /**
     * Corrige la orientación de la imagen según los datos EXIF
     */
    private fun correctImageOrientation(
        context: Context,
        uri: Uri,
        bitmap: Bitmap
    ): Bitmap {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }

            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0,
                bitmap.width, bitmap.height,
                matrix, true
            )

            if (rotated != bitmap) {
                bitmap.recycle()
            }

            return rotated
        } catch (e: IOException) {
            android.util.Log.w("ImageUtils", "No se pudo leer EXIF", e)
            return bitmap
        }
    }

    /**
     * Calcula el tamaño aproximado en bytes de una imagen URI
     */
    fun getImageSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
