import re
content = open("app/src/main/java/com/example/MainActivity.kt").read()

# find the last NavHost composable
navhost_end_index = content.rfind("        }\n    }\n}")

if navhost_end_index != -1:
    new_end = """        }
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
"""
    # Replace from navhost_end_index to the end of the file, keeping the comments
    comments = content[content.rfind("// KARAOKE"):]
    content = content[:navhost_end_index] + new_end + comments
    
open("app/src/main/java/com/example/MainActivity.kt", "w").write(content)
