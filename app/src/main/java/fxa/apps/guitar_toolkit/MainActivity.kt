package fxa.apps.guitar_toolkit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fxa.apps.guitar_toolkit.ui.theme.Guitar_toolkitTheme
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchDetectionResult
import be.tarsos.dsp.pitch.PitchProcessor
import kotlin.math.roundToInt

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
                AudioVisualizerWithPitch()
            } else {
                Text("Microphone permission is required.", modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@RequiresPermission(Manifest.permission.RECORD_AUDIO)
@Composable
fun AudioVisualizerWithPitch() {
    val context = LocalContext.current
    var pitch by remember { mutableStateOf(-1f) }
    val note = frequencyToNoteName(pitch)

    DisposableEffect(Unit) {
        val dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(44100, 7168, 0)

        val pdh = PitchDetectionHandler { res: PitchDetectionResult, _: AudioEvent ->
            pitch = res.pitch
        }

        val pitchProcessor = PitchProcessor(
            PitchProcessor.PitchEstimationAlgorithm.YIN,
            44100f,
            1024,
            pdh
        )
        dispatcher.addAudioProcessor(pitchProcessor)

        val thread = Thread(dispatcher, "Audio Dispatcher")
        thread.start()

        onDispose {
            dispatcher.stop()
            thread.interrupt()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Detected Pitch: ${if (pitch > 0) "%.2f Hz".format(pitch) else "No pitch"}")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Note: $note", style = MaterialTheme.typography.headlineLarge)
    }
}

fun frequencyToNoteName(freq: Float): String {
    if (freq <= 0) return "N/A"
    val noteNames = listOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )
    // Reference frequency for A4
    val A4 = 440.0
    // Calculate the number of semitones away from A4
    val semitonesFromA4 = (12 * kotlin.math.log2(freq / A4)).roundToInt()
    // Note index (0 = C, 9 = A)
    val noteIndex = (semitonesFromA4 + 9).mod(12)
    // Calculate octave number (A4 is in octave 4)
    val octave = 4 + ((semitonesFromA4 + 9) / 12)

    return noteNames[noteIndex] + octave
}