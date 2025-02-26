package com.ruyue.flymeframetools

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener

// 定义列表类型
enum class ListType {
    FRAME_INTERPOLATION,
    SUPER_RESOLUTION
}

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private val shizukuListener = OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 100) handlePermissionResult(grantResult)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        Shizuku.addRequestPermissionResultListener(shizukuListener)

        setContent {
            AppEntryPoint()
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }

    private fun handlePermissionResult(result: Int) {}
}

// 引导流程
@Composable
private fun OnboardingFlow(onComplete: () -> Unit) {
    var currentStep by remember { mutableStateOf(1) }
    val context = LocalContext.current

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (currentStep) {
                1 -> OnboardingPage(
                    title = "欢迎使用帧率优化工具",
                    desc = "本工具需要Shizuku权限管理系统级设置",
                    imageId = R.drawable.test,
                    buttonText = "继续",
                    onAction = { currentStep++ }
                )
                2 -> PermissionStep(
                    onGranted = { currentStep++ },
                    onDenied = { showPermissionDeniedAlert(context) }
                )
                3 -> OnboardingPage(
                    title = "准备就绪",
                    desc = "已完成所有必要设置\n现在可以开始配置优化方案",
                    imageId = R.drawable.test1,
                    buttonText = "开始使用",
                    onAction = {
                        onComplete()
                    }
                )
            }

            StepIndicator(currentStep, 3)
        }
    }
}

@Composable
private fun AppEntryPoint() {
    val context = LocalContext.current
    val onboardingComplete = remember { isOnboardingComplete(context) }
    val showOnboarding = remember { mutableStateOf(!onboardingComplete) }

    if (showOnboarding.value) {
        OnboardingFlow {
            markOnboardingComplete(context)
            showOnboarding.value = false
        }
    } else {
        MainContent()
    }
}

// 主界面
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("主页", "关于")

    var frameList by remember { mutableStateOf<List<String>>(emptyList()) }
    var superList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDialog by remember { mutableStateOf<ListType?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val hasShizukuPermission = remember { mutableStateOf(checkShizukuPermission()) }
    var showPermissionDialog by remember { mutableStateOf(!hasShizukuPermission.value) }

    LaunchedEffect(Unit) {
        loadAllLists(
            onFrameLoaded = { frameList = it },
            onSuperLoaded = { superList = it }
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要Shizuku权限") },
            text = { Text("本应用需要Shizuku权限来管理系统设置") },
            confirmButton = {
                TextButton(onClick = {
                    // 请求Shizuku权限
                    Shizuku.requestPermission(100)
                }) {
                    Text("立即授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Flyme视效Pro") }) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                when (index) {
                                    0 -> Icons.Default.Home
                                    else -> Icons.Default.Info
                                },
                                contentDescription = title
                            )
                        },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeContent(
                    frameList = frameList,
                    superList = superList,
                    onAddFrame = { showDialog = ListType.FRAME_INTERPOLATION },
                    onAddSuper = { showDialog = ListType.SUPER_RESOLUTION },
                    onRemoveFrame = { pkg ->
                        coroutineScope.launch {
                            frameList = removePackage(pkg, "flyme_vpp_frc_pkg_list")
                        }
                    },
                    onRemoveSuper = { pkg ->
                        coroutineScope.launch {
                            superList = removePackage(pkg, "flyme_vpp_ais_pkg_list")
                        }
                    }
                )
                1 -> AboutContent()
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 100) {
                hasShizukuPermission.value = grantResult == PackageManager.PERMISSION_GRANTED
                showPermissionDialog = !hasShizukuPermission.value
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)

        onDispose {
            Shizuku.removeRequestPermissionResultListener(listener)
        }
    }

    when (showDialog) {
        ListType.FRAME_INTERPOLATION -> AppPickerDialog(
            onDismiss = { showDialog = null },
            onConfirm = { pkg ->
                coroutineScope.launch {
                    frameList = addPackage(pkg, frameList, "flyme_vpp_frc_pkg_list")
                }
            }
        )
        ListType.SUPER_RESOLUTION -> AppPickerDialog(
            onDismiss = { showDialog = null },
            onConfirm = { pkg ->
                coroutineScope.launch {
                    superList = addPackage(pkg, superList, "flyme_vpp_ais_pkg_list")
                }
            }
        )
        null -> Unit
    }
}

@Composable
private fun HomeContent(
    frameList: List<String>,
    superList: List<String>,
    onAddFrame: () -> Unit,
    onAddSuper: () -> Unit,
    onRemoveFrame: (String) -> Unit,
    onRemoveSuper: (String) -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        ListSection(
            title = "插帧应用列表",
            list = frameList,
            onAdd = onAddFrame,
            onRemove = onRemoveFrame
        )
        Spacer(Modifier.height(16.dp))
        ListSection(
            title = "超分应用列表",
            list = superList,
            onAdd = onAddSuper,
            onRemove = onRemoveSuper
        )
    }
}

