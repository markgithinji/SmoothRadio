package com.smoothradio.radio.feature.info.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.smoothradio.radio.R
import com.smoothradio.radio.core.util.Resource
import com.smoothradio.radio.feature.info.domain.model.IssueCategory
import com.smoothradio.radio.feature.info.ui.AppInfoViewModel

@Composable
fun ReportIssueDialog(
    onDismiss: () -> Unit,
    context: Context,
    appVersion: String,
    viewModel: AppInfoViewModel
) {
    var selectedCategory by remember { mutableStateOf(IssueCategory.AUDIO_ISSUES) }
    var description by remember { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        screenshotUri = uri
    }

    val submissionState by viewModel.reportState.collectAsStateWithLifecycle()

    LaunchedEffect(submissionState) {
        when (submissionState) {
            is Resource.Success -> {
                Toast.makeText(context, "Report sent successfully! Thank you.", Toast.LENGTH_LONG).show()
                viewModel.resetReportState()
                onDismiss()
            }
            is Resource.Error -> {
                val error = (submissionState as Resource.Error).message
                Toast.makeText(context, "Failed to send report: $error", Toast.LENGTH_LONG).show()
                viewModel.resetReportState()
            }
            else -> {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .fillMaxHeight(0.85f),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Report a Problem",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "Describe the issue you encountered to help us improve your experience.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Selection
                Column {
                    Text(
                        "Issue Category",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { isDropdownExpanded = true }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedCategory.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        MaterialTheme(
                            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
                        ) {
                            DropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                IssueCategory.entries.forEach { category ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = category.displayName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (category == selectedCategory) FontWeight.Bold else FontWeight.Normal,
                                                color = if (category == selectedCategory)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            selectedCategory = category
                                            isDropdownExpanded = false
                                        },
                                        leadingIcon = {
                                            if (category == selectedCategory) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 16.dp,
                                            vertical = 8.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Description
                Column {
                    Text(
                        "Details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { 
                            Text(
                                "e.g. The station stops playing after a few minutes...",
                                fontSize = 14.sp
                            ) 
                        },
                        minLines = 4,
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        )
                    )
                }

                // Screenshot Attachment
                Column {
                    Text(
                        "Attach Screenshot (Optional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (screenshotUri == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.dp, 
                                    MaterialTheme.colorScheme.outlineVariant, 
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Add Photo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            AsyncImage(
                                model = screenshotUri,
                                contentDescription = "Screenshot preview",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { screenshotUri = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove photo",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Info hint
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        "💡 Device info will be sent automatically.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
        },
        confirmButton = {
            val isLoading = submissionState is Resource.Loading
            
            Button(
                onClick = {
                    if (screenshotUri != null) {
                        sendCustomReport(
                            context,
                            appVersion,
                            selectedCategory.displayName,
                            description,
                            screenshotUri
                        )
                        onDismiss()
                    } else {
                        viewModel.submitReport(
                            category = selectedCategory.displayName,
                            description = description,
                            appVersion = appVersion,
                            deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}",
                            androidVersion = Build.VERSION.RELEASE
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = description.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (screenshotUri != null) "Send via Email" else "Send Report",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

private fun sendCustomReport(
    context: Context,
    appVersion: String,
    category: String,
    description: String,
    attachmentUri: Uri?
) {
    val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"
    val androidVersion = Build.VERSION.RELEASE
    
    val emailBody = buildString {
        append("--- ISSUE REPORT ---\n")
        append("Category: $category\n")
        append("App Version: $appVersion\n")
        append("Device: $deviceInfo\n")
        append("Android: $androidVersion\n")
        append("\n--- DESCRIPTION ---\n")
        append(description)
        append("\n\n--------------------")
    }

    // 1. Create a base "mailto:" intent to act as a filter for email apps only
    val emailFilterIntent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())

    val intent = if (attachmentUri != null) {
        // Use ACTION_SEND for attachments, but use the selector to filter for email apps
        Intent(Intent.ACTION_SEND).apply {
            selector = emailFilterIntent
            putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.email_address)))
            putExtra(Intent.EXTRA_SUBJECT, "[REPORT] $category - Smooth Radio")
            putExtra(Intent.EXTRA_TEXT, emailBody)
            putExtra(Intent.EXTRA_STREAM, attachmentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        // Simple mailto for text-only
        emailFilterIntent.apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.email_address)))
            putExtra(Intent.EXTRA_SUBJECT, "[REPORT] $category - Smooth Radio")
            putExtra(Intent.EXTRA_TEXT, emailBody)
        }
    }

    try {
        // Remove Intent.createChooser to allow the system to use the user's default app directly
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No email client found.", Toast.LENGTH_SHORT).show()
    }
}
