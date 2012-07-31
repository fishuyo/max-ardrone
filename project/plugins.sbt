resolvers += Resolver.url(
  "sbt-plugin-releases", 
    new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/")
    )(Resolver.ivyStylePatterns)

    addSbtPlugin("com.github.retronym" % "sbt-onejar" % "0.8")

libraryDependencies ++= Seq (
  "com.github.siasia" %% "xsbt-proguard-plugin" % "0.11.2-0.1.1"
 )
