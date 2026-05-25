package com.example.recorderproject.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.recorderproject.audio.BiquadCoeffs
import com.example.recorderproject.model.EQChain
import java.io.OutputStream
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Phase 7 — render the current EQ response curve to a PNG and drop it in MediaStore / Pictures. */
object CurveBitmapExport {

    private const val W = 1600
    private const val H = 900

    fun exportToGallery(context: Context, chain: EQChain, sampleRate: Float): String? {
        val bmp = render(chain, sampleRate)
        val filename = "MEATrec_EQ_${System.currentTimeMillis()}.png"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MEATrec")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out: OutputStream ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            uri.toString()
        } else {
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val dir = java.io.File(pictures, "MEATrec").apply { mkdirs() }
            val outFile = java.io.File(dir, filename)
            outFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            outFile.absolutePath
        }
    }

    private fun render(chain: EQChain, sampleRate: Float): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = AndroidCanvas(bmp)
        // Background
        c.drawColor(AndroidColor.parseColor("#0C0C10"))

        // Grid
        val gridPaint = Paint().apply {
            color = AndroidColor.parseColor("#557B8189")
            strokeWidth = 1.5f
            isAntiAlias = true
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 12f), 0f)
        }
        c.drawLine(0f, H / 2f, W.toFloat(), H / 2f, gridPaint)

        // Curve
        val response = computeResponse(chain, sampleRate)
        val curvePaint = Paint().apply {
            color = AndroidColor.parseColor("#FA4616")
            strokeWidth = 6f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path()
        for ((i, db) in response.withIndex()) {
            val x = i.toFloat() / (response.size - 1) * W
            val t = (db.coerceIn(-18f, 18f) + 18f) / 36f
            val y = (1f - t) * H
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // Glow
        val glow = Paint(curvePaint).apply { strokeWidth = 20f; alpha = 64 }
        c.drawPath(path, glow)
        c.drawPath(path, curvePaint)

        // Yellow handles
        val handlePaint = Paint().apply { color = AndroidColor.parseColor("#FFC72C"); isAntiAlias = true }
        for (band in chain.bands.filter { it.enabled }) {
            val x = freqToX(band.frequencyHz, W.toFloat())
            val t = (band.gainDb.coerceIn(-18f, 18f) + 18f) / 36f
            val y = (1f - t) * H
            c.drawCircle(x, y, 18f, handlePaint)
            c.drawCircle(x, y, 10f, Paint().apply { color = AndroidColor.parseColor("#0C0C10"); isAntiAlias = true })
        }

        // Branding strip at bottom
        val brandPaint = Paint().apply { color = AndroidColor.parseColor("#FFC72C"); textSize = 30f; isAntiAlias = true; isFakeBoldText = true }
        c.drawText("MEATrec EQ", 24f, H - 24f, brandPaint)

        return bmp
    }

    private fun freqToX(freqHz: Float, width: Float): Float {
        val t = (ln(freqHz.toDouble()) - ln(20.0)) / (ln(20_000.0) - ln(20.0))
        return t.toFloat().coerceIn(0f, 1f) * width
    }

    private fun computeResponse(chain: EQChain, sampleRate: Float, bins: Int = 512): FloatArray {
        val active = if (chain.bypassed) emptyList() else chain.bands.filter { it.enabled && !it.muted }
        if (active.isEmpty()) return FloatArray(bins) { 0f }
        val out = FloatArray(bins)
        for (i in 0 until bins) {
            val t = i.toDouble() / (bins - 1)
            val freq = exp(ln(20.0) + t * (ln(20_000.0) - ln(20.0)))
            var totalDb = 0.0
            for (band in active) {
                for (biq in BiquadCoeffs.cascadeForBand(band, sampleRate)) {
                    val coef = biq.coeffs()
                    val omega = 2 * Math.PI * freq / sampleRate
                    val cosW = cos(omega); val sinW = sin(omega)
                    val cos2W = cos(2 * omega); val sin2W = sin(2 * omega)
                    val numR = coef[0] + coef[1] * cosW + coef[2] * cos2W
                    val numI = -coef[1] * sinW - coef[2] * sin2W
                    val denR = 1.0 + coef[3] * cosW + coef[4] * cos2W
                    val denI = -coef[3] * sinW - coef[4] * sin2W
                    val num = sqrt(numR * numR + numI * numI)
                    val den = sqrt(denR * denR + denI * denI).coerceAtLeast(1e-12)
                    totalDb += 20.0 * log10(num / den)
                }
            }
            out[i] = totalDb.toFloat().coerceIn(-18f, 18f)
        }
        return out
    }
}
