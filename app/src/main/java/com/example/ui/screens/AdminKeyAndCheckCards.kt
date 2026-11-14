package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.github.GitHubTokenStatusSnapshot
import com.example.ui.components.NeonCard
import com.example.ui.theme.PrimeTheme

enum class CheckStepState {
    IDLE,
    RUNNING,
    PASSED,
    FAILED
}

data class ReadinessStepResult(
    val titleRes: Int,
    val state: CheckStepState = CheckStepState.IDLE,
    val detail: String? = null
)

@Composable
fun OwnerGitHubKeyCard(
    tokenStatus: GitHubTokenStatusSnapshot,
    tokenInput: String,
    onTokenInputChange: (String) -> Unit,
    isSaving: Boolean,
    isTesting: Boolean,
    isDeleting: Boolean,
    onSaveClick: () -> Unit,
    onTestClick: () -> Unit,
    onDeleteConfirm: () -> Unit
) {
    val colors = PrimeTheme.colors
    var showDeleteConfirm by remember { mutableStateOf(false) }

    NeonCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("owner_github_key_card"),
        hasGlow = false
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.gh_owner_section_title),
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (tokenStatus.isLoading) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val isConfigured = tokenStatus.isConfigured == true
            val statusText = when {
                tokenStatus.isConfigured == null -> stringResource(R.string.loading)
                isConfigured && !tokenStatus.formattedUpdatedAt.isNullOrBlank() ->
                    stringResource(R.string.gh_status_enabled_with_date, tokenStatus.formattedUpdatedAt)
                isConfigured -> stringResource(R.string.gh_status_enabled_short)
                else -> stringResource(R.string.gh_status_not_configured)
            }
            val statusColor = when {
                tokenStatus.isConfigured == null -> colors.textSecondary
                isConfigured -> colors.success
                else -> colors.danger
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.cardAlt)
                    .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (tokenStatus.isExpiringSoon) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.warning.copy(alpha = 0.14f))
                        .border(1.dp, colors.warning.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.gh_key_expiring_soon),
                        color = colors.warning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!tokenStatus.statusError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tokenStatus.statusError,
                    color = colors.danger,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = tokenInput,
                onValueChange = onTokenInputChange,
                label = { Text(stringResource(R.string.gh_paste_token_label), fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.accent,
                    unfocusedLabelColor = colors.textMuted,
                    focusedContainerColor = colors.cardAlt,
                    unfocusedContainerColor = colors.cardAlt,
                    cursorColor = colors.accent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("owner_gh_token_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.buttonGradient)
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .testTag("owner_gh_save_btn")
                        .clickable(enabled = !isSaving && tokenInput.isNotBlank()) { onSaveClick() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSaving) stringResource(R.string.loading) else stringResource(R.string.gh_save_key_btn),
                        color = colors.textOnAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.cardAlt)
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .testTag("owner_gh_test_btn")
                        .clickable(enabled = !isTesting) { onTestClick() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isTesting) stringResource(R.string.admin_gh_testing) else stringResource(R.string.gh_test_conn_btn),
                        color = if (isTesting) colors.textMuted else colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                if (isConfigured) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.danger.copy(alpha = 0.14f))
                            .border(1.dp, colors.danger.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .testTag("owner_gh_delete_btn")
                            .clickable(enabled = !isDeleting) { showDeleteConfirm = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.gh_delete_key_btn),
                            tint = colors.danger,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = colors.card,
            title = {
                Text(
                    text = stringResource(R.string.gh_delete_key_confirm_title),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.gh_delete_key_confirm_msg),
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteConfirm()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger)
                ) {
                    Text(stringResource(R.string.gh_delete_key_btn), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = colors.textMuted)
                }
            }
        )
    }
}

@Composable
fun AdminGitHubStatusCard(
    tokenStatus: GitHubTokenStatusSnapshot
) {
    val colors = PrimeTheme.colors
    val isConfigured = tokenStatus.isConfigured == true
    val statusText = when {
        tokenStatus.isConfigured == null -> stringResource(R.string.loading)
        isConfigured -> stringResource(R.string.gh_status_admin_managed)
        else -> stringResource(R.string.gh_status_admin_not_configured)
    }
    val tintColor = when {
        tokenStatus.isConfigured == null -> colors.textSecondary
        isConfigured -> colors.success
        else -> colors.danger
    }

    NeonCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("admin_github_status_card"),
        hasGlow = false
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    color = tintColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!tokenStatus.statusError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tokenStatus.statusError,
                    color = colors.danger,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun PublishReadinessCard(
    isRunning: Boolean,
    steps: List<ReadinessStepResult>,
    onRunCheck: () -> Unit
) {
    val colors = PrimeTheme.colors
    NeonCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("publish_readiness_card"),
        hasGlow = false
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.self_check_title),
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.buttonGradient)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .testTag("run_readiness_check_btn")
                        .clickable(enabled = !isRunning) { onRunCheck() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                color = colors.textOnAccent,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = colors.textOnAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (isRunning) stringResource(R.string.self_check_running) else stringResource(R.string.self_check_btn),
                            color = colors.textOnAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (steps.any { it.state != CheckStepState.IDLE }) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.forEach { step ->
                        val (mark, markColor) = when (step.state) {
                            CheckStepState.IDLE -> "•" to colors.textMuted
                            CheckStepState.RUNNING -> "…" to colors.accent
                            CheckStepState.PASSED -> "✔" to colors.success
                            CheckStepState.FAILED -> "✘" to colors.danger
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.cardAlt)
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(step.titleRes),
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = mark,
                                    color = markColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!step.detail.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = step.detail,
                                    color = if (step.state == CheckStepState.FAILED) colors.danger else colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
