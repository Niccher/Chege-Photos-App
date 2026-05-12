package com.niccher.prjphotos

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
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.niccher.prjphotos.models.Album as PhotoAlbum
import com.niccher.prjphotos.models.PhotoListResponse
import com.niccher.prjphotos.utils.SessionManager
import com.niccher.prjphotos.models.AuthResponse
import com.niccher.prjphotos.models.Photo
import com.niccher.prjphotos.network.ApiClient
import com.niccher.prjphotos.repository.PhotoRepository
import com.niccher.prjphotos.ui.theme.PrjPhotosTheme
import com.niccher.prjphotos.ui.theme.AppTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import retrofit2.Response
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.foundation.clickable
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

class MainActivity : FragmentActivity() {
    private lateinit var photoRepository: PhotoRepository
    
    // Global Theme State
    private var selectedTheme = mutableStateOf(com.niccher.prjphotos.ui.theme.AppTheme.DEFAULT)

    val pendingSharedFiles = androidx.compose.runtime.mutableStateListOf<java.io.File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoRepository = PhotoRepository(this)
        
        handleSharedContent(intent)
        
        createNotificationChannel()
        
        val sessionManager = SessionManager(this)
        selectedTheme.value = com.niccher.prjphotos.ui.theme.AppTheme.valueOf(sessionManager.getTheme())

