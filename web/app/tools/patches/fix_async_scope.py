import re
content = open("app/src/main/java/com/example/radio/RadioScreen.kt").read()

code_to_replace = """
                val parallelism = 10
                for (chunk in rawStations.chunked(parallelism)) {
                    chunk.map { station ->
                        async {
"""

new_code = """
                val parallelism = 10
                for (chunk in rawStations.chunked(parallelism)) {
                    kotlinx.coroutines.coroutineScope {
                        chunk.map { station ->
                            kotlinx.coroutines.async {
"""

content = content.replace(code_to_replace, new_code)
content = content.replace("}.forEach { it.await() }", "}.forEach { it.await() }\n                    }")

open("app/src/main/java/com/example/radio/RadioScreen.kt", "w").write(content)
