import numpy as np
from skimage.io import imsave

# image = np.array([109, 232, 173, 55,  35, 144, 43, 124, 185, 234, 127, 246], dtype=np.uint8)

height = 240
width = 120

b = np.zeros((height, width), dtype=np.uint8)

imsave('imsave_w=%s_h=%s.tif' % (width, height), b)
print("done")
