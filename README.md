# auxiliary ImageJ plugins 

To compile plugin .java in terminal, needs ij.jar in classpath

```
#!bash
cd src
javac -cp IJ_HOME/ij.jar ImageJ_Plugin.java

```

Mouse_Listener.java -- listens and plot the coordinates of the mouse clicks over the opened (512,512,120) blank image stack, useful to record the mouse pinpointing, make point annotations

Stack_Ovals.java --

copy the compiled class to imagej plugins directory and

```
#!bash
cp Mouse_Pinpointer.class IJ_HOME/plugins/
```

```
#!bash
java -jar IJ_HOME/ij.jar -ijpath IJ_HOME/plugins/
```


**menu**: *ImageJ>Plugins>Mouse Pinpointer*