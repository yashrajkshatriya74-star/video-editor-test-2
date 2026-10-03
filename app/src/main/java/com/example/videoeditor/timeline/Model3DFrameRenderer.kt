package com.example.videoeditor.timeline

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.gltfio.AnimatorInstance
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Renders a rigged/animated .glb model to a sequence of ARGB bitmaps with a transparent
 * background, sampled at [frameRateFps] across [durationUs]. These frames are then used
 * as a video overlay track (see [Model3DOverlay]) so the model appears composited into
 * the exported video.
 *
 * IMPORTANT — read before relying on this:
 * Filament is normally driven by its own SurfaceView/EGL surface. This class instead uses
 * Filament's *offscreen* rendering path (an off-screen SwapChain + a Texture-backed render
 * target, read back via `Renderer.readPixels`), which is documented for screenshot/headless
 * use cases but is far less commonly exercised than the on-screen path. If model rendering
 * comes back blank or black:
 *   - double check the IndirectLight/skybox setup (a scene with no light source renders black)
 *   - confirm your Filament version's offscreen SwapChain flags match `SwapChain.CONFIG_TRANSPARENT`
 *   - this is the #1 place to expect friction in the whole project — budget real debugging time
 *
 * Also note the memory/CPU cost: rendering is done synchronously, frame by frame, before
 * export starts. A 5s overlay at 15fps is 75 bitmaps in memory at once — fine for short
 * overlays, not for minutes-long ones. For longer overlays, write each frame to a PNG in
 * cache and stream them back in [Model3DOverlay] instead of holding a List<Bitmap>.
 */
@UnstableApi
class Model3DFrameRenderer(private val context: Context) {

    data class RenderedFrame(val presentationTimeUs: Long, val bitmap: Bitmap)

    fun renderFrames(
        modelUri: Uri,
        durationUs: Long,
        frameRateFps: Int = 15,
        width: Int = 720,
        height: Int = 1280
    ): List<RenderedFrame> {
        val engine = Engine.create()
        val entityManager = EntityManager.get()
        val renderer = engine.createRenderer()
        val scene = engine.createScene()
        val view = engine.createView()
        val camera = engine.createCamera(entityManager.create())

        // Transparent background so the model composites over video underneath it.
        val swapChain = engine.createSwapChain(width, height, SwapChain.CONFIG_TRANSPARENT.toLong())
        view.scene = scene
        view.camera = camera
        view.viewport = Viewport(0, 0, width, height)
        view.blendMode = View.BlendMode.TRANSLUCENT

        val assetLoader = AssetLoader(engine, UbershaderProvider(engine), entityManager)
        val resourceLoader = ResourceLoader(engine)

        val buffer = readUriToBuffer(modelUri)
        val asset: FilamentAsset = assetLoader.createAsset(buffer)
            ?: throw IllegalStateException("Could not load model for overlay: $modelUri")
        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)

        val animator: AnimatorInstance? = asset.instance.animator
        val animationDurationSec = if (animator != null && animator.animationCount > 0) {
            animator.getAnimationDuration(0)
        } else 0f

        // Basic framing: center the camera on the asset's bounding box.
        val center = asset.boundingBox.center
        val halfExtent = asset.boundingBox.halfExtent
        val radius = maxOf(halfExtent[0], halfExtent[1], halfExtent[2]) * 3f
        camera.lookAt(
            center[0].toDouble(), center[1].toDouble(), (center[2] + radius).toDouble(),
            center[0].toDouble(), center[1].toDouble(), center[2].toDouble(),
            0.0, 1.0, 0.0
        )
        camera.setProjection(45.0, width.toDouble() / height, 0.1, radius * 10.0, com.google.android.filament.Camera.Fov.VERTICAL)

        val frameCount = ((durationUs / 1_000_000.0) * frameRateFps).toInt().coerceAtLeast(1)
        val frames = mutableListOf<RenderedFrame>()

        for (i in 0 until frameCount) {
            val tSec = i.toFloat() / frameRateFps
            if (animator != null && animationDurationSec > 0) {
                animator.applyAnimation(0, tSec % animationDurationSec)
                animator.updateBoneMatrices()
            }

            renderer.beginFrame(swapChain, System.nanoTime())
            renderer.render(view)
            val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4)
            renderer.readPixels(
                0, 0, width, height,
                Texture.PixelBufferDescriptor(
                    pixelBuffer, Texture.Format.RGBA, Texture.Type.UBYTE
                )
            )
            renderer.endFrame()

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            pixelBuffer.rewind()
            bitmap.copyPixelsFromBuffer(pixelBuffer)

            val presentationTimeUs = (i.toLong() * 1_000_000L) / frameRateFps
            frames.add(RenderedFrame(presentationTimeUs, bitmap))
        }

        // Cleanup — Filament resources are not GC'd, must be released explicitly.
        assetLoader.destroyAsset(asset)
        resourceLoader.destroy()
        assetLoader.destroy()
        engine.destroySwapChain(swapChain)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyRenderer(renderer)
        engine.destroyCameraComponent(camera.entity)
        entityManager.destroy(camera.entity)
        engine.destroy()

        return frames
    }

    private fun readUriToBuffer(uri: Uri): ByteBuffer {
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            }
        }
    }
}
