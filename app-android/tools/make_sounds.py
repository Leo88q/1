#!/usr/bin/env python3
"""Синтез звуков Амперграда (ресурсы res/raw).

Запуск:  python3 app-android/tools/make_sounds.py     (нужен numpy)
Готовые WAV кладутся в app-android/app/src/main/res/raw/.

Канон звука (docs/05 §7): гул трансформатора фоном, «смачный» щелчок реле на
поворот, «вдох» при подключении узла, «выдох» при пломбе, короткий сигнал
подсказки. Всё синтезируется здесь, внешних сэмплов в проекте нет — значит нет
и вопросов по лицензиям на аудио.

Формат: 22050 Гц, моно, 16 бит PCM — хватает для сухих механических звуков и
держит APK лёгким.
"""
import math
import os
import wave

import numpy as np

RATE = 22050
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "raw")

rng = np.random.default_rng(20261012)  # фиксированный seed: звуки воспроизводимы


def envelope(length, attack, decay, curve=2.0):
    """Простая AD-огибающая: щелчки железные, хвосты короткие."""
    t = np.arange(length) / RATE
    env = np.ones(length)
    a = max(1, int(attack * RATE))
    d = max(1, int(decay * RATE))
    env[:a] = np.linspace(0, 1, a)
    tail = np.linspace(0, 1, min(d, length - a)) ** 0.5
    env[a : a + len(tail)] = 1 - tail**1.0
    env[a + len(tail) :] = 0.0
    return env * (1.0 / (1.0 + curve * t))


def noise(length):
    return rng.normal(0, 1, length)


def lowpass(x, alpha):
    """Однополюсный ФНЧ: срезает цифровую «иглу», оставляет металл."""
    out = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha * (v - acc)
        out[i] = acc
    return out


def bandpass(x, low_alpha, high_alpha):
    return x - lowpass(lowpass(x, low_alpha), high_alpha)


def to_int16(x):
    x = np.asarray(x, dtype=np.float64)
    peak = np.max(np.abs(x)) or 1.0
    x = x / peak * 0.86
    return (x * 32767).astype(np.int16)


def write(name, samples):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as fh:
        fh.setnchannels(1)
        fh.setsampwidth(2)
        fh.setframerate(RATE)
        fh.writeframes(to_int16(samples).tobytes())
    print(f"{name}: {len(samples) / RATE:.2f} с, {os.path.getsize(path) // 1024} КБ")


def relay_click():
    """Щелчок реле: короткий металлический удар + звон контакта."""
    dur = 0.16
    n = int(dur * RATE)
    t = np.arange(n) / RATE

    # Удар: шумовой всплеск, прошедший через резонанс корпуса.
    body = bandpass(noise(n), 0.35, 0.08) * np.exp(-t * 70)

    # Контакт: два резонанса (1100 и 2650 Гц) с быстрым затуханием.
    ring = (
        np.sin(2 * math.pi * 1100 * t) * np.exp(-t * 55) * 0.5
        + np.sin(2 * math.pi * 2650 * t) * np.exp(-t * 80) * 0.28
    )
    click = (body * 0.9 + ring) * envelope(n, 0.0008, 0.15)
    return click


def connect_inhale():
    """«Вдох» узла: поднимающийся шум с воздушной полосой."""
    dur = 0.55
    n = int(dur * RATE)
    t = np.arange(n) / RATE
    rise = np.clip(t / dur, 0, 1) ** 1.4
    air = bandpass(noise(n), 0.25, 0.05) * rise
    # Тон, ползущий вверх на кварту — «напряжение пошло».
    glide = np.sin(2 * math.pi * (160 + 240 * rise) * t) * 0.35 * rise**2
    return (air * 0.85 + glide) * envelope(n, 0.05, 0.6)


def seal_exhale():
    """«Выдох» пломбы: падение тона, уходящий шум, мягкий подъём гармоник."""
    dur = 1.35
    n = int(dur * RATE)
    t = np.arange(n) / RATE
    fall = np.clip(1 - t / dur, 0, 1) ** 1.2
    tone = (
        np.sin(2 * math.pi * 320 * t)
        + 0.45 * np.sin(2 * math.pi * 480 * t)
        + 0.22 * np.sin(2 * math.pi * 640 * t)
    ) * fall**1.6
    breath = bandpass(noise(n), 0.2, 0.04) * fall**2
    # Второй, тихий отзвук через 0.35 с — «квартал ответил».
    echo = np.zeros(n)
    shift = int(0.35 * RATE)
    echo[shift:] = tone[: n - shift] * 0.28
    return (tone * 0.5 + breath * 0.6 + echo) * envelope(n, 0.02, 1.3)


def hint_beep():
    """Подсказка Клеммы: служебный двухтональный сигнал (регламент, не игра)."""
    parts = []
    for freq, dur in ((880, 0.12), (0.0, 0.06), (1174, 0.22)):
        n = int(dur * RATE)
        t = np.arange(n) / RATE
        if freq == 0:
            parts.append(np.zeros(n))
            continue
        # Ступенчатый, чуть расстроенный тон — «диспетчерская» подача.
        sig = 0.7 * np.sin(2 * math.pi * freq * t) + 0.25 * np.sin(2 * math.pi * (freq * 1.004) * t)
        parts.append(sig * envelope(n, 0.008, dur))
    return np.concatenate(parts)


def array_hum(seconds=6.0):
    """Фон: гул массива. 50 Гц с гармониками, лёгкое биение и дыхание по громкости.
    Длительность кратна периодам всех составляющих, поэтому петля бесшовная;
    6 секунд достаточно, чтобы дыхание не читалось как повтор."""
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    hum = np.zeros(n)
    for freq, amp in ((50, 0.5), (100, 0.22), (150, 0.12), (200, 0.07), (303, 0.04)):
        hum += amp * np.sin(2 * math.pi * freq * t)
    # Биение: две близкие частоты дают «живой» металл.
    hum += 0.12 * np.sin(2 * math.pi * 50.7 * t)
    breathe = 0.75 + 0.25 * np.sin(2 * math.pi * (1 / seconds) * t - math.pi / 2)
    hiss = lowpass(noise(n), 0.02) * 0.35
    return (hum * breathe + hiss) * 0.8


def main():
    write("sfx_relay_click.wav", relay_click())
    write("sfx_connect.wav", connect_inhale())
    write("sfx_seal.wav", seal_exhale())
    write("sfx_hint.wav", hint_beep())
    write("amb_array_hum.wav", array_hum())


if __name__ == "__main__":
    main()
