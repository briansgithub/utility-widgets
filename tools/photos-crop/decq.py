# decode axis image at the output centre: prints source position along the axis (sub-pixel)
import sys, math
from PIL import Image
axis=sys.argv[2]; W,H=3600,4800; P=128; L=W if axis=="x" else H
im=Image.open(sys.argv[1]).convert("RGB"); w,h=im.size; px=im.load()
r=3; s=[0,0,0]; n=0
for y in range(h//2-r,h//2+r+1):
    for x in range(w//2-r,w//2+r+1):
        p=px[x,y]; s=[s[i]+p[i] for i in range(3)]; n+=1
R,G,B=[v/n/255 for v in s]
coarse=R*(L-1)
def phases(v): # triangle value -> two phase candidates in [0,1)
    return [(v/2)%1,(1-v/2)%1]
# tri(p)=1-|2t-1| where t=p/P frac; G gives t, B gives t+1/4
best=None
for a in phases(G):
    for b in phases(B):
        d=abs(((b-0.25)-a+0.5)%1-0.5)
        if best is None or d<best[0]: best=(d,a,(b-0.25)%1)
d,a,b=best
# circular mean of the two estimates
ph=(math.atan2(math.sin(2*math.pi*a)+math.sin(2*math.pi*b), math.cos(2*math.pi*a)+math.cos(2*math.pi*b))/(2*math.pi))%1
k=round((coarse-ph*P)/P); pos=k*P+ph*P
print(f"size={w}x{h} pos={pos:.2f} coarse={coarse:.0f} agree={d*P:.2f}")
