from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/example/MainActivity.kt'
MARKER = '// DJ_SAMPLER_CC0_V1'


def main() -> None:
    text = MAIN.read_text(encoding='utf-8')
    if MARKER in text:
        print('Professional CC0 sampler already mounted')
        return

    start_marker = '        // Live Sound Effects & Instruments Soundboard\n'
    end_marker = '\n    }\n}\n\n@Composable\nfun DJDeckItem('
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit('legacy sampler soundboard block not found')
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit('DJMixerScreen end marker not found')

    replacement = '''        // DJ_SAMPLER_CC0_V1
        // The former generated/legacy sound buttons are removed completely.
        // This is the only sampler surface shown in the DJ page.
        ProfessionalSamplerBoard()
'''
    text = text[:start] + replacement + text[end:]
    MAIN.write_text(text, encoding='utf-8')
    print('Legacy sampler soundboard removed; professional CC0 sampler mounted')


if __name__ == '__main__':
    main()
