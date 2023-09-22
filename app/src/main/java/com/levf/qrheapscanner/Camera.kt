package com.levf.qrheapscanner

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Matrix
import android.graphics.drawable.ShapeDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.ViewOverlay
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.levf.qrheapscanner.databinding.ActivityCameraBinding
import kotlinx.coroutines.launch
import java.lang.Exception

//TODO add flashlight
//TODO fix bug with bbox area

class Camera : AppCompatActivity() {
    private lateinit var viewBinding: ActivityCameraBinding
    private lateinit var bboxOverlay: ViewOverlay

    private var correctionMatrix: Matrix? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityCameraBinding.inflate(layoutInflater)
        bboxOverlay = viewBinding.previewView.overlay
        //viewBinding.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER //TODO
        setContentView(viewBinding.root)

        if (!hasPermissions()) {
            permissionsActivityLauncher.launch(REQUIRED_PERMISSIONS)
        } else {
            lifecycleScope.launch {
                startCamera()
            }
        }
    }

    private fun hasPermissions() =
        REQUIRED_PERMISSIONS.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    private val permissionsActivityLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            val granted = permissions.all {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) {
                    return@all false
                }
                return@all true
            }
            if (!granted) {
                Toast.makeText(this, "Permission(s) denied", Toast.LENGTH_LONG).show()
            } else {
                lifecycleScope.launch {
                    startCamera()
                }
            }
        }

    private fun createAnalyzer(): ImageAnalysis.Analyzer {
        return Analyzer { result ->
            lifecycleScope.launch {
                bboxOverlay.clear()
                if (correctionMatrix == null)
                {
                    correctionMatrix = matrixForFitPreview(result.imageProxy, viewBinding.previewView)
                    Log.d(
                        Analyzer.TAG,
                        "correctionMatrix: $correctionMatrix"
                    )
                }
                result.points?.forEach { point ->
                    bboxOverlay.add(ShapeDrawable().apply {
                        paint.color = Color.RED
                        paint.style = Paint.Style.FILL
                        //alpha = 100
                        bounds = Rect().apply {
                            val arr = floatArrayOf(point.x, point.y)
                            correctionMatrix!!.mapPoints(arr)
                            this.left = arr[0].toInt()
                            this.top = arr[1].toInt()
                            this.right = arr[0].toInt() + 20
                            this.bottom = arr[1].toInt() + 20
                        }
                    })
                }
            }

        }
    }

    private suspend fun startCamera() {
        val cameraProvider = ProcessCameraProvider.getInstance(this).await()
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(viewBinding.previewView.surfaceProvider)

        Log.d(
            Analyzer.TAG,
            "previewView: ${viewBinding.previewView.width} * ${viewBinding.previewView.height}"
        )

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(
                    ContextCompat.getMainExecutor(this@Camera),
                    createAnalyzer()
                )
            }

//        val useCaseGroup = UseCaseGroup.Builder()
//            .addUseCase(preview)
//            .addUseCase(imageAnalysis)
//            .apply {
//                val viewPort = viewBinding.previewView.viewPort
//                if (viewPort != null) {
//                    Log.d(Analyzer.TAG, "UseCaseGroup.Builder().setViewPort")
//                    setViewPort(viewPort)
//                }
//            }
//            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                // useCaseGroup
                imageAnalysis,
                preview
            )
            //camera.cameraControl.enableTorch()
        } catch (e: Exception) {
            Log.e(TAG, "Binding failed", e)
        }
    }

    companion object {
        private const val TAG = "Camera"
        private val REQUIRED_PERMISSIONS = mutableListOf(
            android.Manifest.permission.CAMERA
        ).toTypedArray()
    }
}