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

    val entries: List<Entry> = listOf(
        // BANK A — DJ FX
        cc0("dj_bell", "Bell", "DJ FX", "factory_fx/dj/bell1.wav"),
        cc0("dj_cracker", "Cracker", "DJ FX", "factory_fx/dj/cracker1.wav"),
        cc0("dj_cracker_st", "Cracker Stereo", "DJ FX", "factory_fx/dj/cracker1v-stereo.wav"),
        cc0("dj_cracker_v", "Cracker Wide", "DJ FX", "factory_fx/dj/cracker1v.wav"),
        cc0("dj_cracker2", "Cracker 2", "DJ FX", "factory_fx/dj/cracker2.wav"),
        cc0("dj_metal", "Metal Hit", "DJ FX", "factory_fx/dj/metal1.wav"),
        cc0("dj_steel", "Steel Hit", "DJ FX", "factory_fx/dj/steel1.wav"),
        cc0("dj_switch", "Switch", "DJ FX", "factory_fx/dj/switch1.wav"),
        cc0("dj_laugh", "Crowd Laugh", "DJ FX", "factory_fx/dj/wahaha.wav"),
        cc0("dj_laser", "Laser", "DJ FX", "factory_fx/kenney/laserLarge_000.ogg"),
        cc0("dj_crunch1", "Crunch 1", "DJ FX", "factory_fx/kenney/explosionCrunch_001.ogg"),
        cc0("dj_crunch2", "Crunch 2", "DJ FX", "factory_fx/kenney/explosionCrunch_002.ogg"),
        cc0("dj_crunch3", "Crunch 3", "DJ FX", "factory_fx/kenney/explosionCrunch_003.ogg"),
        cc0("dj_force1", "Force Field", "DJ FX", "factory_fx/kenney/forceField_000.ogg"),
        cc0("dj_force2", "Force Field 2", "DJ FX", "factory_fx/kenney/forceField_001.ogg"),
        cc0("dj_computer", "Digital Computer", "DJ FX", "factory_fx/kenney/computerNoise_000.ogg"),

        // BANK B — DRUMS
        cc0("dr_k1", "Kick 1", "Drums", "factory_fx/drums/hard-kick-01.wav"),
        cc0("dr_k2", "Kick 2", "Drums", "factory_fx/drums/hard-kick-02.wav"),
        cc0("dr_k3", "Kick 3", "Drums", "factory_fx/drums/hard-kick-03.wav"),
        cc0("dr_808d", "808 Dist", "Drums", "factory_fx/drums/808-bass-dist.wav"),
        cc0("dr_808s", "808 Sub", "Drums", "factory_fx/drums/808-bass-sub.wav"),
        cc0("dr_s1", "Snare 1", "Drums", "factory_fx/drums/hard-snare-01.wav"),
        cc0("dr_s2", "Snare 2", "Drums", "factory_fx/drums/hard-snare-02.wav"),
        cc0("dr_s3", "Snare 3", "Drums", "factory_fx/drums/hard-snare-03.wav"),
        cc0("dr_c1", "Clap 1", "Drums", "factory_fx/drums/clap-01.wav"),
        cc0("dr_c2", "Clap 2", "Drums", "factory_fx/drums/cl.wav"),
        cc0("dr_h1", "Closed Hat", "Drums", "factory_fx/drums/hi-hat-closed-01.wav"),
        cc0("dr_h2", "Closed Hat 2", "Drums", "factory_fx/drums/ch.wav"),
        cc0("dr_oh", "Open Hat", "Drums", "factory_fx/drums/open-hat-01.wav"),
        cc0("dr_cb", "Cowbell", "Drums", "factory_fx/drums/perc-cowbell.wav"),
        cc0("dr_rim", "Rimshot", "Drums", "factory_fx/drums/perc-rimshot.wav"),
        cc0("dr_cym", "Cymbal", "Drums", "factory_fx/drums/fx-cymbal.wav"),

        // BANK C — ELECTRONIC / BOUNCE
        cc0("el_k1", "Bounce Kick 1", "Electronic", "factory_fx/electronic/bounce-kick-01.wav"),
        cc0("el_k2", "Bounce Kick 2", "Electronic", "factory_fx/electronic/bounce-kick-02.wav"),
        cc0("el_k3", "Bounce Kick 3", "Electronic", "factory_fx/electronic/bounce-kick-03.wav"),
        cc0("el_808l", "808 Long", "Electronic", "factory_fx/electronic/808-bass-long.wav"),
        cc0("el_808p", "808 Punch", "Electronic", "factory_fx/electronic/808-bass-punch.wav"),
        cc0("el_s1", "Bounce Snare 1", "Electronic", "factory_fx/electronic/bounce-snare-01.wav"),
        cc0("el_s2", "Bounce Snare 2", "Electronic", "factory_fx/electronic/bounce-snare-02.wav"),
        cc0("el_s3", "Bounce Snare 3", "Electronic", "factory_fx/electronic/bounce-snare-03.wav"),
        cc0("el_c1", "Bounce Clap", "Electronic", "factory_fx/electronic/clap-01.wav"),
        cc0("el_h1", "Bounce Hat", "Electronic", "factory_fx/electronic/hi-hat-closed-01.wav"),
        cc0("el_oh", "Bounce Open Hat", "Electronic", "factory_fx/electronic/open-hat-01.wav"),
        cc0("el_ht", "High Tom", "Electronic", "factory_fx/electronic/perc-high-tom.wav"),
        cc0("el_lt", "Low Tom", "Electronic", "factory_fx/electronic/perc-low-tom.wav"),
        cc0("el_808r", "808 Round", "Electronic", "factory_fx/electronic/808-round-long.wav"),
        cc0("el_fx", "Bounce Cymbal FX", "Electronic", "factory_fx/electronic/fx-cymbal.wav"),
        cc0("el_laser", "Sci-Fi Laser", "Electronic", "factory_fx/kenney/laserSmall_000.ogg"),

        // BANK D — PARTY / IMPACT
        cc0("pa_open1", "Door Open", "Party", "factory_fx/party/doorOpen_000.ogg"),
        cc0("pa_open2", "Door Open 2", "Party", "factory_fx/party/doorOpen_001.ogg"),
        cc0("pa_close1", "Door Close", "Party", "factory_fx/party/doorClose_000.ogg"),
        cc0("pa_close2", "Door Close 2", "Party", "factory_fx/party/doorClose_001.ogg"),
        cc0("pa_laser1", "Laser Small", "Party", "factory_fx/party/laserSmall_001.ogg"),
        cc0("pa_laser2", "Laser Small 2", "Party", "factory_fx/party/laserSmall_002.ogg"),
        cc0("pa_impact1", "Metal Impact", "Party", "factory_fx/party/impactMetal_000.ogg"),
        cc0("pa_impact2", "Metal Impact 2", "Party", "factory_fx/party/impactMetal_001.ogg"),
        cc0("pa_impact3", "Metal Impact 3", "Party", "factory_fx/party/impactMetal_002.ogg"),
        cc0("pa_crunch4", "Crunch 4", "Party", "factory_fx/party/explosionCrunch_004.ogg"),
        cc0("pa_lowboom", "Low Frequency Boom", "Party", "factory_fx/party/lowFrequency_explosion_000.ogg"),
        cc0("pa_engine", "Engine", "Party", "factory_fx/party/engineCircular_000.ogg"),
        cc0("pa_lightimpact", "Light Impact", "Party", "factory_fx/party/impactMetal_light_003.ogg"),
        cc0("pa_medimpact", "Medium Impact", "Party", "factory_fx/party/impactMetal_medium_002.ogg"),
        cc0("pa_steel", "Steel Celebration", "Party", "factory_fx/party/steel1.wav"),
        cc0("pa_laugh", "Laugh", "Party", "factory_fx/party/wahaha.wav"),

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
        cc0Remote("tr_access", "Access Granted", "تريندات", "https://raw.githubusercontent.com/jonjonsson/SoundMonster/main/Public%20domain/access%20granted.mp3"),
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
