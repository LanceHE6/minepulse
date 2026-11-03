package com.hycer.minepulse.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Widgets
import com.hycer.minepulse.model.ServerItem
import com.hycer.minepulse.model.ServerRuntimeState
import com.hycer.minepulse.ui.theme.*
import com.hycer.minepulse.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material.ExperimentalMaterialApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ServerListScreen(
    viewModel: MainViewModel,
    onServerClick: (ServerItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val serverList by viewModel.serverList.collectAsState()
    val statusMap by viewModel.serverStatusMap.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var contextMenuServer by remember { mutableStateOf<ServerItem?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var serverToDelete by remember { mutableStateOf<ServerItem?>(null) }

    val isRefreshing = statusMap.values.any { it.isPinging }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refreshAllServers() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(McGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "MinePulse",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = McGreen,
                contentColor = DarkBackground,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加服务器")
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            if (serverList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无服务器，请点击右下角添加", color = TextTertiary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(serverList, key = { it.id }) { server ->
                        val runtimeState = statusMap[server.id]
                        ServerCard(
                            server = server,
                            runtimeState = runtimeState,
                            onClick = { onServerClick(server) },
                            onLongClick = { contextMenuServer = server }
                        )
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = DarkSurface,
                contentColor = McGreen
            )
        }
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, host, pingPort, rconPort, rconPass ->
                viewModel.addServer(name, host, pingPort, rconPort, rconPass)
                showAddDialog = false
            }
        )
    }

    // Context Menu Dialog on Long Press
    if (contextMenuServer != null) {
        val server = contextMenuServer!!
        AlertDialog(
            onDismissRequest = { contextMenuServer = null },
            title = {
                Text(
                    server.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val target = server
                            contextMenuServer = null
                            val hasPermission = com.hycer.minepulse.widget.ServerWidgetProvider.hasShortcutPermission(context)
                            if (hasPermission) {
                                com.hycer.minepulse.widget.ServerWidgetProvider.pinWidget(
                                    context,
                                    target,
                                    onPermissionDenied = {
                                        showPermissionDialog = true
                                    }
                                )
                            } else {
                                showPermissionDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Widgets, contentDescription = null, tint = McGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("添加到桌面小组件", fontSize = 13.sp, color = TextPrimary)
                    }

                    Button(
                        onClick = {
                            serverToDelete = server
                            contextMenuServer = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("删除服务器", fontSize = 13.sp, color = ErrorRed)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextMenuServer = null }) {
                    Text("取消", color = TextTertiary, fontSize = 13.sp)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (serverToDelete != null) {
        val server = serverToDelete!!
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("确认删除", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("确定要删除服务器【${server.name}】吗？", fontSize = 13.sp, color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteServer(server.id)
                        serverToDelete = null
                    }
                ) {
                    Text("删除", color = ErrorRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text("取消", color = TextTertiary, fontSize = 13.sp)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Permission Guidance Dialog (like mainstream apps)
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要开启桌面快捷方式权限", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Text(
                    "检测到系统尚未允许 MinePulse 创建桌面小组件。\n\n请点击下方【前往设置】，在【权限管理】中开启“桌面快捷方式”权限后重试；\n\n另外，您也可以随时在手机桌面长按空白处，在系统小部件库中直接将 MinePulse 拖拽到桌面。",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "打开设置失败", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("前往设置", color = McGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("取消", color = TextTertiary, fontSize = 13.sp)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ServerCard(
    server: ServerItem,
    runtimeState: ServerRuntimeState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val status = runtimeState?.status
    val isOnline = status?.isOnline == true
    val isPinging = runtimeState?.isPinging == true
    var isAddressVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (isOnline) McGreenDark.copy(alpha = 0.5f) else DarkCardBorder, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Icon + Name/Host + Online Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Server Icon (Favicon or fallback)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (status?.iconBitmap != null) {
                        Image(
                            bitmap = status.iconBitmap.asImageBitmap(),
                            contentDescription = "Server Icon",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            tint = if (isOnline) McGreen else TextTertiary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isAddressVisible) "${server.host}:${server.pingPort}" else "••••••••••••",
                            fontSize = 13.sp,
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isAddressVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isAddressVisible) "隐藏地址" else "显示地址",
                            tint = TextTertiary,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { isAddressVisible = !isAddressVisible }
                        )
                    }
                }

                // Ping & Status Badge
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) McGreen else ErrorRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPinging) "探测中" else (if (isOnline) "在线" else "离线"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isOnline) McGreen else ErrorRed
                        )
                    }
                    if (isOnline && status != null && status.pingMs >= 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${status.pingMs} ms",
                            fontSize = 11.sp,
                            color = McGreenLight
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // MOTD
            if (isOnline && status != null && (status.rawMotd.isNotEmpty() || status.motd.isNotEmpty())) {
                Text(
                    text = parseMcColorString(if (status.rawMotd.isNotEmpty()) status.rawMotd else status.motd),
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Divider(color = DarkCardBorder.copy(alpha = 0.5f), thickness = 0.8.dp)

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Version + Players count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isOnline && status != null) status.version else "版本未知",
                    fontSize = 12.sp,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isOnline && status != null) "${status.onlinePlayers} / ${status.maxPlayers}" else "-- / --",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOnline) TextPrimary else TextTertiary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("人", fontSize = 11.sp, color = TextTertiary)
                }
            }
        }
    }
}

@Composable
fun AddServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, host: String, pingPort: Int, rconPort: Int, rconPass: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var pingPort by remember { mutableStateOf("25565") }
    var rconPort by remember { mutableStateOf("25575") }
    var rconPass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 Minecraft 服务器", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("服务器名称 (如: 主服)", fontSize = 12.sp) },
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
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("服务器地址 (域名或 IP)", fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = McGreen,
                        unfocusedBorderColor = DarkCardBorder
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pingPort,
                        onValueChange = { pingPort = it },
                        label = { Text("游戏端口", fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
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
                        label = { Text("RCON端口", fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
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
                OutlinedTextField(
                    value = rconPass,
                    onValueChange = { rconPass = it },
                    label = { Text("RCON 密码", fontSize = 12.sp) },
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
                    if (name.isNotBlank() && host.isNotBlank()) {
                        onConfirm(
                            name.trim(),
                            host.trim(),
                            pingPort.toIntOrNull() ?: 25565,
                            rconPort.toIntOrNull() ?: 25575,
                            rconPass.trim()
                        )
                    }
                }
            ) {
                Text("添加", color = McGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextTertiary, fontSize = 13.sp)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(18.dp)
    )
}
