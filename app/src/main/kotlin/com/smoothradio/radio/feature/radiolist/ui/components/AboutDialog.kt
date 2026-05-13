package com.smoothradio.radio.feature.radiolist.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.smoothradio.radio.R

@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
    context: Context
) {
    val colorScheme = MaterialTheme.colorScheme
    val appVersion = remember { getAppVersion(context) }
    val deviceInfo = remember { "${Build.MANUFACTURER} ${Build.MODEL}" }
    val androidVersion = remember { "${context.getString(R.string.android_version_label)} ${Build.VERSION.RELEASE}" }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.smoothradioapplogored),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Column {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        appVersion,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))

                Text(
                    stringResource(R.string.support_community),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )

                // Share App
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.primary.copy(alpha = 0.1f))
                        .clickable {
                            shareApp(context)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        stringResource(R.string.share_app),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                }

                // Report a Problem
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            sendFeedbackEmail(context, appVersion, deviceInfo, androidVersion)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            stringResource(R.string.report_problem),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            context.getString(R.string.email_address),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }

                // Follow on Facebook
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                context.getString(R.string.facebook_url).toUri()
                            )
                            context.startActivity(intent)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.facebooklogo),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        stringResource(R.string.follow_facebook),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))

                Text(
                    stringResource(R.string.made_in_kenya),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
            ) {
                Text(stringResource(R.string.close), fontWeight = FontWeight.Medium)
            }
        }
    )
}

private fun getAppVersion(context: Context): String {
    return try {
        val pm = context.packageManager
        val appName = context.applicationInfo.loadLabel(pm).toString()
        val version = pm.getPackageInfo(context.packageName, 0).versionName
        "$appName v$version"
    } catch (e: PackageManager.NameNotFoundException) {
        context.getString(R.string.app_name)
    }
}

private fun sendFeedbackEmail(context: Context, version: String, device: String, android: String) {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.email_address)))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.email_subject))
        putExtra(Intent.EXTRA_TEXT, "$version\n$device\n$android\n\n")
    }
    try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_mail)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_email_client), Toast.LENGTH_SHORT)
            .show()
    }
}

private fun shareApp(context: Context) {
    val appPackage = context.getString(R.string.tv_app_package)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
        putExtra(
            Intent.EXTRA_TEXT,
            context.getString(R.string.share_app_text, context.getString(R.string.app_name), appPackage)
        )
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_via)))
}
