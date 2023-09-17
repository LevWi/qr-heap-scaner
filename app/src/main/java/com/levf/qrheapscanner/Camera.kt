package com.levf.qrheapscanner

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.lifecycle.lifecycleScope
import com.levf.qrheapscanner.databinding.ActivityCameraBinding
import kotlinx.coroutines.launch
import java.lang.Exception


class Camera : AppCompatActivity() {
    private lateinit var viewBinding: ActivityCameraBinding

    //private var imageCapture: ImageCapture? = null TODO not needed. We need Analyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityCameraBinding.inflate(layoutInflater)
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

    private suspend fun startCamera() {
        val cameraProvider = ProcessCameraProvider.getInstance(this).await()
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(viewBinding.previewView.surfaceProvider)
        //imageCapture = ImageCapture.Builder().build() TODO not needed. We need Analyzer
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            var camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
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