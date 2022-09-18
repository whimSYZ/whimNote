package net.codebot.pdfviewer

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs


// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not at all obvious from documentation, so we should expect people to need this.
// We may wish to provide this code.
class MainActivity:
    AppCompatActivity(),
    GestureDetector.OnGestureListener {
    val LOGNAME = "pdf_viewer"
    val FILENAME = "shannon1948.pdf"
    val FILERESID = R.raw.shannon1948

    // manage the pages of the PDF, see below
    lateinit var pdfRenderer: PdfRenderer
    lateinit var parcelFileDescriptor: ParcelFileDescriptor
    var currentPage: PdfRenderer.Page? = null
    private lateinit var mDetector: GestureDetectorCompat

    // custom ImageView class that captures strokes and draws them over the image
    lateinit var pageImage: PDFimage
    var pageNumber = 0
    var mode: Mode = Mode.VIEW

    val pencils = ArrayList<ArrayList<SerializablePath?>>()

    val markers = ArrayList<ArrayList<SerializablePath?>>()

    var width = 1800;


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val layout = findViewById<LinearLayout>(R.id.pdfLayout)
        layout.isEnabled = true

        pageImage = PDFimage(this)
        layout.addView(pageImage)

        pageImage.minimumWidth = 2000
        pageImage.minimumHeight = 2000

        mDetector = GestureDetectorCompat(this, this)

        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // In landscape
            width = 2560
        } else {
            // In portrait
            width = 1800
        }


        // open page 0 of the PDF
        // it will be displayed as an image in the pageImage (above)
        try {
            openRenderer(this)

            for (i in 0 until pdfRenderer.pageCount){
                pencils.add(ArrayList())
            }

            for (i in 0 until pdfRenderer.pageCount){
                markers.add(ArrayList())
            }

            showPage(pageNumber)
        } catch (exception: IOException) {
            Log.d(LOGNAME, "Error opening PDF")
        }
    }

    override fun onStart() {
        super.onStart()
        openRenderer(this)
    }

    override fun onStop() {
        super.onStop()
        try {
            closeRenderer()
        } catch (ex: Exception) {
            Log.d(LOGNAME, "Unable to close PDF renderer")
        }
    }

    override fun onFling(
        event1: MotionEvent,
        event2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val diffY: Float = event2.y - event1.y
        val diffX: Float = event2.x - event1.x

        if (abs(diffX) > abs(diffY)) {
            if (diffX > 0) {
                if (pageNumber > 0){
                    pencils[pageNumber] = pageImage.pencilPaths
                    markers[pageNumber] = pageImage.markerPaths
                    pageNumber -= 1
                    showPage(pageNumber)
                }
            } else {
                if (pageNumber+1 < pdfRenderer.pageCount) {
                    pencils[pageNumber] = pageImage.pencilPaths
                    markers[pageNumber] = pageImage.markerPaths
                    pageNumber += 1
                    showPage(pageNumber)
                }
            }
        }
        return true
    }



    override fun onDown(event: MotionEvent): Boolean {
        return true
    }

    override fun onShowPress(event: MotionEvent) {
    }

    override fun onSingleTapUp(event: MotionEvent): Boolean {
        return true
    }

    override fun onScroll(
        event1: MotionEvent,
        event2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return true
    }

    override fun onLongPress(event: MotionEvent) {
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        pageImage.onTouchEvent(event)
        return if (mDetector.onTouchEvent(event)) {
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    fun onPencilClick(view: View) {
        // Do something in response to button click
        val button = view as ImageButton
        val eraseButton = findViewById<ImageButton>(R.id.eraseButton)
        val markerButton = findViewById<ImageButton>(R.id.markerButton)
        eraseButton.setColorFilter(Color.TRANSPARENT)
        markerButton.setColorFilter(Color.TRANSPARENT)

        if (mode == Mode.PENCIL){
            mode = Mode.VIEW
            pageImage.mode = mode
            button.setColorFilter(Color.TRANSPARENT)
        }else{
            mode = Mode.PENCIL
            pageImage.mode = mode
            button.setColorFilter(Color.BLUE)
        }
    }

    fun onEraseClick(view: View) {
        // Do something in response to button click
        val button = view as ImageButton
        val pencilButton = findViewById<ImageButton>(R.id.pencilButton)
        val markerButton = findViewById<ImageButton>(R.id.markerButton)
        pencilButton.setColorFilter(Color.TRANSPARENT)
        markerButton.setColorFilter(Color.TRANSPARENT)

        if (mode == Mode.ERASE){
            mode = Mode.VIEW
            pageImage.mode = mode
            button.setColorFilter(Color.TRANSPARENT)
        }else{
            mode = Mode.ERASE
            pageImage.mode = mode
            button.setColorFilter(Color.BLUE)
        }
    }

    fun onMarkerClick(view: View) {
        // Do something in response to button click
        val button = view as ImageButton
        val pencilButton = findViewById<ImageButton>(R.id.pencilButton)
        val eraseButton = findViewById<ImageButton>(R.id.eraseButton)
        pencilButton.setColorFilter(Color.TRANSPARENT)
        eraseButton.setColorFilter(Color.TRANSPARENT)

        if (mode == Mode.MARKER){
            mode = Mode.VIEW
            pageImage.mode = mode
            button.setColorFilter(Color.TRANSPARENT)
        }else{
            mode = Mode.MARKER
            pageImage.mode = mode
            button.setColorFilter(Color.YELLOW)
        }

    }

    fun onUndo(view: View){
        pageImage.undo()
    }

    fun onRedo(view: View){
        pageImage.redo()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            pageImage.currentMatrix.preScale(2560/1800F, 2560/1800F)
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            pageImage.currentMatrix.preScale(1800/2560F, 1800/2560F)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {

        println("saving state in onSaveInstanceState")

        // save state
        with (outState) {
            putInt("PAGE", pageNumber)
            putInt("PENCILPATHS_SIZE", pageImage.pencilPaths.size)
            putInt("MARKERPATHS_SIZE", pageImage.markerPaths.size)
            for (i in 0 until pageImage.pencilPaths.size){
                putSerializable("PENCILPATH$i", pageImage.pencilPaths[i])
            }
            for (i in 0 until pageImage.markerPaths.size){
                putSerializable("MARKERPATH$i", pageImage.markerPaths[i])
            }

        }

        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(inState: Bundle) {
        super.onRestoreInstanceState(inState)

        println("restoring state in onRestoreInstanceState")

        with (inState) {
            pageNumber = getInt("PAGE")
            showPage(pageNumber)

            val pencilPathsSize = getInt("PENCILPATHS_SIZE")
            val markerPathsSize = getInt("MARKERPATHS_SIZE")

            for (i in 0 until pencilPathsSize){
                pageImage.pencilPaths.add(getSerializable("PENCILPATH$i") as SerializablePath)
            }

            for (i in 0 until markerPathsSize){
                pageImage.markerPaths.add(getSerializable("MARKERPATH$i") as SerializablePath)
            }
        }
    }


    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        // In this sample, we read a PDF from the assets directory.
        val file = File(context.cacheDir, FILENAME)
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            val asset = this.resources.openRawResource(FILERESID)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                output.write(buffer, 0, size)
            }
            asset.close()
            output.close()
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        // capture PDF data
        // all this just to get a handle to the actual PDF representation
        pdfRenderer = PdfRenderer(parcelFileDescriptor)
    }

    // do this before you quit!
    @Throws(IOException::class)
    private fun closeRenderer() {
        currentPage?.close()
        pdfRenderer.close()
        parcelFileDescriptor.close()
    }


    private fun showPage(index: Int) {
        if (pdfRenderer.pageCount <= index) {
            return
        }
        // Close the current page before opening another one.
        try {
            currentPage?.close()
        } catch (e : IllegalStateException){
            println("Already closed")
        }
        val pageText = findViewById<TextView>(R.id.pageView)

        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(index)

        if (currentPage != null) {
            // Important: the destination bitmap must be ARGB (not RGB).



            val bitmap = Bitmap.createBitmap(width, currentPage!!.height*width/currentPage!!.width, Bitmap.Config.ARGB_8888)

            // Here, we render the page onto the Bitmap.
            // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
            // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
            currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Display the page
            pageImage.setImage(bitmap)
            pageImage.undoList.clear()
            pageImage.redoList.clear()
            pageImage.pencilPaths = pencils[index]
            pageImage.markerPaths = markers[index]

            pageText.text = "Page: "+(index+1)+"/"+pdfRenderer.pageCount
        }
    }
}