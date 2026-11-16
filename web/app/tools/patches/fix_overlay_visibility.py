import re
content = open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt").read()

# Make the drawing conditional on actualTargetRect being valid
old_canvas = """        Canvas(modifier = Modifier.fillMaxSize()) {
            if (actualTargetRect != null && actualTargetRect.width > 0 && actualTargetRect.height > 0) {"""

new_canvas = """        val isTargetReady = actualTargetRect != null && actualTargetRect.width > 0 && actualTargetRect.height > 0
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isTargetReady) {"""

content = content.replace(old_canvas, new_canvas)

content = content.replace(
"""            } else {
                drawRect(color = Color.Black.copy(alpha = 0.30f))
            }
        }""",
"""            }
        }""")

content = content.replace(
"""        // Draw callout
        if (actualTargetRect != null && actualTargetRect.width > 0 && actualTargetRect.height > 0) {""",
"""        // Draw callout
        if (isTargetReady) {""")

content = content.replace(
"""        // Bottom Navigation Bar
        Row(""",
"""        // Bottom Navigation Bar
        if (isTargetReady) {
            Row(""")

content = content.replace(
"""                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    if (isLast) "GET STARTED" else "NEXT",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}""",
"""                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    if (isLast) "GET STARTED" else "NEXT",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        }
    }
}""")

open("app/src/main/java/com/example/tutorial/TutorialOverlay.kt", "w").write(content)
