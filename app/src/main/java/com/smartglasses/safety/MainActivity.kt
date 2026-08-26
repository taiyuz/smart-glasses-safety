package com.smartglasses.safety

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smartglasses.safety.databinding.ActivityMainBinding
import com.smartglasses.safety.pipeline.AlertLevel
import com.smartglasses.safety.pipeline.AlertManager
import com.smartglasses.safety.pipeline.DetectorFactory
import com.smartglasses.safety.pipeline.LocalEventLogger
import com.smartglasses.safety.pipeline.PerformanceMonitor
import com.smartglasses.safety.pipeline.RiskProfile
import com.smartglasses.safety.pipeline.RiskScorer
import com.smartglasses.safety.pipeline.VehicleDetector
import com.smartglasses.safety.pipeline.VehicleTracker
import com.smartglasses.safety.pipeline.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var alertView: TextView
    private lateinit var detector: VehicleDetector

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
        detector = DetectorFactory.create()
        detector.initialize(this)

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
                .build()

            analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                val startedAt = SystemClock.elapsedRealtime()
                val bitmap = imageProxy.toBitmap()
                if (bitmap == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                if (!::tracker.isInitialized) {
                    tracker = VehicleTracker(frameWidth = bitmap.width.toFloat())
                }

                val detections = if (detector.isReady) {
                    detector.detect(bitmap)
                } else {
                    emptyList()
                }
                val tracked = tracker.track(detections)
                val risk = riskScorer.score(tracked)

                val latency = SystemClock.elapsedRealtime() - startedAt
                performanceMonitor.markFrame(latency)
                val perf = performanceMonitor.snapshot()
                eventLogger.logRisk(risk, perf)

                val overlay = if (detector.isReady) {
                    "${risk.message}\n${detector.statusMessage}\nLatency: ${latency}ms | FPS: %.1f"
                        .format(perf.fps)
                } else {
                    "${detector.statusMessage}\nNo fake boxes emitted.\nLatency: ${latency}ms | FPS: %.1f"
                        .format(perf.fps)
                }

                lifecycleScope.launch(Dispatchers.Main) {
                    alertView.text = overlay
                    setAlertColor(risk.level)
                }

                alertManager.announce(risk.message, risk.level)
                imageProxy.close()
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
}
