package com.example.taskifyapp.util

import android.graphics.Bitmap
import android.media.Image
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * 封装了项目中所有图片处理相关的辅助函数
 */
object ImageUtils {
    private const val TAG = "ImageUtils"

    /**
     * 将 MediaProjection 捕获的 Image 对象转换为 Bitmap 对象。
     * 这是处理原始像素流的标准方法。
     * @param image 从 ImageReader 获取的 Image 对象
     * @return 转换后的 Bitmap 对象
     */
    fun imageToBitmap(image: Image): Bitmap {
        // 这是处理MediaProjection原始像素流的标准、正确方法
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // 如果存在行填充，则裁剪掉多余部分
        if (rowPadding > 0) {
            return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        }
        return bitmap
    }

    /**
     * 根据设定的目标宽度，等比例缩放 Bitmap，用于减少网络传输的数据量。
     * @param source 原始 Bitmap
     * @param targetWidth 目标宽度
     * @return 缩放后的新 Bitmap
     */
    fun resizeBitmapByWidth(source: Bitmap, targetWidth: Int): Bitmap {
        // 如果原始宽度小于或等于目标宽度，则无需缩放，直接返回原图
        if (source.width <= targetWidth) {
            return source
        }
        val targetHeight = (source.height.toFloat() / source.width.toFloat() * targetWidth).toInt()
        Log.d(TAG, "图片尺寸已从 ${source.width}x${source.height} 缩放至 ${targetWidth}x${targetHeight}")
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    /**
     * 将 Bitmap 对象压缩并编码为 Base64 字符串。
     * @param bitmap 要编码的 Bitmap
     * @param quality 压缩质量 (0-100)
     * @return Base64 编码后的字符串
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 40): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.URL_SAFE or Base64.NO_WRAP)
    }
}