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
import pandas as pd

#####################################
patchDir = r'C:\Users\10250153\bacteria3\data\patch'  # path to the dir with annotated patches
patchDir = os.path.join(patchDir, '')
fname = os.path.join(patchDir, 'patch.log')
print(fname, end='\n\n')

c = []#np.array([])
a = np.array([1,2,3])
c = vstack((c,a))
print(c)

b = np.array([4,5,6]) #https://stackoverflow.com/questions/22732589/concatenating-empty-array-in-numpy
c = vstack((c,b))
print(c)

done = True
if done:
    sys.exit()

#####################################
# read
trainX = []
trainY = []
trainXY = []




f = open(fname, 'r')
reader = csv.reader(f)
for row in reader:
    if not ''.join(row).startswith("#"):
        imgPath = os.path.join(patchDir, row[0])
        im1 = (imread(imgPath)).astype(float32)
        # print(im1.shape, '    ', im1.size)
        im2 = im1.reshape(1, im1.size) # concatenate to the array .reshape(1,N)
        # im3 = pd.DataFrame(data=im2)
        print(row, '\n', imgPath, '\n', im1.shape, '\n', type(im1), '\n', im2.shape, type(im2), end='\n\n')
f.close()

#####################################
#
