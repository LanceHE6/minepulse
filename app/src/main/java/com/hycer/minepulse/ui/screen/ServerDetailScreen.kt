package com.hycer.minepulse.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.hycer.minepulse.model.*
import com.hycer.minepulse.ui.theme.*
import com.hycer.minepulse.viewmodel.MainViewModel

enum class DetailTab(val title: String, val icon: ImageVector) {
    DASHBOARD("仪表盘", Icons.Default.Dashboard),
    PLAYERS("玩家", Icons.Default.People),
    CONSOLE("控制台", Icons.Default.Terminal),
    SETTINGS("配置", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailScreen(
    viewModel: MainViewModel,
    serverId: String,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(DetailTab.DASHBOARD) }
    val statusMap by viewModel.serverStatusMap.collectAsState()
    val runtimeState = statusMap[serverId]
    var isTopAddressVisible by remember { mutableStateOf(false) }

    if (runtimeState == null) {
        onBack()
        return
    }

    val server = runtimeState.server

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            server.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isTopAddressVisible) "${server.host}:${server.pingPort}" else "••••••••••••",
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isTopAddressVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isTopAddressVisible) "隐藏" else "显示",
                                tint = TextTertiary,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { isTopAddressVisible = !isTopAddressVisible }
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 0.dp
            ) {
                DetailTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = McGreen,
                            selectedTextColor = McGreen,
                            indicatorColor = DarkSurfaceVariant,
                            unselectedIconColor = TextTertiary,
                            unselectedTextColor = TextTertiary
                        )
                    )
                }
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                DetailTab.DASHBOARD -> DashboardTabContent(viewModel, runtimeState)
                DetailTab.PLAYERS -> PlayersTabContent(viewModel, runtimeState)
                DetailTab.CONSOLE -> ConsoleTabContent(viewModel, runtimeState)
                DetailTab.SETTINGS -> SettingsTabContent(viewModel, runtimeState, onDeleted = onBack)
            }

            if (runtimeState.isRconConnecting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = McGreen,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在建立 RCON 安全连接...", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// 1. DASHBOARD TAB
@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun DashboardTabContent(viewModel: MainViewModel, state: ServerRuntimeState) {
    val status = state.status
    val isOnline = status.isOnline

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isPinging,
        onRefresh = { viewModel.refreshActiveServerStatus() }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (status.iconBitmap != null) {
                                Image(
                                    bitmap = status.iconBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = McGreen, modifier = Modifier.size(32.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline) McGreen else ErrorRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isOnline) "服务器运行中" else "服务器未联通",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOnline) McGreen else ErrorRed
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "版本: ${if (isOnline) status.version else "未知"}",
                                fontSize = 13.sp,
                                color = TextTertiary
                            )
                        }

                        if (isOnline && status.pingMs >= 0) {
                            Text(
                                text = "${status.pingMs} ms",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = McGreenLight
                            )
                        }
                    }

                    if (isOnline && (status.rawMotd.isNotEmpty() || status.motd.isNotEmpty())) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = parseMcColorString(if (status.rawMotd.isNotEmpty()) status.rawMotd else status.motd),
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = DarkCardBorder, thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        MetricItem(title = "在线玩家", value = if (isOnline) "${status.onlinePlayers} / ${status.maxPlayers}" else "--")
                        MetricItem(title = "RCON 状态", value = if (state.isRconConnected) "已就绪" else "待通信", valueColor = if (state.isRconConnected) McGreen else WarningYellow)
                        MetricItem(
                            title = "游戏时间",
                            value = if (state.gameTicks >= 0) formatGameDayTime(state.gameTicks, state.dayTimeTicks) else "--"
                        )
                    }
                }
            }
        }

        // TPS & Performance Card
        item {
            var showChartPage by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount < -20) {
                                showChartPage = true // swipe left -> chart
                            } else if (dragAmount > 20) {
                                showChartPage = false // swipe right -> gauges
                            }
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("性能监控", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        IconButton(onClick = { viewModel.refreshTps() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新TPS", tint = TextTertiary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val tpsInfo = state.tpsInfo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (tpsInfo.isSupported) {
                            if (!showChartPage) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val tpsColor = when {
                                        tpsInfo.tps >= 20.0 -> McGreen
                                        tpsInfo.tps >= 15.0 -> WarningYellow
                                        else -> ErrorRed
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${String.format("%.1f", tpsInfo.tps)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = tpsColor)
                                        Text("TPS (标准: 20.0)", fontSize = 12.sp, color = TextTertiary)
                                    }

                                    if (tpsInfo.mspt > 0) {
                                        val msptColor = when {
                                            tpsInfo.mspt <= 30.0 -> McGreen
                                            tpsInfo.mspt <= 40.0 -> WarningYellow
                                            tpsInfo.mspt <= 50.0 -> WarningPurple
                                            else -> ErrorRed
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${String.format("%.1f", tpsInfo.mspt)} ms", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = msptColor)
                                            Text("MSPT (单Tick耗时)", fontSize = 12.sp, color = TextTertiary)
                                        }
                                    }
                                }
                            } else {
                                // Page 2: Line chart
                                PerformanceChart(tpsInfo.history)
                            }
                        } else {
                            // Plan B: Placeholder when mod is not installed
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceVariant)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("未检测到性能监测模组 (Carpet / Spark)", fontSize = 13.sp, color = TextTertiary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("需支持 /tick query 或 /tps 指令", fontSize = 11.sp, color = TextTertiary.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }

                    // Indicators (Dots indicating swipeable pages)
                    if (tpsInfo.isSupported) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = if (!showChartPage) 16.dp else 6.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(if (!showChartPage) McGreen else DarkCardBorder)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = if (showChartPage) 16.dp else 6.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(if (showChartPage) McGreen else DarkCardBorder)
                            )
                        }
                    }
                }
            }
        }

        // Online Players Preview
        item {
            val activeOnlinePlayers = state.onlinePlayerList.filter { it.isOnline }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("当前在线玩家 (${activeOnlinePlayers.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (activeOnlinePlayers.isEmpty()) {
                        Text("当前暂无玩家在线", fontSize = 13.sp, color = TextTertiary)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            activeOnlinePlayers.forEach { player ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceVariant)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkBackground),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = "https://minotar.net/avatar/${player.name}/64",
                                            contentDescription = "Avatar",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(player.name, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        PullRefreshIndicator(
            refreshing = state.isPinging,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = DarkSurface,
            contentColor = McGreen
        )
    }
}

