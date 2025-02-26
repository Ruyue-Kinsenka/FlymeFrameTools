package com.ruyue.flymeframetools

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun AppPickerDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    val apps = remember {
        packageManager.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .filter { app ->
                val isSystemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
                val isUpdatedSystemApp = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                !isSystemApp || isUpdatedSystemApp
            }
            .sortedBy { it.loadLabel(packageManager).toString() }
    }
//不知道为什么只能获取shuzuki包名，只能这么写
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("选择应用", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null)
                    }
                }
                Divider(Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.heightIn(max = 500.dp)) {
                    items(apps) { app ->
                        val appName = app.loadLabel(packageManager).toString()
                        ListItem(
                            headlineContent = { Text(appName) },
                            supportingContent = { Text(app.packageName) },
                            modifier = Modifier.clickable {
                                onConfirm(app.packageName)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}