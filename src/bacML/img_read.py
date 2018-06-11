from numpy import *

import numpy as np
import csv
import imageio
import time
import sys
import os
from scipy.misc import imread
from scipy.misc import imresize
import matplotlib.image as mpimg
from scipy.ndimage import filters
import urllib
from numpy import random

#####################################
patchDir = r'C:\Users\10250153\bacteria3\data\patch'  # path to the dir with annotated patches
patchDir = os.path.join(patchDir, '')
fname = os.path.join(patchDir, 'patch.log')
print(fname, end='\n\n')

# done = True
# if done:
#     sys.exit()

#####################################
# read
imgList = []
imgTag = []
imgLoc = []

f = open(fname, 'r')
reader = csv.reader(f)
for row in reader:
    if not ''.join(row).startswith("#"):
        imgPath = os.path.join(patchDir, row[0])
        im1 = (imread(imgPath)).astype(float32)
        print(row, '\n', imgPath, '\n', im1.shape, '\n', type(im1), end='\n\n')
#         # .reshape([])
f.close()

#####################################
#
