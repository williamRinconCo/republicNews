package com.republicnews

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.republicnews.ui.theme.RepublicNewsTheme
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query


data class NewsArticle(
    val title: String?,
    val description: String?,
    val link: String?,
    val creator: List<String>?,
    @SerializedName("pubDate") val publishedAt: String?
)

data class NewsDataResponse(
    val status: String?,
    val results: List<NewsArticle>?
)


interface NewsDataApi {
    @GET("latest")
    suspend fun getLatestNews(
        @Query("apikey") apiKey: String,
        @Query("q") query: String
    ): NewsDataResponse
}


object RetrofitClient {
    private const val BASE_URL = "https://newsdata.io/api/1/"

    fun create(): NewsDataApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NewsDataApi::class.java)
    }
}


class MainActivity : ComponentActivity() {
    private val apiKey = "pub_d6978009f37d454fa1bb2849e1acba0f"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RepublicNewsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NewsScreen(apiKey = apiKey)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(apiKey: String) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var articles by remember { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var connectionType by remember { mutableStateOf("Desconocida") }
    var batteryLevel by remember { mutableIntStateOf(100) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current


    LaunchedEffect(Unit) {
        connectionType = getConnectionType(context)
        batteryLevel = getBatteryLevel(context)

        if (connectionType != "Sin conexión") {
            try {
                isLoading = true
                val api = RetrofitClient.create()
                val response = api.getLatestNews(apiKey, "Colombia")

                if (response.status == "success" && response.results != null) {
                    articles = when {
                        batteryLevel < 20 -> response.results.take(3)
                        connectionType == "Datos móviles" -> response.results.take(5)
                        else -> response.results.take(10)
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Error al obtener noticias iniciales: ${e.message}"
            } finally {
                isLoading = false
            }
        } else {
            errorMessage = "No hay conexión a internet."
        }
    }

    val scrollState = rememberScrollState()


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(20.dp))
        Text("📰 RepublicNews", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("William Rincón #6028113", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("Conexión: $connectionType", style = MaterialTheme.typography.bodySmall)
        Text("🔋 Batería: $batteryLevel%", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))


        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar noticias (ej. tecnología, deportes)") },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        query = TextFieldValue("")
                    }
                }
        )

        Spacer(Modifier.height(8.dp))


        Button(
            onClick = {

                focusManager.clearFocus()
                keyboardController?.hide()

                coroutineScope.launch {
                    connectionType = getConnectionType(context)
                    batteryLevel = getBatteryLevel(context)

                    if (connectionType == "Sin conexión") {
                        errorMessage = "No hay conexión a internet."
                        articles = emptyList()
                        return@launch
                    }

                    try {
                        isLoading = true
                        errorMessage = null
                        val api = RetrofitClient.create()
                        val response = api.getLatestNews(apiKey, query.text)

                        if (response.status == "success" && response.results != null) {
                            articles = when {
                                batteryLevel < 20 -> response.results.take(3)
                                connectionType == "Datos móviles" -> response.results.take(5)
                                else -> response.results.take(10)
                            }
                        } else {
                            errorMessage = "Error al obtener noticias (status=${response.status})"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error al obtener noticias: ${e.message}"
                        Log.e("WilliamNews", "Error: ${e.message}")
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Buscando..." else "Buscar noticias")
        }

        Spacer(Modifier.height(16.dp))


        when {
            errorMessage != null -> Text("⚠️ $errorMessage", color = MaterialTheme.colorScheme.error)
            isLoading -> CircularProgressIndicator()
            articles.isEmpty() -> Text("No hay noticias para mostrar.")
            else -> {
                articles.forEach { article ->
                    Text(article.title ?: "(Sin título)", style = MaterialTheme.typography.titleMedium)
                    article.description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    article.creator?.firstOrNull()?.let {
                        Text("✍️ $it", style = MaterialTheme.typography.labelSmall)
                    }
                    article.publishedAt?.let {
                        Text("🕒 $it", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}


fun getConnectionType(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return "Sin conexión"
    val capabilities = cm.getNetworkCapabilities(network) ?: return "Sin conexión"

    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
        else -> "Desconocida"
    }
}


fun getBatteryLevel(context: Context): Int {
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level != -1 && scale != -1) {
        (level * 100 / scale.toFloat()).toInt()
    } else 100
}
