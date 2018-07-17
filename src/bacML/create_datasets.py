import os
import sys
import numpy as np
import matplotlib.image as mpimg
import matplotlib.pyplot as plt

directory_name="";

try:
    directory_name=sys.argv[1]
    print(directory_name)
except:
    print('Please pass directory_name')

num_classes = 6
np.random.seed(133)

# show example images from each class (each subdir is a class)
print("\n")
classes = []
if os.path.isdir(directory_name):
    for root, dirs, files in os.walk(directory_name):
        for name in dirs:
            classes.append(os.path.join(directory_name, name))
            print(name)

print("\n")
sample_images = []
if os.path.isdir(directory_name):
    for root, dirs, files in os.walk(directory_name):
        for name in files:
            if name.endswith(".png"):
                sample_images.append(os.path.join(directory_name, name))

for c in classes:
    print(c)
    cImg = []
    for root, dirs, files in os.walk(c):
        for name in files:
            if name.endswith(".png"):
                cImg.append(os.path.join(c, name))
    print(len(cImg), " png files.")

    cIdx = np.random.randint(1, len(cImg) + 1)

    print(cIdx, "\t", cImg[cIdx])
    img = mpimg.imread(str(cImg[cIdx]))
    imgplot = plt.imshow(img, cmap="gray")
    plt.show()

if True:
    print("\nquitting...");
    quit()

image_size = 28  # Pixel width and height.
pixel_depth = 255.0  # Number of levels per pixel.
