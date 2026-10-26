package com.kexu.campus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kexu.campus.ui.AppRoot
import com.kexu.campus.ui.theme.KexuTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as CampusApp).repo
        setContent {
            val data by repo.data.collectAsStateWithLifecycle()
            KexuTheme(darkMode = data.settings.darkMode) {
                AppRoot(repo = repo, data = data)
            }
        }
    }
}
