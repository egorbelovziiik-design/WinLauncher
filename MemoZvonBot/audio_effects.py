"""Простые синтетические телефонные эффекты без внешних файлов."""
from pydub import AudioSegment
from pydub.generators import Sine, WhiteNoise


def add_call_effects(speech: AudioSegment) -> AudioSegment:
    """Добавить короткий гудок, негромкий шум и сигнал завершения."""
    speech = speech.set_channels(1).set_frame_rate(48000)
    noise = WhiteNoise().to_audio_segment(duration=len(speech)).set_frame_rate(48000).apply_gain(-45)
    intro = Sine(425).to_audio_segment(duration=350).apply_gain(-17)
    outro = Sine(700).to_audio_segment(duration=180).apply_gain(-20)
    silence = AudioSegment.silent(duration=150, frame_rate=48000)
    return intro + silence + speech.overlay(noise) + silence + outro
