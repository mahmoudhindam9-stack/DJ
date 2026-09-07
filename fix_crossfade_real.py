with open('/tmp/audio.txt', 'r') as f:
    text = f.read()

# Let's see if there is playNext() early
print(text.find("exoPlayer.play()"))
