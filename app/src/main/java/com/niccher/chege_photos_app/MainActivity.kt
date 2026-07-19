package com.niccher.chege_photos_app

import com.niccher.chege_photos_app.R
import android.Manifest
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.niccher.chege_photos_app.models.Album as PhotoAlbum
import com.niccher.chege_photos_app.models.PhotoListResponse
import com.niccher.chege_photos_app.utils.SessionManager
import com.niccher.chege_photos_app.models.AuthResponse
import com.niccher.chege_photos_app.models.Photo
import com.niccher.chege_photos_app.network.ApiClient
import com.niccher.chege_photos_app.repository.PhotoRepository
import com.niccher.chege_photos_app.ui.theme.ChegePhotosTheme
import com.niccher.chege_photos_app.ui.theme.AppTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

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

        enableEdgeToEdge()
        setContent {
            ChegePhotosTheme(appTheme = selectedTheme.value) {
                MainScreen(photoRepository, pendingSharedFiles, selectedTheme)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleSharedContent(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "File Transfers"
            val descriptionText = "Notifications for photo uploads and downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("file_transfer_channel", name, importance).apply {
                description = descriptionText
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
    About("About", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: PhotoRepository, 
    pendingSharedFiles: MutableList<java.io.File> = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf() },
    themeState: MutableState<com.niccher.chege_photos_app.ui.theme.AppTheme>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val sharedPrefs = remember { context.getSharedPreferences("chege_photos_prefs", android.content.Context.MODE_PRIVATE) }
    
    var serverUrl by remember { mutableStateOf(sharedPrefs.getString("server_url", "https://photos.chegecache.co.ke/") ?: "") }
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
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
    val scope = rememberCoroutineScope()
    
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
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "App Icon",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
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
                    listOf(SidebarItem.Archive, SidebarItem.Trash, SidebarItem.Theme).forEach { item ->
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
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (isLoggedIn) {
                    CenterAlignedTopAppBar(
                        title = { Text(if (currentScreen is Screen) (currentScreen as Screen).title else (currentScreen as SidebarItem).title) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showLogoutDialog = true }) {
                                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
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
                            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 4.dp, start = 16.dp, end = 16.dp),
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
                            Text(
                                text = "v$appVersion",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
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
                            serverUrl = it
                            sharedPrefs.edit().putString("server_url", it).apply()
                            ApiClient.updateBaseUrl(it, context)
                        },
                        email = email,
                        onEmailChange = { email = it },
                        password = password,
                        onPasswordChange = { password = it },
                        onLogin = {
                            isLoggedIn = true
                        }
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
                        SidebarItem.ServerConfig -> ServerConfigScreen(
                            currentUrl = serverUrl,
                            onUrlSaved = { newUrl ->
                                serverUrl = newUrl
                                sharedPrefs.edit().putString("server_url", newUrl).apply()
                                ApiClient.updateBaseUrl(newUrl, context)
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
    var photos by remember { mutableStateOf(listOf<Photo>()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Search and Filter States
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<String?>(null) } // null = All, "jpg", "png", "mp4"
    
    // State for Fullscreen Carousel
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedPhotos = remember { mutableStateListOf<Photo>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(title) {
        isLoading = true
        photos = if (fetchPhotos != null) {
            try {
                val response = fetchPhotos(context)
                if (response.isSuccessful) response.body()?.photos ?: emptyList() else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            repository.getRemotePhotos()
        }
        onPhotosLoaded?.invoke(photos.size)
        isLoading = false
    }

    val filteredPhotos = remember(photos, searchQuery, selectedType) {
        photos.filter { photo ->
            val matchesQuery = photo.filename.contains(searchQuery, ignoreCase = true)
            val matchesType = when (selectedType) {
                null -> true
                "jpg" -> photo.filename.endsWith(".jpg", ignoreCase = true) || photo.filename.endsWith(".jpeg", ignoreCase = true)
                "png" -> photo.filename.endsWith(".png", ignoreCase = true)
                "mp4" -> photo.filename.endsWith(".mp4", ignoreCase = true) || photo.filename.endsWith(".mov", ignoreCase = true)
                else -> true
            }
            matchesQuery && matchesType
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSelectionMode) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { 
                            isSelectionMode = false
                            selectedPhotos.clear()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                        Text(text = "${selectedPhotos.size} selected", style = MaterialTheme.typography.titleMedium)
                    }
                    Row {
                        IconButton(
                            enabled = selectedPhotos.isNotEmpty(),
                            onClick = { 
                                selectedPhotos.forEach { photo ->
                                    scope.launch {
                                        downloadRemotePhoto(context, baseUrl, photo)?.let { id ->
                                            activeDownloads[id] = photo.path
                                            downloadProgress[photo.path] = 0f
                                        }
                                    }
                                }
                                isSelectionMode = false
                                selectedPhotos.clear()
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download All")
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
                                    // Re-fetch photos
                                    isLoading = true
                                    photos = if (fetchPhotos != null) {
                                        try {
                                            val response = fetchPhotos(context)
                                            if (response.isSuccessful) response.body()?.photos ?: emptyList() else emptyList()
                                        } catch (e: Exception) { emptyList() }
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
                        if (title == "Trash") {
                            IconButton(
                                enabled = selectedPhotos.isNotEmpty(),
                                onClick = { 
                                    val items = selectedPhotos.toList()
                                    isSelectionMode = false
                                    selectedPhotos.clear()
                                    scope.launch {
                                        items.forEach { photo ->
                                            repository.restorePhoto(photo.id ?: "")
                                        }
                                        // Re-fetch photos
                                        isLoading = true
                                        photos = if (fetchPhotos != null) {
                                            try {
                                                val response = fetchPhotos(context)
                                                if (response.isSuccessful) response.body()?.photos ?: emptyList() else emptyList()
                                            } catch (e: Exception) { emptyList() }
                                        } else {
                                            repository.getRemotePhotos()
                                        }
                                        isLoading = false
                                        Toast.makeText(context, "Restored items", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.RestoreFromTrash, contentDescription = "Restore All")
                            }
                        }
                    }
                }
            }
        } else {
            // Search and Filter UI
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search photos...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val types = listOf(null, "jpg", "png", "mp4")
                    items(types) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type?.uppercase() ?: "All") },
                            leadingIcon = if (selectedType == type) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }
        }

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
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    itemsIndexed(filteredPhotos) { index, photo ->
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
                                Column {
                                AsyncImage(
                                    model = baseUrl.trimEnd('/') + "/" + (photo.thumbnail_path?.trimStart('/') ?: ""),
                                    contentDescription = photo.filename,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp),
                                    contentScale = ContentScale.Crop
                                )
                                
                                downloadProgress[photo.path]?.let { progress ->
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = photo.filename.take(20) + if (photo.filename.length > 20) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1
                                        )
                                        val sizeBytes = photo.size?.toLongOrNull() ?: 0L
                                        Text(
                                            text = formatSize(sizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                    IconButton(
                                        onClick = { 
                                            val id = downloadRemotePhoto(context, baseUrl, photo)
                                            if (id != null) {
                                                activeDownloads[id] = photo.path
                                                downloadProgress[photo.path] = 0f
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = "Download")
                                    }
                                }
                                
                                }
                                
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
                        }
                    }
                }
            }
        }
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
            ) {
                val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { filteredPhotos.size })
                var showInfoSheet by remember { mutableStateOf(false) }

                // Reset states when the user swipes to a different page
                LaunchedEffect(pagerState.currentPage) {
                    showInfoSheet = false
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
                        AsyncImage(
                            model = baseUrl.trimEnd('/') + "/" + photo.path.trimStart('/'),
                            contentDescription = photo.filename,
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
                        
                        downloadProgress[photo.path]?.let { progress ->
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 80.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Top-Right Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    val currentPhoto = filteredPhotos[pagerState.currentPage]
                    IconButton(
                        onClick = { 
                            val id = downloadRemotePhoto(context, baseUrl, currentPhoto)
                            if (id != null) {
                                activeDownloads[id] = currentPhoto.path
                                downloadProgress[currentPhoto.path] = 0f
                            }
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                    }
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                    }
                    IconButton(onClick = { selectedPhotoIndex = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Info Overlay
                val currentPhoto = filteredPhotos[pagerState.currentPage]
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp)
                ) {
                    Text(text = currentPhoto.filename, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    val sizeBytes = currentPhoto.size?.toLongOrNull() ?: 0L
                    Text(text = "Size: ${formatSize(sizeBytes)}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    if (currentPhoto.width != null && currentPhoto.height != null) {
                        Text(text = "Dimensions: ${currentPhoto.width} x ${currentPhoto.height}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (showInfoSheet) {
                    PhotoDetailsBottomSheet(
                        photo = currentPhoto,
                        onDismiss = { showInfoSheet = false }
                    )
                }
            }
        }
    }
}
}


@Composable
fun LoginScreen(
    serverUrl: String,
    onUrlChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Gradient hero background ─────────────────────────────────────
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
            // ── Branding area ────────────────────────────────────────────
            Spacer(modifier = Modifier.height(56.dp))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "App icon",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
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
            Spacer(modifier = Modifier.height(40.dp))

            // ── Login card ───────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enter your credentials to continue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Server URL (first) ───────────────────────────────
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAdvanced = !showAdvanced }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Server Settings",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            if (showAdvanced) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                OutlinedTextField(
                                    value = serverUrl,
                                    onValueChange = onUrlChange,
                                    label = { Text("Server URL") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Cloud,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    placeholder = { Text("e.g. 192.168.1.50:2283 or https://photos.example.com") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Next
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Email field
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            onEmailChange(it)
                            errorMessage = null
                        },
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            onPasswordChange(it)
                            errorMessage = null
                        },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible)
                            androidx.compose.ui.text.input.VisualTransformation.None
                        else
                            androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        )
                    )


                    // Error message
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Login button
                    Button(
                        onClick = {
                            if (email.isBlank()) { errorMessage = "Email is required"; return@Button }
                            if (password.isBlank()) { errorMessage = "Password is required"; return@Button }
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    val response = ApiClient.getPhotoService(context).login(email, password)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Signing in…")
                        } else {
                            Icon(
                                Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Sign In",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Footer hint
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
fun DashboardHeader(title: String, onLogout: () -> Unit) {
    // This is now replaced by TopAppBar in Scaffold
}

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

    var isSyncing by remember { mutableStateOf(false) }
    var currentFileProgress by remember { mutableStateOf(0f) }
    var processedCount by remember { mutableStateOf(0) }
    var currentlySyncingFile by remember { mutableStateOf<com.niccher.chege_photos_app.repository.LocalPhoto?>(null) }
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var selectMode by remember { mutableStateOf(false) }
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
    var showLogsDialog by remember { mutableStateOf(false) }

    val targetPhotos = if (selectMode && selectedIndices.isNotEmpty()) {
        selectedIndices.sorted().map { photos[it] }
    } else {
        photos
    }

    Column {
        // ── Top action bar ─────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = !isSyncing && photos.isNotEmpty(),
                onClick = {
                    isSyncing = true
                    processedCount = 0
                    currentFileProgress = 0f
                    val batch = targetPhotos
                    scope.launch {
                        showUploadNotification(context, 0, batch.size)
                        for ((index, photo) in batch.withIndex()) {
                            currentlySyncingFile = photo
                            showUploadNotification(context, index + 1, batch.size)
                            val success = repository.syncPhoto(photo) { progress ->
                                currentFileProgress = progress
                            }
                            if (success) {
                                processedCount++
                                sessionManager.updateLastUpload()
                            }
                            currentFileProgress = 0f
                            currentlySyncingFile = null
                        }
                        isSyncing = false
                        showUploadNotification(context, processedCount, batch.size, isFinished = true)
                        val label = if (selectMode) "selected" else "local"
                        Toast.makeText(context, "Synced $processedCount out of ${batch.size} $label photos", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                val label = if (selectMode && selectedIndices.isNotEmpty()) "Upload Selected (${selectedIndices.size})"
                            else if (isSyncing) "Syncing... ($processedCount/${targetPhotos.size})"
                            else "Sync Now (${photos.size} local)"
                Text(label)
            }

            OutlinedButton(
                onClick = {
                    selectMode = !selectMode
                    if (!selectMode) selectedIndices = emptySet()
                },
                enabled = !isSyncing
            ) {
                Text(if (selectMode) "Cancel" else "Select")
            }

            OutlinedButton(onClick = { showLogsDialog = true }) {
                Icon(Icons.Default.List, contentDescription = "Logs", modifier = Modifier.size(18.dp))
            }
        }

        // ── Progress ──────────────────────────────────────────────
        if (isSyncing) {
            val overallProgress = if (targetPhotos.isNotEmpty()) (processedCount.toFloat() + currentFileProgress) / targetPhotos.size else 0f
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                LinearProgressIndicator(
                    progress = overallProgress,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Text(
                    text = "Overall Progress: ${(overallProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ── Photo grid ────────────────────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp)
        ) {
            itemsIndexed(photos) { index, photo ->
                val isSelected = index in selectedIndices
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clickable {
                            if (selectMode) {
                                selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
                            } else {
                                selectedPhotoIndex = index
                            }
                        },
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

                        // Checkbox overlay in select mode
                        if (selectMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .size(28.dp)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        selectedIndices = if (isSelected) selectedIndices - index else selectedIndices + index
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // Per-item Sync Progress
                    if (currentlySyncingFile == photo || isSyncing && photo in photos.take(processedCount)) {
                        val progress = if (currentlySyncingFile == photo) currentFileProgress else 1f
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (progress < 1f) MaterialTheme.colorScheme.primary else Color.Green,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }

                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = photo.name.take(20) + if (photo.name.length > 20) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                            Text(
                                text = formatSize(photo.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        if (!selectMode) {
                            IconButton(
                                onClick = {
                                    showUploadNotification(context, 1, 1)
                                    scope.launch {
                                        currentlySyncingFile = photo
                                        val success = repository.syncPhoto(photo) { progress ->
                                            currentFileProgress = progress
                                        }
                                        if (success) {
                                            sessionManager.updateLastUpload()
                                            showUploadNotification(context, 1, 1, isFinished = true)
                                            Toast.makeText(context, "Uploaded ${photo.name}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            showUploadNotification(context, 1, 1, isFinished = true)
                                            Toast.makeText(context, "Upload failed ${photo.name}", Toast.LENGTH_SHORT).show()
                                        }
                                        currentlySyncingFile = null
                                        currentFileProgress = 0f
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = "Upload")
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Logs Dialog ───────────────────────────────────────────────
    if (showLogsDialog) {
        LogsDialog(onDismiss = { showLogsDialog = false })
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
            ) {
                val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { photos.size })
                var showInfoSheet by remember { mutableStateOf(false) }

                // Reset state on swipe
                LaunchedEffect(pagerState.currentPage) {
                    showInfoSheet = false
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val photo = photos[page]
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Top-Right Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    val currentPhoto = photos[pagerState.currentPage]
                    IconButton(
                        onClick = {
                            showUploadNotification(context, 1, 1)
                            scope.launch {
                                val success = repository.syncPhoto(currentPhoto)
                                if (success) {
                                    sessionManager.updateLastUpload()
                                    showUploadNotification(context, 1, 1, isFinished = true)
                                    Toast.makeText(context, "Uploaded ${currentPhoto.name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    showUploadNotification(context, 1, 1, isFinished = true)
                                    Toast.makeText(context, "Upload failed ${currentPhoto.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload", tint = Color.White)
                    }
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                    }
                    IconButton(onClick = { selectedPhotoIndex = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                if (showInfoSheet) {
                    val currentPhoto = photos[pagerState.currentPage]
                    val tempPhoto = Photo(
                        filename = currentPhoto.name,
                        path = currentPhoto.file?.absolutePath ?: currentPhoto.uri.toString(),
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
                        onDismiss = { showInfoSheet = false }
                    )
                }


                // Info Overlay
                val currentPhoto = photos[pagerState.currentPage]
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp)
                ) {
                    Text(text = currentPhoto.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Size: ${formatSize(currentPhoto.size)}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    val date = currentPhoto.file?.let { file ->
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
                    } ?: "Unknown"
                    Text(text = "Date: $date", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
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
@OptIn(ExperimentalFoundationApi::class)
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

    val fetchAlbums: () -> Unit = {
        isLoading = true
        scope.launch {
            try {
                val response = ApiClient.getPhotoService(context).getAlbums()
                if (response.isSuccessful) {
                    albums = response.body()?.albums ?: emptyList()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error fetching albums: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
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
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "${selectedAlbum!!.name} (${albumPhotosCount ?: selectedAlbum!!.photo_count ?: "0"} photos)",
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
            fetchAlbums()
        }

        Box(modifier = Modifier.fillMaxSize()) {
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
                                        Text("${album.photo_count} photos", style = MaterialTheme.typography.labelSmall)
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

private fun downloadRemotePhoto(context: Context, baseUrl: String, photo: Photo): Long? {
    try {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val url = baseUrl.trimEnd('/') + "/" + photo.path.trimStart('/')
        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            .setTitle("Chege Photos: Downloading")
            .setDescription(photo.filename)
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "Chege Photos/" + photo.filename)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val sessionManager = com.niccher.chege_photos_app.utils.SessionManager(context)
        sessionManager.getAuthToken()?.let { token ->
            request.addRequestHeader("Authorization", "Bearer $token")
        }

        val id = downloadManager.enqueue(request)
        Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
        return id
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        return null
    }
}

fun showUploadNotification(context: Context, current: Int, total: Int, isFinished: Boolean = false) {
    val notificationManager = NotificationManagerCompat.from(context)
    val percentage = if (total > 0) (current * 100) / total else 0
    val builder = NotificationCompat.Builder(context, "file_transfer_channel")
        .setSmallIcon(R.drawable.ic_app_icon)
        .setContentTitle("Chege Photos")
        .setSubText(if (isFinished) "Sync Complete" else "Syncing...")
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(!isFinished)
        .setOnlyAlertOnce(true)

    if (isFinished) {
        builder.setContentText("Successfully synced $total items to gallery")
            .setProgress(0, 0, false)
    } else {
        builder.setContentText("Syncing $current of $total items ($percentage%)")
            .setProgress(total, current, false)
    }

    try {
        notificationManager.notify(1001, builder.build())
    } catch (e: SecurityException) {
        // Handle missing permission gracefully
    }
}

@Composable
fun SharedUploadDialog(
    files: MutableList<File>,
    repository: PhotoRepository
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadedCount by remember { mutableStateOf(0) }
    var currentFileProgress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    
    val localPhotos = remember(files.size) {
        files.map { file ->
            com.niccher.chege_photos_app.repository.LocalPhoto(
                uri = android.net.Uri.fromFile(file),
                file = file,
                name = file.name,
                size = file.length()
            )
        }.toMutableList()
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
                    
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        enabled = !isUploading,
                        onClick = {
                            isUploading = true
                            uploadedCount = 0
                            showUploadNotification(context, 0, files.size)
                            scope.launch {
                                for ((index, localPhoto) in localPhotos.withIndex()) {
                                    showUploadNotification(context, index + 1, files.size)
                                    val success = repository.syncPhoto(localPhoto) { progress ->
                                        currentFileProgress = progress
                                    }
                                    if (success) {
                                        uploadedCount++
                                        sessionManager.updateLastUpload()
                                    }
                                }
                                showUploadNotification(context, uploadedCount, files.size, isFinished = true)
                                Toast.makeText(context, "Uploaded $uploadedCount items", Toast.LENGTH_SHORT).show()
                                files.forEach { it.delete() }
                                files.clear()
                            }
                        }
                    ) {
                        Text(if (isUploading) "Uploading... ($uploadedCount/${files.size})" else "Upload All (${files.size} items)")
                    }
                    
                    if (isUploading) {
                        val overallProgress = if (files.isNotEmpty()) (uploadedCount.toFloat() + currentFileProgress) / files.size else 0f
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            LinearProgressIndicator(
                                progress = overallProgress,
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        itemsIndexed(files) { _, file ->
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
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
    val camera: String = "Smartphone Camera",
    val iso: String = "100",
    val shutter: String = "1/120s",
    val aperture: String = "f/1.8",
    val latitude: Double? = null,
    val longitude: Double? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailsBottomSheet(
    photo: Photo,
    localFile: java.io.File? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    
    val exifData = remember(photo, localFile) {
        if (localFile != null && localFile.exists()) {
            try {
                val exif = ExifInterface(localFile.absolutePath)
                val latLong = FloatArray(2)
                val hasLatLong = exif.getLatLong(latLong)
                
                ExifInfo(
                    camera = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "Smartphone Camera",
                    iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS) ?: "100",
                    shutter = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { "${it}s" } ?: "1/120s",
                    aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" } ?: "f/1.8",
                    latitude = if (hasLatLong) latLong[0].toDouble() else null,
                    longitude = if (hasLatLong) latLong[1].toDouble() else null
                )
            } catch (e: Exception) {
                ExifInfo()
            }
        } else {
            ExifInfo(
                latitude = photo.latitude?.toDoubleOrNull(),
                longitude = photo.longitude?.toDoubleOrNull()
            )
        }
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
                MetadataRow(Icons.Default.Label, "Format", photo.mime_type ?: "Unknown")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // EXIF Section
            MetadataSection(title = "Camera EXIF") {
                MetadataRow(Icons.Default.CameraAlt, "Camera", exifData.camera)
                MetadataRow(Icons.Default.Iso, "ISO", exifData.iso)
                MetadataRow(Icons.Default.ShutterSpeed, "Shutter", exifData.shutter)
                MetadataRow(Icons.Default.Camera, "Aperture", exifData.aperture)
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
fun LogsDialog(onDismiss: () -> Unit) {
    val logs = remember { com.niccher.chege_photos_app.utils.LogBuffer.getLogs() }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        while (true) {
            delay(1000)
            refresh++
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Upload Logs", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { com.niccher.chege_photos_app.utils.LogBuffer.clear() }) {
                            Text("Clear")
                        }
                        Button(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                val currentLogs = remember(refresh) { com.niccher.chege_photos_app.utils.LogBuffer.getLogs() }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(currentLogs) { _, line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp, horizontal = 4.dp)
                        )
                    }
                    if (currentLogs.isEmpty()) {
                        item {
                            Text("No logs yet. Try uploading a photo.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        contentPadding = PaddingValues(16.dp),
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
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
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
                        text = "Chege Photos is a premium photo management application designed for high-performance syncing and elegant viewing. It allows you to organize your memories into beautiful albums and access them securely across your devices.",
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
                        "Albums" to "Create, rename, and manage collections of your favorite moments."
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
                    listOf("Jetpack Compose", "Retrofit", "Coil", "OkHttp", "Material 3", "Coroutines", "Biometrics").forEach { lib ->
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
                text = "© 2024 Niccher. All rights reserved.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(80.dp)) // Extra space for bottom bar
        }
    }
}