@Composable
private fun AboutContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.test),
            contentDescription = "Ruyue",
            modifier = Modifier
                .size(120.dp)
                .background(Color.LightGray, CircleShape)
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "GitHub",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val context = LocalContext.current
        Text(
            text = "temp",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("temp")
                }
                context.startActivity(intent)
            }
        )
    }
}

@Composable
private fun ListSection(
    title: String,
    list: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("$title (${list.size})", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onAdd) {
                    Text("添加应用")
                }
            }

            LazyColumn(Modifier.height(200.dp)) {
                items(list, key = { it }) { pkg ->
                    var isRemoving by remember { mutableStateOf(false) }
                    AnimatedVisibility(
                        visible = !isRemoving,
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        ListItem(
                            headlineContent = { Text(pkg) },
                            trailingContent = {
                                IconButton(onClick = {
                                    isRemoving = true
                                    onRemove(pkg)
                                }) {
                                    Icon(Icons.Default.Close, "删除")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// 数据操作
private suspend fun loadAllLists(
    onFrameLoaded: (List<String>) -> Unit,
    onSuperLoaded: (List<String>) -> Unit
) {
    onFrameLoaded(loadPackageList("flyme_vpp_frc_pkg_list"))
    onSuperLoaded(loadPackageList("flyme_vpp_ais_pkg_list"))
}

private suspend fun loadPackageList(key: String): List<String> {
    return if (checkShizukuPermission()) {
        executeCommand("settings get global $key")
            .split(",")
            .filter { it.isNotEmpty() }
            .distinct()
    } else {
        listOf("需要Shizuku权限")
    }
}

private suspend fun addPackage(pkg: String, currentList: List<String>, key: String): List<String> {
    if (pkg in currentList) return currentList
    val newList = currentList + pkg
    executeCommand("settings put global $key ${newList.joinToString(",")}")
    return newList
}

private suspend fun removePackage(pkg: String, key: String): List<String> {
    val newList = executeCommand("settings get global $key")
        .split(",")
        .filter { it.isNotEmpty() && it != pkg }
    executeCommand("settings put global $key ${newList.joinToString(",")}")
    return newList
}

// 引导
//三个页面
//一个app info
//一个权限shuzuki
//一条欢迎
@Composable
private fun OnboardingPage(
    title: String,
    desc: String,
    imageId: Int,
    buttonText: String,
    onAction: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            Image(
                painter = painterResource(imageId),
                contentDescription = null,
                modifier = Modifier.size(200.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onAction, modifier = Modifier.width(200.dp)) {
            Text(buttonText)
        }
    }
}

@Composable
private fun PermissionStep(onGranted: () -> Unit, onDenied: () -> Unit) {
    val context = LocalContext.current
    var showError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when {
            Shizuku.isPreV11() -> onDenied()
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> onGranted()
            else -> Shizuku.requestPermission(100)
        }
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text("权限未授予") },
            text = { Text("必须授予Shizuku权限才能继续使用") },
            confirmButton = {
                TextButton(onClick = { showError = false }) { Text("确定") }
            }
        )
    }

    OnboardingPage(
        title = "权限授权",
        desc = "请点击下方按钮授予Shizuku权限",
        imageId = R.drawable.test2,
        buttonText = "立即授权",
        onAction = {
            when {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> onGranted()
                Shizuku.shouldShowRequestPermissionRationale() -> showError = true
                else -> Shizuku.requestPermission(100)
            }
        }
    )
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(Modifier.padding(16.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        color = if (index == current - 1) MaterialTheme.colorScheme.primary
                        else Color.LightGray,
                        shape = CircleShape
                    )
            )
            if (index < total - 1) Spacer(Modifier.width(8.dp))
        }
    }
}

// 检查权限，然后返回
private fun checkShizukuPermission(): Boolean {
    return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
}

//shuzuki cmd
private suspend fun executeCommand(command: String): String {
    return try {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        process.waitFor()
        process.inputStream.bufferedReader().use { it.readText() }.trim()
    } catch (e: Exception) {
        "执行错误: ${e.message}"
    }
}


private fun isOnboardingComplete(context: Context): Boolean {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("onboarding_complete", false)
}

private fun markOnboardingComplete(context: Context) {
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit {
        putBoolean("onboarding_complete", true)
        apply()
    }
}

private fun showPermissionDeniedAlert(context: android.content.Context) {
    androidx.appcompat.app.AlertDialog.Builder(context)
        .setTitle("权限被拒绝")
        .setMessage("需要Shizuku权限才能使用全部功能")
        .setPositiveButton("设置") { _, _ ->
            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            })
        }
        .setNegativeButton("取消", null)
        .show()
}