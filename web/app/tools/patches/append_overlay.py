import re
content = open("app/src/main/java/com/example/MainActivity.kt").read()

if "TutorialOverlay(onNavigate" not in content:
    content = content.replace(
        "        }\n    }\n}\n// KARAOKE",
        """        }
    }
    
    TutorialOverlay(onNavigate = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    })
    }
}
// KARAOKE"""
    )
    
open("app/src/main/java/com/example/MainActivity.kt", "w").write(content)
