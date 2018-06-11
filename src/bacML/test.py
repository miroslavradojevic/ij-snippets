from numpy import *
import os
#from pylab import *
import numpy as np
import iris_data
#import matplotlib.pyplot as plt
#import matplotlib.cbook as cbook
import time
from scipy.misc import imread
from scipy.misc import imresize
import matplotlib.image as mpimg
from scipy.ndimage import filters
import urllib
from numpy import random
import tensorflow as tf
import pandas as pd
# from caffe_classes import class_names

# train_x = zeros((1, 227,227,3)).astype(float32)
# train_y = zeros((1, 1000))
# xdim = train_x.shape[1:]
# ydim = train_y.shape[1]

################################################################################
#Read Image, and change to BGR
im1 = (imread("laska.png")).astype(float32) # [:,:,:3]
print('png image, ', im1.shape)
print(type(im1))

(train_x, train_y), (test_x, test_y) = iris_data.load_data()

# a = np.array([[1,2,3], [4,5,6]])
# print(a.shape)

##############################################################
print("------------------------------------", end='\n')
data = array([['','Col1','Col2'],['Row1',1,2],['Row2',3,4]])
data1 = pd.DataFrame(data=data[1:,1:],    # values
              index=data[1:,0],    # 1st column as index
              columns=data[0,1:])  # 1st row as the column names
print(data)
print(type(data))
print(data1)
print(type(data1))

row20 = np.zeros((1,20))

# .reshape([])
# https://stackoverflow.com/questions/20763012/creating-a-pandas-dataframe-from-a-numpy-array-how-do-i-specify-the-index-colum
# https://stackoverflow.com/questions/13730468/from-nd-to-1d-arrays
# methods
# https://www.tensorflow.org/get_started/premade_estimators
# https://www.cs.toronto.edu/~guerzhoy/tf_alexnet/
# https://stackoverflow.com/questions/4535374/initialize-a-numpy-array