        enableEdgeToEdge()
        setContent {
            PrjPhotosTheme(appTheme = selectedTheme.value) {
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

enum class Screen(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Sync("Sync", Icons.Default.CloudUpload),
    Gallery("Gallery", Icons.Default.Collections),
    Albums("Albums", Icons.Default.Album)
}

enum class SidebarItem(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Profile("Profile", Icons.Default.Person),
    Memories("Memories", Icons.Default.AutoAwesome),
    Favorites("Favorites", Icons.Default.Favorite),
    Archive("Archive", Icons.Default.Archive),
    Trash("Trash", Icons.Default.Delete),
    Explore("Explore", Icons.Default.Explore),
    Theme("Theme", Icons.Default.Palette)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: PhotoRepository, 
    pendingSharedFiles: MutableList<java.io.File> = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf() },
    themeState: MutableState<com.niccher.prjphotos.ui.theme.AppTheme>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val sharedPrefs = remember { context.getSharedPreferences("prj_photos_prefs", android.content.Context.MODE_PRIVATE) }
    
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
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                // Library Section
                Text("Library", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                listOf(SidebarItem.Explore, SidebarItem.Memories, SidebarItem.Favorites).forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.title) },
                        selected = currentScreen == item,
                        onClick = {
                            currentScreen = item
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Tools Section
                Text("Tools", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
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
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Account Section
                Text("Account", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                listOf(SidebarItem.Profile).forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.title) },
                        selected = currentScreen == item,
                        onClick = {
                            currentScreen = item
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
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
                    NavigationBar {
                        Screen.values().forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isLoggedIn) {
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
                } else {
                    when (currentScreen) {
                        SidebarItem.Profile -> ProfileScreen(sessionManager)
                        Screen.Sync -> SyncScreen(repository)
                        Screen.Gallery -> GalleryScreen(serverUrl, activeDownloads, downloadProgress)
                        Screen.Albums -> AlbumsScreen(serverUrl, activeDownloads, downloadProgress)
                        SidebarItem.Memories -> RemotePhotoListScreen(serverUrl, "Memories", activeDownloads, downloadProgress) { ApiClient.getPhotoService(it).getMemories() }
                        SidebarItem.Favorites -> RemotePhotoListScreen(serverUrl, "Favorites", activeDownloads, downloadProgress) { ApiClient.getPhotoService(it).getFavorites() }
                        SidebarItem.Archive -> RemotePhotoListScreen(serverUrl, "Archive", activeDownloads, downloadProgress) { ApiClient.getPhotoService(it).getArchived() }
                        SidebarItem.Trash -> RemotePhotoListScreen(serverUrl, "Trash", activeDownloads, downloadProgress) { ApiClient.getPhotoService(it).getTrash() }
                        SidebarItem.Explore -> RemotePhotoListScreen(serverUrl, "Explore", activeDownloads, downloadProgress) { ApiClient.getPhotoService(it).getExplore() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RemotePhotoListScreen(
    baseUrl: String, 
    title: String,
    activeDownloads: MutableMap<Long, String>,
    downloadProgress: MutableMap<String, Float>,
    fetchPhotos: suspend (Context) -> Response<PhotoListResponse>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var photos by remember { mutableStateOf(listOf<Photo>()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Search and Filter States
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<String?>(null) } // null = All, "jpg", "png", "mp4"
    
    // State for Fullscreen Carousel
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(title) {
        isLoading = true
        try {
            val response = fetchPhotos(context)
            if (response.isSuccessful) {
                photos = response.body()?.photos ?: emptyList()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error fetching $title: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false
        }
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
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                                .clickable { 
                                    // Find index in original photos list for carousel consistency if needed, 
                                    // but here we use index in filtered list for carousel
                                    selectedPhotoIndex = index 
                                }
                        ) {
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Login to Sync", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onUrlChange,
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = email, onValueChange = onEmailChange, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = onPasswordChange, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            scope.launch {
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
                            Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Login failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }) {
            Text("Login")
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
    var photos by remember { mutableStateOf(listOf<File>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        photos = repository.getLocalPhotos()
        repository.photosRefreshTrigger.collect {
            photos = repository.getLocalPhotos()
        }
    }

    var isSyncing by remember { mutableStateOf(false) }
    var syncOverallProgress by remember { mutableStateOf(0f) }
    var currentFileProgress by remember { mutableStateOf(0f) }
    var processedCount by remember { mutableStateOf(0) }
    var currentlySyncingFile by remember { mutableStateOf<File?>(null) }
    
    // State for Fullscreen Carousel
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }

    Column {
        Button(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            enabled = !isSyncing,
            onClick = {
                if (photos.isNotEmpty()) {
                    isSyncing = true
                    processedCount = 0
                    currentFileProgress = 0f
                    scope.launch {
                        for (photo in photos) {
                            currentlySyncingFile = photo
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
                        Toast.makeText(context, "Synced $processedCount out of ${photos.size} photos", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        ) {
            Text(if (isSyncing) "Syncing... ($processedCount/${photos.size})" else "Sync Now (${photos.size} local photos)")
        }

        if (isSyncing) {
            val overallProgress = if (photos.isNotEmpty()) (processedCount.toFloat() + currentFileProgress) / photos.size else 0f
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp)
        ) {
            itemsIndexed(photos) { index, photo ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clickable { selectedPhotoIndex = index }
                ) {
                    Column {
                        AsyncImage(
                            model = photo,
                            contentDescription = photo.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentScale = ContentScale.Crop
                        )
                        
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
                                    text = formatSize(photo.length()), 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            IconButton(
                                onClick = {
                                    Toast.makeText(context, "Uploading ${photo.name}...", Toast.LENGTH_SHORT).show()
                                    scope.launch {
                                        currentlySyncingFile = photo
                                        val success = repository.syncPhoto(photo) { progress ->
                                            currentFileProgress = progress
                                        }
                                        if (success) {
                                            sessionManager.updateLastUpload()
                                            Toast.makeText(context, "Uploaded ${photo.name}", Toast.LENGTH_SHORT).show()
                                        } else {
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

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val photo = photos[page]
                    AsyncImage(
                        model = photo,
                        contentDescription = photo.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Top-Right Controls (Upload + Close)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    val currentPhoto = photos[pagerState.currentPage]
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Uploading ${currentPhoto.name}...", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val success = repository.syncPhoto(currentPhoto)
                                if (success) {
                                    sessionManager.updateLastUpload()
                                    Toast.makeText(context, "Uploaded ${currentPhoto.name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Upload failed ${currentPhoto.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload", tint = Color.White)
                    }
                    IconButton(onClick = { selectedPhotoIndex = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
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
                    Text(text = "Size: ${formatSize(currentPhoto.length())}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(currentPhoto.lastModified()))
                    Text(text = "Date: $date", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun GalleryScreen(baseUrl: String, activeDownloads: MutableMap<Long, String>, downloadProgress: MutableMap<String, Float>) {
    RemotePhotoListScreen(baseUrl, "Gallery", activeDownloads, downloadProgress) { ApiClient.getPhotoService(it).getRemotePhotos() }
}

@Composable
fun AlbumsScreen(baseUrl: String, activeDownloads: MutableMap<Long, String>, downloadProgress: MutableMap<String, Float>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var albums by remember { mutableStateOf(listOf<PhotoAlbum>()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedAlbum by remember { mutableStateOf<PhotoAlbum?>(null) } // Detail state

    if (selectedAlbum != null) {
        // Show Album Details View
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedAlbum = null }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = selectedAlbum!!.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Reuse the RemotePhotoListScreen to display the album's photos using the new endpoint
            RemotePhotoListScreen(
                baseUrl = baseUrl,
                title = selectedAlbum!!.name,
                activeDownloads = activeDownloads,
                downloadProgress = downloadProgress,
                fetchPhotos = { ApiClient.getPhotoService(it).getAlbumPhotos(selectedAlbum!!.id ?: "") }
            )
        }
    } else {
        // Show Albums List View
        LaunchedEffect(Unit) {
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
                LazyColumn {
                    items(albums) { album ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedAlbum = album }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(album.name, style = MaterialTheme.typography.headlineSmall)
                                album.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                Text("${album.photo_count} photos", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
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
            .setTitle("Prj Photos: Downloading")
            .setDescription(photo.filename)
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "Prj Photos/" + photo.filename)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val sessionManager = com.niccher.prjphotos.utils.SessionManager(context)
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
    val builder = NotificationCompat.Builder(context, "file_transfer_channel")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(if (isFinished) "Prj Photos: Upload Done" else "Prj Photos: Uploading...")
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(!isFinished)
        .setOnlyAlertOnce(true)

    if (isFinished) {
        builder.setContentText("Successfully synced $total items to gallery")
            .setProgress(0, 0, false)
    } else {
        builder.setContentText("Syncing $current of $total items...")
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
                                for ((index, file) in files.withIndex()) {
                                    showUploadNotification(context, index + 1, files.size)
                                    val success = repository.syncPhoto(file) { progress ->
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
fun ProfileScreen(sessionManager: com.niccher.prjphotos.utils.SessionManager) {
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
        .setTitle("Biometric login for PrjPhotos")
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
