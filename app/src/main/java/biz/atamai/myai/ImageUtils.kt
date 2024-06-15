// ImageUtils.kt

package biz.atamai.myai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageUtils {
    fun resizeImage(filePath: String, maxDimension: Int): File? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(filePath, options)
        val (width, height) = options.run { outWidth to outHeight }

        val scaleFactor = Math.max(width, height).toFloat() / maxDimension

        options.inJustDecodeBounds = false
        options.inSampleSize = scaleFactor.toInt()

        val resizedBitmap = BitmapFactory.decodeFile(filePath, options)
        val resizedFile = File(filePath)

        try {
            FileOutputStream(resizedFile).use { out ->
                val extension = filePath.substringAfterLast('.', "").lowercase()
                val format = when (extension) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                    else -> Bitmap.CompressFormat.PNG // Default to PNG for unsupported formats
                }
                resizedBitmap?.compress(format, 85, out)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }

        return resizedFile
    }
}
