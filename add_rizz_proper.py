with open("/app/applet/app/src/main/java/com/example/djfx/FactoryFxCatalog.kt", "r") as f:
    content = f.read()

new_bank = """        // NEW BANK — RIZZ ROBOT
        cc0Remote("rz_robot1", "Rizz Robot Pulse", "روبوت ريز", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/computerNoise_000.ogg"),
        cc0Remote("rz_robot2", "Rizz Robot Scan", "روبوت ريز", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/forceField_000.ogg"),
        cc0Remote("rz_robot3", "Rizz Robot Laser", "روبوت ريز", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/laserLarge_000.ogg"),
        cc0Remote("rz_robot4", "Rizz Robot Engine", "روبوت ريز", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/engineCircular_000.ogg"),
        cc0Remote("rz_robot5", "Rizz Robot Engage", "روبوت ريز", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/doorOpen_001.ogg"),
        cc0Remote("rz_robot6", "Rizz Robot Crunch", "روبوت ريز", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/explosionCrunch_000.ogg")
    )
}"""

content = content.replace("    )\n}", new_bank)

with open("/app/applet/app/src/main/java/com/example/djfx/FactoryFxCatalog.kt", "w") as f:
    f.write(content)
