from numpy import *

import numpy as np
import csv
import imageio
import time
from scipy.misc import imread
from scipy.misc import imresize
import matplotlib.image as mpimg
from scipy.ndimage import filters
import urllib
from numpy import random


fname = './patch/patch.log'

#####################################
# read 
imgList = []
imgTag = []
imgLoc = []

f = open(fname, 'r')
reader = csv.reader(f)
for row in reader:
	if not ''.join(row).startswith("#"):
		print(row, " ", "./patch/"+row[0], end='\n')
		# im = imageio.imread("./patch/"+row[0])
		
		im1 = (imread("./patch/"+row[0])).astype(float32) # [:,:,:3]
		print(', ', im1.shape, ', ', type(im1))

f.close()

#####################################
# 