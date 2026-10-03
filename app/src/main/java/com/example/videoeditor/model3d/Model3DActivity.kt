package com.example.videoeditor.model3d

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.videoeditor.databinding.ActivityModel3dBinding
import com.google.android.filament.Engine
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Imports and previews a 3D model (.glb/.gltf) so it can be composited into a
 * video project as an overlay layer (AR-style prop, watermark, animated logo, etc.).
 *
 * Uses Filament's ModelViewer, which wraps gltfio (glTF 2.0 loader) + Filament's
 * physically-based renderer. Filament outputs to a regular Android Surface, so its
 * frame can later be composited with the video's OpenGL pipeline (same approach as
 * ChromaKeyRenderer) to render 3D content directly on top of a video track.
 */
class Model3DActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModel3dBinding
    private lateinit var modelViewer: ModelViewer

    private val pickModel = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadModel(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModel3dBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelViewer = ModelViewer(binding.surfaceView)
        binding.surfaceView.setOnTouchListener { _, event -> modelViewer.onTouchEvent(event); true }

        binding.btnImportModel.setOnClickListener {
            // glTF/GLB filter — Android doesn't have a standard MIME type for these,
            // so accept common containers and filter by extension after pick.
            pickModel.launch("*/*")
        }
    }

    private fun loadModel(uri: Uri) {
        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val buffer = FileInputStreamBuffer(pfd.fileDescriptor)
            when {
                uri.toString().endsWith(".glb", ignoreCase = true) ->
                    modelViewer.loadModelGlb(buffer)
                else ->
                    modelViewer.loadModelGltf(buffer) { uriStr -> resolveGltfResource(uriStr) }
            }
            modelViewer.transformToUnitCube()
        }
    }

    private fun resolveGltfResource(relativeUri: String): ByteBuffer? {
        // For .gltf (not .glb), external buffers/textures referenced by relative
        // path need to be resolved here (e.g. read sibling files from the same
        // folder the user picked, or from a bundled asset directory).
        return null
    }

    private fun FileInputStreamBuffer(fd: java.io.FileDescriptor): ByteBuffer {
        java.io.FileInputStream(fd).channel.use { channel ->
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    override fun onDestroy() {
        modelViewer.destroyModel()
        super.onDestroy()
    }
}
