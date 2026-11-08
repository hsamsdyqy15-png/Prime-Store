package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppItem
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.ReviewItem
import com.example.ui.components.AppIconBadge
import com.example.ui.components.NeonCard
import com.example.ui.components.NeonGradientButton
import com.example.ui.components.StarRatingBar
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonPurpleGlow
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.NeonPurpleSecondary
import com.example.ui.theme.PrimeCardBackground
import com.example.ui.theme.PrimeGradientBrush
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextPrimary
import com.example.ui.theme.PrimeTextSecondary

@Composable
fun AppDetailsScreen(
    app: AppItem,
    isFavorite: Boolean,
    downloadItem: DownloadItem?,
    reviews: List<ReviewItem>,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallClick: () -> Unit = onDownloadClick,
    onAddReview: (Int, String) -> Unit,
    onScreenshotClick: (Int) -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("حول التطبيق", "مراجعات", "لقطات الشاشة")
    var showMenu by remember { mutableStateOf(false) }
    var showReviewModal by remember { mutableStateOf(false) }

    android.util.Log.d(
        "PrimeStore",
        "AppDetailsScreen opened -> id: ${app.id}, name: ${app.name}, icon_url: ${app.iconUrl}, file_url: ${app.fileUrl}, screenshots: ${app.screenshotUrls}"
    )

    androidx.compose.runtime.LaunchedEffect(app.id) {
        android.util.Log.d(
            "PrimeStore",
            "AppDetailsScreen LaunchedEffect -> id: ${app.id}, name: ${app.name}, icon_url: ${app.iconUrl}, file_url: ${app.fileUrl}, screenshots: ${app.screenshotUrls}"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("app_details_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Top Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("details_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "المفضلة",
                                tint = if (isFavorite) Color(0xFFEF4444) else Color.White
                            )
                        }

                        IconButton(onClick = onSearchClick) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "بحث",
                                tint = Color.White
                            )
                        }

                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "المزيد",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF1E162A))
                                    .border(1.dp, NeonBorder, RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("مشاركة التطبيق 🔗", color = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        onShareClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("إبلاغ عن محتوى ⚠️", color = Color.White) },
                                    onClick = { showMenu = false }
                                )
                            }
                        }
                    }
                }
            }

            // Header info matching Screenshot 4: Big Icon, Title, Rating, Category badges
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIconBadge(
                        emoji = app.iconEmoji,
                        accentColor = app.accentColor,
                        size = 96.dp,
                        shape = RoundedCornerShape(24.dp),
                        imageUrl = app.iconUrl
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = app.name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${app.rating}",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = " (${app.ratingCount})",
                            color = PrimeTextMuted,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tags badges: بناء, مغامرات, ألعاب
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        app.tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF281C38))
                                    .border(1.dp, NeonBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = tag,
                                    color = NeonPurplePrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Download Action Button / Progress
                    if (downloadItem != null && downloadItem.status == DownloadStatus.DOWNLOADING) {
                        // In-progress downloading state with progress, speed, and cancel
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = NeonPurpleDeep, spotColor = NeonPurplePrimary)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF1E142D))
                                .border(1.dp, NeonBorderGlow, RoundedCornerShape(18.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "جاري التحميل... ${(downloadItem.progress * 100).toInt()}%",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = downloadItem.speed,
                                    color = NeonPurplePrimary,
                                    fontSize = 12.sp
                                )
                                IconButton(
                                    onClick = onCancelDownload,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "إلغاء",
                                        tint = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadItem.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = NeonPurplePrimary,
                                trackColor = Color(0xFF3B2456)
                            )
                        }
                    } else if (downloadItem != null && downloadItem.status == DownloadStatus.FAILED) {
                        // Failed state with retry button
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF2D141E))
                                .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(18.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = downloadItem.errorMessage ?: "فشل التحميل، يرجى المحاولة مرة أخرى",
                                color = Color(0xFFFCA5A5),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            NeonGradientButton(
                                text = "إعادة المحاولة 🔄",
                                icon = Icons.Filled.Download,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onDownloadClick
                            )
                        }
                    } else if (downloadItem != null && (downloadItem.isCompleted || downloadItem.status == DownloadStatus.COMPLETED)) {
                        // Completed state -> Direct Install button
                        NeonGradientButton(
                            text = "تثبيت التطبيق 📦",
                            icon = Icons.Filled.CheckCircle,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onInstallClick
                        )
                    } else {
                        // Ready to download button
                        NeonGradientButton(
                            text = "تحميل الآن  (${app.size})",
                            icon = Icons.Filled.Download,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onDownloadClick
                        )
                    }
                }
            }

            // Tabs Row: حول التطبيق, مراجعات, لقطات الشاشة
            item {
                Spacer(modifier = Modifier.height(14.dp))
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = NeonPurplePrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = NeonPurplePrimary,
                            height = 3.dp
                        )
                    },
                    divider = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(NeonBorder)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    color = if (selectedTab == index) Color.White else PrimeTextMuted,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        )
                    }
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // "حول التطبيق" (About App Tab)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = app.descriptionAr,
                                color = PrimeTextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "الميزات الرئيسية:",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Green checkmarks features ✔
                            app.featuresAr.forEach { feature ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = AccentGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = feature,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Screenshots horizontal preview
                            Text(
                                text = "لقطات من داخل اللعبة:",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (app.screenshotUrls.isNotEmpty()) {
                                    itemsIndexed(app.screenshotUrls) { index, shotUrl ->
                                        ScreenshotThumbnail(
                                            colorHex = 0xFF4C1D95,
                                            index = index,
                                            imageUrl = shotUrl,
                                            onClick = { onScreenshotClick(index) }
                                        )
                                    }
                                } else {
                                    itemsIndexed(app.screenshotsColors) { index, colorHex ->
                                        ScreenshotThumbnail(
                                            colorHex = colorHex,
                                            index = index,
                                            onClick = { onScreenshotClick(index) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // "مراجعات" (Reviews Tab)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "آراء ومراجعات المستخدمين",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PrimeGradientBrush)
                                        .clickable { showReviewModal = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "+ أضف تقييمك",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (reviews.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "كن أول من يكتب مراجعة لهذا التطبيق! ⭐",
                                        color = PrimeTextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                reviews.forEach { review ->
                                    ReviewItemCard(review = review)
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // "لقطات الشاشة" (Screenshots Tab)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "اضغط على أي لقطة لعرضها بالحجم الكامل:",
                                color = PrimeTextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (app.screenshotUrls.isNotEmpty()) {
                                    itemsIndexed(app.screenshotUrls) { index, shotUrl ->
                                        ScreenshotThumbnail(
                                            colorHex = 0xFF4C1D95,
                                            index = index,
                                            width = 160.dp,
                                            height = 240.dp,
                                            imageUrl = shotUrl,
                                            onClick = { onScreenshotClick(index) }
                                        )
                                    }
                                } else {
                                    itemsIndexed(app.screenshotsColors) { index, colorHex ->
                                        ScreenshotThumbnail(
                                            colorHex = colorHex,
                                            index = index,
                                            width = 160.dp,
                                            height = 240.dp,
                                            onClick = { onScreenshotClick(index) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Review Modal Dialog
        if (showReviewModal) {
            AddReviewDialog(
                appName = app.nameAr,
                onDismiss = { showReviewModal = false },
                onSubmit = { rating, comment ->
                    onAddReview(rating, comment)
                    showReviewModal = false
                }
            )
        }
    }
}

@Composable
fun ScreenshotThumbnail(
    colorHex: Long,
    index: Int,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 140.dp,
    height: androidx.compose.ui.unit.Dp = 95.dp,
    imageUrl: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(width, height)
            .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = NeonPurpleDeep, spotColor = NeonPurplePrimary)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(colorHex), Color(0xFF16101F))
                )
            )
            .border(1.dp, NeonBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🎮",
                    fontSize = 24.sp
                )
                Text(
                    text = "لقطة ${index + 1}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ReviewItemCard(review: ReviewItem) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        hasGlow = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = review.userAvatar, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = review.userName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = review.date,
                    color = PrimeTextMuted,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            StarRatingBar(rating = review.rating.toFloat(), starSize = 14.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = review.comment,
                color = PrimeTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
fun AddReviewDialog(
    appName: String,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit
) {
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16101F),
        title = {
            Text(
                text = "تقييم $appName",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "اختر تقييمك بالنجوم:",
                    color = PrimeTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (i in 1..5) {
                        IconButton(onClick = { rating = i }) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (i <= rating) AccentGold else Color(0xFF4B4656),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("اكتب رأيك الصريح عن التطبيق...", color = PrimeTextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1F162D),
                        unfocusedContainerColor = Color(0xFF1F162D),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = NeonPurplePrimary,
                        unfocusedIndicatorColor = NeonBorder
                    ),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (comment.isNotBlank()) {
                        onSubmit(rating, comment)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary)
            ) {
                Text("نشر التقييم", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = PrimeTextMuted)
            }
        }
    )
}
