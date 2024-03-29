### ImageJ snippets

#### To compile using [IntelliJ IDEA](https://www.jetbrains.com/idea/) using Maven
Install and open IntelliJ, if opened first time select *New Project*.
+ Name: ij-snippets
+ Location: $SRC_DIR (directory where the _ij-snippets_ repository is cloned)
+ Project should be created in: $SRC_DIR/ij-snippets
+ Language: Java
+ Build system: Maven

When using pom.xml to build the project, open Maven toolbar an double click ProjectName (IJ_Snippets)>Lifecycle>package

Compiled IJ_Snippets-0.0.1.jar will be available in $SRC_DIR/ij-snippets/target/

#### To compile plugin .java in terminal
Use _javac_, with _ij.jar_ in classpath
```
#!bash
cd src
javac -cp ij.jar Some_Plugin.java

```
Copy the compiled class to imagej plugins directory
```
#!bash
cp Some_Plugin.class $ImageJ_Home/plugins/
```
start ImageJ in terminal
```
#!bash
java -jar $ImageJ_Home/ij.jar -ijpath $ImageJ_Home/plugins/
```
and follow **menu**: *ImageJ>Plugins>Some Plugin*

Some_Plugin can be:

* Mouse_Listener.java -- listens and plot the coordinates of the mouse clicks over the opened (512,512,120) blank image stack, useful to record the mouse pinpointing, make point annotations

* Stack_Ovals.java -- plots list of random (x,y,z,r) Ovals on ImageStack and shows the overlayed image. Useful to export video frames of the object tracking estimates.
