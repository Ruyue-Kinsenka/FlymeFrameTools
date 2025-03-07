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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener

// 颜色定义
private val GrayWhiteTheme = lightColorScheme(
    primary = Color(0xFF616161),
    secondary = Color(0xFF757575),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5F5F5),
    background = Color(0xFFFAFAFA),
    onPrimary = Color.White,
    onSurface = Color(0xFF212121),
    onBackground = Color(0xFF212121)
)

// super超分，frame插帧
enum class ListType {
    FRAME_INTERPOLATION,
    SUPER_RESOLUTION
}

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private val shizukuListener = OnRequestPermissionResultListener { _, _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        Shizuku.addRequestPermissionResultListener(shizukuListener)

        setContent {
            MaterialTheme(
                colorScheme = GrayWhiteTheme,
                shapes = MaterialTheme.shapes.copy(
                    small = RoundedCornerShape(16.dp),
                    medium = RoundedCornerShape(24.dp),
                    large = RoundedCornerShape(32.dp)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppEntryPoint()
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }
}

@Composable
private fun OnboardingFlow(onComplete: () -> Unit) {
    var currentStep by remember { mutableStateOf(1) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        when (currentStep) {
            1 -> OnboardingPage(
                title = "FlymeFrameTools",
                desc = "由Shizuku驱动的Frame管理",
                buttonText = "继续",
                onAction = { currentStep++ }
            )
            2 -> PermissionStep(
                onGranted = { currentStep++ },
                onDenied = { showPermissionDeniedAlert(context) }
            )
            3 -> OnboardingPage(
                title = "准备就绪",
                desc = "现在可以开始管理您的应用列表",
                buttonText = "开始体验",
                onAction = onComplete
            )
        }
    }
}

@Composable
private fun OnboardingPage(
    title: String,
    desc: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // 左上方欢迎
        Text(
            text = "欢迎",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.align(Alignment.TopStart)
        )

        // 中间内容区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    )
                }
            }
        }

        // 右下角继续按钮
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 24.dp)
        ) {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .height(48.dp)
                    .width(150.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(buttonText, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent() {
    val isShizukuRunning = remember { mutableStateOf(Shizuku.pingBinder()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // 定时检查Shizuku状态
        while (true) {
            isShizukuRunning.value = Shizuku.pingBinder()
            kotlinx.coroutines.delay(1000)
        }
    }

    if (!isShizukuRunning.value) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Shizuku未运行") },
            text = { Text("请先启动Shizuku服务") },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
                        })
                    }
                ) { Text("前往下载") }
            },
        )
        return
    }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("主页", "关于")

    var frameList by remember { mutableStateOf<List<String>>(emptyList()) }
    var superList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDialog by remember { mutableStateOf<ListType?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loadAllLists(
            onFrameLoaded = { frameList = it },
            onSuperLoaded = { superList = it }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Flyme视效Pro",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .padding(8.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.Home
                                    else -> Icons.Default.Info
                                },
                                contentDescription = title
                            )
                        },
                        label = {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
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
        Text(
            text = "应用列表管理",
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(16.dp))

        DynamicListSection(
            title = "插帧应用列表",
            list = frameList,
            onAdd = onAddFrame,
            onRemove = onRemoveFrame,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.height(16.dp))

        DynamicListSection(
            title = "超分应用列表",
            list = superList,
            onAdd = onAddSuper,
            onRemove = onRemoveSuper,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DynamicListSection(
    title: String,
    list: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$title (${list.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = "添加",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(list, key = { it }) { pkg ->
                    var isRemoving by remember { mutableStateOf(false) }
                    AnimatedVisibility(
                        visible = !isRemoving,
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = getAppName(pkg),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )

                                IconButton(
                                    onClick = {
                                        isRemoving = true
                                        onRemove(pkg)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun getAppName(pkg: String): String {
    val context = LocalContext.current
    return remember(pkg) {
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }
}

@Composable
private fun AboutContent() {
    var showInstructionsDialog by remember { mutableStateOf(false) }
    var showSponsorImage by remember { mutableStateOf(false) }
    val contributors = listOf(
        Contributor("Ruyue", "https://github.com/Ruyue-Kinsenka"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 使用说明弹窗
        if (showInstructionsDialog) {
            AlertDialog(
                onDismissRequest = { showInstructionsDialog = false },
                title = {
                    Text(
                        "使用说明",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    Text(
                        "1.使用后不要进入设置里的管理\n会自动重制为系统指定app\n" +
                                "2. 确保已授予Shizuku权限\n"
                                ,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { showInstructionsDialog = false }
                    ) {
                        Text("确定")
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }

        // 赞助图片弹窗
        if (showSponsorImage) {
            Dialog(onDismissRequest = { showSponsorImage = false }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .width(280.dp)
                        .height(360.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.sponsor),
                        contentDescription = "赞助二维码",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // 关于页面内容
        Card(
            modifier = Modifier
                .size(120.dp)
                .shadow(8.dp, CircleShape),
            shape = CircleShape
        ) {
            Image(
                painter = painterResource(R.drawable.icon_page),
                contentDescription = "logo",
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Flyme视效Pro",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        )

        Text(
            text = "1.0.0",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(32.dp))

        // 功能卡片组
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AboutCard(
                title = "使用说明",
                icon = Icons.Default.Info,
                onClick = { showInstructionsDialog = true }
            )

            AboutCard(
                title = "赞助支持",
                icon = Icons.Default.Favorite,
                onClick = { showSponsorImage = true }
            )

            ContributorSection(
                title = "开发者",
                contributors = contributors
            )
        }
    }
}

@Composable
private fun AboutCard(
    title: String,
    icon: ImageVector = Icons.Default.Info,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
private fun ContributorSection(
    title: String,
    contributors: List<Contributor>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn {
            items(contributors) { contributor ->
                ContributorItem(contributor = contributor)
            }
        }
    }
}

@Composable
private fun ContributorItem(contributor: Contributor) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(contributor.url))
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "贡献者",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = contributor.name,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

data class Contributor(val name: String, val url: String)


// 数据操作
private suspend fun loadAllLists(
    onFrameLoaded: (List<String>) -> Unit,
    onSuperLoaded: (List<String>) -> Unit
) {
    onFrameLoaded(loadPackageList("flyme_vpp_frc_pkg_list"))
    onSuperLoaded(loadPackageList("flyme_vpp_ais_pkg_list"))
}

private suspend fun loadPackageList(key: String): List<String> {
    return try {
        if (!Shizuku.pingBinder()) return emptyList()
        if (checkShizukuPermission()) {
            executeCommand("settings get global $key")
                .split(",")
                .filter { it.isNotEmpty() }
                .distinct()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        emptyList()
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


@Composable
private fun PermissionStep(onGranted: () -> Unit, onDenied: () -> Unit) {
    var showError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when {
            !Shizuku.pingBinder() -> {
                showError = true
                onDenied()
            }
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> onGranted()
            else -> Shizuku.requestPermission(100)
        }
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text("服务未启动") },
            text = { Text("请先启动Shizuku服务并授予权限") },
            confirmButton = {
            },
            shape = RoundedCornerShape(25.dp)
        )
    }

    OnboardingPage(
        title = "权限授权",
        desc = "请点击下方按钮授予Shizuku权限",
        buttonText = "立即授权",
        onAction = {
            when {
                !Shizuku.pingBinder() -> showError = true
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> onGranted()
                Shizuku.shouldShowRequestPermissionRationale() -> showError = true
                else -> Shizuku.requestPermission(100)
            }
        }
    )
}

// 工具函数
private fun checkShizukuPermission(): Boolean {
    return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
}

private suspend fun executeCommand(command: String): String {
    return try {
        if (!Shizuku.pingBinder()) return "Shizuku未运行"
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        process.waitFor()
        process.inputStream.bufferedReader().use { it.readText() }.trim()
    } catch (e: Exception) {
        "执行错误: ${e.message}"
    }
}

private fun isOnboardingComplete(context: Context): Boolean {
    return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getBoolean("onboarding_complete", false)
}

private fun markOnboardingComplete(context: Context) {
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit {
        putBoolean("onboarding_complete", true)
    }
}

private fun showPermissionDeniedAlert(context: Context) {
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