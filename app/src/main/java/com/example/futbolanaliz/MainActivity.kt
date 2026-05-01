package com.example.futbolanaliz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LiveFootballApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveFootballApp() {
    var teamName by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<MatchUiModel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "⚽ Canlı Futbol Skorları",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = teamName,
            onValueChange = { teamName = it },
            label = { Text("Takım adı gir (ör: Fenerbahçe, Beşiktaş...)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                scope.launch {
                    loading = true
                    errorMessage = null
                    matches = fetchLiveScores(teamName.trim())
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && teamName.isNotBlank()
        ) {
            Text("Canlı Skorları Getir")
        }

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error)
            }
            matches.isEmpty() && teamName.isNotBlank() -> {
                Text("Bu takım için şu anda canlı maç bulunmuyor veya veri alınamadı.")
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(matches) { match ->
                        MatchCard(match)
                    }
                }
            }
        }
    }
}

@Composable
fun MatchCard(match: MatchUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (match.isLive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = match.league,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = match.minute,
                    fontWeight = FontWeight.Bold,
                    color = if (match.isLive) Color.Red else Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = match.homeTeam,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "${match.homeScore} - ${match.awayScore}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = match.awayTeam,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

// Data Model
data class MatchUiModel(
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int,
    val awayScore: Int,
    val minute: String,
    val league: String,
    val isLive: Boolean
)

// API Fonksiyonu
suspend fun fetchLiveScores(teamFilter: String): List<MatchUiModel> {
    if (teamFilter.isBlank()) return emptyList()

    return withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://v3.football.api-sports.io/fixtures?live=all"

            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("x-apisports-key", BuildConfig.API_FOOTBALL_KEY)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 15000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext emptyList()
            }

            val response = connection.inputStream.bufferedReader().readText()
            val jsonObject = JSONObject(response)
            val responseArray = jsonObject.getJSONArray("response")

            val matchList = mutableListOf<MatchUiModel>()

            for (i in 0 until responseArray.length()) {
                val item = responseArray.getJSONObject(i)
                val fixture = item.getJSONObject("fixture")
                val teams = item.getJSONObject("teams")
                val goals = item.getJSONObject("goals")
                val leagueObj = item.getJSONObject("league")

                val homeName = teams.getJSONObject("home").getString("name")
                val awayName = teams.getJSONObject("away").getString("name")

                if (!homeName.contains(teamFilter, ignoreCase = true) &&
                    !awayName.contains(teamFilter, ignoreCase = true)) {
                    continue
                }

                val homeScore = goals.optInt("home", 0)
                val awayScore = goals.optInt("away", 0)

                val elapsed = fixture.optInt("elapsed", 0)
                val statusShort = fixture.getJSONObject("status").getString("short")
                val minute = if (statusShort == "FT") "FT" else "${elapsed}'"

                val isLive = statusShort in listOf("1H", "HT", "2H", "ET", "BT", "P")

                matchList.add(
                    MatchUiModel(
                        homeTeam = homeName,
                        awayTeam = awayName,
                        homeScore = homeScore,
                        awayScore = awayScore,
                        minute = minute,
                        league = leagueObj.getString("name"),
                        isLive = isLive
                    )
                )
            }
            matchList

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
