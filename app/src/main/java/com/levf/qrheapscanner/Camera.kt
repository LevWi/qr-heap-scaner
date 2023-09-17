package com.levf.qrheapscanner

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ShapeDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.ViewOverlay
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.levf.qrheapscanner.databinding.ActivityCameraBinding
import kotlinx.coroutines.launch
import java.lang.Exception

//TODO add flashlight
//TODO fix bug with bbox area

class Camera : AppCompatActivity() {
    private lateinit var viewBinding: ActivityCameraBinding
    private lateinit var bboxOverlay: ViewOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityCameraBinding.inflate(layoutInflater)
        bboxOverlay = viewBinding.previewView.overlay
        setContentView(viewBinding.root)

        viewBinding.cameraButton.setOnClickListener {
            bboxOverlay.add(ShapeDrawable().apply {
                paint.color = Color.GREEN
                bounds = Rect().apply {
                    top = 20
                    left = 10
                    bottom = 30
                    right = 40
                }
            })
        }

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
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()

        val barcodeScanner = BarcodeScanning.getClient(options)
        val executor = ContextCompat.getMainExecutor(this)
        return MlKitAnalyzer(
            listOf(barcodeScanner),
            COORDINATE_SYSTEM_ORIGINAL,
            executor
        ) { result: MlKitAnalyzer.Result? ->
            Log.i(TAG, "Some detected event")
            if (result == null)
                return@MlKitAnalyzer
            lifecycleScope.launch {
                bboxOverlay.clear()
                result.getValue(barcodeScanner)?.forEach { barcode: Barcode ->
                    barcode.boundingBox?.also { rect ->
                        bboxOverlay.add(ShapeDrawable().apply {
                            paint.color = Color.RED
                            paint.style = Paint.Style.FILL
                            alpha = 100
                            bounds = rect
                        })
                    }
                }
            }
        }

    }

    private suspend fun startCamera() {
        val cameraProvider = ProcessCameraProvider.getInstance(this).await()
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(viewBinding.previewView.surfaceProvider)

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(
                Size(viewBinding.previewView.width, viewBinding.previewView.height)
            )
            .build()
            .apply {
                setAnalyzer(
                    ContextCompat.getMainExecutor(this@Camera),
                    createAnalyzer()
                )
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
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