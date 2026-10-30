"""Procedural audio synthesis for DEAD WORLD: APOCALYPSE (numpy -> WAV).
All sounds are synthesized from scratch - no external samples, no licensing issues.
Run: python tools/gen_audio.py -> assets/audio/*.wav
"""
import os, wave, struct
import numpy as np

OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "audio")
os.makedirs(OUT, exist_ok=True)
SR = 22050
rng = np.random.default_rng(42)


def save(name, samples, sr=SR):
    samples = np.clip(samples, -1.0, 1.0)
    pcm = (samples * 32767).astype(np.int16)
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes(pcm.tobytes())
    print("audio:", name, f"{len(samples)/sr:.2f}s")


def t(dur): return np.arange(int(SR * dur)) / SR
def env_exp(n, decay): return np.exp(-np.arange(n) / SR * decay)
def lowpass(x, a=0.12):
    y = np.zeros_like(x); acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc); y[i] = acc
    return y


def gunshot(name, power=1.0, body=90.0, dur=0.28):
    n = int(SR * dur)
    crack = rng.normal(0, 1, n) * env_exp(n, 90 * power)
    boom = np.sin(2 * np.pi * body * t(dur)) * env_exp(n, 26) * 0.9
    tail = lowpass(rng.normal(0, 0.5, n), 0.05) * env_exp(n, 11)
    save(name, (crack * 0.85 + boom + tail) * 0.9)


def shotgun():
    dur = 0.55; n = int(SR * dur)
    crack = rng.normal(0, 1, n) * env_exp(n, 55)
    boom = np.sin(2 * np.pi * 64 * t(dur)) * env_exp(n, 15)
    save("shoot_shotgun.wav", crack * 0.8 + boom + lowpass(rng.normal(0, .6, n), .04) * env_exp(n, 8))


def dry_fire():
    dur = .07; n = int(SR * dur)
    save("dry_fire.wav", rng.normal(0, .7, n) * env_exp(n, 160) * np.hanning(n) )


def reload_snd():
    dur = 0.8; n = int(SR * dur); x = np.zeros(n)
    for at, dur_c, amp in [(0.02, .05, .7), (0.22, .06, .9), (0.45, .05, .8), (0.62, .08, 1.0)]:
        i = int(at * SR); m = int(dur_c * SR)
        chunk = rng.normal(0, 1, m) * env_exp(m, 220)
        chunk = np.diff(chunk, prepend=0.0)  # brighten "click"
        x[i:i + m] += chunk * amp
    save("reload.wav", x * 0.8)


def swing():
    dur = .22; n = int(SR * dur); tt = t(dur)
    f = 900 - 2400 * tt
    x = lowpass(rng.normal(0, 1, n), 0.3) * np.sin(np.pi * tt / dur) ** 2
    save("melee_swing.wav", x * 0.7)


def hit_flesh(name="hit_flesh.wav", pitch=130):
    dur = .18; n = int(SR * dur)
    thump = np.sin(2 * np.pi * pitch * t(dur)) * env_exp(n, 32)
    wet = lowpass(rng.normal(0, 1, n), 0.2) * env_exp(n, 40)
    save(name, thump * .8 + wet * .6)


def zombie_voice(name, base=82, dur=1.1, scream=False):
    tt = t(dur); n = len(tt)
    wobble = 1 + 0.14 * np.sin(2 * np.pi * (5.3 if not scream else 11.0) * tt)
    phase = np.cumsum(2 * np.pi * base * wobble / SR)
    src = np.sign(np.sin(phase)) * 0.55 + 0.45 * np.sin(phase)  # harsh glottal-ish
    am = 0.75 + 0.25 * np.sin(2 * np.pi * 2.7 * tt)
    formant = lowpass(src, 0.045 if not scream else 0.11)
    breath = lowpass(rng.normal(0, 1, n), 0.08) * 0.35
    env = np.minimum(1, tt * 6) * np.exp(-np.maximum(0, tt - dur * 0.55) * 3.2)
    save(name, (formant * 2.2 + breath) * am * env * 0.75)


