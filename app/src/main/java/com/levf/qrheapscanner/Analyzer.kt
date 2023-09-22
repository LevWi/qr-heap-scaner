package com.levf.qrheapscanner

import android.graphics.ImageFormat.YUV_420_888
import android.graphics.PointF

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

class Result(
    val points: Array<PointF>?,
    val text: String,
    val imageProxy: ImageProxy
)

class Analyzer(val callback: (Result) -> Unit ) : ImageAnalysis.Analyzer { //todo register callback for result
    private val reader = MultiFormatReader()
    private var buffer: ByteArray = ByteArray(0)
//    private val hints = mapOf(
//        DecodeHintType.POSSIBLE_FORMATS to listOf(
//            BarcodeFormat.DATA_MATRIX, BarcodeFormat.QR_CODE
//        )
//    )

    override fun analyze(image: ImageProxy) {
        try {
            Log.d(TAG, "Analyzer.analyze() CropRect ${image.cropRect}")
            val lumSource = toLuminanceSource(image)
            val binarizer = HybridBinarizer(lumSource)
            val bitmap = BinaryBitmap(binarizer)
            val result = reader.decode(bitmap)
            Log.v(TAG, "Analyzer.analyze() result: ${result.text}")
            if(result.resultPoints.isNotEmpty())
            {
                callback( Result(result.resultPoints.map {
                    return@map PointF(it.x, it.y)
                }.toTypedArray(), result.text, image) )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Analyzer.analyze() error:", e)
        } finally {
            image.close()
        }
    }

    private fun toLuminanceSource(imageProxy: ImageProxy): PlanarYUVLuminanceSource {
        assert(imageProxy.format == YUV_420_888)
        Log.d(TAG, "toLuminanceSource: ${imageProxy.width} * ${imageProxy.height}")

        val yPlane = imageProxy.planes[0] //lets check only Y plane
        assert(yPlane.pixelStride == 1)
        val bufferSize = yPlane.buffer.remaining()
        if (buffer.size < bufferSize) {
            buffer = ByteArray(bufferSize)
        }


        yPlane.buffer.get(buffer, 0, bufferSize)

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
            if (imageProxy.width * imageProxy.height < bufferSize) yPlane.rowStride else imageProxy.width,
            imageProxy.height,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            reverseHorizontal
        )
    }

    companion object {
        const val TAG = "Analyzer"
    }
}