// 2. PLAYERS TAB
@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun PlayersTabContent(viewModel: MainViewModel, state: ServerRuntimeState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var selectedPlayer by remember { mutableStateOf<PlayerInfo?>(null) }
    var dmPlayer by remember { mutableStateOf<PlayerInfo?>(null) }
    var dmMessage by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<Triple<String, String, String>?>(null) } // (title, cmd, confirmText)
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredPlayers = remember(state.onlinePlayerList, searchQuery) {
        if (searchQuery.isBlank()) {
            state.onlinePlayerList
        } else {
            val q = searchQuery.trim().lowercase()
            state.onlinePlayerList.filter {
                it.name.lowercase().contains(q) || it.uuid.lowercase().contains(q)
            }
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isPinging,
        onRefresh = { viewModel.executeRconCommand("list", silent = true) }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("在线玩家 (${state.onlinePlayerList.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索玩家名称或 UUID...", fontSize = 13.sp, color = TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清除", tint = TextTertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = McGreen,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredPlayers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.isBlank()) "暂无在线玩家，可下拉刷新" else "未找到匹配的玩家", color = TextTertiary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredPlayers, key = { it.name }) { player ->
                    val isOnline = player.isOnline
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedPlayer = player },
                        colors = CardDefaults.cardColors(containerColor = if (isOnline) DarkSurface else DarkSurface.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = "https://minotar.net/avatar/${player.name}/100",
                                    contentDescription = "Player Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    alpha = if (isOnline) 1f else 0.4f
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isOnline) McGreen else TextTertiary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = player.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isOnline) TextPrimary else TextTertiary
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = player.uuid,
                                    fontSize = 11.sp,
                                    color = TextTertiary.copy(alpha = if (isOnline) 0.8f else 0.5f),
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (isOnline) {
                                            dmPlayer = player
                                            dmMessage = ""
                                        }
                                    },
                                    enabled = isOnline,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Chat,
                                        contentDescription = "私信",
                                        tint = if (isOnline) McGreen else TextTertiary.copy(alpha = 0.35f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.MoreVert, contentDescription = null, tint = TextTertiary)
                            }
                        }
                    }
                }
            }
        }
        }

        PullRefreshIndicator(
            refreshing = state.isPinging,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = DarkSurface,
            contentColor = McGreen
        )
    }

    // Direct Message Dialog
    if (dmPlayer != null) {
        val player = dmPlayer!!
        AlertDialog(
            onDismissRequest = { dmPlayer = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = McGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("发送私信给: ${player.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dmMessage,
                        onValueChange = { dmMessage = it },
                        placeholder = { Text("输入要发送的消息内容...", fontSize = 13.sp, color = TextTertiary) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = McGreen,
                            unfocusedBorderColor = DarkCardBorder
                        )
                    )
                    Text("将通过 /tell 或 /msg 指令发送到玩家游戏聊天窗口", fontSize = 11.sp, color = TextTertiary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val textToSend = dmMessage.trim()
                        if (textToSend.isNotEmpty()) {
                            val toRun = "msg ${player.name} $textToSend"
                            dmPlayer = null
                            scope.launch {
                                val res = viewModel.executeCommandWithResult(toRun)
                                res.onSuccess {
                                    Toast.makeText(context, "私信已发送给 ${player.name}", Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    Toast.makeText(context, "私信发送失败: ${err.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("发送", color = McGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { dmPlayer = null }) {
                    Text("取消", color = TextTertiary, fontSize = 13.sp)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Player action dialog (Compact, OP above kick/ban)
    if (selectedPlayer != null) {
        val player = selectedPlayer!!
        AlertDialog(
            onDismissRequest = { selectedPlayer = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = "https://minotar.net/avatar/${player.name}/64",
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            player.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(player.name))
                            Toast.makeText(context, "已复制玩家名称", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制名字", tint = McGreen, modifier = Modifier.size(16.dp))
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "UUID: ${player.uuid}",
                            fontSize = 11.sp,
                            color = TextTertiary,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(player.uuid))
                                Toast.makeText(context, "已复制 UUID", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制UUID", tint = TextTertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))

                    // Row 1: OP & Deop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                confirmAction = Triple("设为管理员", "op ${player.name}", "确定要将【${player.name}】设为服务器管理员 (OP) 吗？")
                                selectedPlayer = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = McGreen.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("提权 OP", fontSize = 13.sp, color = McGreen, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                confirmAction = Triple("撤销管理员", "deop ${player.name}", "确定要撤销【${player.name}】的管理员权限吗？")
                                selectedPlayer = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("撤销 OP", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Row 2: Kick & Ban
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                confirmAction = Triple("踢出玩家", "kick ${player.name}", "确定要将【${player.name}】踢出服务器吗？")
                                selectedPlayer = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("踢出 Kick", fontSize = 13.sp, color = WarningYellow, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                confirmAction = Triple("封禁玩家", "ban ${player.name}", "确定要永久封禁【${player.name}】吗？该操作将拉黑其玩家账号。")
                                selectedPlayer = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("封禁 Ban", fontSize = 13.sp, color = ErrorRed, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { selectedPlayer = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextTertiary)
                ) {
                    Text("取消", fontSize = 13.sp)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Secondary Confirmation Dialog
    if (confirmAction != null) {
        val (title, cmd, prompt) = confirmAction!!
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text(prompt, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toRun = cmd
                        confirmAction = null
                        scope.launch {
                            val res = viewModel.executeCommandWithResult(toRun)
                            res.onSuccess { out ->
                                actionFeedback = if (out.isNotBlank()) out else "指令执行成功：$toRun"
                            }.onFailure { err ->
                                actionFeedback = "执行失败：${err.message}"
                            }
                        }
                    }
                ) {
                    Text("确认执行", color = McGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text("取消", color = TextTertiary, fontSize = 13.sp)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (actionFeedback != null) {
        AlertDialog(
            onDismissRequest = { actionFeedback = null },
            title = {            Text("执行结果", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Text(
                    actionFeedback!!,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { actionFeedback = null }) {
                    Text("知道了", color = McGreen, fontSize = 13.sp)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ConsoleTabContent(viewModel: MainViewModel, state: ServerRuntimeState) {
    var commandInput by remember { mutableStateOf("") }
    val macros by viewModel.macroList.collectAsState()
    val listState = rememberLazyListState()
    var showAddMacroDialog by remember { mutableStateOf(false) }
    var macroToDelete by remember { mutableStateOf<QuickMacro?>(null) }

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            try {
                listState.scrollToItem(state.logs.size - 1)
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Quick Macro Chips (Horizontal Scroll)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF08080A))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (state.logs.isEmpty()) {
                item {
                    Text(
                        "终端准备就绪。在下方输入指令或点击快捷指令执行。",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                items(state.logs, key = { it.id }) { log ->
                    val color = when (log.type) {
                        LogType.COMMAND -> McGreen
                        LogType.ERROR -> ErrorRed
                        LogType.OUTPUT -> Color(0xFFE0E0E0)
                        LogType.INFO -> TextTertiary
                    }
                    Text(
                        text = "[${log.timestamp}] ${log.text}",
                        color = color,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Quick Macros bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(top = 8.dp, bottom = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "常用命令 (点击执行，长按删除)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextTertiary
                )
                TextButton(
                    onClick = { showAddMacroDialog = true },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加", tint = McGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("添加指令", fontSize = 11.sp, color = McGreen)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Scrollable row of macros
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(macros, key = { it.id }) { macro ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = { viewModel.executeRconCommand(macro.command) },
                                    onLongClick = { macroToDelete = macro }
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(macro.label, fontSize = 12.sp, color = TextPrimary)
                        }
                    }
                }
            }
        }

        // Command input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = { Text("输入服务器指令 (如: help, time set day)...", fontSize = 13.sp, color = TextTertiary) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (commandInput.isNotBlank()) {
                        viewModel.executeRconCommand(commandInput.trim())
                        commandInput = ""
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = McGreen,
                    unfocusedBorderColor = DarkCardBorder
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        viewModel.executeRconCommand(commandInput.trim())
                        commandInput = ""
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(McGreen)
            ) {
                Icon(Icons.Default.Send, contentDescription = "发送", tint = DarkBackground)
            }
        }
    }

    if (showAddMacroDialog) {
        var label by remember { mutableStateOf("") }
        var command by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddMacroDialog = false },
            title = { Text("添加常用指令", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("按钮名称 (如: 白名单)", fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = McGreen,
                            unfocusedBorderColor = DarkCardBorder
                        )
                    )
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("执行指令 (如: whitelist list)", fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = McGreen,
                            unfocusedBorderColor = DarkCardBorder
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (label.isNotBlank() && command.isNotBlank()) {
                            viewModel.addMacro(label.trim(), command.trim())
                            showAddMacroDialog = false
                        }
                    }
                ) {
                    Text("添加", color = McGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMacroDialog = false }) {
                    Text("取消", color = TextTertiary, fontSize = 13.sp)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (macroToDelete != null) {
        val macro = macroToDelete!!
        AlertDialog(
            onDismissRequest = { macroToDelete = null },
            title = { Text("删除常用指令", color = TextPrimary) },
            text = { Text("确定要删除快捷指令【${macro.label}】(${macro.command}) 吗？", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMacro(macro.id)
                        macroToDelete = null
                    }
                ) {
                    Text("删除", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { macroToDelete = null }) {
                    Text("取消", color = TextTertiary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

// 4. SETTINGS TAB
@Composable
fun SettingsTabContent(
    viewModel: MainViewModel,
    state: ServerRuntimeState,
    onDeleted: () -> Unit
) {
    val server = state.server
    var name by remember { mutableStateOf(server.name) }
    var host by remember { mutableStateOf(server.host) }
    var pingPort by remember { mutableStateOf(server.pingPort.toString()) }
    var rconPort by remember { mutableStateOf(server.rconPort.toString()) }
    var rconPass by remember { mutableStateOf(server.rconPassword) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("服务器连接配置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("服务器名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = McGreen,
                    unfocusedBorderColor = DarkCardBorder
                )
            )
        }

        item {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("地址 (IP 或 域名)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = McGreen,
                    unfocusedBorderColor = DarkCardBorder
                )
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pingPort,
                    onValueChange = { pingPort = it },
                    label = { Text("Ping 端口") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = McGreen,
                        unfocusedBorderColor = DarkCardBorder
                    )
                )
                OutlinedTextField(
                    value = rconPort,
                    onValueChange = { rconPort = it },
                    label = { Text("RCON 端口") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = McGreen,
                        unfocusedBorderColor = DarkCardBorder
                    )
                )
            }
        }

        item {
            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = rconPass,
                onValueChange = { rconPass = it },
                label = { Text("RCON 密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            tint = TextTertiary
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = McGreen,
                    unfocusedBorderColor = DarkCardBorder
                )
            )
        }

        item {
            Button(
                onClick = {
                    val updated = server.copy(
                        name = name.trim(),
                        host = host.trim(),
                        pingPort = pingPort.toIntOrNull() ?: 20000,
                        rconPort = rconPort.toIntOrNull() ?: 25574,
                        rconPassword = rconPass.trim()
                    )
                    viewModel.updateServer(updated)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = McGreen)
            ) {
                Text("保存配置修改", color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = DarkCardBorder, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.15f))
            ) {
                Text("删除此服务器", color = ErrorRed, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除", color = TextPrimary) },
            text = { Text("确定要从列表中移除【${server.name}】吗？此操作无法撤销。", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteServer(server.id)
                        showDeleteConfirm = false
                        onDeleted()
                    }
                ) {
                    Text("确认删除", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = TextTertiary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun PerformanceChart(history: List<TpsHistoryPoint>) {
    if (history.size < 2) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("正在收集采样数据，请稍候...", fontSize = 12.sp, color = TextTertiary)
        }
        return
    }

    val latestPoint = history.last()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "最新: TPS ${String.format("%.1f", latestPoint.tps)}",
                    fontSize = 11.sp,
                    color = McGreen,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "MSPT ${String.format("%.1f", latestPoint.mspt)}ms",
                    fontSize = 11.sp,
                    color = WarningPurple,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(McGreen))
                Spacer(modifier = Modifier.width(3.dp))
                Text("TPS", fontSize = 10.sp, color = TextTertiary)
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(WarningPurple))
                Spacer(modifier = Modifier.width(3.dp))
                Text("MSPT", fontSize = 10.sp, color = TextTertiary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            val width = size.width
            val height = size.height

            val leftMargin = 70f
            val chartWidth = width - leftMargin
            val maxTps = 20.0
            val maxMspt = 60.0

            val paint = android.graphics.Paint().apply {
                color = 0xFF71717A.toInt() // TextTertiary
                textSize = 24f
                isAntiAlias = true
            }

            // Draw Y-Axis Guideline & Labels (20 TPS / 60ms)
            drawLine(
                color = DarkCardBorder,
                start = Offset(leftMargin, 0f),
                end = Offset(width, 0f),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText("20 / 60", 4f, 26f, paint)

            // Draw Middle Guideline (10 TPS / 30ms)
            drawLine(
                color = DarkCardBorder.copy(alpha = 0.5f),
                start = Offset(leftMargin, height / 2),
                end = Offset(width, height / 2),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText("10 / 30", 4f, height / 2 + 8f, paint)

            // Draw Bottom Guideline (0 TPS / 0ms)
            drawLine(
                color = DarkCardBorder,
                start = Offset(leftMargin, height),
                end = Offset(width, height),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText("0 / 0", 12f, height - 4f, paint)

            // Vertical axis line
            drawLine(
                color = DarkCardBorder,
                start = Offset(leftMargin, 0f),
                end = Offset(leftMargin, height),
                strokeWidth = 1.5f
            )

            val stepX = chartWidth / (history.size - 1).coerceAtLeast(1)

            // Draw TPS line (McGreen)
            val tpsPath = Path()
            history.forEachIndexed { index, point ->
                val x = leftMargin + index * stepX
                val normalizedTps = (point.tps / maxTps).coerceIn(0.0, 1.0)
                val y = height - (normalizedTps * height).toFloat()
                if (index == 0) tpsPath.moveTo(x, y) else tpsPath.lineTo(x, y)
            }
            drawPath(
                path = tpsPath,
                color = McGreen,
                style = Stroke(width = 3.5f)
            )

            // Draw MSPT line (WarningPurple)
            val msptPath = Path()
            history.forEachIndexed { index, point ->
                val x = leftMargin + index * stepX
                val normalizedMspt = (point.mspt / maxMspt).coerceIn(0.0, 1.0)
                val y = height - (normalizedMspt * height).toFloat()
                if (index == 0) msptPath.moveTo(x, y) else msptPath.lineTo(x, y)
            }
            drawPath(
                path = msptPath,
                color = WarningPurple,
                style = Stroke(width = 3.5f)
            )
        }
    }
}

fun formatGameDayTime(totalGameTicks: Long, dayTicks: Long): String {
    val days = totalGameTicks / 24000
    val currentDayTicks = if (dayTicks >= 0) dayTicks else (totalGameTicks % 24000)
    // 0 tick is 06:00, 6000 is 12:00
    val totalMinutes = ((currentDayTicks + 6000) % 24000) * 1440 / 24000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val timeStr = String.format("%02d:%02d", hours, minutes)
    return "第${days}天 $timeStr"
}

@Composable
fun MetricItem(title: String, value: String, valueColor: Color = TextPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Spacer(modifier = Modifier.height(2.dp))
        Text(title, fontSize = 11.sp, color = TextTertiary)
    }
}
