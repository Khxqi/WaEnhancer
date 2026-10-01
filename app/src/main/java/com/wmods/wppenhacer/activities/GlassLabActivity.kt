package com.wmods.wppenhacer.activities

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.ui.glass.GlassFrameMonitor
import com.wmods.wppenhacer.ui.glass.GlassLabView

class GlassLabActivity : BaseActivity() {
    private lateinit var lab: GlassLabView
    private lateinit var status: TextView
    private lateinit var monitor: GlassFrameMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Glass Lab"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        lab = GlassLabView(this)
        root.addView(lab, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding((12 * density).toInt())
        }
        root.addView(status)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * density).toInt())
            setBackgroundColor(0xFF15171C.toInt())
        }
        controls.addView(slider("Blur", 0, 60, lab.style.blurRadiusPx.toInt()) { lab.style = lab.style.copy(blurRadiusPx = it.toFloat()) })
        controls.addView(slider("Refraction", 0, 30, lab.style.refractionStrengthPx.toInt()) { lab.style = lab.style.copy(refractionStrengthPx = it.toFloat()) })
        controls.addView(slider("Tint", 0, 50, (lab.style.tintOpacity * 100).toInt()) { lab.style = lab.style.copy(tintOpacity = it / 100f) })
        controls.addView(slider("Edge", 0, 100, (lab.style.edgeIntensity * 100).toInt()) { lab.style = lab.style.copy(edgeIntensity = it / 100f) })
        controls.addView(slider("Highlight", 0, 100, (lab.style.highlightIntensity * 100).toInt()) { lab.style = lab.style.copy(highlightIntensity = it / 100f) })
        controls.addView(slider("Radius", 0, 80, lab.style.cornerRadiusPx.toInt()) { lab.style = lab.style.copy(cornerRadiusPx = it.toFloat()) })
        controls.addView(slider("Depth", 0, 100, (lab.style.depth * 100).toInt()) { lab.style = lab.style.copy(depth = it / 100f) })
        controls.addView(slider("Saturation", 50, 150, (lab.style.saturation * 100).toInt()) { lab.style = lab.style.copy(saturation = it / 100f) })
        controls.addView(slider("Motion", 0, 320, lab.motionSpeedPxPerSecond.toInt()) { lab.motionSpeedPxPerSecond = it.toFloat() })
        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER }
        buttons.addView(Button(this).apply { text = "Light / dark"; setOnClickListener { lab.darkBackdrop = !lab.darkBackdrop } })
        buttons.addView(Button(this).apply { text = "Animate"; setOnClickListener { lab.animate = !lab.animate } })
        buttons.addView(Button(this).apply { text = "GPU / fallback"; setOnClickListener { lab.forceFallback = !lab.forceFallback } })
        controls.addView(buttons)
        root.addView(ScrollView(this).apply { addView(controls) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (300 * density).toInt()
        ))
        setContentView(root)

        val refreshRate = window.decorView.display?.refreshRate ?: 60f
        monitor = GlassFrameMonitor(window, refreshRate)
        monitor.start()
        status.post(object : Runnable {
            override fun run() {
                val stats = monitor.snapshot()
                status.text = "Backend: ${lab.activeBackend} • frames ${stats.frames} • slow ${stats.slowFrames} • worst %.2f ms • target %.2f ms".format(stats.worstFrameMs, stats.targetFrameMs)
                status.postDelayed(this, 1000)
            }
        })
    }

    override fun onDestroy() {
        if (::monitor.isInitialized) monitor.stop()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun slider(
        name: String,
        min: Int,
        max: Int,
        initial: Int,
        changed: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val label = TextView(this).apply { text = "$name $initial"; setTextColor(Color.WHITE) }
        row.addView(label, LinearLayout.LayoutParams((96 * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = initial
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    label.text = "$name $progress"
                    changed(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }
}
