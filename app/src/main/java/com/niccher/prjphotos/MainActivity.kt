package com.niccher.prjphotos

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.niccher.prjphotos.models.Album as PhotoAlbum
import com.niccher.prjphotos.models.PhotoListResponse
import com.niccher.prjphotos.utils.SessionManager
import com.niccher.prjphotos.models.AuthResponse
import com.niccher.prjphotos.models.Photo
import com.niccher.prjphotos.network.ApiClient
import com.niccher.prjphotos.repository.PhotoRepository
import com.niccher.prjphotos.ui.theme.PrjPhotosTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import retrofit2.Response
import java.io.File

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background

class MainActivity : ComponentActivity() {
    private lateinit var photoRepository: PhotoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoRepository = PhotoRepository(this)
        enableEdgeToEdge()
        setContent {
            PrjPhotosTheme {
                MainScreen(photoRepository)
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
    Memories("Memories", Icons.Default.AutoAwesome),
    Favorites("Favorites", Icons.Default.Favorite),
    Archive("Archive", Icons.Default.Archive),
    Trash("Trash", Icons.Default.Delete),
    Explore("Explore", Icons.Default.Explore)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repository: PhotoRepository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val sharedPrefs = remember { context.getSharedPreferences("prj_photos_prefs", android.content.Context.MODE_PRIVATE) }
    
    var serverUrl by remember { mutableStateOf(sharedPrefs.getString("server_url", "https://photos.chegecache.co.ke/") ?: "") }
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    var currentScreen by remember { mutableStateOf<Any>(Screen.Sync) }
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        ApiClient.updateBaseUrl(serverUrl, context)
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isLoggedIn,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Management", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                SidebarItem.values().forEach { item ->
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
                            IconButton(onClick = {
                                sessionManager.clearSession()
                                isLoggedIn = false
                            }) {
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

@OptIn(ExperimentalFoundationApi::class)
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

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photos found in $title")
            }
        } else {
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
                                model = baseUrl.trimEnd('/') + "/" + (photo.thumbnail_path?.trimStart('/') ?: ""),
                                contentDescription = photo.filename,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                contentScale = ContentScale.Crop
                            )
                            
                            // Download Progress Overlay for individual item
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
                            
                            // Remove the hacky global progress check
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Image Carousel Dialog
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
                val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { photos.size })

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val photo = photos[page]
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            // Use full path for the full-screen view instead of thumbnail
                            model = baseUrl.trimEnd('/') + "/" + photo.path.trimStart('/'),
                            contentDescription = photo.filename,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        
                        // Fullscreen Download Progress
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

                // Top-Right Controls (Download + Close)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    val currentPhoto = photos[pagerState.currentPage]
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
                val currentPhoto = photos[pagerState.currentPage]
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
                    if (currentPhoto.taken_at != null) {
                        Text(text = "Date: ${currentPhoto.taken_at}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
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
                                sessionManager.saveUserProfile(user.id, user.email)
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
    var photos by remember { mutableStateOf(listOf<File>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        photos = repository.getLocalPhotos()
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
            .setTitle(photo.filename)
            .setDescription("Downloading photo...")
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