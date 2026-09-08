import re

with open("/app/applet/app/src/main/java/com/example/DJFxRackScreen.kt", "r") as f:
    content = f.read()

# Add imports
imports = """import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import android.net.Uri
import android.util.Log
import android.widget.Toast"""
content = content.replace("import android.content.Context", "import android.content.Context\n" + imports)

# Fix the header in DJFxRack
header_target = """            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "MODULAR FX RACK",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "On this deck: ${activePlugins.size} • real-time DSP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showLibraryDialog = true }) {
                    Text("Effects Library", fontWeight = FontWeight.Bold)
                }
            }"""

header_replacement = """            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "FX RACK",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${activePlugins.size} Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = { showLibraryDialog = true },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Library", style = MaterialTheme.typography.labelSmall)
                }
            }"""

content = content.replace(header_target, header_replacement)

# Fix the grid
grid_target = """                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = true
                ) {
                    val displayPlugins = allPlugins.filter { activePlugins.contains(it.id) }
                    items(displayPlugins) { effect ->
                        EffectTile(
                            name = effect.displayName,
                            isActive = deck.isEffectActive(effect.id),
                            onClick = { deck.toggleEffect(effect.id) }
                        )
                    }
                }"""

grid_replacement = """                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val displayPlugins = allPlugins.filter { activePlugins.contains(it.id) }
                    displayPlugins.forEach { effect ->
                        EffectTile(
                            name = effect.displayName,
                            isActive = deck.isEffectActive(effect.id),
                            onClick = { deck.toggleEffect(effect.id) },
                            modifier = Modifier.height(45.dp).weight(1f, fill = false)
                        )
                    }
                }"""

content = content.replace(grid_target, grid_replacement)

# Update EffectTile to use the passed modifier without overwriting width if we want dynamic, wait, EffectTile has hardcoded width.
tile_target = """        modifier = modifier.height(45.dp).width(110.dp),"""
tile_replacement = """        modifier = modifier.height(45.dp).widthIn(min=90.dp),"""
content = content.replace(tile_target, tile_replacement)

with open("/app/applet/app/src/main/java/com/example/DJFxRackScreen.kt", "w") as f:
    f.write(content)
