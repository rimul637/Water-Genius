"""Render the original pipe sprites with cylindrical lighting and brass fittings."""
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

DEST = Path(__file__).resolve().parents[1] / "app/src/main/assets"
S = 320
y, x = np.mgrid[0:S, 0:S] / S
radius = .180
rng = np.random.default_rng(963)
texture = rng.normal(0, 1.5, (S, S))

def render(name, ends, cord, variant):
    distance = np.full(x.shape, 10.)
    signed = np.zeros_like(distance)
    for (px, py), (qx, qy) in zip(*[iter(cord)]*2, strict=True):
        dx, dy = qx-px, qy-py
        fraction = np.clip(((x-px)*dx + (y-py)*dy)/(dx*dx+dy*dy),0,1)
        d = np.hypot(x-px-dx*fraction, y-py-dy*fraction)
        sign = ((x-px)*dy - (y-py)*dx) >= 0
        signed = np.where(d < distance, np.where(sign, d, -d), signed)
        distance = np.minimum(distance, d)
    bevel = (distance < radius)
    inner = distance < .104
    t = np.clip(signed / radius, -1, 1)
    body = .28 + .63*np.sqrt(1-t*t)
    specular = .62*np.exp(-((t+.66)/.12)**2) + .32*np.exp(-((t-.73)/.095)**2)
    rgb = np.array([145,172,184]) * (body + specular)[:,:,None]
    stripe = .76 + .20*np.exp(-((t+.02)/.47)**2) + .53*np.exp(-((t+.39)/.095)**2)
    channel = np.array([18,55,73] if variant == "dry" else [6,168,235])
    rgb[inner] = (stripe[:,:,None]*channel)[inner]
    ridge = (distance > .106) & (distance < .114)
    rgb[ridge] *= 1.50
    edge = (distance > .169) & (distance < .175)
    rgb[edge] *= .69
    for end in ends:
        axis, offset = [(y,x-.5),(1-x,y-.5),(1-y,x-.5),(x,y-.5)][end]
        fit = (axis > .04) & (axis < .19) & (abs(offset) < .193)
        cylinder = np.sqrt(np.clip(1-(offset/.195)**2,0,1))
        shine = .38 + .57*cylinder + .45*np.exp(-((offset+.071)/.044)**2)
        lip = np.where((axis < .060)|(axis > .170),1.42,1.0)
        lip *= np.where((axis > .069) & (axis < .079), .77, 1.0)
        side = np.where(axis > .181,.63,1.0)
        rgb[fit] = ((shine*lip*side)[:,:,None]*[214,141,40])[fit]
        bevel |= fit
    rgb = np.uint8(np.clip(rgb + texture[:,:,None],0,255))
    shadow = Image.fromarray(np.uint8(bevel)*180).filter(ImageFilter.GaussianBlur(8))
    shadow_canvas = Image.new("L",(S,S))
    shadow_canvas.paste(shadow,(3,6))
    canvas = Image.new("RGBA",(S,S),(2,14,27))
    canvas.putalpha(shadow_canvas)
    obj = Image.fromarray(np.dstack((rgb,np.uint8(bevel)*255)))
    canvas.alpha_composite(obj)
    if name.startswith("valve"):
        d = ImageDraw.Draw(canvas)
        for bounds, fill in [((105,108,220,224),"#57311e"),((105,101,215,209),"#995c27"),((109,104,212,202),"#ffd580"),((118,111,205,192),"#a85830"),((122,112,202,190),"#e37537")]:
            d.ellipse(bounds, fill=fill)
        d.arc((125,115,196,182), 202, 314, fill="#ffcc8d", width=5)
        if name == "valve_open":
            d.rounded_rectangle((146,115,176,197), radius=10, fill="#873024", outline="#f8b066", width=4)
        else:
            d.rounded_rectangle((120,139,201,169), radius=10, fill="#822325", outline="#ffd48a", width=4)
        d.ellipse((152,143,170,161), fill="#ffdb8b", outline="#753923",width=3)
    canvas.resize((160,160),Image.Resampling.LANCZOS).save(DEST / f"{name}_{variant}.png",optimize=True)

def links(*nodes):
    return [point for pair in zip(nodes,nodes[1:]) for point in pair]

arc = [(1 - .5*np.cos(a), .5*np.sin(a)) for a in np.linspace(0,np.pi/2,21)]
vertical = links((.5,0),(.5,1))
for variant in ("dry","wet"):
    render("straight",[0,2],vertical,variant)
    render("elbow",[0,1],links(*arc),variant)
    render("tee",[0,1,2],vertical + links((.5,.5),(1,.5)),variant)
    render("cross",[0,1,2,3],vertical + links((0,.5),(1,.5)),variant)
    render("valve_open",[0,2],vertical,variant)
    render("valve_closed",[0,2],vertical,variant)
