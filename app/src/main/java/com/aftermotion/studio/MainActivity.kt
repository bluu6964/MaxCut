package com.aftermotion.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.aftermotion.studio.ui.VideoEditorScreen
import com.aftermotion.studio.ui.theme.AfterMotionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.aftermotion.studio.engine.StickerLibrary.init(applicationContext)
        com.aftermotion.studio.engine.CutoutTrace.incomplete(applicationContext)?.let {
            android.widget.Toast.makeText(this, "Last cutout stopped at: $it", android.widget.Toast.LENGTH_LONG).show()
        }
        enableEdgeToEdge()
        setContent {
            AfterMotionTheme {
                VideoEditorScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
