import numpy as np

a = np.array([[1, 2, 3], [4, 5, 6], [7, 8, 9]])

print(a)
print(np.prod(a.shape))

b = a.reshape(np.prod(a.shape))

print("b.shape=", b.shape)

print("len(b)=", len(b))

print("b=", b)

print("b[0]=", b[0])

c = np.zeros(b.shape)

print(c)

print(len(c))

for i in range(10):
    print("-- ", i)

d = np.zeros(5)
print(d)

import random
e = random.uniform(0, 1)
print(e)

np.save("C:\\Users\\10250153\\bacteria3\\data\\pilot.npy", a)
a1 = np.load("C:\\Users\\10250153\\bacteria3\\data\\pilot.npy")
print(a)
print(a1)

