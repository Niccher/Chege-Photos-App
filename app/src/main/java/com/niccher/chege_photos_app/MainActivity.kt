package com.niccher.chege_photos_app

import com.niccher.chege_photos_app.R
import android.util.Log
import android.Manifest
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import android.os.Build
import android.os.Bundle
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.widget.Toast
import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.request.CachePolicy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.niccher.chege_photos_app.models.Album as PhotoAlbum
import com.niccher.chege_photos_app.models.PhotoListResponse
import com.niccher.chege_photos_app.utils.SessionManager
import com.niccher.chege_photos_app.utils.DeviceFingerprint
import com.niccher.chege_photos_app.models.AuthResponse
import com.niccher.chege_photos_app.models.Photo
import com.niccher.chege_photos_app.network.ApiClient
import com.niccher.chege_photos_app.repository.PhotoRepository
import com.niccher.chege_photos_app.repository.PhotoSyncResult
import kotlinx.serialization.json.Json
import com.niccher.chege_photos_app.ui.theme.ChegePhotosTheme
import com.niccher.chege_photos_app.ui.theme.AppTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import retrofit2.Response
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.Canvas

private const val REMOTE_PAGE_SIZE = 40

class MainActivity : FragmentActivity() {
    private lateinit var photoRepository: PhotoRepository
    
    // Global Theme State
    private var selectedTheme = mutableStateOf(com.niccher.chege_photos_app.ui.theme.AppTheme.DEFAULT)

    val pendingSharedFiles = androidx.compose.runtime.mutableStateListOf<java.io.File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoRepository = PhotoRepository(this)
        
        handleSharedContent(intent)
        
        createNotificationChannel()
        
        val sessionManager = SessionManager(this)
        selectedTheme.value = com.niccher.chege_photos_app.ui.theme.AppTheme.valueOf(sessionManager.getTheme())

        scheduleBackgroundSync()
        scheduleOfflineActionsSync()

