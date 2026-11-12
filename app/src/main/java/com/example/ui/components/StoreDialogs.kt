package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentRed
import com.example.ui.theme.NeonBorder
import com.example.ui.theme.NeonBorderGlow
import com.example.ui.theme.NeonPurplePrimary
import com.example.ui.theme.PrimeTextMuted
import com.example.ui.theme.PrimeTextSecondary

data class SignatureConflictData(
    val packageName: String,
    val installedVersionName: String,
    val newVersionName: String,
    val apkFilePath: String
)

data class DowngradeData(
    val packageName: String,
    val installedVersionName: String,
    val newVersionName: String,
    val apkFilePath: String
)

@Composable
fun SignatureConflictDialog(
    data: SignatureConflictData,
    onConfirmUninstallAndInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1F122B),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تعارض في توقيع الحزمة ⚠️",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "هذه النسخة موقّعة بتوقيع رقمي مختلف عن النسخة المثبتة حالياً على هاتفك (v${data.installedVersionName} ➔ v${data.newVersionName}).",
                    color = Color(0xFFE2E8F0),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2E1528))
                        .border(1.dp, AccentRed.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "نظام أندرويد يرفض التحديث فوق تطبيق بتوقيع مختلف. لحل المشكلة، يجب حذف النسخة القديمة أولاً ثم متابعة تثبيت هذه النسخة.\n(ملاحظة: قد تفقد بيانات النسخة القديمة)",
                        color = Color(0xFFFFB4AB),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmUninstallAndInstall,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("حذف القديمة ومتابعة التثبيت", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = PrimeTextMuted)
            }
        }
    )
}

@Composable
fun DowngradeOrSameVersionDialog(
    data: DowngradeData,
    onProceedAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E142D),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = NeonPurplePrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "النسخة مثبتة بالفعل ℹ️",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Text(
                text = "لديك نسخة أحدث أو مماثلة مثبتة بالفعل على جهازك (المثبتة: v${data.installedVersionName}، ملف التثبيت: v${data.newVersionName}). هل ترغب في تثبيتها فوق النسخة الحالية على أي حال؟",
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onProceedAnyway,
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("تثبيت على أي حال", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = PrimeTextMuted)
            }
        }
    )
}

@Composable
fun LowStorageDialog(
    neededSize: String,
    availableSize: String,
    onProceedAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF261220),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "مساحة التخزين منخفضة 💾",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Text(
                text = "حجم التطبيق المطلوب ($neededSize) والمساحة الحرة المتبقية في هاتفك هي ($availableSize). قد يفشل التحميل إذا امتلأت الذاكرة. هل ترغب في المتابعة على أي حال؟",
                color = Color(0xFFF3E8FF),
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onProceedAnyway,
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("متابعة التحميل", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = PrimeTextMuted)
            }
        }
    )
}

@Composable
fun ProblemReportDialog(
    appName: String,
    onSubmit: (reason: String, details: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedReason by remember { mutableStateOf("توقف مفاجئ للتطبيق") }
    var detailsText by remember { mutableStateOf("") }
    val reasons = listOf(
        "توقف مفاجئ للتطبيق",
        "لا يمكن تثبيت ملف APK",
        "رابط التحميل لا يعمل",
        "محتوى غير لائق أو مخالف",
        "مشكلة أخرى"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E142D),
        title = {
            Text(
                text = "إبلاغ عن مشكلة في $appName ⚠️",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "حدد نوع المشكلة:",
                    color = PrimeTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                reasons.forEach { reason ->
                    val isSelected = selectedReason == reason
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF3B205C) else Color(0xFF140C20))
                            .clickable { selectedReason = reason }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSelected) "🔘 " else "⚪ ",
                            fontSize = 12.sp
                        )
                        Text(
                            text = reason,
                            color = if (isSelected) Color.White else PrimeTextMuted,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "تفاصيل إضافية (اختياري):",
                    color = PrimeTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = detailsText,
                    onValueChange = { detailsText = it },
                    placeholder = { Text("اكتب وصفاً مختصراً للمشكلة...", color = PrimeTextMuted, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonPurplePrimary,
                        unfocusedBorderColor = NeonBorder,
                        focusedContainerColor = Color(0xFF140D20),
                        unfocusedContainerColor = Color(0xFF140D20)
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(selectedReason, detailsText)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurplePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("إرسال البلاغ", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = PrimeTextMuted)
            }
        }
    )
}
