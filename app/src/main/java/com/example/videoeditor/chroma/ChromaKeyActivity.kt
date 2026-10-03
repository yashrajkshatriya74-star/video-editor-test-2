package com.example.videoeditor.chroma

import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.videoeditor.databinding.ActivityChromaKeyBinding

/**
 * Standalone chroma-key tool: preview + threshold/smoothing sliders.
 * Feed the renderer's SurfaceTexture from CameraX (live) or a MediaCodec video
 * decoder (recorded footage) — both just need to render into the SurfaceTexture
 * handed back via the onSurfaceReady callback.
 */
class ChromaKeyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChromaKeyBinding
    private lateinit var renderer: ChromaKeyRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChromaKeyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderer = ChromaKeyRenderer { surfaceTexture ->
            // Hook your video/camera frame source here, e.g.:
            // videoDecoder.setOutputSurface(Surface(surfaceTexture))
        }

        binding.glSurfaceView.apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        binding.thresholdSlider.addOnChangeListener { _, value, _ ->
            renderer.threshold = value
        }
        binding.smoothingSlider.addOnChangeListener { _, value, _ ->
            renderer.smoothing = value
        }
    }

    override fun onResume() {
        super.onResume()
        binding.glSurfaceView.onResume()
    }

    override fun onPause() {
        binding.glSurfaceView.onPause()
        super.onPause()
    }
}
