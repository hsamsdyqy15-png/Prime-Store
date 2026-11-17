package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.AccentGold
import com.example.ui.theme.PrimeTheme

@Composable
fun rememberLightHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

@Composable
fun rememberDebouncedClick(
    debounceMs: Long = 500L,
    withHaptic: Boolean = false,
    onClick: () -> Unit
): () -> Unit {
    val haptic = LocalHapticFeedback.current
    var lastClickMs by remember { mutableLongStateOf(0L) }
    return remember(onClick, debounceMs, withHaptic) {
        {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastClickMs >= debounceMs) {
                lastClickMs = now
                if (withHaptic) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }
        }
    }
}

fun Modifier.pressScaleClickable(
    enabled: Boolean = true,
    debounceMs: Long = 500L,
    withHaptic: Boolean = false,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press_scale"
    )
    val debounced = rememberDebouncedClick(
        debounceMs = debounceMs,
        withHaptic = withHaptic,
        onClick = onClick
    )
    this
        .scale(scale)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = ripple(color = PrimeTheme.colors.accent),
            onClick = debounced
        )
}

@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = PrimeTheme.colors.card,
    borderColor: Color = PrimeTheme.colors.border,
    borderWidth: Dp = 1.dp,
    hasGlow: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.pressScaleClickable(
            debounceMs = 500L,
            withHaptic = false,
            pressedScale = 0.985f,
            onClick = onClick
        )
    } else {
        Modifier
    }

    val effectiveBorderColor = if (hasGlow) borderColor else borderColor.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(width = borderWidth, color = effectiveBorderColor, shape = shape)
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
    val buttonColors = PrimeTheme.colors
    val elevation = if (buttonColors.isDark) 8.dp else 3.dp
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "button_press_scale"
    )
    val debouncedClick = rememberDebouncedClick(debounceMs = 500L, withHaptic = true, onClick = onClick)

    Box(
        modifier = modifier
            .testTag(testTag)
            .minimumInteractiveComponentSize()
            .scale(scale)
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = buttonColors.accentDark,
                spotColor = buttonColors.accent
            )
            .clip(shape)
            .background(buttonColors.buttonGradient)
            .border(1.dp, buttonColors.border, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                onClick = debouncedClick
            )
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
                    tint = buttonColors.textOnAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (iconEmoji != null) {
                Text(text = iconEmoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = buttonColors.textOnAccent,
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
    val context = LocalContext.current
    val cardBg = PrimeTheme.colors.card
    val tintColor = if (accentColor != 0L) Color(accentColor) else PrimeTheme.colors.accent
    val imageLoader = remember(context) { StoreImageLoader.get(context) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tintColor.copy(alpha = 0.25f),
                        cardBg
                    )
                )
            )
            .border(1.dp, tintColor.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center
    ) {
        val targetUrl = if (!imageUrl.isNullOrBlank()) imageUrl else if (emoji.startsWith("http")) emoji else null
        if (targetUrl != null) {
            val sizePx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
            val request = remember(targetUrl, sizePx, context) {
                ImageRequest.Builder(context)
                    .data(targetUrl)
                    .size(sizePx)
                    .crossfade(150)
                    .error(R.drawable.ic_app_default)
                    .fallback(R.drawable.ic_app_default)
                    .build()
            }
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.ic_app_default),
                contentDescription = null,
                modifier = Modifier.size(size * 0.55f),
                colorFilter = ColorFilter.tint(tintColor)
            )
        }
    }
}

@Composable
fun rememberShimmerBrush(): Brush {
    val colors = PrimeTheme.colors
    val baseColor = if (colors.isDark) {
        colors.cardAlt.copy(alpha = 0.7f)
    } else {
        colors.border.copy(alpha = 0.35f)
    }
    val highlightColor = if (colors.isDark) {
        colors.accent.copy(alpha = 0.18f)
    } else {
        colors.surface.copy(alpha = 0.85f)
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 220f)
    )
}

@Composable
fun SkeletonAppCard(
    brush: Brush = rememberShimmerBrush(),
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(76.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
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
    val unselectedColor = if (PrimeTheme.colors.isDark) Color(0xFF4B4656) else Color(0xFFD1C4E9)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        for (i in 1..5) {
            val filled = i <= rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (filled) starColor else unselectedColor,
                modifier = Modifier.size(starSize)
            )
        }
    }
}

