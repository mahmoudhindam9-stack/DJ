#!/bin/bash
curl -s "https://api.github.com/repos/kael-melo/Sound-Effects/git/trees/master?recursive=1" | grep ".wav"
