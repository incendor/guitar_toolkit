package fxa.apps.guitar_toolkit

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fxa.apps.guitar_toolkit.ui.theme.Guitar_toolkitTheme
import kotlinx.coroutines.*
import kotlin.math.log10
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GuitarToolkitApp()
        }
    }
}

@Composable
fun GuitarToolkitApp() {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Guitar_toolkitTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (permissionGranted) {
                AudioVisualizer()
            } else {
                Text("Microphone permission is required.", modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@RequiresPermission(Manifest.permission.RECORD_AUDIO)
@Composable
fun AudioVisualizer() {
    val scope = rememberCoroutineScope()
    var level by remember { mutableFloatStateOf(0f) }


    DisposableEffect(Unit) {
        val bufferSize = AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize)

        audioRecord.startRecording()

        val buffer = ShortArray(bufferSize)
        val job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val rms = sqrt(buffer.take(read).map { it * it.toFloat() }.average())
                    level = (log10(rms + 1f) / 4f).coerceIn(0.0, 1.0).toFloat()
                }
                delay(16)
            }
        }

        onDispose {
            job.cancel()
            audioRecord.stop()
            audioRecord.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Mic Level")
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .height(30.dp)
                .fillMaxWidth()
                .background(Color.Gray)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(level)
                    .background(Color.Green)
            )
        }
    }
}

