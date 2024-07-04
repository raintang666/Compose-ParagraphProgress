package com.rain.bar

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rain.bar.ui.theme.ParagraphProgressTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val paragraphPoints = listOf(0.151564f, 0.384686f, 0.7165465f, 0.9135143f)
        enableEdgeToEdge()
        setContent {
            var progress by remember { mutableFloatStateOf(0f) }
            ParagraphProgressTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF39CE5E))
                ) {
                    ParagraphProgress(
                        modifier = Modifier
                            .padding(horizontal = 15.dp)
                            .fillMaxWidth()
                            .background(Color.White)
                            .align(Alignment.Center),
                        value = progress,
                        paragraphPoints = paragraphPoints,
                        onValueChange = {
                            progress = it
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ParagraphProgress(
    modifier: Modifier,
    value:Float,
    paragraphPoints: List<Float>? = null,
    paragraphSpace: Dp = 4.dp,
    progressNormalHeight:Dp = 2.dp,
    progressSelectHeight:Dp = 4.dp,
    dotSize: Float = 20f,
    onValueChange: (value: Float) -> Unit = {}
) {
    if (value !in 0f..1f) {
        throw IllegalArgumentException("value must be in 0f and 1.0f")
    }
    var width by remember { mutableIntStateOf(0) }
    var dotX by remember { mutableFloatStateOf(dotSize) }
    var centerY by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(value) }
    val progressPoints = remember { mutableStateListOf<Float>() }
    var dragModel:Boolean by remember { mutableStateOf(false) }
    var currentSelectParagraph by remember { mutableIntStateOf(0) }
    val space = paragraphSpace.px / 2
    val offsetList = ArrayList<ParagraphProp>()
    val progressOffsetList = ArrayList<ParagraphProp>()
    
    if (!dragModel && progress != value){
        progress = value
        dotX = (width * value).coerceIn(dotSize, width.toFloat() - dotSize)
        progressPoints.clear()
        paragraphPoints?.forEach { point ->
            if (point < progress) progressPoints.add(point)
        }
        progressPoints.add(progress)
    }

    if (paragraphPoints.isNullOrEmpty()) {
        offsetList.add(
            ParagraphProp(
                0f,
                1f,
                listOf(Offset(0f, centerY), Offset(width.toFloat(), centerY))
            )
        )
    } else {
        paragraphPoints.forEachIndexed { index, point ->
            val pointStartOffsetX =
                if (index == 0) 0f else (width * paragraphPoints[index - 1]) + space
            val startProgress = if (index == 0) 0f else paragraphPoints[index - 1]
            offsetList.add(
                ParagraphProp(
                    startProgress,
                    point,
                    listOf(
                        Offset(pointStartOffsetX, centerY),
                        Offset((width * point) - space, centerY)
                    )
                )
            )
            if (index == paragraphPoints.size - 1) {//最后一个点需多画一个向后的线
                offsetList.add(
                    ParagraphProp(
                        point,
                        1f,
                        listOf(
                            Offset((width * point) + space, centerY),
                            Offset(width.toFloat(), centerY)
                        )
                    )
                )
            }
        }
    }
    progressPoints.forEachIndexed { index, point ->
        val pointStartOffsetX =
            if (index == 0) 0f else (width * progressPoints[index - 1]) + space
        val startProgress = if (index == 0) 0f else progressPoints[index - 1]
        progressOffsetList.add(
            ParagraphProp(
                startProgress,
                point,
                listOf(
                    Offset(pointStartOffsetX, centerY),
                    Offset((width * point) - space, centerY)
                )
            )
        )
    }
    offsetList.forEachIndexed { index, paragraphProp ->
        if (progress in paragraphProp.startProgress..paragraphProp.endProgress){
            if (index != currentSelectParagraph){
                currentSelectParagraph = index
                vibrator(LocalContext.current,50)
            }
        }
    }
    val draggableState = rememberDraggableState {
        dotX = (dotX + it).coerceIn(dotSize, width.toFloat() - dotSize)
        progress = ((dotX - dotSize) / (width - 2 * dotSize))
        progressPoints.clear()
        paragraphPoints?.forEach { point ->
            if (point < progress) progressPoints.add(point)
        }
        progressPoints.add(progress)
        onValueChange.invoke(progress)
    }
    Canvas(
        modifier
            .height(dotSize.dp)
            .clipToBounds()
            .onSizeChanged { width = it.width }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        dotX = it.x.coerceIn(dotSize, width.toFloat() - dotSize)
                        progress = ((dotX - dotSize) / (width - 2 * dotSize))
                        progressPoints.clear()
                        paragraphPoints?.forEach { point ->
                            if (point < progress) progressPoints.add(point)
                        }
                        progressPoints.add(progress)
                        onValueChange.invoke(progress)
                    }
                )
            }
            .draggable(draggableState, orientation = Orientation.Horizontal, onDragStarted = {
                dragModel = true
            }, onDragStopped = {
                dragModel = false
            })
    ) {
        centerY = size.center.y
        offsetList.forEach {
            drawPoints(
                points = it.offsetList,
                pointMode = PointMode.Lines,
                color = Color(0x80F7CCCC),
                strokeWidth = if (progress in it.startProgress..it.endProgress) progressSelectHeight.toPx() else progressNormalHeight.toPx()
            )
        }
        progressOffsetList.forEach {
            drawPoints(
                points = it.offsetList,
                pointMode = PointMode.Lines,
                color = Color(0xFF07FFC4),
                strokeWidth = if (progress in it.startProgress..it.endProgress) progressSelectHeight.toPx() else progressNormalHeight.toPx()
            )
        }
        drawCircle(
            color = Color.Black,
            radius = dotSize,
            Offset(dotX, centerY)
        )
    }
}

fun vibrator(context: Context,milliseconds:Long){
    val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }else{
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vib.vibrate(milliseconds)
}

data class ParagraphProp(
    val startProgress: Float,
    val endProgress: Float,
    val offsetList: List<Offset>
)

val Dp.px: Float
    @Composable
    @ReadOnlyComposable
    get() {
        val dp = this
        return LocalDensity.current.run { dp.toPx() }
    }