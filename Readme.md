maxdrone - DroneControl
========

A Max/Msp mxj external for controlling the ARDrone written in scala, uses [JavaDrone](http://code.google.com/p/javadrone/) api

-------

To build:

  * put a copy of max.jar and jitter.jar in ./lib (found in your max installation directory)
  * from inside root of repo run:
  
```
  sbt
  project maxmsp
  compile
  proguard
```

To run:

  * build or download latest build: [DroneControlv0.4.2a.zip](http://fishuyo.com/drone/DroneControl-0.4.2a.zip)
  * add path to jar in MAXMSP_PATH/Cyclin' 74/java/max.java.config.txt or place built jar into MAXMSP_PATH/Cyclin' 74/java/lib 
  * see DroneControl.maxhelp for usage
