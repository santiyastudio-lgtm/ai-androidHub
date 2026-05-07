package com.santiya.localaihub.activity

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.santiya.localaihub.models.enums.ProviderType
import com.santiya.localaihub.ui.screen.files.ModelPickerScreen
import com.santiya.localaihub.ui.theme.SantiyaLocalAiHubTheme

class ModelPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SantiyaLocalAiHubTheme {
                Surface(Modifier.fillMaxSize()) {
                    ModelPickerScreen(
                        onModelPicked = { uri, providerType ->
                            startActivity(
                                Intent(this, ModelLoadingActivity::class.java).apply {
                                    putExtra(EXTRA_RESULT_URI, uri.toString())
                                    putExtra(EXTRA_PICKER_MODE, providerType.name)
                                    data = uri
                                    clipData = ClipData.newUri(contentResolver, "model", uri)
                                    addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    )
                                }
                            )
                            finish()
                        },
                        onModelFilePicked = { filePath, providerType ->
                            startActivity(
                                Intent(this, ModelLoadingActivity::class.java).apply {
                                    putExtra(EXTRA_RESULT_FILE_PATH, filePath)
                                    putExtra(EXTRA_PICKER_MODE, providerType.name)
                                    addFlags(
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    )
                                }
                            )
                            finish()
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_RESULT_URI = "model_uri"
        const val EXTRA_RESULT_FILE_PATH = "model_file_path"  // Legacy compat
        const val EXTRA_PICKER_MODE = "picker_mode"
    }
}
