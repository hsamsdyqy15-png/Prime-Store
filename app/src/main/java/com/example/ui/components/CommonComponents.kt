package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGold
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurpleGlow
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.NeonPurpleSecondary
import com.example.ui.theme.PrimeCardBackground
import com.example.ui.theme.PrimeCardGradient
import com.example.ui.theme.PrimeGradientBrush

@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = PrimeCardBackground,
    borderColor: Color = NeonBorder,
    borderWidth: Dp = 1.dp,
    hasGlow: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = NeonPurplePrimary)
        ) { onClick() }
    } else Modifier

    Box(
        modifier = modifier
            .then(
                if (hasGlow) {
                    Modifier.shadow(
                        elevation = 6.dp,
                        shape = shape,
                        ambientColor = NeonPurpleGlow,
                        spotColor = NeonPurplePrimary
                    )
                } else Modifier
            )
            .clip(shape)
            .background(backgroundColor)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .then(clickModifier)
    ) {
        content()
    }
}

@Composable
fun NeonGradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconEmoji: String? = null,
    enabled: Boolean = true,
    testTag: String = "neon_button",
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = NeonPurpleDeep,
                spotColor = NeonPurplePrimary
            )
            .clip(shape)
            .background(PrimeGradientBrush)
            .border(1.dp, NeonBorderGlow, shape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White)
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (iconEmoji != null) {
                Text(text = iconEmoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AppIconBadge(
    emoji: String,
    accentColor: Long,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    imageUrl: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(accentColor).copy(alpha = 0.35f),
                        Color(0xFF16101F)
                    )
                )
            )
            .border(1.dp, Color(accentColor).copy(alpha = 0.5f), shape),
        contentAlignment = Alignment.Center
    ) {
        val targetUrl = if (!imageUrl.isNullOrBlank()) imageUrl else if (emoji.startsWith("http")) emoji else null
        if (targetUrl != null) {
            coil.compose.AsyncImage(
                model = targetUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(shape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_app_default),
                contentDescription = null,
                modifier = Modifier.size(size * 0.55f),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(accentColor))
            )
        }
    }
}

@Composable
fun StarRatingBar(
    rating: Float,
    modifier: Modifier = Modifier,
    starSize: Dp = 16.dp,
    starColor: Color = AccentGold
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        for (i in 1..5) {
            val filled = i <= rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (filled) starColor else Color(0xFF4B4656),
                modifier = Modifier.size(starSize)
            )
        }
    }
}
