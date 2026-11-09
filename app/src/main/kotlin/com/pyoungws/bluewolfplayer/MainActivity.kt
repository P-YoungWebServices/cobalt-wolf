package com.pyoungws.bluewolfplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pyoungws.bluewolfplayer.ui.BlueWolfApp
import com.pyoungws.bluewolfplayer.ui.theme.BlueWolfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BlueWolfTheme {
                BlueWolfApp()
            }
        }
    }
}