def zombie_attack_snd():
    zombie_voice("zombie_attack.wav", base=110, dur=0.55, scream=True)


def zombie_death_snd():
    dur = 1.0; tt = t(dur); n = len(tt)
    f = 120 * np.exp(-tt * 2.4) + 38
    phase = np.cumsum(2 * np.pi * f / SR)
    x = lowpass(np.sign(np.sin(phase)) * .5 + .5 * np.sin(phase), 0.05) * 2.0
    save("zombie_death.wav", x * env_exp(n, 2.8) * 0.8)


def player_hurt():
    dur = .3; tt = t(dur)
    x = np.sin(2 * np.pi * (210 - 320 * tt) * tt) * env_exp(int(SR*dur), 10)
    save("player_hurt.wav", x * .55 + lowpass(rng.normal(0, 1, len(tt)), .1) * .2 * env_exp(len(tt), 12))


def footstep(name, hard=False):
    dur = .13; n = int(SR * dur)
    x = lowpass(rng.normal(0, 1, n), 0.25 if hard else 0.1) * env_exp(n, 42)
    save(name, x * 0.5)


def pickup():
    dur = .16; tt = t(dur)
    x = np.sin(2 * np.pi * (520 + 1400 * tt) * tt) * np.hanning(len(tt))
    save("pickup.wav", x * .3)


def eat():
    dur = .7; n = int(SR * dur); x = np.zeros(n)
    for at in (0.05, 0.28, 0.5):
        i = int(at * SR); m = int(.12 * SR)
        x[i:i+m] += lowpass(rng.normal(0, 1, m), .15) * env_exp(m, 30)
    save("eat.wav", x * .8)


def drink():
    dur = .8; tt = t(dur); n = len(tt)
    f = 300 + 260 * np.sin(2 * np.pi * 1.6 * tt)
    x = np.sin(np.cumsum(2*np.pi*f/SR)) * .18 + lowpass(rng.normal(0, 1, n), .06) * .5
    save("drink.wav", x * np.hanning(n) * .8)


def door():
    dur = .6; tt = t(dur); n = len(tt)
    creak = np.sin(np.cumsum(2*np.pi*(180+420*tt+60*np.sin(2*np.pi*7*tt))/SR)) * .12
    thud = np.sin(2*np.pi*72*tt) * env_exp(n, 9) * .6
    save("door.wav", (creak + thud) * np.minimum(1, tt*8))


def ui_click():
    dur = .06; tt = t(dur)
    save("ui_click.wav", np.sin(2*np.pi*880*tt) * np.hanning(len(tt)) * .25)


def heartbeat():
    dur = 1.0; n = int(SR * dur); x = np.zeros(n)
    for at in (0.05, 0.32):
        i = int(at * SR); m = int(.14 * SR)
        x[i:i+m] += np.sin(2*np.pi*52*t(.14)) * env_exp(m, 24)
    save("heartbeat.wav", x * .9)


def wind():
    dur = 6.0; n = int(SR * dur); tt = t(dur)
    base = lowpass(rng.normal(0, 1, n), 0.012)
    gusts = 0.55 + 0.45 * np.sin(2*np.pi*0.13*tt) * np.sin(2*np.pi*0.041*tt + 1.7)
    x = base * 6 * gusts
    # loop-crossfade
    fade = int(SR * .5)
    x[:fade] = x[:fade] * np.linspace(0, 1, fade) + x[-fade:] * np.linspace(1, 0, fade)
    save("wind.wav", x * .5)


def rain():
    dur = 5.0; n = int(SR * dur)
    x = lowpass(rng.normal(0, 1, n), 0.35) * .5 + rng.normal(0, .12, n)
    fade = int(SR * .5)
    x[:fade] = x[:fade] * np.linspace(0, 1, fade) + x[-fade:] * np.linspace(1, 0, fade)
    save("rain.wav", x * .55)


