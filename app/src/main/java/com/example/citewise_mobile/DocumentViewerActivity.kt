package com.example.citewise_mobile

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
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
import java.io.File
import java.net.URLConnection

class DocumentViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    private lateinit var btnClose: ImageButton
    private lateinit var titleView: TextView
    private lateinit var pager: ViewPager2
    private lateinit var imgSingle: ImageView

    private var pdfRenderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var pdfAdapter: PdfPagerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force light bars/content for this screen
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_viewer)

        btnClose = findViewById(R.id.btnClose)
        titleView = findViewById(R.id.tvTitle)
        pager = findViewById(R.id.pager)
        imgSingle = findViewById(R.id.imageSingle)

        btnClose.setOnClickListener { finish() }

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        if (filePath.isNullOrBlank()) {
            toast("No file to preview")
            finish()
            return
        }

        val file = File(filePath)
        titleView.text = file.name

        val mime = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        when {
            mime == "application/pdf" -> setupPdf(file)
            mime.startsWith("image/") -> showSingleImage(file)
            else -> {
                // Unsupported types: just show a message; no external open/download
                pager.visibility = View.GONE
                imgSingle.visibility = View.GONE
                toast("Preview not supported for this file type.")
            }
        }
    }

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

            val displaySize = Point().also { windowManager.defaultDisplay.getSize(it) }
            val targetWidth = displaySize.x

            pdfAdapter = PdfPagerAdapter(pdfRenderer!!, targetWidth)
            pager.adapter = pdfAdapter

        } catch (t: Throwable) {
            toast("Unable to render PDF: ${t.message}")
        }
    }

    private fun showSingleImage(file: File) {
        pager.visibility = View.GONE
        imgSingle.visibility = View.VISIBLE
        imgSingle.setImageURI(Uri.fromFile(file))
    }

    override fun onDestroy() {
        super.onDestroy()
        try { pdfAdapter?.clear() } catch (_: Throwable) {}
        try { pdfRenderer?.close() } catch (_: Throwable) {}
        try { pfd?.close() } catch (_: Throwable) {}
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

/** ViewPager2 adapter that renders PDF pages on demand and caches white-backed bitmaps. */
private class PdfPagerAdapter(
    private val renderer: PdfRenderer,
    private val targetWidthPx: Int
) : RecyclerView.Adapter<PdfPagerAdapter.Holder>() {

    // Cache a few pages to keep memory stable
    private val cache = object : LruCache<Int, Bitmap>(6) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
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

        // Create a white canvas first to avoid dark/transparent background
        val bmp = Bitmap.createBitmap(targetWidthPx, targetHeight, Bitmap.Config.ARGB_8888)
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
