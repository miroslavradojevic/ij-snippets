from numpy.random import randint
from numpy import argmax
from keras.utils.np_utils import to_categorical
k = 8
n = 20
x = randint(0, k, (n,))
print(x)
print(argmax(to_categorical(x, k)))