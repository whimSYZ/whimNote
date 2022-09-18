package net.codebot.pdfviewer

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlin.math.pow
import kotlin.math.sqrt

@SuppressLint("AppCompatCustomView")
class PDFimage  // constructor
    (context: Context?) : ImageView(context) {
    val LOGNAME = "pdf_image"

    // drawing

    var pencilBrush = Paint(Color.BLUE)
    var markerBrush = Paint(Color.BLUE)

    // we save a lot of points because they need to be processed
    // during touch events e.g. ACTION_MOVE
    var x1 = 0f
    var x2 = 0f
    var y1 = 0f
    var y2 = 0f
    var old_x1 = 0f
    var old_y1 = 0f
    var old_x2 = 0f
    var old_y2 = 0f
    var mid_x = -1f
    var mid_y = -1f
    var old_mid_x = -1f
    var old_mid_y = -1f
    var p1_id = 0
    var p1_index = 0
    var p2_id = 0
    var p2_index = 0

    // store cumulative transformations
    // the inverse matrix is used to align points with the transformations - see below
    var currentMatrix = Matrix()
    var inverse = Matrix()


    // drawing path
    var path: SerializablePath? = null
    var pencilPaths = ArrayList<SerializablePath?>()

    var markerPaths = ArrayList<SerializablePath?>()

    var erasePath: SerializablePath? = null
    // image to display
    var bitmap: Bitmap? = null
    //var paint = Paint(Color.BLUE)

    var mode = Mode.VIEW

    enum class Action{
        ADD, ERASE
    }

    enum class Stroke{
        PENCIL, MARKER
    }

    val undoList = ArrayList<Triple<Action, Stroke, SerializablePath?>>()
    val redoList = ArrayList<Triple<Action, Stroke, SerializablePath?>>()

    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    /*
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            /*
            MotionEvent.ACTION_DOWN -> {
                Log.d(LOGNAME, "Action down")
                path = Path()
                path!!.moveTo(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                Log.d(LOGNAME, "Action move")
                path!!.lineTo(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                Log.d(LOGNAME, "Action up")
                paths.add(path)
            }*/
        }
        return true
    }*/



    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
            var inverted = floatArrayOf()
            var result = true
            when (event.pointerCount) {
                1 -> {
                    p1_id = event.getPointerId(0)
                    p1_index = event.findPointerIndex(p1_id)

                    // invert using the current matrix to account for pan/scale
                    // inverts in-place and returns boolean
                    inverse = Matrix()
                    currentMatrix.invert(inverse)

                    // mapPoints returns values in-place
                    inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                    inverse.mapPoints(inverted)
                    x1 = inverted[0]
                    y1 = inverted[1]

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            Log.d(LOGNAME, "Action down")
                            path = SerializablePath()
                            when(mode){
                                Mode.PENCIL -> {
                                    pencilPaths.add(path)
                                    undoList.add(Triple(Action.ADD,Stroke.PENCIL, path))
                                }
                                Mode.MARKER -> {
                                    markerPaths.add(path)
                                    undoList.add(Triple(Action.ADD,Stroke.MARKER, path))
                                }
                                Mode.ERASE -> {}
                                else -> {result = false}
                            }
                            //paths.add(path)
                            path!!.moveTo(x1, y1)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            Log.d(LOGNAME, "Action move")
                            path!!.lineTo(x1, y1)
                        }
                        MotionEvent.ACTION_UP -> {
                            Log.d(LOGNAME, "Action up")
                            if (mode == Mode.ERASE){
                                val removing = mutableListOf<SerializablePath>()
                                for (pencil in pencilPaths) {
                                    val res = Path()
                                    if (pencil != null && path != null) {
                                        res.op(pencil, path!!, Path.Op.INTERSECT)
                                        if (!res.isEmpty){
                                            removing.add(pencil)
                                            undoList.add(Triple(Action.ERASE,Stroke.PENCIL, pencil))
                                        }
                                    }
                                }
                                pencilPaths.removeAll(removing)
                                removing.clear()

                                for (marker in markerPaths) {
                                    val res = Path()
                                    if (marker != null && path != null) {
                                        res.op(marker, path!!, Path.Op.INTERSECT)
                                        if (!res.isEmpty){
                                            removing.add(marker)
                                            undoList.add(Triple(Action.ERASE,Stroke.MARKER, marker))
                                        }
                                    }
                                }

                                markerPaths.removeAll(removing)
                                removing.clear()
                            }
                        }
                    }

                    redoList.clear()
                }
                2 -> {
                    // point 1
                    result = true
                    p1_id = event.getPointerId(0)
                    p1_index = event.findPointerIndex(p1_id)

                    // mapPoints returns values in-place
                    inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                    inverse.mapPoints(inverted)

                    // first pass, initialize the old == current value
                    if (old_x1 < 0 || old_y1 < 0) {
                        x1 = inverted[0]
                        old_x1 = x1
                        y1 = inverted[1]
                        old_y1 = y1
                    } else {
                        old_x1 = x1
                        old_y1 = y1
                        x1 = inverted[0]
                        y1 = inverted[1]
                    }

                    // point 2
                    p2_id = event.getPointerId(1)
                    p2_index = event.findPointerIndex(p2_id)

                    // mapPoints returns values in-place
                    inverted = floatArrayOf(event.getX(p2_index), event.getY(p2_index))
                    inverse.mapPoints(inverted)

                    // first pass, initialize the old == current value
                    if (old_x2 < 0 || old_y2 < 0) {
                        x2 = inverted[0]
                        old_x2 = x2
                        y2 = inverted[1]
                        old_y2 = y2
                    } else {
                        old_x2 = x2
                        old_y2 = y2
                        x2 = inverted[0]
                        y2 = inverted[1]
                    }

                    // midpoint
                    mid_x = (x1 + x2) / 2
                    mid_y = (y1 + y2) / 2
                    old_mid_x = (old_x1 + old_x2) / 2
                    old_mid_y = (old_y1 + old_y2) / 2

                    // distance
                    val d_old =
                        sqrt((old_x1 - old_x2).toDouble().pow(2.0) + Math.pow((old_y1 - old_y2).toDouble(), 2.0))
                            .toFloat()
                    val d = sqrt((x1 - x2).toDouble().pow(2.0) + Math.pow((y1 - y2).toDouble(), 2.0))
                        .toFloat()

                    // pan and zoom during MOVE event
                    if (event.action == MotionEvent.ACTION_MOVE) {
                        Log.d(LOGNAME, "Multitouch move")
                        // pan == translate of midpoint
                        val dx = mid_x - old_mid_x
                        val dy = mid_y - old_mid_y
                        currentMatrix.preTranslate(dx, dy)
                        Log.d(LOGNAME, "translate: $dx,$dy")

                        // zoom == change of spread between p1 and p2
                        var scale = d / d_old
                        scale = 0f.coerceAtLeast(scale)
                        currentMatrix.preScale(scale, scale, mid_x, mid_y)
                        Log.d(LOGNAME, "scale: $scale")

                        // reset on up
                    } else if (event.action == MotionEvent.ACTION_UP) {
                        old_x1 = -1f
                        old_y1 = -1f
                        old_x2 = -1f
                        old_y2 = -1f
                        old_mid_x = -1f
                        old_mid_y = -1f
                    }
                }
                else -> {
                }
            }
            return result
    }


    // set image as background
    fun setImage(bitmap: Bitmap?) {
        this.bitmap = bitmap
    }

    // set brush characteristics
    // e.g. color, thickness, alpha
    /*fun setBrush(paint: Paint) {
        this.paint = paint
    }*/

    fun undo(){
        if(undoList.size > 0){
            var action = undoList.last()
            undoList.removeLast()
            redoList.add(action)

            if(action.first == Action.ADD){
                if (action.second == Stroke.PENCIL){
                    pencilPaths.remove(action.third)
                }else{
                    markerPaths.remove(action.third)
                }
            }else{
                if (action.second == Stroke.PENCIL){
                    pencilPaths.add(action.third)
                }else{
                    markerPaths.add(action.third)
                }
            }
        }
    }

    fun redo(){
        if(redoList.size > 0){
            var action = redoList.last()

            redoList.removeLast()
            undoList.add(action)

            if(action.first == Action.ADD){
                if (action.second == Stroke.PENCIL){
                    pencilPaths.add(action.third)
                }else{
                    markerPaths.add(action.third)
                }
            }else{
                if (action.second == Stroke.PENCIL){
                    pencilPaths.remove(action.third)
                }else{
                    markerPaths.remove(action.third)
                }
            }
        }
    }


    override fun onDraw(canvas: Canvas) {
        // draw background

        canvas.concat(currentMatrix)

        //println(pathScale)
        if (bitmap != null) {

            setImageBitmap(bitmap)
        }
        // draw lines over it
        for (path in markerPaths) {
            /*var p = Path()
            p.addPath(path!!, pathScale)*/
            canvas.drawPath(path!!, markerBrush)
        }

        for (path in pencilPaths) {
            /*
            var p = Path()
            p.addPath(path!!, pathScale)*/
            canvas.drawPath(path!!, pencilBrush)
        }

        super.onDraw(canvas)
    }

    init{
        pencilBrush.style = Paint.Style.STROKE
        pencilBrush.strokeWidth = 5f

        markerBrush.style = Paint.Style.STROKE
        markerBrush.strokeWidth = 40f
        markerBrush.alpha = 50
        markerBrush.color = Color.YELLOW

        scaleType = ScaleType.MATRIX
    }
}