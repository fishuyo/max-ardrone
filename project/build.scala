import sbt._

import Keys._

import ProguardPlugin._

object Settings {
  lazy val common = Defaults.defaultSettings ++ Seq (
    version := "0.3",
    scalaVersion := "2.9.1",
    fork in Compile := true,
    //mainClass := Some("Main")
    resolvers ++= Seq(
      "Sonatypes's Maven" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "ScalaNLP Maven2" at "http://repo.scalanlp.org/repo"
    ),
    libraryDependencies ++= Seq(
      "log4j" % "log4j" % "1.2.16"
    )
   )

  lazy val proguard = proguardSettings ++ Seq(
    proguardOptions := Seq( 
      //"-libraryjars lib/max.jar:lib/jitter.jar",
      "-keep class DroneControl { *; }",
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
  val maxdrone = Project (
    "DroneControl",
    file("."),
    settings = Settings.common ++ Settings.proguard
  )
}
