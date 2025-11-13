package com.example.citewise_mobile

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.citewise_mobile.annotation.PenOverlayView
import com.example.citewise_mobile.annotation.ZoomFrameLayout
import com.example.citewise_mobile.api.RetrofitInstance
import com.example.citewise_mobile.data.NetResult
import com.example.citewise_mobile.data.ServiceReviewsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URLConnection

class DocumentViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_REQUEST_ID = "extra_request_id"
        const val EXTRA_DISPLAY_NAME = "extra_display_name" // custom base name for _annotated
        const val EXTRA_CAN_ANNOTATE = "extra_can_annotate"
    }

    // Top bar
    private lateinit var btnClose: ImageButton
    private lateinit var btnToggleDraw: ImageButton
    private lateinit var titleView: TextView

    // Content
    private lateinit var zoomContainer: ZoomFrameLayout
    private lateinit var pager: ViewPager2
    private lateinit var imgSingle: ImageView
    private lateinit var overlay: PenOverlayView

    // HUD
    private lateinit var modePill: TextView
    private lateinit var zoomHud: TextView

    // Tools
    private lateinit var toolsBar: View
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnColor: ImageButton
    private lateinit var btnStroke: ImageButton
    private lateinit var btnSave: ImageButton
    private lateinit var colorSwatch: View

    // PDF
    private var pdfRenderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var pdfAdapter: PdfPagerAdapter? = null

    // Input data
    private var filePath: String? = null
    private var requestId: String? = null
    private var displayName: String? = null
    private var canAnnotate: Boolean = false

    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_viewer)

        // Read flags BEFORE wiring UI logic
        filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
        canAnnotate = intent.getBooleanExtra(EXTRA_CAN_ANNOTATE, false)

        initViews()

        if (filePath.isNullOrBlank()) {
            toast("No file to preview")
            finish()
            return
        }

        val file = File(filePath!!)
        titleView.text = displayName?.takeIf { it.isNotBlank() } ?: file.name

        val mime = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        if (mime == "application/pdf") {
            setupPdf(file)
        } else if (mime.startsWith("image/")) {
            showSingleImage(file)
        } else {
            pager.visibility = View.GONE
            imgSingle.visibility = View.GONE
            toast("Preview not supported for this file type.")
        }

        // Start in NAVIGATE mode
        setDrawing(false)
        updateZoomHud()
    }

    private fun initViews() {
        // Top bar
        btnClose = findViewById(R.id.btnClose)
        btnToggleDraw = findViewById(R.id.btnToggleDraw)
        titleView = findViewById(R.id.tvTitle)

        btnClose.setOnClickListener { finish() }

        // Content
        zoomContainer = findViewById(R.id.zoomContainer)
        pager = findViewById(R.id.pager)
        imgSingle = findViewById(R.id.imageSingle)
        overlay = findViewById(R.id.overlay)

        // HUD
        modePill = findViewById(R.id.modePill)
        zoomHud = findViewById(R.id.zoomHud)

        // Tools row
        toolsBar = findViewById(R.id.toolsBar)
        colorSwatch = findViewById(R.id.colorSwatch)
        btnColor = findViewById(R.id.btnColor)
        btnStroke = findViewById(R.id.btnStroke)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnSave = findViewById(R.id.btnSave)

        // Annotation allowed?
        if (canAnnotate) {
            // Wire annotation interactions
            btnToggleDraw.setOnClickListener { setDrawing(!overlay.drawingEnabled) }

            btnUndo.setOnClickListener { overlay.undo() }
            btnRedo.setOnClickListener { overlay.redo() }
            btnColor.setOnClickListener { pickColor() }
            btnStroke.setOnClickListener { pickStroke() }
            btnSave.setOnClickListener { saveAndMaybeUpload() }

            overlay.bringToFront()
            overlay.setOnTouchListener { _, _ ->
                if (overlay.drawingEnabled) overlay.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }

            zoomContainer.shouldAllowChildDraw = { overlay.drawingEnabled }
        } else {
            // VIEW-ONLY MODE (e.g. student or reading annotated file)
            overlay.drawingEnabled = false
            overlay.visibility = View.GONE

            toolsBar.visibility = View.GONE
            btnToggleDraw.visibility = View.GONE
            modePill.visibility = View.GONE

            // Never treat child as drawing surface
            zoomContainer.shouldAllowChildDraw = { false }
        }

        zoomContainer.onScaleOrPanChanged = {
            updatePagerSwiping()
            updateZoomHud()
        }
    }

    // ---------- PDF setup ----------
    private fun setupPdf(file: File) {
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(pfd!!)
            val pageCount = pdfRenderer?.pageCount ?: 0
            if (pageCount <= 0) {
                toast("Empty PDF")
                finish()
                return
            }

            pager.visibility = View.VISIBLE
            imgSingle.visibility = View.GONE

            val displayWidth = resources.displayMetrics.widthPixels
            pdfAdapter = PdfPagerAdapter(pdfRenderer!!, displayWidth)
            pager.adapter = pdfAdapter

            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    overlay.currentPageIndex = position
                    zoomContainer.resetZoom()
                    updatePagerSwiping()
                    updateZoomHud()
                }
            })
            overlay.currentPageIndex = 0
        } catch (t: Throwable) {
            toast("Unable to render PDF: ${t.message}")
        }
    }

    private fun showSingleImage(file: File) {
        pager.visibility = View.GONE
        imgSingle.visibility = View.VISIBLE
        overlay.currentPageIndex = 0
        imgSingle.setImageURI(Uri.fromFile(file))
    }

    override fun onDestroy() {
        super.onDestroy()
        try { pdfAdapter?.clear() } catch (_: Throwable) {}
        try { pdfRenderer?.close() } catch (_: Throwable) {}
        try { pfd?.close() } catch (_: Throwable) {}
    }

    // ---------- Draw/Navigate ----------
    private fun setDrawing(enable: Boolean) {
        // No-op in view-only mode
        if (!canAnnotate) return

        overlay.drawingEnabled = enable
        btnToggleDraw.alpha = if (enable) 1f else 0.7f
        updateModeUi()
        updatePagerSwiping()
    }

    private fun updateModeUi() {
        if (!canAnnotate) return

        if (overlay.drawingEnabled) {
            modePill.text = "DRAW"
            modePill.background = getDrawable(R.drawable.bg_pill_green)
        } else {
            modePill.text = "NAVIGATE"
            modePill.background = getDrawable(R.drawable.bg_pill_gray)
        }
    }

    private fun updateZoomHud() {
        val pct = (zoomContainer.currentScale() * 100f).toInt().coerceAtLeast(100)
        zoomHud.text = "${pct}%"
    }

    private fun updatePagerSwiping() {
        val zoomed = zoomContainer.currentScale() > 1f
        val drawing = overlay.drawingEnabled && canAnnotate
        // Enable ViewPager swipes only when not drawing AND not zoomed
        pager.isUserInputEnabled = !drawing && !zoomed
    }

    // ---------- Color & stroke ----------
    private val palette = intArrayOf(
        Color.RED,
        Color.BLUE,
        Color.BLACK,
        Color.GREEN,
        Color.MAGENTA,
        Color.CYAN,
        Color.DKGRAY,
        Color.rgb(255, 140, 0)
    )

    private fun pickColor() {
        if (!canAnnotate) return
        val names = arrayOf("Red", "Blue", "Black", "Green", "Magenta", "Cyan", "Dark Gray", "Orange")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Stroke color")
            .setItems(names) { _, which ->
                overlay.strokeColor = palette[which]
                updateSwatch()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateSwatch() {
        if (!canAnnotate) return
        val d = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(overlay.strokeColor)
        }
        colorSwatch.background = d
    }

    private fun pickStroke() {
        if (!canAnnotate) return
        val widths = arrayOf("2 px", "4 px", "6 px", "10 px", "14 px", "20 px")
        val values = floatArrayOf(2f, 4f, 6f, 10f, 14f, 20f)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Stroke width")
            .setItems(widths) { _, which ->
                overlay.strokeWidthPx = values[which]
                toast("Stroke: ${values[which].toInt()}px")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Save + Upload: <custom>_annotated.pdf ----------
    private fun saveAndMaybeUpload() {
        if (!canAnnotate) return

        val srcPath = filePath ?: run {
            toast("No source file.")
            return
        }
        val src = File(srcPath)

        ioScope.launch {
            try {
                val baseName = (displayName ?: src.nameWithoutExtension)
                    .removeSuffix(".pdf")
                    .trim()
                    .ifBlank { "document" }

                val safeBase = baseName.replace(Regex("""[\\/:*?"<>|]"""), "_")
                val out = File(cacheDir, "${safeBase}_annotated.pdf")

                exportFlattenedPdf(src, out)

                withContext(Dispatchers.Main) {
                    toast("Saved: ${out.name}")
                    val rid = requestId
                    if (!rid.isNullOrBlank()) uploadAnnotated(rid, out)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    toast("Save failed: ${t.message}")
                }
            }
        }
    }

    private fun exportFlattenedPdf(original: File, dest: File) {
        val mime = URLConnection.guessContentTypeFromName(original.name) ?: ""
        val document = PdfDocument()

        if (mime == "application/pdf") {
            val pfd = ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                val pageCount = renderer.pageCount
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val width = page.width
                    val height = page.height

                    val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val c = Canvas(b)
                    c.drawColor(Color.WHITE)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pdfPage = document.startPage(
                        PdfDocument.PageInfo.Builder(width, height, i + 1).create()
                    )
                    pdfPage.canvas.drawBitmap(b, 0f, 0f, null)
                    drawStrokesToCanvas(pdfPage.canvas, i, width, height)
                    document.finishPage(pdfPage)
                    b.recycle()
                }
            } finally {
                renderer.close()
                pfd.close()
            }
        } else {
            val bmp = BitmapFactory.decodeFile(original.absolutePath) ?: error("Bad image")
            val width = bmp.width
            val height = bmp.height
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(width, height, 1).create()
            )
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(bmp, 0f, 0f, null)
            drawStrokesToCanvas(page.canvas, 0, width, height)
            document.finishPage(page)
            bmp.recycle()
        }

        FileOutputStream(dest).use { document.writeTo(it) }
        document.close()
    }

    private fun drawStrokesToCanvas(
        canvas: Canvas,
        pageIndex: Int,
        srcSurfaceWidth: Int,
        srcSurfaceHeight: Int
    ) {
        if (!canAnnotate) return

        // Map overlay view pixels -> PDF page pixels
        val overlayW = overlay.width.coerceAtLeast(1)
        val overlayH = overlay.height.coerceAtLeast(1)
        val sx = srcSurfaceWidth / overlayW.toFloat()
        val sy = srcSurfaceHeight / overlayH.toFloat()

        canvas.save()
        canvas.scale(sx, sy)

        overlay.strokesForPage(pageIndex).forEach { s ->
            val p = Paint(s.paint)
            canvas.drawPath(s.path, p)
        }
        canvas.restore()
    }

    private fun uploadAnnotated(requestId: String, file: File) {
        val repo = ServiceReviewsRepository(RetrofitInstance.api)
        ioScope.launch {
            val res = repo.uploadAnnotatedFile(
                requestId = requestId,
                file = file,
                mime = "application/pdf",
                newStatus = "review_submitted"
            )
            withContext(Dispatchers.Main) {
                when (res) {
                    is NetResult.Ok -> toast("Uploaded feedback ✔")
                    is NetResult.Err -> toast("Upload failed: ${res.message}")
                }
            }
        }
    }

    /** ViewPager2 adapter rendering PDF pages and caching bitmaps. */
    private class PdfPagerAdapter(
        private val renderer: PdfRenderer,
        private val targetWidthPx: Int
    ) : RecyclerView.Adapter<PdfPagerAdapter.Holder>() {

        private val cache = object : LruCache<Int, Bitmap>(6) {}

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view: View = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pdf_page, parent, false)
            return Holder(view as ImageView)
        }

        override fun getItemCount(): Int = renderer.pageCount

        override fun onBindViewHolder(holder: Holder, position: Int) {
            cache.get(position)?.let {
                holder.image.setImageBitmap(it)
                return
            }

            val page = renderer.openPage(position)
            val scale = targetWidthPx.toFloat() / page.width.toFloat()
            val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)

            val bmp = Bitmap.createBitmap(
                targetWidthPx,
                targetHeight,
                Bitmap.Config.ARGB_8888
            )
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            cache.put(position, bmp)
            holder.image.setImageBitmap(bmp)
        }

        fun clear() {
            cache.evictAll()
        }

        class Holder(val image: ImageView) : RecyclerView.ViewHolder(image)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
