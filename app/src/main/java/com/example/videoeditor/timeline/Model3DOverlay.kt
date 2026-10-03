package com.example.videoeditor.timeline

import android.graphics.Bitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings

/**
 * Picks the nearest pre-rendered [Model3DFrameRenderer.RenderedFrame] for a given
 * presentation time and hands it to Media3's overlay compositor. Position/scale is
 * fixed (centered, ~60% of frame height) for now — swap `StaticOverlaySettings` for
 * per-frame settings if you want drag-to-position or scale-over-time.
 *
 * Note on the base class: `BitmapOverlay` is Media3's convenience class for exactly this
 * ("give me a Bitmap per timestamp, I'll composite it") — the API name/shape has been
 * stable across recent Media3 releases, so this one is lower-risk than [FadeEffect]'s
 * raw shader approach, but still pin-and-verify against whatever Media3 version you use.
 */
@UnstableApi
class Model3DOverlay(
    private val frames: List<Model3DFrameRenderer.RenderedFrame>
) : BitmapOverlay() {

    private val settings = StaticOverlaySettings.Builder()
        .setScale(0.6f, 0.6f)
        // Centered by default; StaticOverlaySettings positions via a transform matrix —
        // adjust here (or make this per-clip) if you want the model anchored elsewhere.
        .build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (frames.isEmpty()) throw IllegalStateException("No pre-rendered 3D frames to overlay")
        val nearest = frames.minByOrNull { kotlin.math.abs(it.presentationTimeUs - presentationTimeUs) }!!
        return nearest.bitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
}
