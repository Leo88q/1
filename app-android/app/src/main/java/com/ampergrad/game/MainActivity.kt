package com.ampergrad.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ampergrad.game.solana.MwaWallet
import com.ampergrad.game.ui.AmpergradTheme

class MainActivity : ComponentActivity() {

    // Кошелёк создаётся до первой композиции: ActivityResultSender обязан
    // зарегистрировать launcher до состояния STARTED.
    private lateinit var wallet: MwaWallet
    private lateinit var sound: SoundKit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        wallet = MwaWallet(activity = this, useMainnet = !BuildConfig.DEBUG)
        sound = SoundKit()

        setContent {
            AmpergradTheme {
                AmpergradApp(activity = this, wallet = wallet, sound = sound)
            }
        }
    }

    override fun onDestroy() {
        sound.release()
        super.onDestroy()
    }
}
