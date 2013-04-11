package com.fishuyo
package drone
package sim

import maths._
import graphics._
import spatial._
import io._
import dynamic._
import audio._

import com.fishuyo.SimpleAppRun

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.glutils._
//import com.badlogic.gdx.graphics.GL10

import scala.collection.mutable.ListBuffer

object Main extends App with GLAnimatable{

	var plots = new ListBuffer[Plot2D]()
	var traces = new ListBuffer[Trace3D]()

  SimpleAppRun.loadLibs()
  GLScene.push(this)

  //simulation objects
  val simDrone = new ARDroneSim
  val simControl = new DroneControl
  simControl.drone = simDrone
  simControl.ready = true

  // Plots
  var plotsFollowCam = false
  def togglePlotFollow() = plotsFollowCam = !plotsFollowCam
  plots += new Plot2D(100, 15.f)
  plots(0).pose.pos = Vec3(0.f, 2.f, 0.f)
  plots += new Plot2D(100, 15.f)
  plots(1).pose.pos = Vec3(0.f, 2.f, 0.f)
  plots(1).color = Vec3(2.f,0.f,0.f)

  plots += new Plot2D(100, 15.f)
  plots(2).pose.pos = Vec3(2.f, 2.f, 0.f)
  plots += new Plot2D(100, 15.f)
  plots(3).pose.pos = Vec3(2.f, 2.f, 0.f) 
  plots(3).color = Vec3(2.f,0.f,0.f)

  plots += new Plot2D(100, 15.f)
  plots(4).pose.pos = Vec3(4.f, 2.f, 0.f)
  plots += new Plot2D(100, 15.f)
  plots(5).pose.pos = Vec3(4.f, 2.f, 0.f)
  plots(5).color = Vec3(2.f,0.f,0.f)

  //Traces
  traces += new Trace3D(100)
  traces += new Trace3D(100)
  traces += new Trace3D(100)
  traces += new Trace3D(100)


  //real drone
  val realDroneBody = GLPrimitive.cube(Pose(), Vec3(0.5f,.05f,.5f))
  val control = new DroneControl

  //var ground = 	ObjParser("res/obj/grid.obj")
  val ground = 	GLPrimitive.cube(Pose(Vec3(0,-.03f,0),Quat()), Vec3(6,-.01f,6))
	ground.color.set(0.f,0.f,.6f)
  val moveCube = GLPrimitive.cube(Pose(), Vec3(.05f))


  val live = new Ruby("src/main/scala/sim/sim.rb")
  SimpleAppRun() 

  override def draw(){
  	//realDroneBody.draw()
  	simDrone.draw()
  	ground.draw()
  	moveCube.draw()
  	plots.foreach( _.draw() )
  	traces.foreach( _.draw() )
  }
  override def step(dt:Float){
  	live.step(dt)
  	moveCube.pose.pos = simControl.destPose.pos
  	//realDroneBody.
  	simDrone.step(dt)
  	//simControl.step2( simDrone.sPose )
  } 


}






