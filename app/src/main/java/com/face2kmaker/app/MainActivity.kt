package com.face2kmaker.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : Activity() {
    private val size = 2048
    private val maps = linkedMapOf<String, Bitmap>()
    private var selected = "Diffuse"
    private var source: Bitmap? = null
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var mapButtons: LinearLayout
    private var brightness = 0
    private var contrast = 100
    private var hairThreshold = 95

    private val pickImage = Picker(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16,17,26)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 16, 18, 16); setBackgroundColor(Color.rgb(18,19,29)) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply { text = "2K FACE MAKER"; textSize = 25f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 8, 0, 4) }
        val sub = TextView(this).apply { text = "Offline cyberface texture workshop • 2048 × 2048 PNG"; textSize = 13f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0, 0, 0, 14) }
        preview = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; minimumHeight = 240; setBackgroundColor(Color.rgb(35,36,48)) }
        status = TextView(this).apply { text = "Import a face or existing texture to begin."; setTextColor(Color.LTGRAY); textSize = 13f; setPadding(2, 12, 2, 12) }
        content.addView(title); content.addView(sub); content.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 290))
        content.addView(button("Import Face / Texture") { pickImage.launch("image/*") })
        content.addView(button("Generate All Maps") { generateMaps() })
        content.addView(button("Export All PNGs") { exportAll() })
        content.addView(status)
        val mapTitle = label("MAPS — tap to preview")
        content.addView(mapTitle)
        mapButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 2, 0, 6) }
        content.addView(mapButtons)
        content.addView(label("QUICK EDIT — affects selected map preview/export"))
        content.addView(label("Brightness"))
        content.addView(seekBar(-80, 80, 0) { brightness = it; updatePreview() })
        content.addView(label("Contrast"))
        content.addView(seekBar(25, 200, 100) { contrast = it; updatePreview() })
        content.addView(label("Hair map darkness threshold"))
        content.addView(seekBar(20, 220, 95) { hairThreshold = it; status.text = "Hair threshold set to $it. Regenerate maps to apply." })
        content.addView(button("Apply Edits to Selected Map") { applyEdits() })
        content.addView(button("Export Selected PNG") { maps[selected]?.let { savePng(it, "${selected.lowercase().replace('/', '_')}_2048.png") } ?: toast("Generate or import a map first") })
        content.addView(TextView(this).apply { text = "Notes: generated maps are starting points, not guaranteed NBA 2K20-ready UV textures. Use the matching game UV layout and verify channel conventions before installing."; setTextColor(Color.LTGRAY); textSize = 12f; setPadding(4, 16, 4, 22) })
        scroll.addView(content); root.addView(scroll); setContentView(root)
        refreshMapButtons()
    }

    // Uses Android's system document picker; no broad storage permission is needed.
    private class Picker(private val activity: Activity) {
        fun launch(type: String) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); this.type = type }
            activity.startActivityForResult(i, 4821)
        }
    }
    @Deprecated("legacy document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4821 && resultCode == RESULT_OK) handlePicked(data?.data)
    }
    private fun handlePicked(uri: Uri?) {
        if (uri == null) return
        try {
            val b = if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) else {
                @Suppress("DEPRECATION")
                contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            }
            source = b?.copy(Bitmap.Config.ARGB_8888, false)
            maps.clear()
            if (source != null) { maps["Diffuse"] = Bitmap.createScaledBitmap(source!!, size, size, true); selected = "Diffuse"; updatePreview(); status.text = "Imported ${b!!.width}×${b.height}. Generate maps when ready." }
        } catch (e: Exception) { status.text = "Import failed: ${e.message}" }
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; isAllCaps = false; setOnClickListener { action() } }
    private fun label(text: String) = TextView(this).apply { this.text = text; setTextColor(Color.WHITE); textSize = 14f; setPadding(2, 8, 2, 2) }
    private fun seekBar(minV: Int, maxV: Int, initial: Int, changed: (Int) -> Unit) = SeekBar(this).apply {
        max = maxV - minV; progress = initial - minV; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) changed(p + minV) }
            override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun generateMaps() {
        val input = source ?: run { toast("Import a face or texture first"); return }
        status.text = "Generating 2048×2048 maps…"
        Thread {
            try {
                val base = Bitmap.createScaledBitmap(input, size, size, true)
                val w = size; val h = size; val count = w * h
                val px = IntArray(count); base.getPixels(px, 0, w, 0, 0, w, h)
                val lum = FloatArray(count)
                for (i in px.indices) lum[i] = (Color.red(px[i]) * .299f + Color.green(px[i]) * .587f + Color.blue(px[i]) * .114f) / 255f
                val normalPx = IntArray(count); val specPx = IntArray(count); val detailPx = IntArray(count); val hairPx = IntArray(count)
                for (y in 0 until h) for (x in 0 until w) {
                    val i = y * w + x
                    val xl = max(0, x - 1); val xr = min(w - 1, x + 1); val yu = max(0, y - 1); val yd = min(h - 1, y + 1)
                    val dx = lum[y * w + xr] - lum[y * w + xl]
                    val dy = lum[yd * w + x] - lum[yu * w + x]
                    var nx = -dx * 5f; var ny = -dy * 5f; var nz = 1f
                    val len = sqrt(nx * nx + ny * ny + nz * nz); nx /= len; ny /= len; nz /= len
                    normalPx[i] = Color.rgb(((nx * .5f + .5f) * 255).toInt(), ((ny * .5f + .5f) * 255).toInt(), ((nz * .5f + .5f) * 255).toInt())
                    val edge = min(255, (abs(dx) + abs(dy)) * 900).toInt()
                    val l = lum[i]
                    val shine = (max(0f, min(1f, (l - .18f) * 1.35f)) * 255).toInt()
                    specPx[i] = Color.rgb(shine, shine, shine)
                    detailPx[i] = Color.rgb(edge, edge, edge)
                    val dark = if (l * 255 < hairThreshold) 255 else 0
                    hairPx[i] = Color.argb(255, dark, dark, dark)
                }
                val generated = linkedMapOf<String, Bitmap>()
                generated["Diffuse"] = base
                generated["Normal"] = Bitmap.createBitmap(normalPx, w, h, Bitmap.Config.ARGB_8888)
                generated["Specular"] = Bitmap.createBitmap(specPx, w, h, Bitmap.Config.ARGB_8888)
                generated["Face Detail"] = Bitmap.createBitmap(detailPx, w, h, Bitmap.Config.ARGB_8888)
                generated["Hair"] = Bitmap.createBitmap(hairPx, w, h, Bitmap.Config.ARGB_8888)
                runOnUiThread { maps.clear(); maps.putAll(generated); selected = "Diffuse"; brightness = 0; contrast = 100; refreshMapButtons(); updatePreview(); status.text = "Generated 5 maps at 2048×2048. Select a map, edit, and export." }
            } catch (e: Exception) { runOnUiThread { status.text = "Generation failed: ${e.message}" } }
        }.start()
    }

    private fun refreshMapButtons() {
        if (!::mapButtons.isInitialized) return
        mapButtons.removeAllViews()
        listOf("Diffuse", "Normal", "Specular", "Face Detail", "Hair").forEach { name ->
            val b = Button(this).apply { text = name; textSize = 10f; isAllCaps = false; setPadding(3, 0, 3, 0); setOnClickListener { selected = name; updatePreview() } }
            mapButtons.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
    private fun updatePreview() {
        val bmp = maps[selected]
        if (bmp == null) { preview.setImageDrawable(null); return }
        preview.setImageBitmap(adjusted(bmp, brightness, contrast, 512))
        status.text = "$selected • ${bmp.width}×${bmp.height}"
    }
    private fun adjusted(src: Bitmap, bright: Int, cont: Int, outSize: Int): Bitmap {
        val small = Bitmap.createScaledBitmap(src, outSize, outSize, true)
        if (bright == 0 && cont == 100) return small
        val p = IntArray(outSize * outSize); small.getPixels(p, 0, outSize, 0, 0, outSize, outSize)
        val c = cont / 100f
        for (i in p.indices) {
            val old = p[i]
            fun ch(v: Int) = min(255, max(0, (((v - 128) * c + 128 + bright).toInt())))
            p[i] = Color.argb(Color.alpha(old), ch(Color.red(old)), ch(Color.green(old)), ch(Color.blue(old)))
        }
        return Bitmap.createBitmap(p, outSize, outSize, Bitmap.Config.ARGB_8888)
    }
    private fun applyEdits() {
        val bmp = maps[selected] ?: run { toast("Select or generate a map first"); return }
        val p = IntArray(bmp.width * bmp.height); bmp.getPixels(p, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val c = contrast / 100f
        for (i in p.indices) {
            val old = p[i]
            fun ch(v: Int) = min(255, max(0, (((v - 128) * c + 128 + brightness).toInt())))
            p[i] = Color.argb(Color.alpha(old), ch(Color.red(old)), ch(Color.green(old)), ch(Color.blue(old)))
        }
        maps[selected] = Bitmap.createBitmap(p, bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        brightness = 0; contrast = 100; updatePreview(); toast("Edits applied to $selected")
    }
    private fun exportAll() {
        if (maps.isEmpty()) { toast("Import a texture and generate maps first"); return }
        Thread {
            var success = 0
            maps.forEach { (name, bmp) -> if (savePng(bmp, "${name.lowercase().replace(' ', '_')}_2048.png", false)) success++ }
            runOnUiThread { status.text = "Exported $success/${maps.size} PNG maps to Pictures/2KFaceMaker."; toast("Export complete: $success maps") }
        }.start()
    }
    private fun savePng(bitmap: Bitmap, fileName: String, showToast: Boolean = true): Boolean {
        return try {
            // Keep a private app-internal copy as well as the user-visible shared export.
            val internalDir = java.io.File(filesDir, "2KFaceMaker").apply { mkdirs() }
            java.io.FileOutputStream(java.io.File(internalDir, fileName)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/2KFaceMaker")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            contentResolver.openOutputStream(uri).use { out: OutputStream? -> if (out == null) throw Exception("Cannot open output"); bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); contentResolver.update(uri, values, null, null)
            if (showToast) runOnUiThread { toast("Saved to Pictures/2KFaceMaker/$fileName") }
            true
        } catch (e: Exception) { if (showToast) runOnUiThread { toast("Save failed: ${e.message}") }; false }
    }
}