def thunder():
    dur = 2.6; n = int(SR * dur); tt = t(dur)
    x = lowpass(rng.normal(0, 1, n), 0.02) * 8 * env_exp(n, 1.6) * np.minimum(1, tt * 14)
    save("thunder.wav", x * .8)


def engine_loop():
    dur = 1.0; tt = t(dur); n = len(tt)
    f0 = 55.0
    x = sum(a * np.sin(2*np.pi*f0*k*tt) for k, a in [(1, .5), (2, .3), (3, .18), (4, .1)])
    x += lowpass(rng.normal(0, 1, n), .2) * .12
    save("engine.wav", x * .7)


def car_hit():
    dur = .35; n = int(SR*dur); tt = t(dur)
    metal = sum(np.sin(2*np.pi*f*tt)*np.exp(-tt*d) for f, d in [(312, 18), (527, 24), (891, 30), (140, 12)])
    save("car_hit.wav", metal*.3 + rng.normal(0, 1, n)*env_exp(n, 40)*.5)


def ambient_music():
    """Dark ambient pad loop (minor drones + sparse bell)."""
    dur = 24.0; tt = t(dur); n = len(tt)
    x = np.zeros(n)
    for f, a in [(55.0, .30), (82.4, .18), (110.0, .14), (130.8, .08)]:  # A1 E2 A2 C3
        x += a * np.sin(2*np.pi*f*tt + 0.6*np.sin(2*np.pi*0.05*tt)) * (0.8 + 0.2*np.sin(2*np.pi*0.03*tt))
    for at, f in [(3.0, 440.0), (9.5, 329.6), (15.0, 523.2), (20.0, 392.0)]:
        i = int(at*SR); m = int(2.8*SR)
        seg = np.sin(2*np.pi*f*t(2.8)) * np.exp(-t(2.8)*2.2) * .06
        x[i:i+m] += seg
    x += lowpass(rng.normal(0, 1, n), .004) * 1.4  # air
    fade = int(SR*1.0)
    x[:fade] = x[:fade]*np.linspace(0, 1, fade) + x[-fade:]*np.linspace(1, 0, fade)
    save("music_ambient.wav", x * .55)


def levelup_like(name, freqs, dur=.5):
    tt = t(dur); x = np.zeros(len(tt))
    for i, f in enumerate(freqs):
        s = int(i * dur / len(freqs) * SR); m = len(tt) - s
        x[s:] += np.sin(2*np.pi*f*t(m/SR)) * np.exp(-t(m/SR)*6) * .22
    save(name, x)


def main():
    gunshot("shoot_pistol.wav", power=1.0, body=120, dur=0.22)
    gunshot("shoot_rifle.wav", power=1.25, body=95, dur=0.2)
    gunshot("shoot_sniper.wav", power=0.9, body=70, dur=0.5)
    shotgun(); dry_fire(); reload_snd(); swing(); hit_flesh()
    hit_flesh("melee_hit.wav", pitch=95)
    zombie_voice("zombie_groan1.wav", base=76, dur=1.3)
    zombie_voice("zombie_groan2.wav", base=94, dur=1.0)
    zombie_voice("zombie_scream.wav", base=210, dur=0.9, scream=True)
    zombie_attack_snd(); zombie_death_snd(); player_hurt()
    footstep("step_grass.wav"); footstep("step_hard.wav", hard=True)
    pickup(); eat(); drink(); door(); ui_click(); heartbeat()
    wind(); rain(); thunder(); engine_loop(); car_hit(); ambient_music()
    levelup_like("quest_done.wav", [392, 523, 659])
    levelup_like("death_sting.wav", [220, 174, 110], dur=1.2)
    print("AUDIO_DONE")


if __name__ == "__main__":
    main()
