package com.santiya.localaihub.ui.screen.live

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.FaceDetector
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.viewmodel.ChatViewModel
import com.santiya.localaihub.viewmodel.LLMModelViewModel
import com.santiya.localaihub.worker.LlmModelWorker
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private enum class LiveMode(
    val title: String,
    val prompt: String,
) {
    OBJECTS("Объекты", "Назови ключевые объекты в кадре кратко."),
    FACES("Лица", ""),
    ASK_FRAME("Вопрос по кадру", "Опиши, что происходит в кадре и выдели важные детали."),
    TEXT("Текст в кадре", "Извлеки видимый текст из кадра и верни его без лишних пояснений."),
    FACE_MATCH("Распознавание лиц", "")
}

private data class FaceBox(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val confidence: Float,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveAiScreen(
    onNavigateBack: () -> Unit,
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    var liveMode by remember { mutableStateOf(LiveMode.FACES) }
    var status by remember { mutableStateOf("Откройте камеру и выберите режим.") }
    var latestFrame by remember { mutableStateOf<Bitmap?>(null) }
    var frameSize by remember { mutableStateOf(Size(1, 1)) }
    val faceBoxes = remember { mutableStateListOf<FaceBox>() }
    val lastAnalysisTs: MutableLongState = remember { mutableLongStateOf(0L) }

    val installedModels by llmModelViewModel.installedModels.collectAsStateWithLifecycle(emptyList())
    val currentModelId by llmModelViewModel.currentModelID.collectAsStateWithLifecycle()
    val currentModelName = installedModels.firstOrNull { it.id == currentModelId }?.modelName ?: "Модель не выбрана"
    val isVlmLoaded by chatViewModel.isVlmLoaded.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(context) {
        hasCameraPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Live AI")
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Beta",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Назад"
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiveMode.entries.forEach { mode ->
                    FilterChip(
                        selected = liveMode == mode,
                        onClick = { liveMode = mode },
                        label = { Text(mode.title) }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = currentModelName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (liveMode) {
                            LiveMode.FACES -> "Живая детекция лиц работает прямо на устройстве."
                            LiveMode.FACE_MATCH -> "Контракт для face matching идёт через внешний API. В live-режиме показывается локальная детекция лиц."
                            else -> if (isVlmLoaded) {
                                "Захват кадра отправляется в локальный VLM без облака."
                            } else {
                                "Для режима нужен загруженный text model + mmproj projector."
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                if (!hasCameraPermission) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = TnIcons.Eye,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Нужен доступ к камере", color = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onFrame = { bitmap, size ->
                            latestFrame = bitmap
                            frameSize = size
                            val now = System.currentTimeMillis()
                            if (now - lastAnalysisTs.longValue < TimeUnit.MILLISECONDS.convert(600, TimeUnit.MILLISECONDS)) {
                                return@CameraPreview
                            }
                            lastAnalysisTs.longValue = now
                            when (liveMode) {
                                LiveMode.FACES, LiveMode.FACE_MATCH -> {
                                    val detections = detectFaces(bitmap)
                                    faceBoxes.clear()
                                    faceBoxes.addAll(detections)
                                    status = if (detections.isEmpty()) {
                                        "Лица не найдены."
                                    } else {
                                        "Найдено лиц: ${detections.size}"
                                    }
                                }
                                else -> {
                                    faceBoxes.clear()
                                }
                            }
                        }
                    )

                    if (liveMode == LiveMode.FACES || liveMode == LiveMode.FACE_MATCH) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val scaleX = size.width / frameSize.width.toFloat().coerceAtLeast(1f)
                            val scaleY = size.height / frameSize.height.toFloat().coerceAtLeast(1f)
                            faceBoxes.forEach { face ->
                                drawRect(
                                    color = Color(0xFF8B5CF6),
                                    topLeft = Offset(
                                        x = (face.centerX - face.width / 2f) * scaleX,
                                        y = (face.centerY - face.height / 2f) * scaleY
                                    ),
                                    size = ComposeSize(face.width * scaleX, face.height * scaleY),
                                    style = Stroke(width = 4f)
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(
                            onClickListener = {
                                val frame = latestFrame
                                if (frame == null) {
                                    status = "Кадр ещё не готов."
                                    return@ActionButton
                                }
                                when (liveMode) {
                                    LiveMode.FACES -> status = if (faceBoxes.isEmpty()) "Лица не найдены." else "Найдено лиц: ${faceBoxes.size}"
                                    LiveMode.FACE_MATCH -> status = "Для live-распознавания нужен внешний watchlist через Hub API. Локальная детекция работает."
                                    else -> {
                                        if (!isVlmLoaded || currentModelId.isNullOrBlank()) {
                                            status = "Сначала загрузите text model и mmproj projector."
                                            return@ActionButton
                                        }
                                        chatViewModel.sendChatWithImages(
                                            prompt = liveMode.prompt,
                                            imageData = listOf(bitmapToJpeg(frame))
                                        )
                                        status = "Кадр отправлен в локальный VLM."
                                    }
                                }
                            },
                            icon = TnIcons.PlayerPlay,
                            contentDescription = "Запустить"
                        )
                        ActionButton(
                            onClickListener = {
                                faceBoxes.clear()
                                status = "Состояние очищено."
                            },
                            icon = TnIcons.Eraser,
                            contentDescription = "Очистить"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    onFrame: (Bitmap, Size) -> Unit,
) {
    val context = LocalContext.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            val previewView = PreviewView(it)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(it)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    val bitmap = imageProxy.toBitmap()
                    if (bitmap != null) {
                        onFrame(bitmap, Size(imageProxy.width, imageProxy.height))
                    }
                    imageProxy.close()
                }
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        context as androidx.lifecycle.LifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }
            }, ContextCompat.getMainExecutor(it))
            previewView
        }
    )
}

private fun detectFaces(bitmap: Bitmap): List<FaceBox> {
    val source = bitmap.copy(Bitmap.Config.RGB_565, true)
    val detector = FaceDetector(source.width, source.height, 8)
    val faces = arrayOfNulls<FaceDetector.Face>(8)
    val count = detector.findFaces(source, faces)
    return buildList {
        repeat(count) { index ->
            val face = faces[index] ?: return@repeat
            val midpoint = android.graphics.PointF()
            face.getMidPoint(midpoint)
            val halfWidth = face.eyesDistance() * 1.6f
            add(
                FaceBox(
                    centerX = midpoint.x,
                    centerY = midpoint.y,
                    width = halfWidth * 2f,
                    height = halfWidth * 2.4f,
                    confidence = face.confidence()
                )
            )
        }
    }
}

private fun ImageProxy.toBitmap(): Bitmap? {
    val nv21 = yuv420888ToNv21(this)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 70, out)
    val bytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    return nv21
}

private fun bitmapToJpeg(bitmap: Bitmap): ByteArray {
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
    return output.toByteArray()
}
