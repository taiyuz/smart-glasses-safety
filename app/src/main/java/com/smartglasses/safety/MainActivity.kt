package com.smartglasses.safety

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.smartglasses.safety.databinding.ActivityMainBinding
import com.smartglasses.safety.pipeline.AlertLevel
import com.smartglasses.safety.pipeline.AlertManager
import com.smartglasses.safety.pipeline.LocalEventLogger
import com.smartglasses.safety.pipeline.MlKitVehicleDetector
import com.smartglasses.safety.pipeline.MockVehicleDetector
import com.smartglasses.safety.pipeline.PerformanceMonitor
import com.smartglasses.safety.pipeline.RiskProfile
import com.smartglasses.safety.pipeline.RiskScorer
import com.smartglasses.safety.pipeline.VehicleDetector
import com.smartglasses.safety.pipeline.VehicleTracker
import com.smartglasses.safety.pipeline.toBitmap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var alertView: TextView

    // Release/default: MlKitVehicleDetector. Mock only when DEBUG && USE_MOCK_DETECTOR.
    private val detector: VehicleDetector =
        if (BuildConfig.DEBUG && BuildConfig.USE_MOCK_DETECTOR) {
            MockVehicleDetector()
        } else {
            MlKitVehicleDetector()
        }
    private val riskScorer = RiskScorer(profile = RiskProfile.BALANCED)
    private lateinit var tracker: VehicleTracker
    private lateinit var alertManager: AlertManager
    private val performanceMonitor = PerformanceMonitor()
    private val eventLogger = LocalEventLogger()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alertView = binding.alertText
        cameraExecutor = Executors.newSingleThreadExecutor()
        alertManager = AlertManager(this)
        detector.initialize(this)
        detector.diagnosticMessage?.let { message ->
            Log.e(TAG, message)
            alertView.text = message
        }

        checkCameraPermissionAndStart()
    }

    private fun checkCameraPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> startCamera()
            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(640, 480))
                .build()

            analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    val bitmap = imageProxy.toBitmap() ?: return@setAnalyzer

                    if (!::tracker.isInitialized) {
                        tracker = VehicleTracker(frameWidth = bitmap.width.toFloat())
                    }

                    val detections = detector.detect(bitmap)
                    val tracked = tracker.track(detections)
                    val risk = riskScorer.score(tracked)

                    val latency = SystemClock.elapsedRealtime() - startedAt
                    performanceMonitor.markFrame(latency)
                    val perf = performanceMonitor.snapshot()
                    eventLogger.logRisk(risk, perf)

                    runOnUiThread {
                        val diag = detector.diagnosticMessage
                        val headline = diag ?: risk.message
                        alertView.text = "$headline\nLatency: ${latency}ms | FPS: %.1f".format(perf.fps)
                        setAlertColor(if (diag != null) AlertLevel.WARNING else risk.level)
                    }

                    alertManager.announce(risk.message, risk.level)
                } finally {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, analyzer)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setAlertColor(level: AlertLevel) {
        val color = when (level) {
            AlertLevel.CRITICAL -> 0xFFB00020.toInt()
            AlertLevel.WARNING -> 0xFFE65100.toInt()
            AlertLevel.ADVISORY -> 0xFF1565C0.toInt()
            AlertLevel.IDLE -> 0xFF212121.toInt()
        }
        alertView.setBackgroundColor(color)
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.close()
        alertManager.shutdown()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "SmartGlassesSafety"
    }
}
