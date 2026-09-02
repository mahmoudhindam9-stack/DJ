package com.example.onlinemusic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnlineMusicScreen(viewModel: OnlineMusicViewModel) {
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Online Music", style = MaterialTheme.typography.headlineMedium)
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search songs, artists or albums") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        
        Button(onClick = { viewModel.search(searchQuery) }, modifier = Modifier.fillMaxWidth()) {
            Text("Search")
        }
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(viewModel.tracks) { track ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(text = track.title, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
