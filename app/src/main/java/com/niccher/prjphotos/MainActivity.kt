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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.niccher.prjphotos.utils.SessionManager
import com.niccher.prjphotos.models.AuthResponse
import com.niccher.prjphotos.network.ApiClient
import com.niccher.prjphotos.repository.PhotoRepository
import com.niccher.prjphotos.ui.theme.PrjPhotosTheme
import kotlinx.coroutines.launch
import java.io.File

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

@Composable
fun MainScreen(repository: PhotoRepository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val sharedPrefs = remember { context.getSharedPreferences("prj_photos_prefs", android.content.Context.MODE_PRIVATE) }
    
    var serverUrl by remember { mutableStateOf(sharedPrefs.getString("server_url", "https://photos.chegecache.co.ke/") ?: "") }
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    var photos by remember { mutableStateOf(listOf<File>()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            photos = repository.getLocalPhotos()
        }
    }

    LaunchedEffect(Unit) {
        // Initialize ApiClient with persisted URL
        ApiClient.updateBaseUrl(serverUrl, context)
        
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isLoggedIn) {
                Text("Login to Sync", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { 
                        serverUrl = it
                        sharedPrefs.edit().putString("server_url", it).apply()
                        ApiClient.updateBaseUrl(it, context)
                    },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                
                Spacer(modifier = Modifier.height(16.dp))
                val scope = rememberCoroutineScope()
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
                                    isLoggedIn = true
                                    Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Login failed: ${response.body()?.messageText ?: response.message()}", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Login")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
                    IconButton(onClick = {
                        sessionManager.clearSession()
                        isLoggedIn = false
                    }) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = { /* Implement full sync */ }) {
                    Text("Sync Now (${photos.size} local photos)")
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn {
                    items(photos) { photo ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(photo.name, modifier = Modifier.weight(1f))
                                val scope = rememberCoroutineScope()
                                Button(onClick = {
                                    scope.launch {
                                        val success = repository.syncPhoto(photo)
                                        if (success) {
                                            Toast.makeText(context, "Uploaded ${photo.name}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Upload failed ${photo.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Text("Upload")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}