# axis test image: R = coarse position, G/B = quadrature triangle waves of the same axis (period 128 px)
import sys
from PIL import Image
axis=sys.argv[2]; W,H=3600,4800; P=128
def tri(p): t=(p%P)/P; return 1-abs(2*t-1)
L=W if axis=="x" else H
row=[(round(p*255/(L-1)), round(255*tri(p)), round(255*tri(p+P/4))) for p in range(L)]
im=Image.new("RGB",(W,H)); px=im.load()
if axis=="x":
    line=Image.new("RGB",(W,1)); line.putdata(row); im=line.resize((W,H),Image.NEAREST)
else:
    col=Image.new("RGB",(1,H)); col.putdata(row); im=col.resize((W,H),Image.NEAREST)
im.save(sys.argv[1])
