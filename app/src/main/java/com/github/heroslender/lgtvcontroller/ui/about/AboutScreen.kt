package com.github.heroslender.lgtvcontroller.ui.about

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heroslender.lgtvcontroller.R

private const val SOURCE_CODE_URL = "https://github.com/CaptechOmar/lg-remote"
private const val ORIGINAL_URL = "https://github.com/heroslender/lg-remote"
private const val ICON_URL = "https://www.flaticon.com/free-icon/remote-control_18091280"

@Composable
fun AboutScreen(navigateBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(bottomEnd = 28.dp, bottomStart = 28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    IconButton(
                        onClick = navigateBack,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "LG TV Controller",
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = "Version ${appVersion(context)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Image(
                            painter = painterResource(R.drawable.about_icon),
                            contentDescription = "App icon",
                            modifier = Modifier.size(80.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1F)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp)
            ) {
                AboutItem(
                    title = "Source code",
                    subtitle = "On GitHub",
                    icon = Icons.Filled.Code,
                    onClick = { uriHandler.openUri(SOURCE_CODE_URL) },
                )
                AboutItem(
                    title = "Create an issue",
                    subtitle = "On GitHub",
                    icon = Icons.Filled.BugReport,
                    onClick = { uriHandler.openUri("$SOURCE_CODE_URL/issues/") },
                )
                AboutItem(
                    title = "Original project",
                    subtitle = "heroslender/lg-remote, which this app is a fork of",
                    icon = Icons.Filled.Favorite,
                    onClick = { uriHandler.openUri(ORIGINAL_URL) },
                )
                AboutItem(
                    title = "App icon",
                    subtitle = "Remote control icon by cah nggunung from Flaticon, edited",
                    icon = Icons.Filled.Image,
                    onClick = { uriHandler.openUri(ICON_URL) },
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AboutItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Suppress("DEPRECATION")
private fun appVersion(context: Context): String {
    return try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.versionCode})"
    } catch (e: Exception) {
        "unknown"
    }
}
