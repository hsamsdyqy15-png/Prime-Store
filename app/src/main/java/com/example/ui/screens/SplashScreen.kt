package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurpleGlow
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.NeonPurpleSecondary
import com.example.ui.theme.PrimeBackground
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary

@Composable
fun SplashScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("splash_screen")
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF28114B),
                        Color(0xFF140827),
                        PrimeBackground
                    )
                )
            )
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Glowing Crown at Top
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(pulseScale)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = NeonPurpleDeep,
                        spotColor = NeonPurplePrimary
                    )
                    .clip(RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.prime_store_icon),
                    contentDescription = "Prime Store Icon",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // App Name: "Prime Store" (Prime white, Store neon purple)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Prime ",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Store",
                        color = NeonPurplePrimary,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle: متجر التطبيقات والألعاب
            Text(
                text = "متجر التطبيقات والألعاب",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Separator with phrase "كل ما تحتاجه في مكان واحد"
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, NeonBorderGlow)
                            )
                        )
                )
                Text(
                    text = "  كل ما تحتاجه في مكان واحد  ",
                    color = PrimeTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(NeonBorderGlow, Color.Transparent)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Gamepad Controller Emblem with vibrant neon purple aura
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale)
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(32.dp),
                        ambientColor = NeonPurpleDeep,
                        spotColor = NeonPurplePrimary
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.5.dp, NeonBorderGlow, RoundedCornerShape(32.dp))
                    .background(Color(0xFF16101F)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_prime_store),
                    contentDescription = "Prime Store Controller",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Circular Spinner and "جاري التحميل..."
            CircularProgressIndicator(
                color = NeonPurplePrimary,
                trackColor = Color(0x33A855F7),
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "جاري التحميل ...",
                color = PrimeTextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
