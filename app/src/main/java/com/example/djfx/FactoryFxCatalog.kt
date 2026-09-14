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

    private fun localAsset(id: String, name: String, category: String, filename: String): Entry {
        return Entry(
            id = id,
            name = name,
            category = category,
            assetPath = "asset:///factory_fx/$filename",
            source = "CC0-1.0"
        )
    }

    val entries: List<Entry> = listOf(
        // BANK A — DJ FX (professional Kenney Sci-Fi Sounds pack, CC0-1.0)
        localAsset("dj_laser_big", "Big Laser Blast", "DJ FX", "laserLarge_000.ogg"),
        localAsset("dj_laser_small1", "Laser Zap 1", "DJ FX", "laserSmall_000.ogg"),
        localAsset("dj_laser_small2", "Laser Zap 2", "DJ FX", "laserSmall_001.ogg"),
        localAsset("dj_scan", "Digital Scan", "DJ FX", "computerNoise_000.ogg"),
        localAsset("dj_force1", "Force Field Pulse", "DJ FX", "forceField_000.ogg"),
        localAsset("dj_force2", "Force Field Charge", "DJ FX", "forceField_001.ogg"),
        localAsset("dj_engine", "Engine Rev", "DJ FX", "engineCircular_000.ogg"),
        localAsset("dj_swoosh_open", "Door Swoosh Open", "DJ FX", "doorOpen_000.ogg"),
        localAsset("dj_swoosh_close", "Door Swoosh Close", "DJ FX", "doorClose_000.ogg"),
        localAsset("dj_crunch1", "Impact Crunch 1", "DJ FX", "explosionCrunch_000.ogg"),
        localAsset("dj_crunch2", "Impact Crunch 2", "DJ FX", "explosionCrunch_001.ogg"),
        localAsset("dj_metal_hit", "Metal Impact Hit", "DJ FX", "impactMetal_000.ogg"),
        localAsset("dj_subboom", "Sub Bass Boom", "DJ FX", "lowFrequency_explosion_000.ogg"),

        // NEW BANK — COMEDY / MEME-STYLE (PUBLIC DOMAIN / CC0)
        localAsset("co_boing", "Boing", "Comedy", "boing cartoon.mp3"),
        localAsset("co_bruh", "Bruh", "Comedy", "bruh.mp3"),
        localAsset("co_buzzer", "Buzzer", "Comedy", "buzzer.mp3"),
        localAsset("co_confused", "Ehhh?", "Comedy", "confused ehhh.mp3"),
        localAsset("co_crickets", "Bad Joke Crickets", "Comedy", "crickets bad joke.mp3"),
        localAsset("co_evil", "Evil Laughter", "Comedy", "evil laughter.mp3"),
        localAsset("co_fart_long", "Fart Long", "Comedy", "fart long.mp3"),
        localAsset("co_fart_power", "Fart Powerful", "Comedy", "fart powerful.mp3"),
        localAsset("co_fart_short", "Fart Short", "Comedy", "fart short.mp3"),
        localAsset("co_fart_wet", "Fart Wet", "Comedy", "fart wet.mp3"),
        localAsset("co_slide", "Cartoon Fall", "Comedy", "flute slide cartoon falling.mp3"),
        localAsset("co_golf", "Golf Clap", "Comedy", "golf clap.mp3"),
        localAsset("co_laugh_cute", "Cute Laugh", "Comedy", "laughter cute.mp3"),
        localAsset("co_laugh_sitcom", "Sitcom Laugh", "Comedy", "laughter sitcom audience crowd.mp3"),
        localAsset("co_quack", "Quack", "Comedy", "quack duck.mp3"),
        localAsset("co_nope", "Nope", "Comedy", "nope.mp3"),

        // NEW BANK — VIRAL / TRENDS (PUBLIC DOMAIN / CC0)
        localAsset("tr_access", "Air Horn", "Trends", "hype air horn.mp3"),
        localAsset("tr_bye", "Bye Bye", "Trends", "bye bye.mp3"),
        localAsset("tr_bruh", "Bruh", "Trends", "bruh.mp3"),
        localAsset("tr_correct", "That's Correct", "Trends", "correct that's correct radio.mp3"),
        localAsset("tr_danger", "Danger", "Trends", "danger.mp3"),
        localAsset("tr_haters", "Haters Gonna Hate", "Trends", "haters gonna hate.mp3"),
        localAsset("tr_money", "Money", "Trends", "money cash register purchase.mp3"),
        localAsset("tr_nice", "Nice", "Trends", "nice mmm.mp3"),
        localAsset("tr_what", "What?", "Trends", "what short.mp3"),
        localAsset("tr_surprise", "What?!", "Trends", "what surprised.mp3"),
        localAsset("tr_win", "Winning Jingle", "Trends", "winning jingle.mp3"),
        localAsset("tr_yeah1", "Yeah Ohh Yeah", "Trends", "yeah ohh yeah.mp3"),
        localAsset("tr_yeah2", "Yeah Song", "Trends", "yeah song.mp3"),
        localAsset("tr_yeet", "Yeet", "Trends", "yeet.mp3"),
        localAsset("tr_wow", "Wow", "Trends", "wow.mp3"),
        localAsset("tr_fail", "Fail / Wah Wah", "Trends", "fail game over wah wah sad trombone.mp3")
    )
}
