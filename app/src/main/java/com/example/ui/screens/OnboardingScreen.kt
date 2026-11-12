package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.installer.PackageHelper
import com.example.ui.components.NeonGradientButton
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeBackground
import com.example.ui.theme.PrimeGradientBrush
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    onRequestNotificationPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrimeBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < 2) {
                    TextButton(onClick = onFinishOnboarding) {
                        Text("تخطي", color = PrimeTextMuted, fontSize = 14.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.height(36.dp))
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingPageOne()
                    1 -> OnboardingPageTwo(
                        onRequestInstall = { PackageHelper.openUnknownAppSourcesSettings(context) },
                        onRequestNotifications = onRequestNotificationPermission
                    )
                    2 -> OnboardingPageThree()
                }
            }

            // Bottom indicators and navigation buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dot indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (isSelected) 24.dp else 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) NeonPurplePrimary else Color(0xFF33204B))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Navigation Buttons
                if (pagerState.currentPage < 2) {
                    NeonGradientButton(
                        text = "التالي ➔",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    )
                } else {
                    NeonGradientButton(
                        text = "ابدأ الاستخدام الآن 🚀",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onFinishOnboarding
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun OnboardingPageOne() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(NeonPurplePrimary.copy(alpha = 0.3f), Color.Transparent)
                    )
                )
                .border(2.dp, NeonBorderGlow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "👑", fontSize = 64.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "مرحباً بك في Prime Store",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "المتجر العربي الأقوى والأسرع لتحميل التطبيقات والألعاب بروابط مباشرة وسرعة تحميل صاروخية، مع دعم فحص الأمان بالذكاء الاصطناعي وحل تعارض التثبيت.",
            color = PrimeTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun OnboardingPageTwo(
    onRequestInstall: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    val context = LocalContext.current
    val hasInstallPermission = PackageHelper.canRequestPackageInstalls(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color(0xFF201335))
                .border(2.dp, NeonPurplePrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = NeonPurplePrimary,
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "صلاحيات لتجربة متكاملة",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "يحتاج المتجر إلى بعض الصلاحيات لكي يتمكن من تثبيت التطبيقات بسلاسة وإشعارك بالتحديثات الجديدة.",
            color = PrimeTextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Permission Card 1: Install Unknown Apps
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E142D))
                .border(1.dp, NeonBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "📦 تثبيت التطبيقات",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "لتثبيت ملفات APK المحملة مباشرة",
                    color = PrimeTextMuted,
                    fontSize = 11.sp
                )
            }

            if (hasInstallPermission) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentGreen.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("مفعلة ✓", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onRequestInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تفعيل", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Permission Card 2: Notifications
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E142D))
                .border(1.dp, NeonBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🔔 الإشعارات",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "لتنبيهك باكتمال التحميل وتوفر التحديثات",
                    color = PrimeTextMuted,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = onRequestNotifications,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B2456)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("سماح", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun OnboardingPageThree() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color(0xFF201335))
                .border(2.dp, AccentGold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🛡️", fontSize = 50.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "أمان وسرعة بلا حدود",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        val features = listOf(
            "⚡ تسريع التحميل بنقرة واحدة وروابط مباشرة",
            "🛡️ فحص الأمان بالذكاء الاصطناعي قبل التثبيت",
            "🔄 حل مشكلة تعارض التوقيع وحذف النسخ القديمة",
            "🌐 ترجمة تلقائية ذكية لوصف وميزات التطبيقات"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            features.forEach { feat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E142D))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = feat,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
