package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// BlueDeck brand colors (mirrors res/values/bluedeck_colors.xml).
private val BlueDeckNavy = Color(0xFF07111F)
private val BlueDeckNavyMid = Color(0xFF101827)
private val BlueDeckNavyEnd = Color(0xFF0B1D3A)
private val BlueDeckCyan = Color(0xFF00D4FF)
private val BlueDeckSoftWhite = Color(0xFFF8FAFC)

/**
 * Full-screen branded launch screen shown briefly after the system splash.
 * The Android system splash can only show a circular icon, so this Compose
 * screen is what carries the BlueDeck wordmark and tagline.
 */
@Composable
fun BlueDeckSplash(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(BlueDeckNavy, BlueDeckNavyMid, BlueDeckNavyEnd),
                    ),
                )
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_splash_bluedeck),
            contentDescription = null,
            modifier = Modifier.size(200.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "BlueDeck",
            color = BlueDeckCyan,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The handy keyboard and mouse",
            color = BlueDeckSoftWhite,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}
