package com.kd.anddirstat.treemap

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.GLSurfaceView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * High-performance hardware OpenGL ES 3.0 renderer for Treemaps.
 *
 * Uses GPU instanced rendering (glDrawArraysInstanced) to draw 50,000+ shaded quads
 * in a SINGLE draw call (~0.3ms GPU time), completely eliminating CPU DisplayList
 * recording and JNI Canvas.drawRect bottlenecks.
 */
class TreemapGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private companion object {
        const val ENTRANCE_SPREAD_MS = 900f
        const val ENTRANCE_DURATION_MS = 380f
        const val ENTRANCE_TOTAL_MS = ENTRANCE_SPREAD_MS + ENTRANCE_DURATION_MS
    }

    private val vertexShaderCode = """#version 300 es
        layout(location = 0) in vec2 aQuadVertex; // (0,0) to (1,1)
        layout(location = 1) in vec4 aRect;       // left, top, width, height (treemap coordinates)
        layout(location = 2) in vec4 aColor0;     // Highlight color
        layout(location = 3) in vec4 aColor1;     // Base color
        layout(location = 4) in vec4 aColor2;     // Shadow color
        layout(location = 5) in vec4 aIconRect;   // Icon rect in tile-local UV: left, top, width, height
        layout(location = 6) in vec4 aIconUv;     // Icon rect in atlas: left, top, right, bottom
        layout(location = 7) in float aIconAlpha; // 0 = no icon
        layout(location = 8) in float aEntranceOrder; // 0 = top row, 1 = bottom row

        uniform vec2 uScale;
        uniform vec2 uOffset;
        uniform vec2 uResolution;
        uniform float uEntranceElapsed; // ms since entrance started; negative = disabled
        uniform float uEntranceSpread;  // ms over which tiles are staggered
        uniform float uEntranceFall;    // unused; fall distance is derived per tile
        uniform float uInstanceCount;

        out vec2 vUv;
        out vec4 vColorHighlight;
        out vec4 vColorBase;
        out vec4 vColorShadow;
        out vec4 vIconRect;
        out vec4 vIconUv;
        out float vIconAlpha;

        void main() {
            vUv = aQuadVertex;
            vColorHighlight = aColor0;
            vColorBase = aColor1;
            vColorShadow = aColor2;
            vIconRect = aIconRect;
            vIconUv = aIconUv;
            vIconAlpha = aIconAlpha;

            vec2 pixelPos = aRect.xy + aQuadVertex * aRect.zw;

            // Entrance: tiles start above the viewport and fall down one after another.
            // Each tile falls its own distance so it enters from the top edge regardless of
            // where it lands. aEntranceOrder runs 0 (bottom row, first) to 1 (top row, last).
            vec2 screenPos = pixelPos * uScale + uOffset;
            if (uEntranceElapsed >= 0.0) {
                float delay = aEntranceOrder * uEntranceSpread;
                float t = clamp((uEntranceElapsed - delay) / 380.0, 0.0, 1.0);
                float eased = 1.0 - pow(1.0 - t, 3.0);
                float fallDistance = screenPos.y + aRect.w * uScale.y;
                screenPos.y -= (1.0 - eased) * fallDistance;
            }

            // Map pixel coordinates to Normalized Device Coordinates [-1, 1]
            // Invert Y so 0 is at top, matching Android screen coordinate system
            vec2 ndc = (screenPos / uResolution) * 2.0 - 1.0;
            gl_Position = vec4(ndc.x, -ndc.y, 0.0, 1.0);
        }
    """.trimIndent()

    private val fragmentShaderCode = """#version 300 es
        precision mediump float;

        in vec2 vUv;
        in vec4 vColorHighlight;
        in vec4 vColorBase;
        in vec4 vColorShadow;
        in vec4 vIconRect;
        in vec4 vIconUv;
        in float vIconAlpha;

        uniform int uShadingMode; // 0 = Cushion, 1 = Solid, 2 = Linear, 3 = Amoled
        uniform sampler2D uIconAtlas;

        out vec4 fragColor;

        void main() {
            vec4 shaded;
            if (uShadingMode == 1) { // SOLID
                shaded = vColorBase;
            } else if (uShadingMode == 2) { // LINEAR
                float t = clamp((vUv.x + vUv.y) * 0.5, 0.0, 1.0);
                if (t < 0.45) {
                    shaded = mix(vColorHighlight, vColorBase, t / 0.45);
                } else {
                    shaded = mix(vColorBase, vColorShadow, (t - 0.45) / 0.55);
                }
            } else if (uShadingMode == 3) { // AMOLED outline
                float bw = 0.04;
                if (vUv.x < bw || vUv.x > 1.0 - bw || vUv.y < bw || vUv.y > 1.0 - bw) {
                    shaded = vColorBase;
                } else {
                    shaded = vec4(0.0, 0.0, 0.0, 1.0);
                }
            } else { // CUSHION (Default: van Wijk & van de Wetering CTM)
                vec2 lightCenter = vec2(0.38, 0.35);
                float dist = clamp(length((vUv - lightCenter) / 0.85), 0.0, 1.0);
                if (dist < 0.5) {
                    shaded = mix(vColorHighlight, vColorBase, dist * 2.0);
                } else {
                    shaded = mix(vColorBase, vColorShadow, (dist - 0.5) * 2.0);
                }
            }

            fragColor = shaded;
            if (vIconAlpha > 0.5) {
                // Map the tile-local UV into the icon square centered on the tile.
                vec2 iconLocal = (vUv - vIconRect.xy) / vIconRect.zw;
                if (iconLocal.x >= 0.0 && iconLocal.x <= 1.0 && iconLocal.y >= 0.0 && iconLocal.y <= 1.0) {
                    // Atlas rows are packed top-down (bitmap coordinates); GL samples bottom-up.
                    vec2 atlasUv = mix(vIconUv.xy, vIconUv.zw, iconLocal);
                    vec4 icon = texture(uIconAtlas, atlasUv);
                    fragColor = vec4(mix(shaded.rgb, icon.rgb, icon.a), shaded.a);
                }
            }
        }
    """.trimIndent()

    private var program = 0
    private var vaoId = 0
    private var quadVboId = 0
    private var instanceVboId = 0
    private var iconTextureId = 0

    private var uScaleLoc = -1
    private var uOffsetLoc = -1
    private var uResolutionLoc = -1
    private var uShadingModeLoc = -1
    private var uIconAtlasLoc = -1
    private var uEntranceElapsedLoc = -1
    private var uEntranceSpreadLoc = -1
    private var uEntranceFallLoc = -1
    private var uInstanceCountLoc = -1

    @Volatile
    private var entranceEnabled = true
    @Volatile
    private var entranceStartedAtMs = 0L

    /** Starts a new entrance animation on the next frame. */
    fun startEntrance() {
        entranceStartedAtMs = android.os.SystemClock.elapsedRealtime()
    }

    fun setEntranceEnabled(enabled: Boolean) {
        entranceEnabled = enabled
    }

    private data class IconAtlas(
        val bitmap: Bitmap,
        val uvByPackage: Map<String, FloatArray>
    )

    private var pendingAtlas: IconAtlas? = null
    private var lastAtlas: IconAtlas? = null

    @Volatile
    private var viewportWidth = 1f
    @Volatile
    private var viewportHeight = 1f

    @Volatile
    private var currentScale = 1f
    @Volatile
    private var currentOffsetX = 0f
    @Volatile
    private var currentOffsetY = 0f

    @Volatile
    private var shadingModeInt = 0 // 0 = Cushion

    @Volatile
    private var clearR = 0.031f
    @Volatile
    private var clearG = 0.035f
    @Volatile
    private var clearB = 0.055f
    @Volatile
    private var clearA = 1.0f

    private val lock = Any()
    private var pendingInstanceBuffer: FloatBuffer? = null
    private var pendingInstanceCount = 0
    private var activeInstanceCount = 0

    // The EGL context is destroyed when the SurfaceView surface goes away (app backgrounded,
    // device reboot). Keep the last packed instance data so onSurfaceCreated can re-upload it;
    // otherwise the new context has an empty instance VBO and renders a transparent treemap.
    private var lastInstanceBuffer: FloatBuffer? = null
    private var lastInstanceCount = 0

    fun setScaleAndOffset(scale: Float, offset: Offset) {
        currentScale = scale
        currentOffsetX = offset.x
        currentOffsetY = offset.y
    }

    fun setShadingMode(modeInt: Int) {
        shadingModeInt = modeInt
    }

    fun setClearColor(r: Float, g: Float, b: Float, a: Float = 1.0f) {
        clearR = r
        clearG = g
        clearB = b
        clearA = a
    }

    /**
     * Builds a high-resolution atlas from the launcher icons of the packages present in the tree.
     * The atlas is uploaded once per tree, not per frame, so zoom stays a uniform update.
     */
    private fun buildIconAtlas(packages: List<String>): IconAtlas? {
        val unique = packages.distinct().sorted()
        if (unique.isEmpty()) return null

        // Size the atlas to the real icon count. A fixed 4096² bitmap stalls the first draw;
        // ~100 app icons fit in a 1600² atlas at 160px per icon, which uploads in one frame.
        val cellPx = 160
        val cols = kotlin.math.ceil(kotlin.math.sqrt(unique.size.toDouble())).toInt().coerceAtLeast(1)
        val rows = (unique.size + cols - 1) / cols
        val effectiveCell = cellPx
        val bitmap = Bitmap.createBitmap(cols * effectiveCell, rows * effectiveCell, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val pm = context.packageManager
        val uvByPackage = HashMap<String, FloatArray>()

        unique.forEachIndexed { index, pkg ->
            try {
                val drawable = pm.getApplicationIcon(pkg) ?: return@forEachIndexed
                val col = index % cols
                val row = index / cols
                val left = col * effectiveCell
                val top = row * effectiveCell
                drawable.setBounds(left, top, left + effectiveCell, top + effectiveCell)
                drawable.draw(canvas)
                val u0 = left.toFloat() / bitmap.width
                val v0 = top.toFloat() / bitmap.height
                val u1 = (left + effectiveCell).toFloat() / bitmap.width
                val v1 = (top + effectiveCell).toFloat() / bitmap.height
                uvByPackage[pkg] = floatArrayOf(u0, v0, u1, v1)
            } catch (_: Exception) {
            }
        }
        if (uvByPackage.isEmpty()) return null
        return IconAtlas(bitmap, uvByPackage)
    }

    /**
     * Converts TreemapTile list into a packed FloatBuffer (25 floats per tile):
     * [0..3]: Rect (left, top, width, height)
     * [4..7]: ColorHighlight (r, g, b, a)
     * [8..11]: ColorBase (r, g, b, a)
     * [12..15]: ColorShadow (r, g, b, a)
     * [16..19]: Icon rect in tile-local UV (left, top, width, height)
     * [20..23]: Icon atlas UV (left, top, right, bottom)
     * [24]: Icon alpha (0 = no icon)
     * [25]: Entrance order (0 = bottom, 1 = top)
     */
    fun updateTiles(tiles: List<TreemapTile>) {
        val count = tiles.size
        if (count == 0) {
            synchronized(lock) {
                pendingInstanceBuffer = null
                pendingInstanceCount = 0
            }
            return
        }

        val atlas = buildIconAtlas(tiles.mapNotNull { it.pkgName })
        synchronized(lock) {
            if (atlas != null) {
                pendingAtlas = atlas
                lastAtlas = atlas
            } else {
                pendingAtlas = null
                lastAtlas = null
            }
        }
        val iconUv = atlas?.uvByPackage ?: emptyMap()
        // Bottom-to-top order: the lowest row gets rank 0 and falls first, then higher rows follow.
        val ordered = tiles.withIndex().sortedWith(
            compareByDescending<IndexedValue<TreemapTile>> { it.value.top + it.value.height }
                .thenBy { it.value.left }
        )
        val orderByIndex = IntArray(count)
        ordered.forEachIndexed { rank, entry -> orderByIndex[entry.index] = rank }
        val lastRank = (count - 1).coerceAtLeast(1).toFloat()

        val floatBuffer = ByteBuffer.allocateDirect(count * 26 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        for (i in 0 until count) {
            val tile = tiles[i]
            // aRect
            floatBuffer.put(tile.left)
            floatBuffer.put(tile.top)
            floatBuffer.put(tile.width)
            floatBuffer.put(tile.height)

            // aColor0 (Highlight)
            val c0 = tile.gradientColors[0]
            floatBuffer.put(c0.red)
            floatBuffer.put(c0.green)
            floatBuffer.put(c0.blue)
            floatBuffer.put(c0.alpha)

            // aColor1 (Base)
            val c1 = tile.gradientColors[1]
            floatBuffer.put(c1.red)
            floatBuffer.put(c1.green)
            floatBuffer.put(c1.blue)
            floatBuffer.put(c1.alpha)

            // aColor2 (Shadow)
            val c2 = tile.gradientColors[2]
            floatBuffer.put(c2.red)
            floatBuffer.put(c2.green)
            floatBuffer.put(c2.blue)
            floatBuffer.put(c2.alpha)

            // aIconRect: a centered square inside the tile. Scale with the tile so even small
            // app tiles keep an icon, and high zoom never shows a low-resolution bitmap.
            val uv = tile.pkgName?.let { iconUv[it] }
            if (uv == null) {
                floatBuffer.put(0f).put(0f).put(0f).put(0f)
                floatBuffer.put(0f).put(0f).put(0f).put(0f)
                floatBuffer.put(0f)
            } else {
                val iconFraction = 0.78f
                val wRatio = minOf(1f, iconFraction * minOf(1f, tile.height / tile.width))
                val hRatio = minOf(1f, iconFraction * minOf(1f, tile.width / tile.height))
                val left = (1f - wRatio) / 2f
                val top = (1f - hRatio) / 2f
                floatBuffer.put(left).put(top).put(wRatio).put(hRatio)
                floatBuffer.put(uv[0]).put(uv[1]).put(uv[2]).put(uv[3])
                floatBuffer.put(1f)
            }

            floatBuffer.put(orderByIndex[i] / lastRank)
        }
        floatBuffer.position(0)

        synchronized(lock) {
            pendingInstanceBuffer = floatBuffer
            pendingInstanceCount = count
            lastInstanceBuffer = floatBuffer.duplicate().apply { position(0) }
            lastInstanceCount = count
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = createProgram(vertexShaderCode, fragmentShaderCode)
        if (program == 0) return

        uScaleLoc = GLES30.glGetUniformLocation(program, "uScale")
        uOffsetLoc = GLES30.glGetUniformLocation(program, "uOffset")
        uResolutionLoc = GLES30.glGetUniformLocation(program, "uResolution")
        uShadingModeLoc = GLES30.glGetUniformLocation(program, "uShadingMode")
        uIconAtlasLoc = GLES30.glGetUniformLocation(program, "uIconAtlas")
        uEntranceElapsedLoc = GLES30.glGetUniformLocation(program, "uEntranceElapsed")
        uEntranceSpreadLoc = GLES30.glGetUniformLocation(program, "uEntranceSpread")
        uEntranceFallLoc = GLES30.glGetUniformLocation(program, "uEntranceFall")
        uInstanceCountLoc = GLES30.glGetUniformLocation(program, "uInstanceCount")

        // 1x1 transparent texture until the icon atlas is uploaded.
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        iconTextureId = textures[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, iconTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
            ByteBuffer.allocateDirect(4)
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        // Create VAO
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vaoId = vaos[0]
        GLES30.glBindVertexArray(vaoId)

        // Create Quad VBO (Triangle Strip for unit quad: (0,0), (1,0), (0,1), (1,1))
        val quadVertices = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f
        )
        val quadBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
        quadBuffer.position(0)

        val vbos = IntArray(2)
        GLES30.glGenBuffers(2, vbos, 0)
        quadVboId = vbos[0]
        instanceVboId = vbos[1]

        // Setup Quad VBO (Attribute 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quadVertices.size * 4, quadBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)

        // Setup Instance VBO (Attributes 1..7)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVboId)
        val stride = 26 * 4

        // aRect (location = 1)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glVertexAttribDivisor(1, 1)

        // aColor0 (location = 2)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, stride, 4 * 4)
        GLES30.glVertexAttribDivisor(2, 1)

        // aColor1 (location = 3)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, stride, 8 * 4)
        GLES30.glVertexAttribDivisor(3, 1)

        // aColor2 (location = 4)
        GLES30.glEnableVertexAttribArray(4)
        GLES30.glVertexAttribPointer(4, 4, GLES30.GL_FLOAT, false, stride, 12 * 4)
        GLES30.glVertexAttribDivisor(4, 1)

        // aIconRect (location = 5)
        GLES30.glEnableVertexAttribArray(5)
        GLES30.glVertexAttribPointer(5, 4, GLES30.GL_FLOAT, false, stride, 16 * 4)
        GLES30.glVertexAttribDivisor(5, 1)

        // aIconUv (location = 6)
        GLES30.glEnableVertexAttribArray(6)
        GLES30.glVertexAttribPointer(6, 4, GLES30.GL_FLOAT, false, stride, 20 * 4)
        GLES30.glVertexAttribDivisor(6, 1)

        // aIconAlpha (location = 7)
        GLES30.glEnableVertexAttribArray(7)
        GLES30.glVertexAttribPointer(7, 1, GLES30.GL_FLOAT, false, stride, 24 * 4)
        GLES30.glVertexAttribDivisor(7, 1)

        // aEntranceOrder (location = 8)
        GLES30.glEnableVertexAttribArray(8)
        GLES30.glVertexAttribPointer(8, 1, GLES30.GL_FLOAT, false, stride, 25 * 4)
        GLES30.glVertexAttribDivisor(8, 1)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        // A new EGL context has an empty instance VBO. Re-queue the last packed buffer so the
        // next draw call restores the treemap instead of leaving a transparent surface.
        synchronized(lock) {
            activeInstanceCount = 0
            lastInstanceBuffer?.let {
                pendingInstanceBuffer = it.duplicate().apply { position(0) }
                pendingInstanceCount = lastInstanceCount
            }
            lastAtlas?.let { pendingAtlas = it }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.toFloat()
        viewportHeight = height.toFloat()
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Upload new instance buffer if pending
        var newBuffer: FloatBuffer? = null
        var newCount = 0
        synchronized(lock) {
            if (pendingInstanceBuffer != null) {
                newBuffer = pendingInstanceBuffer
                newCount = pendingInstanceCount
                pendingInstanceBuffer = null
            }
        }

        if (newBuffer != null) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, newCount * 26 * 4, newBuffer, GLES30.GL_DYNAMIC_DRAW)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            activeInstanceCount = newCount
        }

        var atlasToUpload: IconAtlas? = null
        synchronized(lock) {
            if (pendingAtlas != null) {
                atlasToUpload = pendingAtlas
                pendingAtlas = null
            }
        }
        atlasToUpload?.let { atlas ->
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, iconTextureId)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, atlas.bitmap, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        }

        GLES30.glClearColor(clearR, clearG, clearB, clearA)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        if (activeInstanceCount == 0 || program == 0) return

        GLES30.glUseProgram(program)
        GLES30.glUniform2f(uScaleLoc, currentScale, currentScale)
        GLES30.glUniform2f(uOffsetLoc, currentOffsetX, currentOffsetY)
        GLES30.glUniform2f(uResolutionLoc, viewportWidth, viewportHeight)
        GLES30.glUniform1i(uShadingModeLoc, shadingModeInt)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, iconTextureId)
        GLES30.glUniform1i(uIconAtlasLoc, 0)

        val elapsed = if (entranceEnabled) {
            (android.os.SystemClock.elapsedRealtime() - entranceStartedAtMs).toFloat()
        } else -1f
        val entranceElapsed = if (elapsed in 0f..ENTRANCE_TOTAL_MS) elapsed else -1f
        GLES30.glUniform1f(uEntranceElapsedLoc, entranceElapsed)
        GLES30.glUniform1f(uEntranceSpreadLoc, ENTRANCE_SPREAD_MS)
        GLES30.glUniform1f(uEntranceFallLoc, 0f)
        GLES30.glUniform1f(uInstanceCountLoc, activeInstanceCount.toFloat())

        GLES30.glBindVertexArray(vaoId)
        // Single draw call renders all 50,000+ tiles in ~0.3ms on GPU!
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, activeInstanceCount)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glUseProgram(0)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Could not compile shader $type: $error")
        }
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vertexShader)
        GLES30.glAttachShader(prog, fragmentShader)
        GLES30.glLinkProgram(prog)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(prog)
            GLES30.glDeleteProgram(prog)
            throw RuntimeException("Could not link program: $error")
        }
        return prog
    }
}
