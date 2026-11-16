import re

content = open("app/src/main/java/com/example/MainActivity.kt").read()

import_tutorial = "import com.example.tutorial.*\nimport com.example.tutorial.TutorialOverlay\n"
if "import com.example.tutorial.*" not in content:
    content = content.replace("import com.example.model.AudioItem", import_tutorial + "import com.example.model.AudioItem")

main_app_start = """fun MainApp() {
    val context = LocalContext.current
    val navController = rememberNavController()

    var appVersion by remember { mutableStateOf("1.0") }
    LaunchedEffect(Unit) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appVersion = pInfo.versionName ?: "1.0"
            TutorialManager.init(context, appVersion)
        } catch (e: Exception) {}
    }
"""

content = re.sub(r'fun MainApp\(\) \{\n    val context = LocalContext\.current\n    val navController = rememberNavController\(\)\n', main_app_start, content)

# Inject TutorialOverlay inside the root component
scaffold_block = """    Scaffold(
        bottomBar = {"""
content = content.replace(scaffold_block, """    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {""")

end_scaffold = """            }
        }
    )
}"""
content = content.replace(end_scaffold, """            }
        }
    )
    TutorialOverlay(onNavigate = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    })
    }
}""")

open("app/src/main/java/com/example/MainActivity.kt", "w").write(content)
