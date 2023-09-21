package com.levf.qrheapscanner

import android.graphics.ImageFormat.YUV_420_888
import android.graphics.Point

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

class Analyzer(val callback: (Array<Point>) -> Unit ) : ImageAnalysis.Analyzer { //todo register callback for result
    private val reader = MultiFormatReader()
    private var buffer: ByteArray = ByteArray(0)
//    private val hints = mapOf(
//        DecodeHintType.POSSIBLE_FORMATS to listOf(
//            BarcodeFormat.DATA_MATRIX, BarcodeFormat.QR_CODE
//        )
//    )

    override fun analyze(image: ImageProxy) {
        try {
            Log.d(TAG, "Analyzer.analyze()...") //TODO remove
            val lumSource = toLuminanceSource(image)
            val binarizer = HybridBinarizer(lumSource)
            val bitmap = BinaryBitmap(binarizer)
            val result = reader.decode(bitmap)
            Log.d(TAG, "Analyzer.analyze() result: ${result.text}") //TODO remove
            if(result.resultPoints.isNotEmpty())
            {
                callback( result.resultPoints.map {
                    return@map Point(it.x.toInt(), it.y.toInt())
                }.toTypedArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analyzer.analyze() error:", e)
        } finally {
            image.close()
        }
    }

    private fun toLuminanceSource(imageProxy: ImageProxy): PlanarYUVLuminanceSource {
        assert(imageProxy.format == YUV_420_888)
        assert(imageProxy.planes.size == 3)
        assert(imageProxy.planes[2].buffer.position() == 0)

        val yPlane = imageProxy.planes[0] //lets check only Y plane
        val requiredCapacity = yPlane.buffer.remaining()
        if (buffer.size < requiredCapacity) {
            buffer = ByteArray(requiredCapacity)
        }
        yPlane.buffer.get(buffer, 0, yPlane.buffer.remaining())
//        val rowStride = yPlane.rowStride
//        for(x : Int in 0..imageProxy.height)
//        {
//            val planeOffset = x * rowStride
//            val bufferOffset = x * imageProxy.width
//
//            System.arraycopy(yPlane.buffer, planeOffset, buffer, bufferOffset, imageProxy.width)
//        }

        val reverseHorizontal = false
        return PlanarYUVLuminanceSource(
            buffer,
            imageProxy.width,
            imageProxy.height,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            reverseHorizontal
        )
    }

    companion object {
        val TAG = "Analyzer"
    }
}