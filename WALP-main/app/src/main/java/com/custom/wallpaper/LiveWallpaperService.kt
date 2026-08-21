package com.custom.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.custom.wallpaper.renderers.PresentationRenderer

class LiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return WallpaperEngine(this)
    }

    inner class WallpaperEngine(private val context: Context) : Engine(), SensorEventListener {
        private var renderer: PresentationRenderer? = null
        private val settings = SettingsManager(context)
        
        private var isVisible = false
        private var batteryLow = false
        
        private var sensorManager: SensorManager? = null
        private var gyroSensor: Sensor? = null

        private val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = level * 100 / scale.toFloat()
                    
                    // Use exact persisted threshold from HTML UI
                    batteryLow = batteryPct <= settings.batteryThreshold
                    updatePlaybackState()
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.isVisible = visible
            
            if (visible && settings.gyroEnabled) {
                gyroSensor?.let {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
            } else {
                sensorManager?.unregisterListener(this)
            }
            
            updatePlaybackState()
        }

        private fun updatePlaybackState() {
            val pauseWhenNotUse = settings.pauseWhenNotUse
            val batterySaverActive = settings.batterySaver && batteryLow

            val shouldPlay = isVisible && !batterySaverActive

            if (!pauseWhenNotUse && !batterySaverActive) {
                renderer?.resume()
            } else if (shouldPlay) {
                renderer?.resume()
            } else {
                renderer?.pause()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            
            renderer = PresentationRenderer(context, holder, settings)
            updatePlaybackState()
        }
        
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer?.updateDimensions(width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            renderer?.release()
            renderer = null
        }

        override fun onDestroy() {
            super.onDestroy()
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                // Ignore if not registered
            }
            sensorManager?.unregisterListener(this)
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event == null) return
            
            val handledByRenderer = renderer?.handleTouchEvent(event) ?: false
            
            if (!handledByRenderer && event.action == MotionEvent.ACTION_DOWN) {
                // If touch wasn't consumed by the interaction button, handle keyboard overlay
                // Only if Pass-through is inactive or interaction is explicitly locked, depending on HTML definition.
                // The HTML specifies launching transparent Activity to force system keyboard.
                if (settings.kbdOverlay && !settings.interactionUnlocked) {
                    val intent = Intent(context, KeyboardActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
                }
            }
        }

        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                // Pass rotation matrix/degrees to renderer if gyro is enabled
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientationVals = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientationVals)
                
                val roll = Math.toDegrees(orientationVals[2].toDouble()).toFloat()
                val pitch = Math.toDegrees(orientationVals[1].toDouble()).toFloat()
                
                renderer?.applyRotation(roll, pitch)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}
