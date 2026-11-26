"""Compose offline music and mechanical/water effects, exported as Ogg Vorbis."""
from pathlib import Path
import subprocess
import numpy as np

OUT = Path(__file__).resolve().parents[1] / "app/src/main/assets"
RATE = 24000
TIME = 2*np.pi
rng = np.random.default_rng(7487)

def write(name, values):
    samples = (np.clip(values, -.95, .95)*32767).astype("<i2")
    subprocess.run(["ffmpeg","-loglevel","error","-y","-f","s16le","-ar",str(RATE),"-ac","1","-i","pipe:0","-c:a","libvorbis","-q:a","3",str(OUT/f"{name}.ogg")],input=samples.tobytes(),check=True)

def note(midi, length, decay):
    t = np.arange(int(length*RATE))/RATE
    freq = 440*2**((midi-69)/12)
    env = np.minimum(1,t/.032)*np.minimum(1,(length-t)/.090)*np.exp(-t*decay)
    return env*(.77*np.sin(TIME*freq*t)+.17*np.sin(TIME*freq*2*t)+.06*np.sin(TIME*freq*3.003*t))

def add(song, start, values, amp):
    idx = int(start*RATE) + np.arange(len(values))
    song[idx % len(song)] += values*amp

progressions = [[(60,64,67,71),(57,60,64,67),(53,57,60,64),(55,59,62,69)],
                [(62,65,69,72),(58,62,65,69),(53,57,60,65),(60,64,67,74)]]
for name, chords in zip(("menu_music","game_music"),progressions):
    track = np.zeros(int(32*RATE))
    for bar in range(8):
        notes = chords[bar % 4]
        for n in notes:
            add(track,bar*4,note(n-12,4.7,.46),.023)
        for step, index in enumerate([0,2,1,3,2,3,1,2]):
            add(track,bar*4+.5*step,note(notes[index]+12,1.45,3.8),.061 if step%2==0 else .035)
    write(name,track)
for name, pitches in {"click":[60],"rotate":[54,57],"bloom":[69,76,81],"hint":[72,76],"star_sound":[72,76,79],"win":[60,64,67,72,76]}.items():
    s = np.zeros(int(RATE*(.53+len(pitches)*.10)))
    for i, key in enumerate(pitches):
        add(s,i*.085,note(key,.50,10 if name in ("rotate","click") else 4),.34)
    write(name,s)
s = np.zeros(RATE*3)
for _ in range(80):
    add(s,rng.uniform(0,3),note(rng.uniform(47,105),.18,28),rng.uniform(.006,.024))
write("stream",s)
