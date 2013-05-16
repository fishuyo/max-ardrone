import sbt._

import Keys._

import ProguardPlugin._


object Settings {
  
  lazy val common = Defaults.defaultSettings ++ Seq (
    version := "0.4.2",
    scalaVersion := "2.9.2",
    resolvers ++= Seq(
      "NativeLibs4Java Repository" at "http://nativelibs4java.sourceforge.net/maven/",
      "xuggle repo" at "http://xuggle.googlecode.com/svn/trunk/repo/share/java/",
      "Sonatypes OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "ScalaNLP Maven2" at "http://repo.scalanlp.org/repo"
    ),
    libraryDependencies ++= Seq(
      //"org.scala-lang" % "scala-compiler" % "2.9.1",
      "com.nativelibs4java" % "scalacl" % "0.2",
      "de.sciss" %% "scalaosc" % "1.1.+"
      //"com.nativelibs4java" % "javacl" % "1.0.0-RC2",
      //"xuggle" % "xuggle-xuggler" % "5.4"
      //"org.scalala" % "scalala_2.9.0" % "1.0.0.RC2-SNAPSHOT",
      //"net.sf.bluecove" % "bluecove" % "2.1.0",
      //"net.sf.bluecove" % "bluecove-gpl" % "2.1.0"
    ),
    autoCompilerPlugins := true,
    addCompilerPlugin("com.nativelibs4java" % "scalacl-compiler-plugin" % "0.2"),
    scalacOptions += "-Xexperimental"
    //sourceDirectories in Compile += new File("common/src"),
    //fork in Compile := true
   )

  lazy val desktop = Settings.common ++ Seq (
    fork in Compile := true
  )

  lazy val maxmsp = Defaults.defaultSettings ++ Seq (
    version := "0.4.2",
    scalaVersion := "2.9.2",
    fork in Compile := true,
    //mainClass := Some("Main")
    resolvers ++= Seq(
      "Sonatypes's Maven" at "https://oss.sonatype.org/content/repositories/snapshots/"
    ),
    libraryDependencies ++= Seq(
      "log4j" % "log4j" % "1.2.16"
    )
  )

  lazy val proguard = proguardSettings ++ Seq(
    proguardOptions := Seq( 
      //"-libraryjars lib/max.jar:lib/jitter.jar",
      "-keep class DroneControl { *; }",
      "-keep class com.fishuyo.drone.DroneControl { *; }",
      "-keep class com.fishuyo.maths.Vec3",
      "-keep class com.fishuyo.maths.Quat",
      """-keepclasseswithmembers public class * {
        public static void main(java.lang.String[]);
      }

      -keep class * implements org.xml.sax.EntityResolver
      -keepclassmembers class * {
        ** MODULE$;
      }

      -keepclassmembernames class scala.concurrent.forkjoin.ForkJoinPool {
        long eventCount;
        int  workerCounts;
        int  runControl;
        scala.concurrent.forkjoin.ForkJoinPool$WaitQueueNode syncStack;
        scala.concurrent.forkjoin.ForkJoinPool$WaitQueueNode spareStack;
      }

      -keepclassmembernames class scala.concurrent.forkjoin.ForkJoinWorkerThread {
        int base;
        int sp;
        int runState;
      }

      -keepclassmembernames class scala.concurrent.forkjoin.ForkJoinTask {
        int status;
      }

      -keepclassmembernames class scala.concurrent.forkjoin.LinkedTransferQueue {
        scala.concurrent.forkjoin.LinkedTransferQueue$PaddedAtomicReference head;
        scala.concurrent.forkjoin.LinkedTransferQueue$PaddedAtomicReference tail;
        scala.concurrent.forkjoin.LinkedTransferQueue$PaddedAtomicReference cleanMe;
      }"""
    )
  )
}



object droneBuild extends Build {
    val common = Project (
    "common",
    file("./common"),
    settings = Settings.common
  )

  lazy val a_desktop = Project (
    "desktop",
    file("./desktop"),
    settings = Settings.desktop
  ) dependsOn common

  val maxdrone = Project (
    "maxmsp",
    file("./maxmsp"),
    settings = Settings.maxmsp ++ Settings.proguard
  ) dependsOn common
}
