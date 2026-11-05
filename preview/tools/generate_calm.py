"""Original procedural sound textures; CC0. No sampled recordings or music."""
import numpy as np, wave, pathlib
root=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/calm'
rng=np.random.default_rng(1705);rate=22050;n=rate*16;t=np.arange(n)/rate
noise=rng.normal(0,1,n)
f=np.fft.rfftfreq(n,1/rate);spectrum=np.fft.rfft(noise)
def filtered(cut):return np.fft.irfft(spectrum/(1+(f/cut)**2),n)
low=filtered(350);smooth=filtered(1500)
textures={'rain':smooth*(.7+.12*np.sin(2*np.pi*t/8)), 'ocean':low*(.5+.45*np.sin(2*np.pi*t/16)**2),'wind':filtered(180)*(.7+.3*np.sin(2*np.pi*t/16)), 'white':noise*.22,'ambient':.12*np.sin(2*np.pi*110*t)+.07*np.sin(2*np.pi*165*t)+filtered(90)*.2,'fireplace':filtered(600)*.5}
clicks=np.zeros(n)
for point in rng.integers(0,n-1500,100):clicks[point:point+1200]+=rng.normal(0,.3,1200)*np.exp(-np.arange(1200)/150)
textures['fireplace']+=clicks
# 100 ms cyclic crossfade avoids a click at loop boundary.
for name,x in textures.items():
 fade=2205;blend=np.linspace(0,1,fade);x[:fade]=x[:fade]*blend+x[-fade:]*(1-blend);x=x[:-fade];x=x/max(1,np.max(np.abs(x)))*.65
 with wave.open(str(root/(name+'.wav')),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes((x*32767).astype('<i2').tobytes())
(root/'LICENSE.txt').write_text('Original procedural NovaOS sound textures generated with generate_calm.py. CC0 1.0 Universal. No third-party recordings or music.\n')
