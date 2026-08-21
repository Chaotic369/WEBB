package com.custom.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var historyManager: HistoryManager
    private lateinit var historyAdapter: HistoryAdapter

    private var activeType = "web"

    // SAF Picker for Local Files (Video & HTML)
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val uriString = it.toString()
            saveAndPreview(uriString)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsManager = SettingsManager(this)
        historyManager = HistoryManager(this)

        setupMainUI()
        setupConfigUI()
        setupHistoryUI()
        setupPopups()

        loadInitialState()
    }

    private fun saveAndPreview(uriString: String) {
        settingsManager.currentUri = uriString
        settingsManager.currentType = activeType
        historyManager.addHistory(HistoryItem(System.currentTimeMillis(), activeType, uriString))
        
        val display = findViewById<TextView>(R.id.fileNameDisplay)
        display.text = "FILE SELECTED"
        display.setTextColor(Color.parseColor("#27272a")) // text_color
        
        showStatus("Saved")
    }

    private fun setupMainUI() {
        val btnWeb = findViewById<TextView>(R.id.btnTypeWeb)
        val btnVideo = findViewById<TextView>(R.id.btnTypeVideo)
        val btnHtml = findViewById<TextView>(R.id.btnTypeHtml)
        val urlInput = findViewById<EditText>(R.id.urlInput)
        val urlGoBtn = findViewById<TextView>(R.id.urlGoBtn)
        val fileWrap = findViewById<LinearLayout>(R.id.fileWrap)
        
        val switchType = { type: String ->
            activeType = type
            btnWeb.setTextColor(Color.parseColor("#71717a"))
            btnVideo.setTextColor(Color.parseColor("#71717a"))
            btnHtml.setTextColor(Color.parseColor("#71717a"))
            btnWeb.setBackgroundColor(Color.TRANSPARENT)
            btnVideo.setBackgroundColor(Color.TRANSPARENT)
            btnHtml.setBackgroundColor(Color.TRANSPARENT)

            val activeColor = Color.parseColor("#111111")
            when (type) {
                "web" -> {
                    btnWeb.setTextColor(activeColor)
                    btnWeb.setBackgroundResource(R.drawable.bg_field)
                    urlInput.visibility = View.VISIBLE
                    urlGoBtn.visibility = if (urlInput.text.isNotEmpty()) View.VISIBLE else View.GONE
                    fileWrap.visibility = View.GONE
                }
                "video" -> {
                    btnVideo.setTextColor(activeColor)
                    btnVideo.setBackgroundResource(R.drawable.bg_field)
                    urlInput.visibility = View.GONE
                    urlGoBtn.visibility = View.GONE
                    fileWrap.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.fileNameDisplay).text = "UPLOAD VIDEO"
                }
                "html" -> {
                    btnHtml.setTextColor(activeColor)
                    btnHtml.setBackgroundResource(R.drawable.bg_field)
                    urlInput.visibility = View.GONE
                    urlGoBtn.visibility = View.GONE
                    fileWrap.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.fileNameDisplay).text = "UPLOAD HTML"
                }
            }
        }

        btnWeb.setOnClickListener { switchType("web") }
        btnVideo.setOnClickListener { switchType("video") }
        btnHtml.setOnClickListener { switchType("html") }

        urlInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                urlGoBtn.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        urlGoBtn.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
                urlInput.setText(formattedUrl)
                saveAndPreview(formattedUrl)
            }
        }

        fileWrap.setOnClickListener {
            val mime = if (activeType == "video") "video/*" else "text/html"
            filePickerLauncher.launch(arrayOf(mime))
        }

        findViewById<ImageView>(R.id.configBtn).setOnClickListener {
            findViewById<View>(R.id.mainSettings).visibility = View.GONE
            findViewById<View>(R.id.configView).visibility = View.VISIBLE
        }

        findViewById<View>(R.id.historyBtn).setOnClickListener {
            historyAdapter.updateData(historyManager.getHistory())
            findViewById<View>(R.id.mainSettings).visibility = View.GONE
            findViewById<View>(R.id.historyView).visibility = View.VISIBLE
        }

        findViewById<TextView>(R.id.applyBtn).setOnClickListener {
            if (settingsManager.currentUri.isEmpty()) {
                showStatus("Select a source first")
                return@setOnClickListener
            }
            findViewById<View>(R.id.popupOverlay).visibility = View.VISIBLE
            findViewById<View>(R.id.applyPopup).visibility = View.VISIBLE
        }
    }

    private fun setupConfigUI() {
        val configBack = findViewById<View>(R.id.configBackWrap)
        configBack.setOnClickListener {
            findViewById<View>(R.id.configView).visibility = View.GONE
            findViewById<View>(R.id.mainSettings).visibility = View.VISIBLE
        }

        // Toggles
        val kbdToggle = findViewById<Switch>(R.id.kbdToggle)
        kbdToggle.isChecked = settingsManager.kbdOverlay
        kbdToggle.setOnCheckedChangeListener { _, c -> settingsManager.kbdOverlay = c }

        val obsToggle = findViewById<Switch>(R.id.obsToggle)
        obsToggle.isChecked = settingsManager.pauseWhenNotUse
        obsToggle.setOnCheckedChangeListener { _, c -> settingsManager.pauseWhenNotUse = c }

        val gyroToggle = findViewById<Switch>(R.id.gyroToggle)
        gyroToggle.isChecked = settingsManager.gyroEnabled
        gyroToggle.setOnCheckedChangeListener { _, c -> settingsManager.gyroEnabled = c }

        // Battery Saver
        val batToggle = findViewById<Switch>(R.id.batToggle)
        val batSubmenu = findViewById<View>(R.id.batSubmenu)
        val batSlider = findViewById<SeekBar>(R.id.batSlider)
        val batPercentVal = findViewById<TextView>(R.id.batPercentVal)
        val batSliderVal = findViewById<TextView>(R.id.batSliderVal)

        batToggle.isChecked = settingsManager.batterySaver
        batSubmenu.visibility = if (settingsManager.batterySaver) View.VISIBLE else View.GONE
        batToggle.setOnCheckedChangeListener { _, c ->
            settingsManager.batterySaver = c
            batSubmenu.visibility = if (c) View.VISIBLE else View.GONE
        }

        val initBat = settingsManager.batteryThreshold
        batSlider.progress = initBat - 5
        batPercentVal.text = "${initBat}%"
        batSliderVal.text = "${initBat}%"
        batSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                val v = prog + 5
                batPercentVal.text = "${v}%"
                batSliderVal.text = "${v}%"
                settingsManager.batteryThreshold = v
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Touch Pass-Through
        val touchToggle = findViewById<Switch>(R.id.touchToggle)
        val touchSubmenu = findViewById<View>(R.id.touchSubmenu)
        val btnSizeSlider = findViewById<SeekBar>(R.id.btnSizeSlider)
        val btnSizeVal = findViewById<TextView>(R.id.btnSizeVal)

        touchToggle.isChecked = settingsManager.touchPassThrough
        touchSubmenu.visibility = if (settingsManager.touchPassThrough) View.VISIBLE else View.GONE
        touchToggle.setOnCheckedChangeListener { _, c ->
            settingsManager.touchPassThrough = c
            touchSubmenu.visibility = if (c) View.VISIBLE else View.GONE
        }

        val initSize = settingsManager.btnSize
        btnSizeSlider.progress = initSize - 5
        btnSizeVal.text = "${initSize}px"
        btnSizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                val v = prog + 5
                btnSizeVal.text = "${v}px"
                settingsManager.btnSize = v
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Spinners
        val fpsSpinner = findViewById<Spinner>(R.id.fpsSpinner)
        val scaleSpinner = findViewById<Spinner>(R.id.scaleSpinner)
        
        val fpsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Default", "Custom", "60 FPS", "30 FPS", "Uncapped"))
        fpsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fpsSpinner.adapter = fpsAdapter
        fpsSpinner.setSelection(fpsAdapter.getPosition(settingsManager.fpsLimit).takeIf { it >= 0 } ?: 0)
        fpsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                settingsManager.fpsLimit = fpsAdapter.getItem(p2).toString()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val scaleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("Center Crop", "Fit", "Stretch"))
        scaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        scaleSpinner.adapter = scaleAdapter
        scaleSpinner.setSelection(scaleAdapter.getPosition(settingsManager.scaleMode).takeIf { it >= 0 } ?: 0)
        scaleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                settingsManager.scaleMode = scaleAdapter.getItem(p2).toString()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Cache Purge
        findViewById<View>(R.id.purgeBtn).setOnClickListener {
            val popup = findViewById<View>(R.id.popupOverlay)
            val confirm = findViewById<View>(R.id.confirmPopup)
            findViewById<TextView>(R.id.confirmMsg).text = "Confirm clear cache?"
            val btnAction = findViewById<TextView>(R.id.confirmBtnAction)
            
            popup.visibility = View.VISIBLE
            confirm.visibility = View.VISIBLE
            
            btnAction.setOnClickListener {
                cacheDir.deleteRecursively()
                WebView(this).clearCache(true)
                
                popup.visibility = View.GONE
                confirm.visibility = View.GONE
                
                val status = findViewById<TextView>(R.id.configStatusRow)
                status.text = "Cache Purged Successfully"
                status.postDelayed({ status.text = "" }, 3000)
            }
        }
    }

    private fun setupHistoryUI() {
        val grid = findViewById<RecyclerView>(R.id.historyGrid)
        grid.layoutManager = GridLayoutManager(this, 4)
        
        historyAdapter = HistoryAdapter(
            mutableListOf(),
            onRestore = { item ->
                settingsManager.currentUri = item.uri
                settingsManager.currentType = item.type
                activeType = item.type
                loadInitialState() // Refresh UI inputs to match
                showStatus("Loaded from history")
                
                findViewById<View>(R.id.historyView).visibility = View.GONE
                findViewById<View>(R.id.mainSettings).visibility = View.VISIBLE
            },
            onDelete = { item ->
                historyManager.removeHistory(item.id)
            }
        )
        grid.adapter = historyAdapter

        findViewById<View>(R.id.historyBackWrap).setOnClickListener {
            findViewById<View>(R.id.historyView).visibility = View.GONE
            findViewById<View>(R.id.mainSettings).visibility = View.VISIBLE
        }

        findViewById<View>(R.id.clearHistoryBtn).setOnClickListener {
            val popup = findViewById<View>(R.id.popupOverlay)
            val confirm = findViewById<View>(R.id.confirmPopup)
            findViewById<TextView>(R.id.confirmMsg).text = "Confirm clear?"
            val btnAction = findViewById<TextView>(R.id.confirmBtnAction)
            
            popup.visibility = View.VISIBLE
            confirm.visibility = View.VISIBLE
            
            btnAction.setOnClickListener {
                historyManager.clearAll()
                historyAdapter.updateData(emptyList())
                popup.visibility = View.GONE
                confirm.visibility = View.GONE
            }
        }
    }

    private fun setupPopups() {
        val overlay = findViewById<View>(R.id.popupOverlay)
        val applyPopup = findViewById<View>(R.id.applyPopup)
        val zoomPopup = findViewById<View>(R.id.zoomPopup)
        val confirmPopup = findViewById<View>(R.id.confirmPopup)

        // Close popups on outside click
        overlay.setOnClickListener {
            overlay.visibility = View.GONE
            applyPopup.visibility = View.GONE
            zoomPopup.visibility = View.GONE
            confirmPopup.visibility = View.GONE
        }

        // Apply Actions (Launch System Intent)
        val launchIntent = {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, LiveWallpaperService::class.java))
            startActivity(intent)
            overlay.visibility = View.GONE
            applyPopup.visibility = View.GONE
            showStatus("Applied")
        }
        
        findViewById<View>(R.id.applyHome).setOnClickListener { launchIntent() }
        findViewById<View>(R.id.applyLock).setOnClickListener { launchIntent() }
        findViewById<View>(R.id.applyBoth).setOnClickListener { launchIntent() }

        // Zoom UI
        findViewById<View>(R.id.zoomBtn).setOnClickListener {
            overlay.visibility = View.VISIBLE
            zoomPopup.visibility = View.VISIBLE
        }
        
        val zSlider = findViewById<SeekBar>(R.id.zoomSlider)
        val zInput = findViewById<EditText>(R.id.zoomInput)
        
        // Map 0.25 - 1.25 to 0-100 scale
        val mapZoomToProg = { z: Float -> ((z - 0.25f) / 1.0f * 100).toInt().coerceIn(0, 100) }
        val mapProgToZoom = { p: Int -> 0.25f + (p / 100f) * 1.0f }

        zSlider.progress = mapZoomToProg(settingsManager.zoomLevel)
        zInput.setText(String.format("%.2f", settingsManager.zoomLevel))
        
        zSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                if (fromUser) {
                    val z = mapProgToZoom(prog)
                    settingsManager.zoomLevel = z
                    zInput.setText(String.format("%.2f", z))
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun loadInitialState() {
        val btnWeb = findViewById<TextView>(R.id.btnTypeWeb)
        val btnVideo = findViewById<TextView>(R.id.btnTypeVideo)
        val btnHtml = findViewById<TextView>(R.id.btnTypeHtml)
        
        btnWeb.performClick() // reset
        when (settingsManager.currentType) {
            "web" -> btnWeb.performClick()
            "video" -> btnVideo.performClick()
            "html" -> btnHtml.performClick()
        }
        
        if (activeType == "web") {
            findViewById<EditText>(R.id.urlInput).setText(settingsManager.currentUri)
        } else if (settingsManager.currentUri.isNotEmpty()) {
            findViewById<TextView>(R.id.fileNameDisplay).text = "FILE SELECTED"
            findViewById<TextView>(R.id.fileNameDisplay).setTextColor(Color.parseColor("#27272a"))
        }
    }

    private fun showStatus(msg: String) {
        val sr = findViewById<TextView>(R.id.statusRow)
        sr.text = msg
        sr.setTextColor(Color.parseColor("#111111")) // ok color
        sr.postDelayed({ sr.text = "" }, 3000)
    }
}
