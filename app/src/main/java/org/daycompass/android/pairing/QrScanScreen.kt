package org.daycompass.android.pairing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.daycompass.android.R
import org.daycompass.android.ui.components.DcButton
import org.daycompass.android.ui.components.DcButtonVariant
import org.daycompass.android.ui.theme.DaycompassTheme
import org.daycompass.android.ui.theme.Dc
import org.daycompass.android.ui.theme.DcSpacing
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.tooling.preview.Preview as ComposePreview

/** Scans a Daycompass pairing QR code, offline, with no cloud or Play Services dependency. */
@Composable
fun QrScanScreen(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
            if (!granted) permissionDenied = true
        }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    ScreenColumn {
        Text(
            text = stringResource(R.string.qr_scan_title),
            style = Dc.type.heading1,
            color = Dc.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        when {
            hasPermission -> {
                Text(stringResource(R.string.qr_scan_body), style = Dc.type.body, color = Dc.colors.textMuted)
                QrCameraPreview(onScanned = onScanned)
            }

            permissionDenied -> {
                Text(
                    stringResource(R.string.qr_scan_permission_denied_title),
                    style = Dc.type.heading2,
                    color = Dc.colors.text,
                )
                Text(
                    stringResource(R.string.qr_scan_permission_denied_body),
                    style = Dc.type.body,
                    color = Dc.colors.textMuted,
                )
                DcButton(
                    text = stringResource(R.string.qr_scan_try_again),
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                // Waiting on the system permission dialog.
            }
        }
        DcButton(
            text = stringResource(R.string.qr_scan_enter_manually),
            onClick = onCancel,
            variant = DcButtonVariant.Neutral,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun QrCameraPreview(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember { MultiFormatReader() }
    val handled = remember { AtomicBoolean(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(previewView) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        cameraProvider = provider
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis =
            ImageAnalysis
                .Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        analysis.setAnalyzer(cameraExecutor) { image ->
            val text = decodeFrame(image, reader)
            if (text != null && handled.compareAndSet(false, true)) onScanned(text)
        }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT))
}

/** `null` means no QR code was found in this frame; closes [image] in every case. */
private fun decodeFrame(
    image: ImageProxy,
    reader: MultiFormatReader,
): String? =
    try {
        val plane = image.planes[0]
        if (plane.pixelStride == 1) {
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val source =
                PlanarYUVLuminanceSource(
                    bytes,
                    plane.rowStride,
                    image.height,
                    0,
                    0,
                    image.width,
                    image.height,
                    false,
                )
            reader.decode(BinaryBitmap(HybridBinarizer(source)), DECODE_HINTS).text
        } else {
            null
        }
    } catch (_: NotFoundException) {
        null
    } catch (_: ChecksumException) {
        null
    } catch (_: FormatException) {
        null
    } finally {
        image.close()
    }

private val DECODE_HINTS: Map<DecodeHintType, Any> =
    mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
private val PREVIEW_HEIGHT = 320.dp

@ComposePreview(showBackground = true)
@Composable
private fun QrScanScreenPreview() {
    DaycompassTheme(darkTheme = false) {
        QrScanScreen(onScanned = {}, onCancel = {})
    }
}
