# ImageJ plugin which listens and plot the coordinates of the mouse clicks over the opened (512,512,120) blank image stack. 
# Useful to record the mouse pinpointing, annotate.
compile in terminal

```
#!bash
cd src
javac -cp ~/ij.jar Mouse_Pinpointer.java

```
copy the compiled class to imagej plugins directory and
```
#!bash
cp Mouse_Pinpointer.class /ImageJ/plugins/
```
**menu**: *ImageJ>Plugins>Mouse Pinpointer*