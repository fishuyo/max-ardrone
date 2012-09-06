maxdrone
========

A Max/Msp mxj external for controlling the ARDrone written in scala, uses [JavaDrone](http://code.google.com/p/javadrone/) api

-------

To build:

  * put a copy of max.jar and jitter.jar in ./lib (found in your max installation directory)
  * sbt compile
  * sbt proguard -> to package as single jar file

To run:

  * place built jar into MAXMSP_PATH/Cyclin' 74/java/lib 
  * see DroneControl.maxhelp for usage
