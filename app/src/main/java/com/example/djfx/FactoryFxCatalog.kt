package com.example.djfx

object FactoryFxCatalog {
    data class Entry(
        val id: String,
        val name: String,
        val category: String,
        val assetPath: String,
        val source: String = "CC0-1.0",
        val sourceUrl: String? = null
    )

    private fun cc0(id: String, name: String, category: String, path: String) =
        Entry(id, name, category, path)

    private fun cc0Remote(id: String, name: String, category: String, url: String) =
        Entry(id, name, category, "", "CC0-1.0", url)

    private fun kenney(id: String, name: String, file: String) = cc0Remote(
        id, name, "DJ FX",
        "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/$file"
    )

    val entries: List<Entry> = listOf(
        // BANK A — DJ FX (professional Kenney Sci-Fi Sounds pack, CC0-1.0, streamed remotely)
        kenney("dj_laser_big", "Big Laser Blast", "laserLarge_000.ogg"),
        kenney("dj_laser_small1", "Laser Zap 1", "laserSmall_000.ogg"),
        kenney("dj_laser_small2", "Laser Zap 2", "laserSmall_001.ogg"),
        kenney("dj_scan", "Digital Scan", "computerNoise_000.ogg"),
        kenney("dj_force1", "Force Field Pulse", "forceField_000.ogg"),
        kenney("dj_force2", "Force Field Charge", "forceField_001.ogg"),
        kenney("dj_engine", "Engine Rev", "engineCircular_000.ogg"),
        kenney("dj_swoosh_open", "Door Swoosh Open", "doorOpen_000.ogg"),
        kenney("dj_swoosh_close", "Door Swoosh Close", "doorClose_000.ogg"),
        kenney("dj_crunch1", "Impact Crunch 1", "explosionCrunch_000.ogg"),
        kenney("dj_crunch2", "Impact Crunch 2", "explosionCrunch_001.ogg"),
        kenney("dj_metal_hit", "Metal Impact Hit", "impactMetal_000.ogg"),
        kenney("dj_subboom", "Sub Bass Boom", "lowFrequency_explosion_000.ogg"),

        // NEW BANK — ORIENTAL / ARABIC
        cc0Remote("or_doom01", "Darbuka Doom 01", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_01.flac"),
        cc0Remote("or_doom02", "Darbuka Doom 02", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_02.flac"),
        cc0Remote("or_doom03", "Darbuka Doom 03", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_03.flac"),
        cc0Remote("or_doom04", "Darbuka Doom 04", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_04.flac"),
        cc0Remote("or_doom05", "Darbuka Doom 05", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_05.flac"),
        cc0Remote("or_doom06", "Darbuka Doom 06", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_06.flac"),
        cc0Remote("or_doom07", "Darbuka Doom 07", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_07.flac"),
        cc0Remote("or_doom08", "Darbuka Doom 08", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_08.flac"),
        cc0Remote("or_doom09", "Darbuka Doom 09", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_09.flac"),
        cc0Remote("or_doom10", "Darbuka Doom 10", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_10.flac"),
        cc0Remote("or_doom11", "Darbuka Doom 11", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_11.flac"),
        cc0Remote("or_doom12", "Darbuka Doom 12", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_12.flac"),
        cc0Remote("or_bongo01", "Bongo 01", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Bongos/1_01.flac"),
        cc0Remote("or_bongo02", "Bongo 02", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Bongos/1_02.flac"),
        cc0Remote("or_bongo03", "Bongo 03", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Bongos/1_03.flac"),
        cc0Remote("or_bongo04", "Bongo 04", "شرقي", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Bongos/1_04.flac"),

        // NEW BANK — COMEDY / MEME-STYLE (PUBLIC DOMAIN / CC0)
        cc0Remote("co_boing", "Boing", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/boing%20cartoon.mp3"),
        cc0Remote("co_bruh", "Bruh", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/bruh.mp3"),
        cc0Remote("co_buzzer", "Buzzer", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/buzzer.mp3"),
        cc0Remote("co_confused", "Ehhh?", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/confused%20ehhh.mp3"),
        cc0Remote("co_crickets", "Bad Joke Crickets", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/crickets%20bad%20joke.mp3"),
        cc0Remote("co_evil", "Evil Laughter", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/evil%20laughter.mp3"),
        cc0Remote("co_fart_long", "Fart Long", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/fart%20long.mp3"),
        cc0Remote("co_fart_power", "Fart Powerful", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/fart%20powerful.mp3"),
        cc0Remote("co_fart_short", "Fart Short", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/fart%20short.mp3"),
        cc0Remote("co_fart_wet", "Fart Wet", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/fart%20wet.mp3"),
        cc0Remote("co_slide", "Cartoon Fall", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/flute%20slide%20cartoon%20falling.mp3"),
        cc0Remote("co_golf", "Golf Clap", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/golf%20clap.mp3"),
        cc0Remote("co_laugh_cute", "Cute Laugh", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/laughter%20cute.mp3"),
        cc0Remote("co_laugh_sitcom", "Sitcom Laugh", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/laughter%20sitcom%20audience%20crowd.mp3"),
        cc0Remote("co_quack", "Quack", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/quack%20duck.mp3"),
        cc0Remote("co_nope", "Nope", "كوميدي", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/nope.mp3"),

        // NEW BANK — VIRAL / TRENDS (PUBLIC DOMAIN / CC0)
        cc0Remote("tr_access", "Air Horn", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/hype%20air%20horn.mp3"),
        cc0Remote("tr_bye", "Bye Bye", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/bye%20bye.mp3"),
        cc0Remote("tr_bruh", "Bruh", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/bruh.mp3"),
        cc0Remote("tr_correct", "That's Correct", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/correct%20that's%20correct%20radio.mp3"),
        cc0Remote("tr_danger", "Danger", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/danger.mp3"),
        cc0Remote("tr_haters", "Haters Gonna Hate", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/haters%20gonna%20hate.mp3"),
        cc0Remote("tr_money", "Money", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/money%20cash%20register%20purchase.mp3"),
        cc0Remote("tr_nice", "Nice", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/nice%20mmm.mp3"),
        cc0Remote("tr_what", "What?", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/what%20short.mp3"),
        cc0Remote("tr_surprise", "What?!", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/what%20surprised.mp3"),
        cc0Remote("tr_win", "Winning Jingle", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/winning%20jingle.mp3"),
        cc0Remote("tr_yeah1", "Yeah Ohh Yeah", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/yeah%20ohh%20yeah.mp3"),
        cc0Remote("tr_yeah2", "Yeah Song", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/yeah%20song.mp3"),
        cc0Remote("tr_yeet", "Yeet", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/yeet.mp3"),
        cc0Remote("tr_wow", "Wow", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/wow.mp3"),
        cc0Remote("tr_fail", "Fail / Wah Wah", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/fail%20game%20over%20wah%20wah%20sad%20trombone.mp3")
    )
}
