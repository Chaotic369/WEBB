package com.custom.wallpaper.renderers

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import com.custom.wallpaper.R
import com.custom.wallpaper.SettingsManager
import kotlin.math.max
import kotlin.math.min

class PresentationRenderer(
    private val context: Context,
    private val surfaceHolder: SurfaceHolder,
    private val settings: SettingsManager
) {
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: WallpaperPresentation? = null
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var currentWidth = 1080
    private var currentHeight = 1920

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            // HTML behavior: Refresh on Reconnect
            if (settings.currentType == "web") {
                Handler(Looper.getMainLooper()).post {
                    presentation?.reloadWeb()
                }
            }
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        setupVirtualDisplay()
        applyFpsLimit()
    }

    private fun setupVirtualDisplay() {
        val metrics = context.resources.displayMetrics
        currentWidth = metrics.widthPixels
        currentHeight = metrics.heightPixels

        virtualDisplay = displayManager.createVirtualDisplay(
            "WallpaperVirtualDisplay",
            currentWidth,
            currentHeight,
            metrics.densityDpi,
            surfaceHolder.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        )

        virtualDisplay?.display?.let { display ->
            presentation = WallpaperPresentation(context, display, settings, currentWidth, currentHeight)
            try {
                presentation?.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun applyFpsLimit() {
        // Use native Android API to limit surface frame rate to save battery, mirroring HTML FPS selector
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val fps = when (settings.fpsLimit) {
                "60 FPS" -> 60f
                "30 FPS" -> 30f
                "Uncapped" -> 120f
                else -> 0f // Default/Custom leaves it to system
            }
            if (fps > 0f) {
                surfaceHolder.surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            } else {
                surfaceHolder.surface.clearFrameRate()
            }
        }
    }

    fun updateDimensions(width: Int, height: Int) {
        currentWidth = width
        currentHeight = height
        virtualDisplay?.resize(width, height, context.resources.displayMetrics.densityDpi)
        presentation?.updateSize(width, height)
    }

    fun resume() {
        presentation?.resume()
    }

    fun pause() {
        presentation?.pause()
    }

    fun release() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        presentation?.release()
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    fun handleTouchEvent(event: MotionEvent): Boolean {
        return presentation?.processTouch(event) ?: false
    }

    fun applyRotation(roll: Float, pitch: Float) {
        presentation?.applyRotation(roll, pitch)
    }

    private class WallpaperPresentation(
        context: Context,
        display: Display,
        private val settings: SettingsManager,
        private var viewWidth: Int,
        private var viewHeight: Int
    ) : Presentation(context, display) {

        private lateinit var rootFrame: FrameLayout
        private lateinit var contentFrame: FrameLayout
        private var webView: WebView? = null
        private var videoView: TextureView? = null
        private var mediaPlayer: MediaPlayer? = null
        private var interactBtn: ImageView? = null

        private var isDraggingBtn = false
        private var dragStartX = 0f
        private var dragStartY = 0f
        private var initialBtnX = 0f
        private var initialBtnY = 0f

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            rootFrame = FrameLayout(context).apply {
                setBackgroundColor(Color.BLACK)
            }
            
            contentFrame = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            rootFrame.addView(contentFrame)

            setupContent()
            setupInteractionButton()

            setContentView(rootFrame)
            applyZoomAndScale()
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun setupContent() {
            val type = settings.currentType
            val uri = settings.currentUri

            if (type == "web" || type == "html") {
                webView = WebView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    
                    // Security/Access overrides for Local HTML
                    if (type == "html") {
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.allowFileAccessFromFileURLs = true
                        settings.allowUniversalAccessFromFileURLs = true
                    }
                    
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                    }
                }
                contentFrame.addView(webView)
                webView?.loadUrl(uri)
            } else if (type == "video") {
                videoView = TextureView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            prepareMediaPlayer(Surface(st))
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            mediaPlayer?.release()
                            mediaPlayer = null
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
                contentFrame.addView(videoView)
            }
        }

        private fun prepareMediaPlayer(surface: Surface) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setSurface(surface)
                    setDataSource(context, Uri.parse(settings.currentUri))
                    setVolume(0f, 0f)
                    isLooping = true 
                    /* 
                     * OS LIMITATION NOTE:
                     * The HTML mentions a "Bounce/Ping-Pong" loop mode. 
                     * Native Android MediaPlayer does NOT support reverse playback natively.
                     * Implementing a reverse frame reader requires ExoPlayer or MediaCodec extraction,
                     * which violates the constraint to avoid unnecessary heavy dependencies.
                     * Closest legitimate behavior: Normal seamless looping is enforced.
                     */
                    setOnVideoSizeChangedListener { _, vWidth, vHeight ->
                        applyVideoScale(vWidth, vHeight)
                    }
                    prepareAsync()
                    setOnPreparedListener { 
                        if (!settings.pauseWhenNotUse) start() 
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun setupInteractionButton() {
            if (settings.touchPassThrough) {
                interactBtn = ImageView(context).apply {
                    val sizePx = settings.btnSize * 3 // approx dp to px mapping for demo
                    layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                        gravity = Gravity.TOP or Gravity.START
                    }
                    
                    x = settings.btnX.coerceIn(0f, viewWidth.toFloat() - sizePx)
                    y = settings.btnY.coerceIn(0f, viewHeight.toFloat() - sizePx)
                    
                    setImageResource(if (settings.interactionUnlocked) R.drawable.ic_settings else R.drawable.ic_mute)
                    setBackgroundColor(if (settings.interactionUnlocked) Color.parseColor("#80111111") else Color.TRANSPARENT)
                }
                rootFrame.addView(interactBtn)
            }
        }

        private fun applyZoomAndScale() {
            // Apply Zoom
            val zoom = settings.zoomLevel
            contentFrame.scaleX = zoom
            contentFrame.scaleY = zoom

            // Scale Mode for Web
            if (webView != null) {
                // WebView scale is managed internally or via layout bounds.
                // We map Center Crop by expanding the bounds beyond the screen if zoomed
                webView?.setInitialScale((zoom * 100).toInt())
            }
        }

        private fun applyVideoScale(videoWidth: Int, videoHeight: Int) {
            if (videoWidth == 0 || videoHeight == 0) return
            
            val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
            val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
            
            val scaleX: Float
            val scaleY: Float
            
            when (settings.scaleMode) {
                "Center Crop" -> {
                    if (videoRatio > viewRatio) {
                        scaleX = videoRatio / viewRatio
                        scaleY = 1f
                    } else {
                        scaleX = 1f
                        scaleY = viewRatio / videoRatio
                    }
                }
                "Fit" -> {
                    if (videoRatio > viewRatio) {
                        scaleX = 1f
                        scaleY = viewRatio / videoRatio
                    } else {
                        scaleX = videoRatio / viewRatio
                        scaleY = 1f
                    }
                }
                "Stretch" -> {
                    scaleX = 1f
                    scaleY = 1f
                }
                else -> {
                    scaleX = 1f
                    scaleY = 1f
                }
            }

            val matrix = Matrix()
            matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
            videoView?.setTransform(matrix)
        }

        fun updateSize(width: Int, height: Int) {
            viewWidth = width
            viewHeight = height
            applyZoomAndScale()
        }

        fun processTouch(event: MotionEvent): Boolean {
            if (!settings.touchPassThrough || interactBtn == null) return false

            val btn = interactBtn!!
            val hitRect = android.graphics.Rect()
            btn.getHitRect(hitRect)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (hitRect.contains(event.x.toInt(), event.y.toInt())) {
                        isDraggingBtn = true
                        dragStartX = event.x
                        dragStartY = event.y
                        initialBtnX = btn.x
                        initialBtnY = btn.y
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingBtn) {
                        val dx = event.x - dragStartX
                        val dy = event.y - dragStartY
                        btn.x = (initialBtnX + dx).coerceIn(0f, viewWidth - btn.width.toFloat())
                        btn.y = (initialBtnY + dy).coerceIn(0f, viewHeight - btn.height.toFloat())
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDraggingBtn) {
                        isDraggingBtn = false
                        val dx = Math.abs(event.x - dragStartX)
                        val dy = Math.abs(event.y - dragStartY)
                        
                        if (dx < 10 && dy < 10) { // Click threshold
                            settings.interactionUnlocked = !settings.interactionUnlocked
                            btn.setImageResource(if (settings.interactionUnlocked) R.drawable.ic_settings else R.drawable.ic_mute)
                            btn.setBackgroundColor(if (settings.interactionUnlocked) Color.parseColor("#80111111") else Color.TRANSPARENT)
                        } else {
                            // Persist drag position
                            settings.btnX = btn.x
                            settings.btnY = btn.y
                        }
                        return true
                    }
                }
            }

            // If not handled by button, pass to webview if unlocked
            if (settings.interactionUnlocked && webView != null) {
                webView?.dispatchTouchEvent(event)
                return true
            }

            return false
        }

        fun applyRotation(roll: Float, pitch: Float) {
            // Apply a subtle parallax rotation mapping to the HTML gyro description
            contentFrame.rotation = roll / 2f
            contentFrame.rotationX = pitch / 2f
        }

        fun reloadWeb() {
            webView?.reload()
        }

        fun resume() {
            webView?.onResume()
            mediaPlayer?.start()
        }

        fun pause() {
            webView?.onPause()
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        }

        fun release() {
            webView?.destroy()
            webView = null
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
}
