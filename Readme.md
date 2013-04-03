maxdrone - DroneControl
========

A Max/Msp mxj external for controlling the ARDrone written in scala, uses [JavaDrone](http://code.google.com/p/javadrone/) api

-------

To build:

  * put a copy of max.jar and jitter.jar in ./lib (found in your max installation directory)
  * sbt compile
  * sbt proguard -> to package as single jar file

To run:

  * build or download latest build: [DroneControlv0.4.0.zip](http://beta.zentopy.com/p/#!/5136a2d9bf6b77763b000007/515c9022bf6b77057600005c) [DroneControlv0.3.3.zip](https://github.com/downloads/fishuyo/max-ardrone/DroneControlv0.3.3.zip)
  * add path to jar in MAXMSP_PATH/Cyclin' 74/java/max.java.config.txt or place built jar into MAXMSP_PATH/Cyclin' 74/java/lib 
  * see DroneControl.maxhelp for usage