        enableEdgeToEdge()
        setContent {
            ChegePhotosTheme(appTheme = selectedTheme.value) {
                MainScreen(photoRepository, pendingSharedFiles, selectedTheme)
            }
        }
    }

    fun scheduleBackgroundSync() {
        val sessionManager = SessionManager(this)
        val workManager = androidx.work.WorkManager.getInstance(applicationContext)

        if (!sessionManager.isBackupAutoEnabled()) {
            workManager.cancelUniqueWork("ChegePhotosSyncWork")
            Log.d("MainActivity", "Background auto-backup is disabled. Cancelled pending work.")
            return
        }

        val netType = if (sessionManager.isBackupOnlyWifi()) {
            androidx.work.NetworkType.UNMETERED
        } else {
            androidx.work.NetworkType.CONNECTED
        }

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(netType)
            .setRequiresCharging(sessionManager.isBackupOnlyCharging())
            .setRequiresBatteryNotLow(true)
            .build()

        val syncWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.niccher.chege_photos_app.utils.SyncWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES,
            5, java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "ChegePhotosSyncWork",
            androidx.work.ExistingPeriodicWorkPolicy.REPLACE, // Update constraints if they changed
            syncWorkRequest
        )
        Log.d("MainActivity", "Scheduled background auto-backup (15m interval) with current constraints.")
    }

    fun triggerImmediateBackup() {
        val sessionManager = SessionManager(this)
        val workManager = androidx.work.WorkManager.getInstance(applicationContext)

        val netType = if (sessionManager.isBackupOnlyWifi()) {
            androidx.work.NetworkType.UNMETERED
        } else {
            androidx.work.NetworkType.CONNECTED
        }

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(netType)
            .build()

        val oneTimeSync = androidx.work.OneTimeWorkRequestBuilder<com.niccher.chege_photos_app.utils.SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "ChegePhotosImmediateSync",
            androidx.work.ExistingWorkPolicy.REPLACE,
            oneTimeSync
        )
        Log.d("MainActivity", "Enqueued immediate camera backup work.")
    }

    override fun onResume() {
        super.onResume()
        val sessionManager = SessionManager(this)
        if (sessionManager.isLoggedIn() && sessionManager.isBackupAutoEnabled()) {
            triggerImmediateBackup()
        }
    }

    private fun scheduleOfflineActionsSync() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val offlineSyncRequest = androidx.work.PeriodicWorkRequestBuilder<com.niccher.chege_photos_app.utils.OfflineSyncWorker>(
            1, java.util.concurrent.TimeUnit.HOURS // Check for offline actions every hour
        )
            .setConstraints(constraints)
            .build()

        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "ChegePhotosOfflineSyncWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            offlineSyncRequest
        )
        Log.d("MainActivity", "Scheduled background offline actions sync work.")
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleSharedContent(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Photo Sync"
            val descriptionText = "Live notifications for photo uploads and background sync"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("chege_photos_sync_v2", name, importance).apply {
                description = descriptionText
                setShowBadge(true)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun handleSharedContent(intent: android.content.Intent?) {
        if (intent == null) return
        val uris = mutableListOf<android.net.Uri>()
        if (intent.action == android.content.Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            uri?.let { uris.add(it) }
        } else if (intent.action == android.content.Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            val list = intent.getParcelableArrayListExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            list?.let { uris.addAll(it) }
        }

        if (uris.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val sharedDir = java.io.File(cacheDir, "intent_shared")
                if (!sharedDir.exists()) sharedDir.mkdirs()

                val imported = mutableListOf<java.io.File>()
                uris.forEach { uri ->
                    try {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            val tempFile = java.io.File(sharedDir, "shared_${System.currentTimeMillis()}_" + (uri.lastPathSegment?.replace("/", "_") ?: "img") + ".jpg")
                            tempFile.outputStream().use { out ->
                                inputStream.copyTo(out)
                                imported.add(tempFile)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (imported.isNotEmpty()) {
                        pendingSharedFiles.clear()
                        pendingSharedFiles.addAll(imported)
                    }
                }
            }
        }
    }
}

enum class Screen(val title: String, val iconResId: Int) {
    Sync("Sync", R.drawable.ic_nav_sync),
    Gallery("Gallery", R.drawable.ic_nav_gallery),
    Albums("Albums", R.drawable.ic_nav_albums)
}

enum class SidebarItem(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Profile("Profile", Icons.Default.Person),
    Memories("Memories", Icons.Default.AutoAwesome),
    Favorites("Favorites", Icons.Default.Favorite),
    Archive("Archive", Icons.Default.Archive),
    Trash("Trash", Icons.Default.Delete),
    Explore("Explore", Icons.Default.Explore),
    Theme("Theme", Icons.Default.Palette),
    ServerConfig("Server", Icons.Default.Cloud),
    About("About", Icons.Default.Info),
    FaceSearch("Faces", Icons.Default.Face)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: PhotoRepository, 
    pendingSharedFiles: MutableList<java.io.File> = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf() },
    themeState: MutableState<com.niccher.chege_photos_app.ui.theme.AppTheme>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }
    val sharedPrefs = remember { context.getSharedPreferences("chege_photos_prefs", android.content.Context.MODE_PRIVATE) }
    
    var serverUrl by remember { mutableStateOf(ApiClient.normalizeUrl(sharedPrefs.getString("server_url", "https://chege-photos-webapp-production.up.railway.app/") ?: "")) }
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    LaunchedEffect(Unit) {
        ApiClient.onUnauthorizedCallback = {
            scope.launch {
                repository.clearLocalUserData()
            }
            isLoggedIn = false
        }
    }

    // Bootstrap Handshake: Refresh live server configuration limits and capabilities
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            try {
                val configRes = withContext(Dispatchers.IO) {
                    ApiClient.getPhotoService(context).getServerConfig()
                }
                if (configRes.isSuccessful && configRes.body()?.data != null) {
                    sessionManager.saveServerConfig(configRes.body()!!.data!!)
                    Log.i("MainActivity", "Bootstrapped config: maxUpload=${configRes.body()!!.data!!.max_upload_size_mb}MB")
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Could not bootstrap remote server config: ${e.message}")
            }
        }
    }
    var currentScreen by remember { mutableStateOf<Any>(Screen.Sync) }
    var showThemeDialog by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isUnlocked by remember { mutableStateOf(!sessionManager.isBiometricEnabled() || !isLoggedIn) }

    if (!isUnlocked && isLoggedIn) {
        Box(modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = true, onClick = {}), // Block touch events from passing through
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    showBiometricPrompt(context as FragmentActivity) { success ->
                        if (success) isUnlocked = true
                    }
                }) {
                    Text("Unlock with Biometrics")
                }
            }
        }
        
        LaunchedEffect(Unit) {
            showBiometricPrompt(context as FragmentActivity) { success ->
                if (success) isUnlocked = true
            }
        }
        return // Stop rendering anything else if locked
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        ApiClient.updateBaseUrl(serverUrl, context)
        DeviceFingerprint.init(context)
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES, 
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    // Ensure drawer is closed on startup to prevent auto-opening due to state restoration
    LaunchedEffect(Unit) {
        drawerState.close()
    }
    
    // Global Progress States for Persistence
    val activeDownloads = remember { mutableStateMapOf<Long, String>() } // ID -> Photo Path
    val downloadProgress = remember { mutableStateMapOf<String, Float>() } // Photo Path -> Progress
    val downloadManager = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager }

    LaunchedEffect(activeDownloads.size) {
        if (activeDownloads.isEmpty()) return@LaunchedEffect
        while (true) {
            val ids = activeDownloads.keys.toList()
            if (ids.isEmpty()) break
            for (id in ids) {
                val path = activeDownloads[id] ?: continue
                val query = android.app.DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusCol = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                    val downloadedCol = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalCol = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    if (statusCol != -1 && downloadedCol != -1 && totalCol != -1) {
                        val status = cursor.getInt(statusCol)
                        if (status == android.app.DownloadManager.STATUS_SUCCESSFUL || status == android.app.DownloadManager.STATUS_FAILED) {
                            activeDownloads.remove(id)
                            downloadProgress.remove(path)
                        } else {
                            val downloaded = cursor.getLong(downloadedCol)
                            val total = cursor.getLong(totalCol)
                            if (total > 0) {
                                downloadProgress[path] = downloaded.toFloat() / total
                            }
                        }
                    } else {
                        activeDownloads.remove(id)
                        downloadProgress.remove(path)
                    }
                    cursor.close()
                } else {
                    activeDownloads.remove(id)
                    downloadProgress.remove(path)
                    cursor?.close()
                }
            }
            delay(500)
        }
    }

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        ThemeSettingsDialog(
            currentTheme = themeState.value,
            onThemeSelected = { newTheme ->
                themeState.value = newTheme
                sessionManager.saveTheme(newTheme.name)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Confirm Logout") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    scope.launch {
                        repository.clearLocalData()
                    }
                    sessionManager.clearSession()
                    isLoggedIn = false
                }) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pendingSharedFiles.isNotEmpty()) {
        SharedUploadDialog(files = pendingSharedFiles, repository = repository)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isLoggedIn,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                // Unique Sidebar Header with Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_icon),
                            contentDescription = "App Icon",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Chege Photos",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
                
                Column(modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Library Section
                    Text("Library", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    listOf(SidebarItem.Explore, SidebarItem.Memories, SidebarItem.Favorites).forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.title) },
                            selected = currentScreen == item,
                            onClick = {
                                currentScreen = item
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(item.icon, contentDescription = item.title, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Tools Section
                    Text("Tools", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    listOf(SidebarItem.Archive, SidebarItem.Trash, SidebarItem.FaceSearch, SidebarItem.Theme).forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.title) },
                            selected = currentScreen == item,
                            onClick = {
                                if (item == SidebarItem.Theme) {
                                    showThemeDialog = true
                                } else {
                                    currentScreen = item
                                }
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(item.icon, contentDescription = item.title, tint = MaterialTheme.colorScheme.secondary) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Account Section
                    Text("Account", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    listOf(SidebarItem.Profile, SidebarItem.ServerConfig, SidebarItem.About).forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.title) },
                            selected = currentScreen == item,
                            onClick = {
                                currentScreen = item
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(item.icon, contentDescription = item.title, tint = MaterialTheme.colorScheme.tertiary) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "v$appVersion",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (isLoggedIn) {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_app_icon),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (currentScreen is Screen) (currentScreen as Screen).title else (currentScreen as SidebarItem).title)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showLogoutDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (isLoggedIn) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Screen.values().forEach { screen ->
                                val selected = currentScreen == screen
                                val iconColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null,
                                        onClick = { currentScreen = screen }
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .padding(horizontal = 20.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = screen.iconResId),
                                            contentDescription = screen.title,
                                            tint = iconColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = screen.title, 
                                        style = MaterialTheme.typography.labelSmall, 
                                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                                        color = iconColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (!isLoggedIn) {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    LoginScreen(
                        serverUrl = serverUrl,
                        onUrlChange = {
                            val normalized = ApiClient.normalizeUrl(it)
                            serverUrl = normalized
                            sharedPrefs.edit().putString("server_url", normalized).apply()
                            ApiClient.updateBaseUrl(normalized, context)
                        },
                        email = email,
                        onEmailChange = { email = it },
                        password = password,
                        onPasswordChange = { password = it },
                        onLogin = {
                            isLoggedIn = true
                        },
                        context = context
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (currentScreen) {
                        SidebarItem.Profile -> ProfileScreen(sessionManager)
                        Screen.Sync -> SyncScreen(repository)
                        Screen.Gallery -> GalleryScreen(repository, serverUrl, activeDownloads, downloadProgress)
                        Screen.Albums -> AlbumsScreen(repository, serverUrl, activeDownloads, downloadProgress)
                        SidebarItem.Memories -> RemotePhotoListScreen(repository, serverUrl, "Memories", activeDownloads, downloadProgress, fetchPhotos = { ApiClient.getPhotoService(it).getMemories() })
                        SidebarItem.Favorites -> RemotePhotoListScreen(repository, serverUrl, "Favorites", activeDownloads, downloadProgress, fetchPhotos = { ApiClient.getPhotoService(it).getFavorites() })
                        SidebarItem.Archive -> RemotePhotoListScreen(repository, serverUrl, "Archive", activeDownloads, downloadProgress, fetchPhotos = { ApiClient.getPhotoService(it).getArchived() })
                        SidebarItem.Trash -> RemotePhotoListScreen(repository, serverUrl, "Trash", activeDownloads, downloadProgress, fetchPhotos = { ApiClient.getPhotoService(it).getTrash() })
                        SidebarItem.Explore -> RemotePhotoListScreen(repository, serverUrl, "Explore", activeDownloads, downloadProgress, fetchPhotos = { ApiClient.getPhotoService(it).getExplore() })
                        SidebarItem.About -> AboutScreen(appVersion)
                        SidebarItem.FaceSearch -> FaceSearchScreen(baseUrl = serverUrl)
                        SidebarItem.ServerConfig -> ServerConfigScreen(
                            currentUrl = serverUrl,
                            onUrlSaved = { newUrl ->
                                val normalized = ApiClient.normalizeUrl(newUrl)
                                serverUrl = normalized
                                sharedPrefs.edit().putString("server_url", normalized).apply()
                                ApiClient.updateBaseUrl(normalized, context)
                            },
                            onNavigateBack = { currentScreen = Screen.Sync }
                        )
                    }
                }
            }
        }

    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RemotePhotoListScreen(
    repository: PhotoRepository,
    baseUrl: String, 
    title: String,
    activeDownloads: MutableMap<Long, String>,
    downloadProgress: MutableMap<String, Float>,
    fetchPhotos: (suspend (Context) -> Response<PhotoListResponse>)? = null,
    onPhotosLoaded: ((Int) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coilImageLoader = remember(context) { ApiClient.getImageLoader(context) }
    val dbPhotos by remember(repository) { repository.getPhotosFlow() }.collectAsState(initial = emptyList())
    var remotePhotos by remember { mutableStateOf(listOf<Photo>()) }
    val photos = if (fetchPhotos == null) dbPhotos else remotePhotos

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // State for Fullscreen Carousel
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedPhotos = remember { mutableStateListOf<Photo>() }
    var showAlbumPicker by remember { mutableStateOf(false) }
    var albumPickerAlbums by remember { mutableStateOf(listOf<PhotoAlbum>()) }

    // Search / type filter / client-side pagination
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("All") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var currentSort by remember { mutableStateOf("date_desc") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMetadataChips by remember { mutableStateOf(false) }
    var visibleCount by remember { mutableStateOf(REMOTE_PAGE_SIZE) }
    val gridState = rememberLazyGridState()

    val refreshData = {
        scope.launch {
            isRefreshing = true
            if (fetchPhotos != null) {
                remotePhotos = try {
                    val response = fetchPhotos(context)
                    if (response.isSuccessful) response.body()?.photos ?: emptyList() else emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                try {
                    val resp = ApiClient.getPhotoService(context).getRemotePhotos(sort = currentSort)
                    if (resp.isSuccessful) {
                        remotePhotos = resp.body()?.photos ?: emptyList()
                    } else {
                        repository.getRemotePhotos(sort = currentSort)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to refresh remote photos: ${e.message}")
                }
            }
            isRefreshing = false
        }
    }

    LaunchedEffect(currentSort) {
        if (fetchPhotos == null) {
            isLoading = true
            try {
                val resp = ApiClient.getPhotoService(context).getRemotePhotos(sort = currentSort)
                if (resp.isSuccessful) {
                    remotePhotos = resp.body()?.photos ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to sort remote photos: ${e.message}")
            }
            isLoading = false
        }
    }

    var selectedPersonId by remember { mutableStateOf<Int?>(null) }
    var gridColumns by remember { mutableIntStateOf(3) }
    var semanticSearchResults by remember { mutableStateOf<List<Photo>?>(null) }
    var isSemanticSearching by remember { mutableStateOf(false) }

    if (selectedPersonId != null) {
        PersonPhotosScreen(
            baseUrl = baseUrl,
            personId = selectedPersonId!!,
            onBack = { selectedPersonId = null }
        )
        return
    }

    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length >= 2) {
            delay(400)
            isSemanticSearching = true
            try {
                val resp = ApiClient.getPhotoService(context).getRemotePhotos(query = q)
                if (resp.isSuccessful) {
                    semanticSearchResults = resp.body()?.photos
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Semantic search error: ${e.message}")
            } finally {
                isSemanticSearching = false
            }
        } else {
            semanticSearchResults = null
            isSemanticSearching = false
        }
    }

    LaunchedEffect(title) {
        isLoading = true
        if (fetchPhotos != null) {
            remotePhotos = try {
                val response = fetchPhotos(context)
                if (response.isSuccessful) response.body()?.photos ?: emptyList() else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            try {
                repository.getRemotePhotos()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to refresh remote photos: ${e.message}")
            }
        }
        isLoading = false
    }

    LaunchedEffect(photos.size) {
        onPhotosLoaded?.invoke(photos.size)
    }

    val activePhotoList = semanticSearchResults ?: photos
    val filteredPhotos = remember(activePhotoList, searchQuery, typeFilter, semanticSearchResults) {
        val query = searchQuery.trim()
        activePhotoList.filter { photo ->
            val matchesQuery = if (semanticSearchResults != null) true else (
                query.isEmpty() ||
                photo.filename.contains(query, ignoreCase = true) ||
                photo.path.contains(query, ignoreCase = true)
            )
            val isVideo = photo.mime_type?.contains("video", ignoreCase = true) == true ||
                photo.mime_type?.contains("mp4", ignoreCase = true) == true ||
                photo.filename.endsWith(".mp4", ignoreCase = true) ||
                photo.filename.endsWith(".webm", ignoreCase = true) ||
                photo.filename.endsWith(".mkv", ignoreCase = true) ||
                photo.filename.endsWith(".mov", ignoreCase = true)
            val matchesType = when (typeFilter) {
                "Images" -> !isVideo
                "Videos" -> isVideo
                else -> true
            }
            matchesQuery && matchesType
        }
    }

    // Reset pagination when the query or type filter changes
    LaunchedEffect(searchQuery, typeFilter, semanticSearchResults) {
        visibleCount = REMOTE_PAGE_SIZE
        gridState.scrollToItem(0)
    }

    // Auto-load the next page when the user scrolls near the end of the current page
    LaunchedEffect(gridState, visibleCount, filteredPhotos.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= visibleCount - 1 && visibleCount < filteredPhotos.size) {
                    visibleCount = minOf(visibleCount + REMOTE_PAGE_SIZE, filteredPhotos.size)
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        if (title == "Trash" && photos.isNotEmpty()) {
            var showEmptyTrashConfirm by remember { mutableStateOf(false) }
            var isPurging by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${photos.size} item${if (photos.size > 1) "s" else ""} in Trash",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Auto-purged after retention window",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                    Button(
                        onClick = { showEmptyTrashConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isPurging,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        if (isPurging) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Empty Trash")
                        }
                    }
                }
            }

            if (showEmptyTrashConfirm) {
                AlertDialog(
                    onDismissRequest = { showEmptyTrashConfirm = false },
                    icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Empty Trash?") },
                    text = { Text("Permanently delete all ${photos.size} item(s)? This action cannot be undone and will recover your cloud storage quota.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showEmptyTrashConfirm = false
                                scope.launch {
                                    isPurging = true
                                    try {
                                        val success = repository.emptyTrash()
                                        if (success) {
                                            Toast.makeText(context, "Trash emptied successfully!", Toast.LENGTH_SHORT).show()
                                            refreshData()
                                        } else {
                                            Toast.makeText(context, "Failed to empty trash", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isPurging = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Empty Permanently")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEmptyTrashConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search photos") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                    IconButton(onClick = {
                        gridColumns = when (gridColumns) {
                            2 -> 3
                            3 -> 4
                            else -> 2
                        }
                    }) {
                        Icon(
                            imageVector = when (gridColumns) {
                                2 -> Icons.Default.ViewStream
                                3 -> Icons.Default.GridView
                                else -> Icons.Default.ViewCompact
                            },
                            contentDescription = "Switch grid density ($gridColumns columns)",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter media by type",
                                tint = if (typeFilter != "All") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All") },
                                onClick = {
                                    typeFilter = "All"
                                    showFilterMenu = false
                                },
                                leadingIcon = if (typeFilter == "All") {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                            DropdownMenuItem(
                                text = { Text("Images") },
                                onClick = {
                                    typeFilter = "Images"
                                    showFilterMenu = false
                                },
                                leadingIcon = if (typeFilter == "Images") {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                            DropdownMenuItem(
                                text = { Text("Videos") },
                                onClick = {
                                    typeFilter = "Videos"
                                    showFilterMenu = false
                                },
                                leadingIcon = if (typeFilter == "Videos") {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Sort photos & options",
                                tint = if (currentSort != "date_desc" || showMetadataChips) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Date (Newest First)") },
                                onClick = { currentSort = "date_desc"; showSortMenu = false },
                                leadingIcon = if (currentSort == "date_desc") {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                            DropdownMenuItem(
                                text = { Text("Date (Oldest First)") },
                                onClick = { currentSort = "date_asc"; showSortMenu = false },
                                leadingIcon = if (currentSort == "date_asc") {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                            DropdownMenuItem(
                                text = { Text("Recently Uploaded") },
                                onClick = { currentSort = "upload_desc"; showSortMenu = false },
                                leadingIcon = if (currentSort == "upload_desc") {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                            DropdownMenuItem(
                                text = { Text("File Size (Largest)") },
                                onClick = { currentSort = "size_desc"; showSortMenu = false },
                                leadingIcon = if (currentSort == "size_desc") {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                            DropdownMenuItem(
                                text = { Text("File Size (Smallest)") },
                                onClick = { currentSort = "size_asc"; showSortMenu = false },
                                leadingIcon = if (currentSort == "size_asc") {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                            DropdownMenuItem(
                                text = { Text("Resolution (Highest MP)") },
                                onClick = { currentSort = "resolution_desc"; showSortMenu = false },
                                leadingIcon = if (currentSort == "resolution_desc") {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                            DropdownMenuItem(
                                text = { Text("Name (A to Z)") },
                                onClick = { currentSort = "name_asc"; showSortMenu = false },
                                leadingIcon = if (currentSort == "name_asc") {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                            DropdownMenuItem(
                                text = { Text("Favorites First") },
                                onClick = { currentSort = "favorites"; showSortMenu = false },
                                leadingIcon = if (currentSort == "favorites") {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (showMetadataChips) "Hide Info Badges" else "Show Info Badges") },
                                onClick = { showMetadataChips = !showMetadataChips; showSortMenu = false },
                                leadingIcon = {
                                    Icon(
                                        if (showMetadataChips) Icons.Default.VisibilityOff else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (showMetadataChips) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            singleLine = true
        )

        if (isSemanticSearching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        if ((title == "Gallery" || title == "Explore") && searchQuery.isEmpty()) {
            PeopleAvatarRow(
                baseUrl = baseUrl,
                coilImageLoader = coilImageLoader,
                onPersonClick = { selectedPersonId = it }
            )
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshData() },
            modifier = Modifier.weight(1f)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (filteredPhotos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff, 
                                contentDescription = null, 
                                modifier = Modifier.size(64.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (photos.isEmpty()) "No photos found in $title" else "No photos match your search",
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        state = gridState
                    ) {
                        itemsIndexed(
                            items = filteredPhotos.take(visibleCount),
                            key = { _, photo -> photo.id ?: photo.path }
                        ) { index, photo ->
                            val isSelected = selectedPhotos.contains(photo)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp)
                                    .combinedClickable(
                                        onClick = { 
                                            if (isSelectionMode) {
                                                if (isSelected) selectedPhotos.remove(photo)
                                                else selectedPhotos.add(photo)
                                                if (selectedPhotos.isEmpty()) isSelectionMode = false
                                            } else {
                                                selectedPhotoIndex = index 
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedPhotos.add(photo)
                                            }
                                        }
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Box {
                                    val imageModel = remember(photo, baseUrl) {
                                        val p = photo.thumbnail_path ?: photo.path
                                        if (p.startsWith("/") || p.startsWith("content:")) {
                                            p
                                        } else {
                                            baseUrl.trimEnd('/') + "/" + p.trimStart('/')
                                        }
                                    }
                                    AsyncImage(
                                        model = imageModel,
                                        contentDescription = photo.filename,
                                        imageLoader = coilImageLoader,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .background(Color.DarkGray),
                                        contentScale = ContentScale.Crop,
                                        onError = { state ->
                                            android.util.Log.e("CoilImageLoad", "Failed to load image: ${state.result.request.data} - ${state.result.throwable.message}", state.result.throwable)
                                        }
                                    )
                                    
                                    if (isSelected) {
                                        Surface(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.padding(4.dp).size(16.dp),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                    if (showMetadataChips) {
                                        val w = photo.width?.toLongOrNull() ?: 0L
                                        val h = photo.height?.toLongOrNull() ?: 0L
                                        val mpText = if (w > 0 && h > 0) {
                                            String.format(java.util.Locale.US, "%.1f MP", (w * h) / 1000000.0)
                                        } else "Media"
                                        val sizeBytes = photo.size?.toLongOrNull() ?: 0L
                                        val sizeMb = String.format(java.util.Locale.US, "%.1f MB", sizeBytes / 1024.0 / 1024.0)
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth(),
                                            color = Color(0x99000000)
                                        ) {
                                            Text(
                                                text = "$mpText • $sizeMb",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                            }
                        }
                        }
                        if (visibleCount < filteredPhotos.size) {
                            item(key = "load_more_footer") {
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    onClick = { visibleCount = minOf(visibleCount + REMOTE_PAGE_SIZE, filteredPhotos.size) }
                                ) {
                                    Text("Load more (${filteredPhotos.size - visibleCount} more)")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isSelectionMode) {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selectedPhotos.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        enabled = selectedPhotos.isNotEmpty(),
                        onClick = {
                            val items = selectedPhotos.toList()
                            isSelectionMode = false
                            selectedPhotos.clear()
                            scope.launch {
                                items.forEach { photo ->
                                    repository.favoritePhoto(photo.id ?: "")
                                }
                                isLoading = true
                                if (fetchPhotos != null) {
                                    try {
                                        val response = fetchPhotos(context)
                                        if (response.isSuccessful) {
                                            remotePhotos = response.body()?.photos ?: emptyList()
                                        }
                                    } catch (e: Exception) { }
                                } else {
                                    repository.getRemotePhotos()
                                }
                                isLoading = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "Favorite")
                    }
                    IconButton(
                        enabled = selectedPhotos.isNotEmpty(),
                        onClick = {
                            val items = selectedPhotos.toList()
                            isSelectionMode = false
                            selectedPhotos.clear()
                            scope.launch {
                                items.forEach { photo ->
                                    repository.archivePhoto(photo.id ?: "")
                                }
                                isLoading = true
                                if (fetchPhotos != null) {
                                    try {
                                        val response = fetchPhotos(context)
                                        if (response.isSuccessful) {
                                            remotePhotos = response.body()?.photos ?: emptyList()
                                        }
                                    } catch (e: Exception) { }
                                } else {
                                    repository.getRemotePhotos()
                                }
                                isLoading = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = "Archive")
                    }
                    IconButton(
                        enabled = selectedPhotos.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                try {
                                    val response = ApiClient.getPhotoService(context).getAlbums()
                                    if (response.isSuccessful) {
                                        albumPickerAlbums = response.body()?.albums ?: emptyList()
                                        showAlbumPicker = true
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to load albums", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Add to Album")
                    }
                    IconButton(
                        enabled = selectedPhotos.isNotEmpty(),
                        onClick = {
                            val items = selectedPhotos.toList()
                            isSelectionMode = false
                            selectedPhotos.clear()
                            scope.launch {
                                items.forEach { photo ->
                                    repository.deletePhoto(photo.id ?: "")
                                }
                                isLoading = true
                                if (fetchPhotos != null) {
                                    try {
                                        val response = fetchPhotos(context)
                                        if (response.isSuccessful) {
                                            remotePhotos = response.body()?.photos ?: emptyList()
                                        }
                                    } catch (e: Exception) { }
                                } else {
                                    repository.getRemotePhotos()
                                }
                                isLoading = false
                                Toast.makeText(context, if (title == "Trash") "Permanently deleted items" else "Moved items to trash", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = if (title == "Trash") "Delete Permanently" else "Trash All")
                    }
                    IconButton(
                        enabled = selectedPhotos.isNotEmpty(),
                        onClick = { 
                            scope.launch {
                                var successCount = 0
                                for (photo in selectedPhotos.toList()) {
                                    val uri = downloadRemotePhoto(context, baseUrl, photo)
                                    if (uri != null) successCount++
                                }
                                Toast.makeText(context, "Downloaded $successCount photos", Toast.LENGTH_SHORT).show()
                            }
                            isSelectionMode = false
                            selectedPhotos.clear()
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download All")
                    }
                }
            }
        }
    }

    if (showAlbumPicker) {
        AlbumPickerDialog(
            albums = albumPickerAlbums,
            selectedCount = selectedPhotos.size,
            onAlbumSelected = { album ->
                val items = selectedPhotos.toList()
                isSelectionMode = false
                selectedPhotos.clear()
                showAlbumPicker = false
                scope.launch {
                    var added = 0
                    items.forEach { photo ->
                        if (repository.addPhotoToAlbum(album.id ?: "", photo.id ?: "")) added++
                    }
                    Toast.makeText(context, "Added $added photos to ${album.name}", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showAlbumPicker = false }
        )
    }

    // Fullscreen Image Carousel Dialog (updated to use filteredPhotos)
    selectedPhotoIndex?.let { initialPage ->
        Dialog(
            onDismissRequest = { selectedPhotoIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false) // Fullscreen
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { filteredPhotos.size })
                var showInfoSheet by remember { mutableStateOf(false) }
                var showFaces by remember { mutableStateOf(false) }
                var facesMap by remember { mutableStateOf<Map<Int, List<com.niccher.chege_photos_app.models.FaceData>>>(emptyMap()) }

                // Reset states when the user swipes to a different page
                LaunchedEffect(pagerState.currentPage) {
                    showInfoSheet = false
                    showFaces = false
                }

                // Fetch faces when page changes
                LaunchedEffect(pagerState.currentPage) {
                    val photo = filteredPhotos[pagerState.currentPage]
                    val pid = photo.id?.toIntOrNull() ?: return@LaunchedEffect
                    if (!facesMap.containsKey(pid)) {
                        // 1. Kickoff local ML Kit face detection first to show instant boxes
                        scope.launch {
                            try {
                                val imageUrl = baseUrl.trimEnd('/') + "/" + photo.path.trimStart('/')
                                val request = coil.request.ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .build()
                                val result = coilImageLoader.execute(request)
                                val drawable = result.drawable
                                if (drawable is android.graphics.drawable.BitmapDrawable) {
                                    val bitmap = drawable.bitmap
                                    val localFaces = com.niccher.chege_photos_app.utils.LocalFaceDetector.detectFaces(bitmap)
                                    if (localFaces.isNotEmpty() && !facesMap.containsKey(pid)) {
                                        facesMap = facesMap + (pid to localFaces)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed to run offline face detection: ${e.message}")
                            }
                        }

                        // 2. Fetch server InsightFace scan
                        try {
                            val resp = ApiClient.getPhotoService(context).getFacesByPhoto(pid)
                            if (resp.isSuccessful) {
                                val body = resp.body()
                                if (body?.status == "success") {
                                    facesMap = facesMap + (pid to body.faces)
                                }
                            }
                        } catch (_: Exception) { }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val photo = filteredPhotos[page]
                    
                    // Zoom and Pan States
                    var scale by remember { mutableStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }

                    // Reset zoom when the user swipes to a different page
                    LaunchedEffect(pagerState.currentPage) {
                        scale = 1f
                        offset = Offset.Zero
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(scale) {
                                detectTransformGesturesCustom(
                                    onGesture = { centroid, pan, zoom, rotation ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        if (scale > 1f) {
                                            offset += pan
                                        } else {
                                            offset = Offset.Zero
                                        }
                                    },
                                    consumeEnabled = scale > 1f
                                )
                            }
                            .draggable(
                                state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                                    if (scale == 1f) {
                                        if (delta < -15f) { // Swipe up
                                            showInfoSheet = true
                                        } else if (delta > 15f) { // Swipe down
                                            if (showInfoSheet) {
                                                showInfoSheet = false
                                            } else {
                                                selectedPhotoIndex = null
                                            }
                                        }
                                    }
                                },
                                orientation = androidx.compose.foundation.gestures.Orientation.Vertical
                            )
                    ) {
                        val imageModel = remember(photo, baseUrl) {
                            val p = photo.path
                            if (p.startsWith("/") || p.startsWith("content:")) {
                                p
                            } else {
                                baseUrl.trimEnd('/') + "/" + p.trimStart('/')
                            }
                        }
                        val isVideo = photo.mime_type?.contains("video", ignoreCase = true) == true || 
                                      photo.filename.endsWith(".mp4", ignoreCase = true) ||
                                      photo.filename.endsWith(".webm", ignoreCase = true) ||
                                      photo.filename.endsWith(".mkv", ignoreCase = true)

                        if (isVideo) {
                            VideoPlayer(videoUrl = imageModel, modifier = Modifier.fillMaxSize())
                        } else {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = photo.filename,
                                imageLoader = coilImageLoader,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    ),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Face bounding boxes overlay
                        val pid = photo.id?.toIntOrNull()
                        if (showFaces && pid != null) {
                            val faces = facesMap[pid]
                            if (!faces.isNullOrEmpty()) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cw = this.size.width
                                    val ch = this.size.height
                                    val pw = photo.width?.toFloatOrNull()
                                    val ph = photo.height?.toFloatOrNull()
                                    if (pw != null && ph != null && pw > 0f && ph > 0f) {
                                        val scaleFactor = minOf(cw / pw, ch / ph)
                                        val dx = (cw - (pw * scaleFactor)) / 2f
                                        val dy = (ch - (ph * scaleFactor)) / 2f
                                        for (face in faces) {
                                            val left = dx + (face.bbox.x.toFloat() * scaleFactor)
                                            val top = dy + (face.bbox.y.toFloat() * scaleFactor)
                                            val w = face.bbox.w.toFloat() * scaleFactor
                                            val h = face.bbox.h.toFloat() * scaleFactor
                                            drawRect(
                                                color = androidx.compose.ui.graphics.Color(0xFF00FF00).copy(alpha = 0.5f),
                                                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                                size = androidx.compose.ui.geometry.Size(w, h),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                                            )
                                        }
                                    } else {
                                        for (face in faces) {
                                            val left = ((face.bbox.x / cw) * cw).toFloat()
                                            val top = ((face.bbox.y / ch) * ch).toFloat()
                                            val w = (face.bbox.w / cw * cw).toFloat()
                                            val h = (face.bbox.h / ch * ch).toFloat()
                                            drawRect(
                                                color = androidx.compose.ui.graphics.Color(0xFF00FF00).copy(alpha = 0.5f),
                                                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                                size = androidx.compose.ui.geometry.Size(w, h),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        downloadProgress[photo.path]?.let { progress ->
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 80.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Image Counter Overlay
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 32.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} of ${filteredPhotos.size}",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Top-Right Controls
                if (showInfoSheet) {
                    val currentPhoto = filteredPhotos[pagerState.currentPage]
                    PhotoDetailsBottomSheet(
                        photo = currentPhoto,
                        baseUrl = baseUrl,
                        onPhotoDeleted = { selectedPhotoIndex = null },
                        onDismiss = { showInfoSheet = false }
                    )
                }
            }
        }
    }
}

enum class LoginMethod(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    QR_SCAN("Scan QR", Icons.Default.QrCodeScanner),
    TOKEN("Token", Icons.Default.Key),
    EMAIL("Email", Icons.Default.Email)
}

@Composable
fun LoginScreen(
    serverUrl: String,
    onUrlChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    context: Context = androidx.compose.ui.platform.LocalContext.current
) {
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()

    var selectedMethod by remember { mutableStateOf(LoginMethod.QR_SCAN) }
    var tokenInput by remember { mutableStateOf("") }
    var isTokenLoading by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val authenticateWithToken: (String, String?) -> Unit = { rawToken, newUrl ->
        scope.launch {
            isTokenLoading = true
            errorMessage = null
            try {
                if (newUrl != null) {
                    val normalized = ApiClient.normalizeUrl(newUrl)
                    onUrlChange(normalized)
                    ApiClient.updateBaseUrl(normalized, context)
                    val sharedPrefs = context.getSharedPreferences("chege_photos_prefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().putString("server_url", normalized).apply()
                }

                val deviceId = DeviceFingerprint.getCompositeDeviceKey(context)
                val fingerprint = DeviceFingerprint.getFingerprint()
                val response = ApiClient.getPhotoService(context).authWithToken(
                    token = rawToken,
                    deviceId = deviceId,
                    deviceFingerprint = fingerprint,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                    deviceUuid = sessionManager.getDeviceUuid(),
                    osVersion = DeviceFingerprint.getOsVersion(),
                    screenMetrics = DeviceFingerprint.getScreenMetrics(context),
                    locale = DeviceFingerprint.getLocale(),
                    timezone = DeviceFingerprint.getTimezone(),
                    kernelVersion = DeviceFingerprint.getKernelVersion()
                )
                if (response.isSuccessful) {
                    val authData = response.body()
                    authData?.access_token?.let {
                        sessionManager.saveAuthToken(it)
                        authData.user?.let { user ->
                            sessionManager.saveUserProfile(user.id, user.email, user.username, user.created_at, user.last_upload)
                            sessionManager.updateLastLogin()
                        }
                        onLogin()
                        Toast.makeText(context, "Connected as ${authData.user?.username ?: "User"}!", Toast.LENGTH_SHORT).show()
                    } ?: run {
                        errorMessage = "Invalid response from server"
                    }
                } else {
                    errorMessage = "Token auth failed: Invalid or expired token"
                }
            } catch (e: Exception) {
                errorMessage = "Cannot reach server. Check network connection."
            } finally {
                isTokenLoading = false
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        result.contents?.let { scanned ->
            val raw = scanned.trim()
            var parsedUrl: String? = null
            var parsedToken: String? = null

            if (raw.startsWith("{") && raw.endsWith("}")) {
                try {
                    val jsonObj = org.json.JSONObject(raw)
                    if (jsonObj.has("url")) parsedUrl = jsonObj.getString("url")
                    if (jsonObj.has("token")) parsedToken = jsonObj.getString("token")
                } catch (_: Exception) {}
            }

            if (parsedUrl == null) {
                try {
                    val uri = android.net.Uri.parse(raw)
                    if (uri.scheme != null && uri.host != null) {
                        parsedUrl = "${uri.scheme}://${uri.host}" + if (uri.port > 0) ":${uri.port}" else ""
                        parsedToken = uri.getQueryParameter("token")
                    }
                } catch (_: Exception) {}
            }

            val finalToken = (parsedToken ?: raw).trim().uppercase().take(8)

            if (finalToken.length == 8) {
                tokenInput = finalToken
                Toast.makeText(context, "Pairing with WebApp...", Toast.LENGTH_SHORT).show()
                authenticateWithToken(finalToken, parsedUrl)
            } else {
                errorMessage = "Scanned QR code did not contain a valid 8-character token"
            }
        }
    }

    val launchScanner = {
        val options = com.journeyapps.barcodescanner.ScanOptions().apply {
            setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
            setPrompt("Scan Chege Photos Token QR Code")
            setCameraId(0)
            setBeepEnabled(false)
            setBarcodeImageEnabled(false)
            setCaptureActivity(com.niccher.chege_photos_app.utils.PortraitCaptureActivity::class.java)
        }
        scanLauncher.launch(options)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_icon),
                    contentDescription = "App icon",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Chege Photos",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = "Your private photo cloud",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )

            var serverStatus by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(serverUrl) {
                serverStatus = null
                try {
                    val res = withContext(Dispatchers.IO) {
                        ApiClient.getPhotoService(context).ping()
                    }
                    serverStatus = res.isSuccessful
                } catch (_: Exception) {
                    serverStatus = false
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                val (indicatorColor, statusText) = when (serverStatus) {
                    true -> androidx.compose.ui.graphics.Color(0xFF4CAF50) to "Server Online"
                    false -> androidx.compose.ui.graphics.Color(0xFFF44336) to "Server Unreachable"
                    null -> androidx.compose.ui.graphics.Color.Gray to "Checking server..."
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(indicatorColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Segmented Method Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LoginMethod.values().forEach { method ->
                            val isSelected = selectedMethod == method
                            Surface(
                                selected = isSelected,
                                onClick = {
                                    selectedMethod = method
                                    errorMessage = null
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(method.icon, contentDescription = null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = method.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    when (selectedMethod) {
                        LoginMethod.QR_SCAN -> {
                            QrPairingContent(
                                isPairing = isTokenLoading,
                                errorMessage = errorMessage,
                                onScanClick = launchScanner
                            )
                        }
                        LoginMethod.TOKEN -> {
                            TokenInputContent(
                                token = tokenInput,
                                onTokenChange = { tokenInput = it; errorMessage = null },
                                isLoading = isTokenLoading,
                                errorMessage = errorMessage,
                                onLogin = {
                                    val trimmed = tokenInput.trim().uppercase()
                                    if (trimmed.length != 8) {
                                        errorMessage = "Token must be 8 characters"
                                    } else {
                                        authenticateWithToken(trimmed, null)
                                    }
                                },
                                onScanClick = launchScanner
                            )
                        }
                        LoginMethod.EMAIL -> {
                            EmailPasswordContent(
                                serverUrl = serverUrl,
                                onUrlChange = onUrlChange,
                                email = email,
                                onEmailChange = onEmailChange,
                                password = password,
                                onPasswordChange = onPasswordChange,
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                passwordVisible = passwordVisible,
                                showAdvanced = showAdvanced,
                                onToggleAdvanced = { showAdvanced = !showAdvanced },
                                onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                                onLogin = {
                                    if (email.isBlank()) { errorMessage = "Email is required"; return@EmailPasswordContent }
                                    if (password.isBlank()) { errorMessage = "Password is required"; return@EmailPasswordContent }
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        try {
                                            val deviceId = DeviceFingerprint.getCompositeDeviceKey(context)
                                            val fingerprint = DeviceFingerprint.getFingerprint()
                                            val response = ApiClient.getPhotoService(context).login(
                                                email = email,
                                                password = password,
                                                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                                                deviceId = deviceId,
                                                deviceFingerprint = fingerprint,
                                                deviceUuid = sessionManager.getDeviceUuid(),
                                                osVersion = DeviceFingerprint.getOsVersion(),
                                                screenMetrics = DeviceFingerprint.getScreenMetrics(context),
                                                locale = DeviceFingerprint.getLocale(),
                                                timezone = DeviceFingerprint.getTimezone(),
                                                kernelVersion = DeviceFingerprint.getKernelVersion()
                                            )
                                            if (response.isSuccessful) {
                                                val authData = response.body()
                                                authData?.access_token?.let {
                                                    sessionManager.saveAuthToken(it)
                                                    authData.user?.let { user ->
                                                        sessionManager.saveUserProfile(user.id, user.email, user.username, user.created_at, user.last_upload)
                                                        sessionManager.updateLastLogin()
                                                    }
                                                    onLogin()
                                                    Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                                } ?: run {
                                                    errorMessage = "Invalid response from server"
                                                }
                                            } else {
                                                errorMessage = "Login failed: ${response.message()}"
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Cannot reach server. Check your URL."
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                onSwitchToQr = { selectedMethod = LoginMethod.QR_SCAN }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Connect to your self-hosted server",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QrPairingContent(
    isPairing: Boolean,
    errorMessage: String?,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "1-Tap QR Pairing",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Instant onboarding: Configures Server URL & Account simultaneously",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(18.dp))

        // Quick instruction card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "How to pair with your WebApp:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("1.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Open Chege Photos WebApp in browser", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("2.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Go to Settings → Access Tokens", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("3.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Click Generate Token & scan QR code below", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        ErrorBanner(errorMessage)

        Spacer(Modifier.height(20.dp))

        if (isPairing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(12.dp))
                Text("Pairing device with server…", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Scan WebApp QR Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TokenInputContent(
    token: String,
    onTokenChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: () -> Unit,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Manual Token Login",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Enter the 8-character token from your WebApp settings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { onTokenChange(it.take(8).uppercase()) },
            label = { Text("Access Token") },
            leadingIcon = { Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                IconButton(onClick = onScanClick) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = MaterialTheme.colorScheme.primary)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            placeholder = { Text("e.g. A1B2C3D4") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done)
        )

        ErrorBanner(errorMessage)

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp)); Text("Authenticating…")
            } else {
                Icon(Icons.Default.Key, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Authenticate Token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Or Scan QR Code Directly")
        }
    }
}

@Composable
private fun EmailPasswordContent(
    serverUrl: String,
    onUrlChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    passwordVisible: Boolean,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLogin: () -> Unit,
    onSwitchToQr: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Account Sign In",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Enter your email and password to sign in",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(20.dp))

        ServerSettingsSection(serverUrl, onUrlChange, showAdvanced, onToggleAdvanced)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
        )

        ErrorBanner(errorMessage)

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp)); Text("Signing in…")
            } else {
                Icon(Icons.AutoMirrored.Filled.Login, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign In", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onSwitchToQr) {
            Icon(Icons.Default.QrCodeScanner, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Quick Pair with QR Code instead")
        }
    }
}

@Composable
private fun ServerSettingsSection(
    serverUrl: String,
    onUrlChange: (String) -> Unit,
    showAdvanced: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Server Settings", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Icon(if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            if (showAdvanced) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                OutlinedTextField(
                    value = serverUrl, onValueChange = onUrlChange,
                    label = { Text("Server URL") },
                    leadingIcon = { Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp), singleLine = true,
                    placeholder = { Text("e.g. 192.168.1.50:2283 or https://photos.example.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(errorMessage: String?) {
    if (errorMessage != null) {
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}



@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(repository: PhotoRepository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    var photos by remember { mutableStateOf(listOf<com.niccher.chege_photos_app.repository.LocalPhoto>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        photos = repository.getLocalPhotos()
        repository.photosRefreshTrigger.collect {
            photos = repository.getLocalPhotos()
        }
    }

    // WorkManager Integration
    val workManager = remember { androidx.work.WorkManager.getInstance(context.applicationContext) }
    val workInfosState = remember {
        workManager.getWorkInfosForUniqueWorkFlow("ChegePhotosManualUpload")
    }.collectAsState(initial = emptyList())

    val workInfo = workInfosState.value.firstOrNull()
    val isSyncing = workInfo != null && workInfo.state == androidx.work.WorkInfo.State.RUNNING

    val progressData = workInfo?.progress
    val currentFileProgress = progressData?.getFloat("progress", 0f) ?: 0f
    val processedCount = progressData?.getInt("current", 0) ?: 0
    val currentlySyncingName = progressData?.getString("current_name")

    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
    var failedItems by remember { mutableStateOf(listOf<Pair<com.niccher.chege_photos_app.repository.LocalPhoto, String>>()) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(workInfo?.state) {
        if (workInfo != null) {
            if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                val output = workInfo.outputData
                val succeeded = output.getInt("succeeded", 0)
                val total = output.getInt("total", 0)
                val failed = output.getInt("failed", (total - succeeded).coerceAtLeast(0))
                if (failed > 0) {
                    Toast.makeText(context, "Uploaded $succeeded of $total photos ($failed unable to upload)", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Synced all $succeeded photos successfully!", Toast.LENGTH_SHORT).show()
                }
                selectedIndices = emptySet()
            } else if (workInfo.state == androidx.work.WorkInfo.State.FAILED) {
                val output = workInfo.outputData
                val succeeded = output.getInt("succeeded", 0)
                val total = output.getInt("total", 0)
                val failed = (total - succeeded).coerceAtLeast(0)
                Toast.makeText(context, "Sync ended: $succeeded uploaded, $failed unable to upload", Toast.LENGTH_LONG).show()
            }
        }
    }

    var selectedFolder by remember { mutableStateOf("All") }

    val folders = remember(photos) {
        listOf("All") + photos.map { it.folderName }.distinct().sorted()
    }

    val folderFilteredPhotos = remember(photos, selectedFolder) {
        if (selectedFolder == "All") photos else photos.filter { it.folderName == selectedFolder }
    }

    val freshCount = remember(folderFilteredPhotos) { folderFilteredPhotos.count { !it.isUploaded } }
    val displayedPhotos = folderFilteredPhotos

    val targetPhotos = if (selectedIndices.isNotEmpty()) {
        selectedIndices.sorted().mapNotNull { displayedPhotos.getOrNull(it) }
    } else {
        folderFilteredPhotos.filter { !it.isUploaded }
    }

    val uploadBatch: (List<com.niccher.chege_photos_app.repository.LocalPhoto>, String) -> Unit = { batch, label ->
        com.niccher.chege_photos_app.utils.ManualUploadWorker.enqueue(
            context = context,
            photos = batch,
            bucketName = if (selectedIndices.isNotEmpty()) "Selected (${batch.size})" else selectedFolder
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                photos = repository.getLocalPhotos()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column {
        // ── Top action bar ─────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = !isSyncing && targetPhotos.isNotEmpty(),
                onClick = {
                    uploadBatch(targetPhotos, if (selectedIndices.isNotEmpty()) "selected" else "folder_${selectedFolder}")
                }
            ) {
                val label = if (selectedIndices.isNotEmpty()) {
                    "Upload Selected (${selectedIndices.size})"
                } else if (isSyncing) {
                    "Syncing $selectedFolder... ($processedCount/${targetPhotos.size.coerceAtLeast(1)})"
                } else if (freshCount == 0 && folderFilteredPhotos.isNotEmpty()) {
                    "All $selectedFolder Synced ✓"
                } else if (selectedFolder != "All") {
                    "Sync $selectedFolder ($freshCount fresh)"
                } else {
                    "Sync All ($freshCount fresh)"
                }
                Text(label)
            }

            if (selectedIndices.isNotEmpty()) {
                OutlinedButton(onClick = { selectedIndices = emptySet() }, enabled = !isSyncing) {
                    Text("Cancel (${selectedIndices.size})")
                }
            }

            if (failedItems.isNotEmpty()) {
                OutlinedButton(
                    onClick = { uploadBatch(failedItems.map { it.first }, "failed") },
                    enabled = !isSyncing
                ) {
                    Text("Retry (${failedItems.size})")
                }
            }
        }

        // Folder selection filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            folders.forEach { folder ->
                FilterChip(
                    selected = selectedFolder == folder,
                    onClick = { 
                        selectedFolder = folder
                        selectedIndices = emptySet() // Reset selections when folder changes
                    },
                    label = { Text(folder) }
                )
            }
        }

        if (failedItems.isNotEmpty() && !isSyncing) {
            Text(
                text = "${failedItems.size} upload(s) failed. First error: ${failedItems.first().second.take(80)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // ── Progress ──────────────────────────────────────────────
        if (isSyncing) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                LinearProgressIndicator(
                    progress = { currentFileProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Syncing: ${currentlySyncingName ?: ""} (${(currentFileProgress * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            workManager.cancelUniqueWork("ChegePhotosManualUpload")
                        }.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ── Photo grid ────────────────────────────────────────────
        if (displayedPhotos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "No photos found in $selectedFolder.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp)
            ) {
                itemsIndexed(
                    items = displayedPhotos,
                    key = { _, photo -> photo.uri.toString() }
                ) { index, photo ->
                    val isSelected = index in selectedIndices
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .combinedClickable(
                                onClick = {
                                    if (selectedIndices.isNotEmpty()) {
                                        selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
                                    } else {
                                        selectedPhotoIndex = index
                                    }
                                },
                                onLongClick = {
                                    selectedIndices = selectedIndices + index
                                }
                            ),
                        colors = if (isSelected) CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else CardDefaults.cardColors()
                    ) {
                        Box {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = photo.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                contentScale = ContentScale.Crop
                            )

                            // Folder label tag overlay
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp),
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = photo.folderName,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    maxLines = 1
                                )
                            }

                            // Checkbox overlay when selected
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .size(28.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Per-item Sync Progress (only for items in the current sync target batch)
                        val isCurrentlySyncing = currentlySyncingName == photo.name
                        val isTargetPhoto = targetPhotos.contains(photo)
                        if (isCurrentlySyncing || (isSyncing && isTargetPhoto && photo in targetPhotos.take(processedCount))) {
                            val progress = if (isCurrentlySyncing) currentFileProgress else 1f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = if (progress < 1f) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }

                    }
                }
            }
        }
    }
    }

    // Fullscreen Image Carousel Dialog for Local Photos
    selectedPhotoIndex?.let { initialPage ->
        Dialog(
            onDismissRequest = { selectedPhotoIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                val safeInitialPage = initialPage.coerceAtMost((displayedPhotos.size - 1).coerceAtLeast(0))
                val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { displayedPhotos.size })
                var showInfoSheet by remember { mutableStateOf(false) }

                // Reset state on swipe
                LaunchedEffect(pagerState.currentPage) {
                    showInfoSheet = false
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val photo = displayedPhotos.getOrNull(page) ?: return@HorizontalPager
                    var scale by remember { mutableStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }

                    LaunchedEffect(pagerState.currentPage) {
                        scale = 1f
                        offset = Offset.Zero
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(scale) {
                                detectTransformGesturesCustom(
                                    onGesture = { centroid, pan, zoom, rotation ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        if (scale > 1f) {
                                            offset += pan
                                        } else {
                                            offset = Offset.Zero
                                        }
                                    },
                                    consumeEnabled = scale > 1f
                                )
                            }
                            .draggable(
                                state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                                    if (scale == 1f) {
                                        if (delta < -15f) { // Swipe up
                                            showInfoSheet = true
                                        } else if (delta > 15f) { // Swipe down
                                            if (showInfoSheet) {
                                                showInfoSheet = false
                                            } else {
                                                selectedPhotoIndex = null
                                            }
                                        }
                                    }
                                },
                                orientation = androidx.compose.foundation.gestures.Orientation.Vertical
                            )
                    ) {
                        val isVideo = photo.name.lowercase().endsWith(".mp4") ||
                                      photo.name.lowercase().endsWith(".webm") ||
                                      photo.name.lowercase().endsWith(".mkv")

                        if (isVideo) {
                            VideoPlayer(videoUrl = photo.uri.toString(), modifier = Modifier.fillMaxSize())
                        } else {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = photo.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    ),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                // Image Counter Overlay
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 32.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} of ${displayedPhotos.size}",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (showInfoSheet) {
                    val currentPhoto = displayedPhotos.getOrNull(pagerState.currentPage)
                    if (currentPhoto != null) {
                        val tempPhoto = Photo(
                            filename = currentPhoto.name,
                            path = currentPhoto.uri.toString(),
                            size = currentPhoto.size.toString(),
                            taken_at = currentPhoto.file?.let { file ->
                                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
                            } ?: java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                            mime_type = if (currentPhoto.name.lowercase().endsWith(".jpg") || currentPhoto.name.lowercase().endsWith(".jpeg")) "image/jpeg" 
                                        else if (currentPhoto.name.lowercase().endsWith(".png")) "image/png"
                                        else if (currentPhoto.name.lowercase().endsWith(".mp4")) "video/mp4"
                                        else "image/unknown"
                        )
                        PhotoDetailsBottomSheet(
                            photo = tempPhoto,
                            localFile = currentPhoto.file,
                            onPhotoDeleted = { selectedPhotoIndex = null },
                            onDismiss = { showInfoSheet = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryScreen(repository: PhotoRepository, baseUrl: String, activeDownloads: MutableMap<Long, String>, downloadProgress: MutableMap<String, Float>) {
    RemotePhotoListScreen(repository, baseUrl, "Gallery", activeDownloads, downloadProgress)
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
fun AlbumsScreen(repository: PhotoRepository, baseUrl: String, activeDownloads: MutableMap<Long, String>, downloadProgress: MutableMap<String, Float>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var albums by remember { mutableStateOf(listOf<PhotoAlbum>()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedAlbum by remember { mutableStateOf<PhotoAlbum?>(null) } // Detail state
    var albumPhotosCount by remember { mutableStateOf<Int?>(null) }

    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var albumToEdit by remember { mutableStateOf<PhotoAlbum?>(null) }
    var albumToDelete by remember { mutableStateOf<PhotoAlbum?>(null) }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    fun fetchAlbums(showLoadingState: Boolean = true) {
        if (showLoadingState) isLoading = true else isRefreshing = true
        scope.launch {
            try {
                val response = ApiClient.getPhotoService(context).getAlbums()
                if (response.isSuccessful) {
                    albums = response.body()?.albums ?: emptyList()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error fetching albums: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                if (showLoadingState) isLoading = false else isRefreshing = false
            }
        }
    }

    if (selectedAlbum != null) {
        // Show Album Details View
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    selectedAlbum = null
                    albumPhotosCount = null 
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                val totalCount = albumPhotosCount ?: (selectedAlbum!!.photo_count?.toIntOrNull() ?: 0) + (selectedAlbum!!.video_count?.toIntOrNull() ?: 0)
                Text(
                    text = "${selectedAlbum!!.name} ($totalCount items)",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Reuse the RemotePhotoListScreen to display the album's photos using the new endpoint
            RemotePhotoListScreen(
                repository = repository,
                baseUrl = baseUrl,
                title = selectedAlbum!!.name,
                activeDownloads = activeDownloads,
                downloadProgress = downloadProgress,
                fetchPhotos = { ApiClient.getPhotoService(it).getAlbumPhotos(selectedAlbum!!.id ?: "") },
                onPhotosLoaded = { albumPhotosCount = it }
            )
        }
    } else {
        // Show Albums List View
        LaunchedEffect(Unit) {
            fetchAlbums(true)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { fetchAlbums(false) },
                modifier = Modifier.fillMaxSize()
            ) {
                if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (albums.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No albums found")
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                        items(albums) { album ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                                    .combinedClickable(
                                        onClick = { selectedAlbum = album },
                                        onLongClick = { albumToEdit = album }
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(album.name, style = MaterialTheme.typography.headlineSmall)
                                        album.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                        val total = (album.photo_count?.toIntOrNull() ?: 0) + (album.video_count?.toIntOrNull() ?: 0)
                                        Text(
                                            text = "$total all, ${album.photo_count ?: "0"} pics, ${album.video_count ?: "0"} videos",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    IconButton(onClick = { albumToDelete = album }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Album", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
            
            FloatingActionButton(
                onClick = { showCreateAlbumDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Album")
            }
        }

        // Dialogs
        if (showCreateAlbumDialog || albumToEdit != null) {
            val isEdit = albumToEdit != null
            var name by remember { mutableStateOf(albumToEdit?.name ?: "") }
            var desc by remember { mutableStateOf(albumToEdit?.description ?: "") }
            
            AlertDialog(
                onDismissRequest = { 
                    showCreateAlbumDialog = false
                    albumToEdit = null 
                },
                title = { Text(if (isEdit) "Edit Album" else "Create Album") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Album Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = desc,
                            onValueChange = { desc = it },
                            label = { Text("Description (Optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                scope.launch {
                                    val success = if (isEdit) {
                                        repository.updateAlbum(albumToEdit!!.id ?: "", name, desc)
                                    } else {
                                        repository.createAlbum(name, desc)
                                    }
                                    if (success) {
                                        Toast.makeText(context, if (isEdit) "Album updated" else "Album created", Toast.LENGTH_SHORT).show()
                                        fetchAlbums()
                                    } else {
                                        Toast.makeText(context, "Failed to save album", Toast.LENGTH_SHORT).show()
                                    }
                                    showCreateAlbumDialog = false
                                    albumToEdit = null
                                }
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showCreateAlbumDialog = false
                        albumToEdit = null 
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (albumToDelete != null) {
            AlertDialog(
                onDismissRequest = { albumToDelete = null },
                title = { Text("Delete Album") },
                text = { Text("Are you sure you want to delete '${albumToDelete?.name}'?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val success = repository.deleteAlbum(albumToDelete!!.id ?: "")
                                if (success) {
                                    Toast.makeText(context, "Album deleted", Toast.LENGTH_SHORT).show()
                                    fetchAlbums()
                                } else {
                                    Toast.makeText(context, "Failed to delete album", Toast.LENGTH_SHORT).show()
                                }
                                albumToDelete = null
                            }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { albumToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.toDouble())).toInt()
    return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private suspend fun downloadRemotePhoto(context: Context, baseUrl: String, photo: Photo): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            val url = baseUrl.trimEnd('/') + "/" + photo.path.trimStart('/')
            val request = okhttp3.Request.Builder().url(url).build()
            val response = ApiClient.getHttpClient(context).newCall(request).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: HTTP ${response.code}", Toast.LENGTH_SHORT).show()
                }
                return@withContext null
            }

            val bytes = response.body?.bytes() ?: return@withContext null
            val mimeType = response.body?.contentType()?.toString() ?: "image/jpeg"

            val filename = photo.filename.ifBlank { "chege_photo_${System.currentTimeMillis()}.jpg" }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Chege Photos")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download failed: could not create file", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext null
                }
                resolver.openOutputStream(uri)?.use { output ->
                    output.write(bytes)
                }
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                return@withContext uri
            } else {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_PICTURES + "/Chege Photos"
                )
                dir.mkdirs()
                val file = File(dir, filename)
                file.writeBytes(bytes)
                // Notify the media scanner
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(mimeType ?: "image/jpeg"),
                    null
                )
                return@withContext Uri.fromFile(file)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

fun showUploadNotification(
    context: Context,
    current: Int,
    total: Int,
    uploadedBytes: Long = 0L,
    totalBytes: Long = 0L,
    isFinished: Boolean = false,
    failedCount: Int = 0,
    currentFileName: String? = null,
    bucketName: String? = null
) {
    val channelId = "chege_photos_sync_v2"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (systemNotificationManager != null && systemNotificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "Photo Sync",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Live notifications for photo uploads and background sync"
                setShowBadge(true)
            }
            systemNotificationManager.createNotificationChannel(channel)
        }
    }

    val notificationManager = NotificationManagerCompat.from(context)
    val percentage = if (totalBytes > 0) {
        ((uploadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
    } else if (total > 0) {
        ((current.toDouble() / total) * 100).toInt().coerceIn(0, 100)
    } else 0

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_sync_notification)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setOngoing(!isFinished)
        .setOnlyAlertOnce(true)

    val targetLabel = if (!bucketName.isNullOrBlank() && bucketName != "All") bucketName else "Photos"

    if (isFinished) {
        val totalSizeStr = if (uploadedBytes > 0) " (${formatFileSize(uploadedBytes)})" else ""
        if (failedCount > 0) {
            builder.setContentTitle("Sync Incomplete • $targetLabel")
                .setContentText("Uploaded $current of $total photos ($failedCount unable to upload)$totalSizeStr")
                .setSubText("$failedCount unable to upload")
        } else {
            builder.setContentTitle("Sync Complete • $targetLabel")
                .setContentText("Successfully uploaded all $current photos$totalSizeStr")
                .setSubText("Done")
        }
        builder.setProgress(0, 0, false)
            .setAutoCancel(true)
    } else {
        val bytesInfo = if (totalBytes > 0) {
            val remainingBytes = (totalBytes - uploadedBytes).coerceAtLeast(0L)
            "${formatFileSize(uploadedBytes)} of ${formatFileSize(totalBytes)} (${formatFileSize(remainingBytes)} left)"
        } else {
            "$percentage%"
        }
        val fileDetail = if (!currentFileName.isNullOrBlank()) " • $currentFileName" else ""
        
        builder.setContentTitle("Syncing $targetLabel ($current of $total)")
            .setContentText("$bytesInfo$fileDetail")
            .setSubText("$percentage%")
            .setProgress(100, percentage, false)
    }

    try {
        notificationManager.notify(1001, builder.build())
    } catch (e: SecurityException) {
        android.util.Log.w("Notification", "Missing notification permission: ${e.message}")
    }
}

@Composable
fun SharedUploadDialog(
    files: MutableList<File>,
    repository: PhotoRepository
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }

    // WorkManager Integration
    val workManager = remember { androidx.work.WorkManager.getInstance(context.applicationContext) }
    val workInfosState = remember {
        workManager.getWorkInfosForUniqueWorkFlow("ChegePhotosManualUpload")
    }.collectAsState(initial = emptyList())

    val workInfo = workInfosState.value.firstOrNull()
    val isUploading = workInfo != null && workInfo.state == androidx.work.WorkInfo.State.RUNNING

    val progressData = workInfo?.progress
    val currentFileProgress = progressData?.getFloat("progress", 0f) ?: 0f
    val uploadedCount = progressData?.getInt("current", 0) ?: 0

    var selectedAlbum by remember { mutableStateOf<PhotoAlbum?>(null) }
    var albums by remember { mutableStateOf(listOf<PhotoAlbum>()) }
    var showAlbumMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val resp = ApiClient.getPhotoService(context).getAlbums()
            if (resp.isSuccessful) {
                albums = resp.body()?.albums ?: emptyList()
            }
        } catch (_: Exception) { }
    }

    LaunchedEffect(workInfo?.state) {
        if (workInfo != null) {
            if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                val output = workInfo.outputData
                val succeeded = output.getInt("succeeded", 0)
                val total = output.getInt("total", 0)
                Toast.makeText(context, "Uploaded $succeeded out of $total items", Toast.LENGTH_SHORT).show()
                files.forEach { it.delete() }
                files.clear()
            } else if (workInfo.state == androidx.work.WorkInfo.State.FAILED) {
                Toast.makeText(context, "Upload failed or cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    Dialog(
        onDismissRequest = { if (!isUploading) files.clear() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Upload Shared Items", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isUploading,
                            onClick = { showAlbumMenu = true }
                        ) {
                            Icon(Icons.Default.PhotoAlbum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                selectedAlbum?.name
                                    ?: if (albums.isEmpty()) "No albums available" else "Choose album (optional)",
                                maxLines = 1
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showAlbumMenu,
                            onDismissRequest = { showAlbumMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("No album") },
                                onClick = {
                                    selectedAlbum = null
                                    showAlbumMenu = false
                                }
                            )
                            albums.forEach { album ->
                                DropdownMenuItem(
                                    text = { Text(album.name ?: "Untitled") },
                                    onClick = {
                                        selectedAlbum = album
                                        showAlbumMenu = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        enabled = !isUploading,
                        onClick = {
                            val localPhotos = files.map { file ->
                                com.niccher.chege_photos_app.repository.LocalPhoto(
                                    uri = android.net.Uri.fromFile(file),
                                    file = file,
                                    name = file.name,
                                    size = file.length()
                                )
                            }
                            com.niccher.chege_photos_app.utils.ManualUploadWorker.enqueue(
                                context = context,
                                photos = localPhotos,
                                albumId = selectedAlbum?.id,
                                bucketName = selectedAlbum?.name ?: "Album Upload"
                            )
                        }
                    ) {
                        Text(if (isUploading) "Uploading... (${uploadedCount}/${files.size})" else "Upload All (${files.size} items)")
                    }
                    
                    if (isUploading) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            LinearProgressIndicator(
                                progress = { currentFileProgress },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Uploading... (${(currentFileProgress * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Cancel",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        workManager.cancelUniqueWork("ChegePhotosManualUpload")
                                    }.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        itemsIndexed(
                            items = files,
                            key = { _, file -> file.absolutePath }
                        ) { _, file ->
                            Card(modifier = Modifier.padding(4.dp).fillMaxWidth()) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().height(150.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
                
                if (!isUploading) {
                    IconButton(
                        onClick = { 
                            files.forEach { it.delete() }
                            files.clear() 
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(sessionManager: com.niccher.chege_photos_app.utils.SessionManager) {
    val context = LocalContext.current
    val userDetails = remember { sessionManager.getUserDetails() }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Person, contentDescription = "Profile", modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = userDetails["username"] ?: "Unknown", style = MaterialTheme.typography.headlineMedium)
        Text(text = userDetails["email"] ?: "Unknown", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileRow("Account Created", userDetails["created"] ?: "Unknown")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProfileRow("Last Login", userDetails["last_login"] ?: "Unknown")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProfileRow("Last Upload", userDetails["last_upload"] ?: "Unknown")
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Biometric Lock", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    var checked by remember { mutableStateOf(sessionManager.isBiometricEnabled()) }
                    Switch(
                        checked = checked,
                        onCheckedChange = { 
                            if (it) {
                                showBiometricPrompt(context as FragmentActivity) { success ->
                                    if (success) {
                                        sessionManager.setBiometricEnabled(true)
                                        checked = true
                                    }
                                }
                            } else {
                                sessionManager.setBiometricEnabled(false)
                                checked = false
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                var autoBackupChecked by remember { mutableStateOf(sessionManager.isBackupAutoEnabled()) }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Auto Backup", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Switch(
                        checked = autoBackupChecked,
                        onCheckedChange = { 
                            sessionManager.setBackupAutoEnabled(it)
                            autoBackupChecked = it
                            (context as? MainActivity)?.scheduleBackgroundSync()
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Backup Only on Wi-Fi", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    var wifiOnlyChecked by remember { mutableStateOf(sessionManager.isBackupOnlyWifi()) }
                    Switch(
                        checked = wifiOnlyChecked,
                        enabled = autoBackupChecked,
                        onCheckedChange = { 
                            sessionManager.setBackupOnlyWifi(it)
                            wifiOnlyChecked = it
                            (context as? MainActivity)?.scheduleBackgroundSync()
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Backup Only When Charging", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    var chargingOnlyChecked by remember { mutableStateOf(sessionManager.isBackupOnlyCharging()) }
                    Switch(
                        checked = chargingOnlyChecked,
                        enabled = autoBackupChecked,
                        onCheckedChange = { 
                            sessionManager.setBackupOnlyCharging(it)
                            chargingOnlyChecked = it
                            (context as? MainActivity)?.scheduleBackgroundSync()
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = {
                        (context as? MainActivity)?.triggerImmediateBackup()
                        android.widget.Toast.makeText(context, "Immediate backup enqueued in background", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Backup Now",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Backup Now")
                }

                val powerManager = remember(context) {
                    context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                }
                val isIgnoringOptimizations = remember(context) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
                    } else true
                }

                if (!isIgnoringOptimizations) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Battery Optimization",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Unrestrict Background Backup", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Current Device Status",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                val batteryIntent = remember(context) {
                    context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                }
                val isCharging = remember(batteryIntent) {
                    val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                    status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
                }
                val batteryPct = remember(batteryIntent) {
                    val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                    if (level >= 0 && scale > 0) (level * 100 / scale) else 100
                }
                val connectivityManager = remember(context) {
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                }
                val isWifi = remember(connectivityManager) {
                    val net = connectivityManager?.activeNetwork
                    val caps = connectivityManager?.getNetworkCapabilities(net)
                    caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (batteryPct <= 15) "Low Battery ($batteryPct%)" else "Battery Guard ($batteryPct%)", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                if (batteryPct <= 15) Icons.Default.BatteryAlert else Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = if (batteryPct <= 15) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(if (isWifi) "Wi-Fi Active" else "Cellular Network", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                if (isWifi) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = if (isWifi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(if (isCharging) "Charging ✓" else "On Battery", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Power,
                                contentDescription = null,
                                tint = if (isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

fun showBiometricPrompt(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onResult(true)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onResult(false)
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onResult(false)
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Biometric login for Chege Photos")
        .setSubtitle("Log in using your biometric credential")
        .setNegativeButtonText("Cancel")
        .build()

    biometricPrompt.authenticate(promptInfo)
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(text = value, color = Color.Gray)
    }
}
@Composable
fun ThemeSettingsDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Switch Atmosphere", 
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Choose a style that fits your mood",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                AppTheme.values().forEach { theme ->
                    ThemeOption(
                        theme = theme,
                        isSelected = theme == currentTheme,
                        onClick = { onThemeSelected(theme) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun ThemeOption(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when (theme) {
        AppTheme.DEFAULT -> MaterialTheme.colorScheme.primaryContainer
        AppTheme.SOLARIZED -> Color(0xFFFDF6E3)
        AppTheme.GREY -> Color(0xFF263238)
        AppTheme.MIDNIGHT -> Color(0xFF1A237E)
        AppTheme.BLACK -> Color(0xFF000000)
    }
    
    val textColor = when (theme) {
        AppTheme.SOLARIZED -> Color(0xFF657B83)
        AppTheme.DEFAULT -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> Color.White
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) backgroundColor else backgroundColor.copy(alpha = 0.3f),
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = theme.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) textColor else MaterialTheme.colorScheme.onSurface
                )
                if (isSelected) {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle, 
                    contentDescription = "Selected",
                    tint = textColor
                )
            }
        }
    }
}

data class ExifInfo(
    val camera: String? = null,
    val iso: String? = null,
    val shutter: String? = null,
    val aperture: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailsBottomSheet(
    photo: Photo,
    localFile: java.io.File? = null,
    baseUrl: String = "",
    onPhotoDeleted: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    var faceList by remember { mutableStateOf<List<com.niccher.chege_photos_app.models.FaceData>>(emptyList()) }
    var facesLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(photo.id, baseUrl) {
        val pid = photo.id?.toIntOrNull()
        if (pid != null && baseUrl.isNotEmpty() && !facesLoaded) {
            try {
                val resp = ApiClient.getPhotoService(context).getFacesByPhoto(pid)
                if (resp.isSuccessful) {
                    val body = resp.body()
                    if (body?.status == "success") {
                        faceList = body.faces
                    }
                }
            } catch (_: Exception) { }
            facesLoaded = true
        }
    }
    
    val exifData = remember(photo, localFile) {
        if (localFile != null && localFile.exists()) {
            try {
                val exif = ExifInterface(localFile.absolutePath)
                val latLong = FloatArray(2)
                val hasLatLong = exif.getLatLong(latLong)
                
                ExifInfo(
                    camera = exif.getAttribute(ExifInterface.TAG_MODEL)?.takeIf { it.isNotBlank() },
                    iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.takeIf { it.isNotBlank() },
                    shutter = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.takeIf { it.isNotBlank() }?.let { "${it}s" },
                    aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.takeIf { it.isNotBlank() }?.let { "f/$it" },
                    latitude = if (hasLatLong) latLong[0].toDouble() else null,
                    longitude = if (hasLatLong) latLong[1].toDouble() else null
                )
            } catch (e: Exception) {
                ExifInfo()
            }
        } else if (photo.exif_data != null) {
            try {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val exif = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(photo.exif_data)

                fun safeGetString(key: String, fallback: String? = null): String? {
                    val el = exif[key] ?: return fallback
                    return when (el) {
                        is kotlinx.serialization.json.JsonPrimitive -> el.content
                        else -> el.toString()
                    }
                }

                fun rational(v: String?): String? {
                    if (v == null) return null
                    val parts = v.split("/")
                    if (parts.size == 2) {
                        val n = parts[0].toDoubleOrNull() ?: return v
                        val d = parts[1].toDoubleOrNull() ?: return v
                        return if (d != 0.0) (n / d).toString() else v
                    }
                    return v
                }
                ExifInfo(
                    camera = safeGetString("Model") ?: safeGetString("Make"),
                    iso = safeGetString("ISOSpeedRatings"),
                    shutter = rational(safeGetString("ExposureTime"))?.let { "${it}s" },
                    aperture = rational(safeGetString("FNumber"))?.let { "f/$it" },
                    latitude = photo.latitude?.toDoubleOrNull(),
                    longitude = photo.longitude?.toDoubleOrNull()
                )
            } catch (e: Exception) {
                ExifInfo(
                    latitude = photo.latitude?.toDoubleOrNull(),
                    longitude = photo.longitude?.toDoubleOrNull()
                )
            }
        } else {
            ExifInfo(
                latitude = photo.latitude?.toDoubleOrNull(),
                longitude = photo.longitude?.toDoubleOrNull()
            )
        }
    }

    val scope = rememberCoroutineScope()
    var isFavoriteState by remember(photo.is_favorite) { mutableStateOf(photo.is_favorite == "1" || photo.is_favorite == "true") }
    var showLocalAlbumPicker by remember { mutableStateOf(false) }
    var albumsList by remember { mutableStateOf(listOf<com.niccher.chege_photos_app.models.Album>()) }

    LaunchedEffect(Unit) {
        try {
            val resp = ApiClient.getPhotoService(context).getAlbums()
            if (resp.isSuccessful) {
                albumsList = resp.body()?.albums ?: emptyList()
            }
        } catch (_: Exception) {}
    }

    if (showLocalAlbumPicker) {
        AlbumPickerDialog(
            albums = albumsList,
            selectedCount = 1,
            onAlbumSelected = { album ->
                showLocalAlbumPicker = false
                scope.launch {
                    val repository = PhotoRepository(context)
                    val ok = repository.addPhotoToAlbum(album.id ?: "", photo.id ?: "")
                    if (ok) {
                        Toast.makeText(context, "Added to ${album.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to add to album", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showLocalAlbumPicker = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Photo Information",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (localFile != null) {
                    // Local photo: display Upload to Server action
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            val fileSize = localFile.length()
                            showUploadNotification(
                                context = context,
                                current = 1,
                                total = 1,
                                uploadedBytes = 0L,
                                totalBytes = fileSize,
                                isFinished = false,
                                currentFileName = photo.filename,
                                bucketName = "Photo"
                            )
                            scope.launch {
                                val uri = Uri.parse(photo.path)
                                val localPhoto = com.niccher.chege_photos_app.repository.LocalPhoto(
                                    uri = uri,
                                    file = localFile,
                                    name = photo.filename,
                                    size = fileSize
                                )
                                val repository = PhotoRepository(context)
                                val result = repository.syncPhoto(localPhoto)
                                if (result is PhotoSyncResult.Success) {
                                    SessionManager(context).updateLastUpload()
                                    showUploadNotification(
                                        context = context,
                                        current = 1,
                                        total = 1,
                                        uploadedBytes = fileSize,
                                        totalBytes = fileSize,
                                        isFinished = true,
                                        bucketName = "Photo"
                                    )
                                    Toast.makeText(context, "Uploaded successfully!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                    onPhotoDeleted?.invoke()
                                } else {
                                    showUploadNotification(
                                        context = context,
                                        current = 0,
                                        total = 1,
                                        uploadedBytes = 0L,
                                        totalBytes = fileSize,
                                        isFinished = true,
                                        bucketName = "Photo"
                                    )
                                    val errMsg = (result as? PhotoSyncResult.Error)?.message ?: "Unknown error"
                                    Toast.makeText(context, "Upload failed: $errMsg", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Upload", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    // Favorite Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            scope.launch {
                                try {
                                    val pid = photo.id ?: return@launch
                                    val resp = ApiClient.getPhotoService(context).favoritePhoto(pid)
                                    if (resp.isSuccessful) {
                                        isFavoriteState = !isFavoriteState
                                        val db = com.niccher.chege_photos_app.data.AppDatabase.getDatabase(context)
                                        val cached = db.photoDao().getPhotoById(photo.id ?: "")
                                        if (cached != null) {
                                            db.photoDao().insertPhotos(listOf(cached.copy(is_favorite = if (isFavoriteState) 1 else 0)))
                                        }
                                        Toast.makeText(context, if (isFavoriteState) "Added to Favorites" else "Removed from Favorites", Toast.LENGTH_SHORT).show()
                                        // Trigger gallery refresh to show updated state
                                        PhotoRepository(context).photosRefreshTrigger.tryEmit(Unit)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isFavoriteState) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavoriteState) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Favorite", style = MaterialTheme.typography.labelSmall)
                    }

                    // Add to Album Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showLocalAlbumPicker = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Add to Album"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Add to Album", style = MaterialTheme.typography.labelSmall)
                    }

                    // Download Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            scope.launch {
                                downloadRemotePhoto(context, baseUrl, photo)?.let {
                                    Toast.makeText(context, "Downloaded to Gallery", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Download", style = MaterialTheme.typography.labelSmall)
                    }

                    // Delete Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            scope.launch {
                                try {
                                    val pid = photo.id ?: return@launch
                                    val resp = ApiClient.getPhotoService(context).deletePhoto(pid)
                                    if (resp.isSuccessful) {
                                        val db = com.niccher.chege_photos_app.data.AppDatabase.getDatabase(context)
                                        db.photoDao().deleteById(pid)
                                        Toast.makeText(context, "Photo moved to Trash", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                        onPhotoDeleted?.invoke()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.Red
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Delete", style = MaterialTheme.typography.labelSmall, color = Color.Red)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Basic Info Section
            MetadataSection(title = "File Details") {
                MetadataRow(Icons.Default.Description, "Filename", photo.filename)
                MetadataRow(Icons.Default.CalendarToday, "Captured", photo.taken_at ?: "Unknown")
                val sizeBytes = photo.size?.toLongOrNull() ?: 0L
                MetadataRow(Icons.Default.Storage, "Size", formatSize(sizeBytes))
                if (photo.width != null) {
                    MetadataRow(Icons.Default.AspectRatio, "Dimensions", "${photo.width} x ${photo.height}")
                }
                MetadataRow(Icons.AutoMirrored.Filled.Label, "Format", photo.mime_type ?: "Unknown")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // EXIF Section
            if (exifData.camera != null || exifData.iso != null || exifData.shutter != null || exifData.aperture != null) {
                Spacer(modifier = Modifier.height(24.dp))
                MetadataSection(title = "Camera EXIF") {
                    exifData.camera?.let { MetadataRow(Icons.Default.CameraAlt, "Camera", it) }
                    exifData.iso?.let { MetadataRow(Icons.Default.Iso, "ISO", it) }
                    exifData.shutter?.let { MetadataRow(Icons.Default.ShutterSpeed, "Shutter", it) }
                    exifData.aperture?.let { MetadataRow(Icons.Default.Camera, "Aperture", it) }
                }
            }

            // Location Section
            if (exifData.latitude != null && exifData.longitude != null) {
                Spacer(modifier = Modifier.height(24.dp))
                MetadataSection(title = "Location") {
                    MetadataRow(Icons.Default.LocationOn, "Coordinates", "${exifData.latitude}, ${exifData.longitude}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val gmmIntentUri = Uri.parse("geo:0,0?q=${exifData.latitude},${exifData.longitude}(${photo.filename})")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                            mapIntent.setPackage("com.google.android.apps.maps")
                            context.startActivity(mapIntent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View on Google Maps")
                    }
                }
            }

            // Faces Section
            if (faceList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                MetadataSection(title = "Faces (${faceList.size})") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(faceList, key = { it.face_id }) { face ->
                            Card(
                                modifier = Modifier.width(100.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .background(Color(0xFF333333)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val imageModel = remember(photo, baseUrl) {
                                            val p = photo.path
                                            if (p.startsWith("/") || p.startsWith("content:")) {
                                                p
                                            } else {
                                                baseUrl.trimEnd('/') + "/" + p.trimStart('/')
                                            }
                                        }
                                        AsyncImage(
                                            model = imageModel,
                                            contentDescription = "Face",
                                            modifier = Modifier
                                                .size(80.dp)
                                                .background(Color(0xFF333333)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Text(
                                        face.person_name ?: "Unassigned",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    Text(
                                        "Score: ${"%.2f".format(face.detection_score)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                    if (face.age != null) {
                                        Text(
                                            "~${face.age}y ${face.gender?.take(1) ?: ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
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
}

@Composable
fun MetadataSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun MetadataRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ServerConfigScreen(
    currentUrl: String,
    onUrlSaved: (String) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(currentUrl) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Server Configuration",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Change the backend server your app connects to.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Server URL", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; testResult = null },
                    label = { Text("Server URL") },
                    leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    placeholder = { Text("e.g. 192.168.1.50:2283 or https://photos.example.com") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            testing = true
                            testResult = null
                            val normalized = ApiClient.normalizeUrl(url)
                            ApiClient.updateBaseUrl(normalized, context)
                            scope.launch {
                                try {
                                    val res = withContext(Dispatchers.IO) {
                                        ApiClient.getPhotoService(context).ping()
                                    }
                                    testResult = if (res.isSuccessful) "Connection successful!"
                                    else "Server responded with ${res.code()}"
                                } catch (e: Exception) {
                                    testResult = "Error: ${e.localizedMessage ?: "Could not reach server"}"
                                }
                                testing = false
                            }
                        },
                        enabled = url.isNotBlank() && !testing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (testing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Test Connection")
                    }

                    Button(
                        onClick = {
                            try {
                                val normalized = ApiClient.normalizeUrl(url)
                                onUrlSaved(normalized)
                                onNavigateBack()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Error: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save")
                    }
                }

                if (testResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val isSuccess = testResult!!.startsWith("Connection successful") ||
                            testResult!!.startsWith("URL saved")
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                testResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Info",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The server URL is where your Chege Photos backend is hosted. " +
                            "For local servers on your network, use the LAN IP (e.g. 192.168.1.50:9005). " +
                            "For remote servers, use the full https URL. " +
                            "The app will automatically detect and use the correct protocol.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun AboutScreen(version: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_icon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Chege Photos",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "Version $version",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About the App",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Chege Photos is a premium photo management application designed for high-performance syncing and elegant viewing. It features ML-powered face recognition — detecting, embedding, clustering, and searching faces across your entire library using Insightface and Qdrant vector search.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How it Works",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf(
                        "Sync" to "Automatically backup and sync your local photos to the cloud.",
                        "Gallery" to "Browse all your photos in a high-performance grid with search and filtering.",
                        "Albums" to "Create, rename, and manage collections of your favorite moments.",
                        "Faces" to "Face recognition powered by Insightface (Buffalo-L) and Qdrant vector search. Detected faces are grouped by person, and you can tap any face to see all photos containing that person.",
                        "Search by Face" to "Upload a photo and find all similar faces across your library using cosine similarity search on 512-dimensional embeddings."
                    ).forEach { (title, desc) ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp).padding(top = 2.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(text = desc, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Libraries Used",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf("Jetpack Compose", "Retrofit", "Coil", "OkHttp", "Material 3", "Coroutines", "Biometrics", "Kotlin Serialization", "Room").forEach { lib ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Box(modifier = Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = lib, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "© 2026 Niccher. All rights reserved.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(80.dp)) // Extra space for bottom bar
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerDialog(
    albums: List<PhotoAlbum>,
    selectedCount: Int,
    onAlbumSelected: (PhotoAlbum) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Add $selectedCount photos to album",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (albums.isEmpty()) {
                    Text(
                        "No albums found. Create one in the Albums tab first.",
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(albums) { album ->
                            Surface(
                                onClick = { onAlbumSelected(album) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PhotoAlbum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(album.name ?: "Untitled", style = MaterialTheme.typography.bodyMedium)
                                        album.photo_count?.let {
                                            Text("$it photos", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceSearchScreen(baseUrl: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var persons by remember { mutableStateOf<List<com.niccher.chege_photos_app.models.PersonData>>(emptyList()) }
    var selectedPersonId by remember { mutableStateOf<Int?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<com.niccher.chege_photos_app.models.FaceSearchResult>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val coilImageLoader = remember(context) { ApiClient.getImageLoader(context) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isSearching = true
                searchResults = emptyList()
                searchError = null
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes == null) {
                        searchError = "Failed to read the selected image"
                    } else {
                        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val filePart = okhttp3.MultipartBody.Part.createFormData(
                            "file",
                            "face_search_${System.currentTimeMillis()}.jpg",
                            bytes.toRequestBody(mime.toMediaTypeOrNull())
                        )
                        val limitPart = okhttp3.MultipartBody.Part.createFormData("limit", "10")
                        val resp = withContext(Dispatchers.IO) {
                            ApiClient.getPhotoService(context).searchFacesByPhoto(filePart, limitPart)
                        }
                        if (resp.isSuccessful) {
                            val body = resp.body()
                            if (body?.status == "success") {
                                searchResults = body.data?.results ?: emptyList()
                            } else {
                                searchError = body?.status ?: "Face search failed"
                            }
                        } else {
                            searchError = "Server error: HTTP ${resp.code()}"
                        }
                    }
                } catch (e: Exception) {
                    searchError = e.localizedMessage ?: "Face search failed"
                }
                isSearching = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!loaded) {
            try {
                val resp = ApiClient.getPhotoService(context).getPersons()
                if (resp.isSuccessful) {
                    val body = resp.body()
                    if (body?.status == "success") {
                        persons = body.persons
                    }
                }
            } catch (_: Exception) { }
            loaded = true
        }
    }

    if (selectedPersonId != null) {
        PersonPhotosScreen(
            baseUrl = baseUrl,
            personId = selectedPersonId!!,
            onBack = { selectedPersonId = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            enabled = !isSearching,
            onClick = { imagePicker.launch("image/*") }
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (isSearching) "Searching..." else "Search by face")
        }

        if (isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }

        if (searchError != null) {
            Text(
                text = searchError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        if (searchResults.isNotEmpty()) {
            Text(
                text = "${searchResults.size} similar face(s)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                items(searchResults, key = { it.face_id }) { result ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(result.person_name ?: "Unknown person", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Match: ${"%.1f".format(result.score * 100)}%  •  photo #${result.photo_id}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (persons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No faces found. Run face detection on the server.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(persons, key = { it.id }) { person ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedPersonId = person.id
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (person.thumbnail != null) {
                                val thumb = person.thumbnail
                                val imgUrl = if (thumb.thumbnail_path?.startsWith("/") == true || thumb.thumbnail_path?.startsWith("content:") == true || thumb.path.startsWith("/") || thumb.path.startsWith("content:")) {
                                    thumb.thumbnail_path ?: thumb.path
                                } else {
                                    baseUrl.trimEnd('/') + "/" + (thumb.thumbnail_path ?: thumb.path).trimStart('/')
                                }
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = person.name,
                                    imageLoader = coilImageLoader,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(Color(0xFF333333), shape = CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${person.face_count} photo${if (person.face_count != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PeopleAvatarRow(
    baseUrl: String,
    coilImageLoader: coil.ImageLoader,
    onPersonClick: (Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var persons by remember { mutableStateOf<List<com.niccher.chege_photos_app.models.PersonData>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val resp = ApiClient.getPhotoService(context).getPersons()
            if (resp.isSuccessful) {
                persons = resp.body()?.persons ?: emptyList()
            }
        } catch (_: Exception) {}
    }

    if (persons.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "People & Pets",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${persons.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(persons, key = { it.id }) { person ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onPersonClick(person.id) }
                            .width(64.dp)
                    ) {
                        val thumb = person.thumbnail
                        val imgUrl = if (thumb != null) {
                            if (thumb.thumbnail_path?.startsWith("/") == true || thumb.thumbnail_path?.startsWith("content:") == true || thumb.path.startsWith("/") || thumb.path.startsWith("content:")) {
                                thumb.thumbnail_path ?: thumb.path
                            } else {
                                baseUrl.trimEnd('/') + "/" + (thumb.thumbnail_path ?: thumb.path).trimStart('/')
                            }
                        } else null

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imgUrl != null) {
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = person.name ?: "Person",
                                    imageLoader = coilImageLoader,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = person.name?.ifBlank { "Person" } ?: "Person",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonPhotosScreen(
    baseUrl: String,
    personId: Int,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<com.niccher.chege_photos_app.models.PersonPhoto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }

    val coilImageLoader = remember(context) { ApiClient.getImageLoader(context) }

    LaunchedEffect(personId) {
        try {
            val resp = ApiClient.getPhotoService(context).getPersonPhotos(personId)
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body?.status == "success") {
                    photos = body.photos
                }
            }
        } catch (_: Exception) { }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Person Photos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photos found.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                    Card(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        onClick = { selectedPhotoIndex = index }
                    ) {
                        val imageModel = remember(photo, baseUrl) {
                            val p = photo.thumbnail_path ?: photo.path
                            if (p.startsWith("/") || p.startsWith("content:")) {
                                p
                            } else {
                                baseUrl.trimEnd('/') + "/" + p.trimStart('/')
                            }
                        }
                        AsyncImage(
                            model = imageModel,
                            contentDescription = photo.filename,
                            imageLoader = coilImageLoader,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }

    // Full-screen photo pager
    selectedPhotoIndex?.let { initialPage ->
        PersonPhotoPager(
            baseUrl = baseUrl,
            photos = photos,
            initialPage = initialPage,
            personId = personId,
            coilImageLoader = coilImageLoader,
            onDismiss = { selectedPhotoIndex = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonPhotoPager(
    baseUrl: String,
    photos: List<com.niccher.chege_photos_app.models.PersonPhoto>,
    initialPage: Int,
    personId: Int,
    coilImageLoader: ImageLoader,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { photos.size })
            var showFaces by remember { mutableStateOf(false) }
            var facesMap by remember { mutableStateOf<Map<Int, List<com.niccher.chege_photos_app.models.FaceData>>>(emptyMap()) }

            LaunchedEffect(pagerState.currentPage) {
                showFaces = false
            }

            LaunchedEffect(pagerState.currentPage) {
                val photo = photos[pagerState.currentPage]
                if (!facesMap.containsKey(photo.id)) {
                    try {
                        val resp = ApiClient.getPhotoService(context).getFacesByPhoto(photo.id)
                        if (resp.isSuccessful) {
                            val body = resp.body()
                            if (body?.status == "success") {
                                facesMap = facesMap + (photo.id to body.faces)
                            }
                        }
                    } catch (_: Exception) { }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val photo = photos[page]
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                LaunchedEffect(pagerState.currentPage) {
                    scale = 1f
                    offset = Offset.Zero
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                ) {
                    val imageModel = remember(photo, baseUrl) {
                        val p = photo.path
                        if (p.startsWith("/") || p.startsWith("content:")) {
                            p
                        } else {
                            baseUrl.trimEnd('/') + "/" + p.trimStart('/')
                        }
                    }
                    AsyncImage(
                        model = imageModel,
                        contentDescription = photo.filename,
                        imageLoader = coilImageLoader,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )

                    // Face bounding boxes overlay
                    if (showFaces) {
                        val faces = facesMap[photo.id]
                        if (!faces.isNullOrEmpty()) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cw = this.size.width
                                val ch = this.size.height
                                val pw = photo.width?.toFloat() ?: cw
                                val ph = photo.height?.toFloat() ?: ch
                                for (face in faces) {
                                    val left = ((face.bbox.x / pw) * cw).toFloat()
                                    val top = ((face.bbox.y / ph) * ch).toFloat()
                                    val right = (((face.bbox.x + face.bbox.w) / pw) * cw).toFloat()
                                    val bottom = (((face.bbox.y + face.bbox.h) / ph) * ch).toFloat()
                                    val isHighlight = face.person_id == personId
                                    drawRect(
                                        color = if (isHighlight) Color(0xFFFFC107).copy(alpha = 0.7f) else Color(0xFF00FF00).copy(alpha = 0.5f),
                                        topLeft = Offset(left, top),
                                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (isHighlight) 5f else 3f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Image Counter Overlay
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} of ${photos.size}",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            // Face toggle button
            IconButton(
                onClick = { showFaces = !showFaces },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    if (showFaces) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle Faces",
                    tint = if (showFaces) Color(0xFFFFC107) else Color.White
                )
            }

            // Photo counter
            Text(
                "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTransformGesturesCustom(
    onGesture: (centroid: androidx.compose.ui.geometry.Offset, pan: androidx.compose.ui.geometry.Offset, zoom: Float, rotation: Float) -> Unit,
    consumeEnabled: Boolean
) {
    awaitPointerEventScope {
        var rotation = 0f
        var zoom = 1f
        var pan = androidx.compose.ui.geometry.Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            var active = true
            rotation = 0f
            zoom = 1f
            pan = androidx.compose.ui.geometry.Offset.Zero
            pastTouchSlop = false
            
            do {
                val event = awaitPointerEvent()
                val canceled = event.changes.any { it.isConsumed }
                if (!canceled) {
                    val zoomChange = event.calculateZoom()
                    val rotationChange = event.calculateRotation()
                    val panChange = event.calculatePan()

                    if (!pastTouchSlop) {
                        zoom *= zoomChange
                        rotation += rotationChange
                        pan += panChange

                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                        val zoomMotion = java.lang.Math.abs(1 - zoom) * centroidSize
                        val panMotion = pan.getDistance()

                        if (zoomMotion > touchSlop || (consumeEnabled && panMotion > touchSlop) || event.changes.size > 1) {
                            pastTouchSlop = true
                        }
                    }

                    if (pastTouchSlop) {
                        val centroid = event.calculateCentroid(useCurrent = false)
                        if (zoomChange != 1f || panChange != androidx.compose.ui.geometry.Offset.Zero) {
                            onGesture(centroid, panChange, zoomChange, rotationChange)
                        }
                        if (consumeEnabled || event.changes.size > 1) {
                            event.changes.forEach {
                                if (it.positionChanged()) {
                                    it.consume()
                                }
                            }
                        }
                    }
                }
                active = event.changes.any { it.pressed }
            } while (active)
        }
    }
}

@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember(videoUrl) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            val mediaItem = androidx.media3.common.MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
