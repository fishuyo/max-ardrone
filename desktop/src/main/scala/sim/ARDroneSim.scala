
package com.fishuyo
package drone



// common javadrone, dronecontrol

// desktop
	// server
		// dronecontrol with osc interface
	// sim
		// seer, dronecontrol

// maxmsp
	// max object dronecontrol


// dronecontrol
  // 

import graphics._
import maths._
import spatial._

import com.codeminders.ardrone._

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics._
import com.badlogic.gdx.graphics.GL10

class ARDroneSim extends ARDrone with GLAnimatable {

	//var body = ObjParser("src/main/scala/drone/drone.obj")
  var body = GLPrimitive.cube(Pose(), Vec3(0.5f,.05f,.5f))

  // simulation state -- represents the exact position velocity and acceleration for a given time
  var sPose = Pose()
	var sVelocity = Vec3()
	var sAcceleration = Vec3()
	var thrust = 0.f
	var g = Vec3(0,-9.8f,0)
	var mass = 1.f

	var flying = false
	var takingOff = false

  // control input (% of maximum)(delta relates to angVel and to jerk)
  var control = Vec3()
  var rot = 0.f
  var maxEuler = .6f  //(0 - .52 rad)
  def setMaxEuler(f:Float) = maxEuler = f
  var maxVert = 1000   //(200-2000 mm/s)
  var maxRot = 3.0f    //(.7 - 6.11 rad/s)


	override def step(dt:Float){

		val p = sPose.pos //drone.p.pos
		val q = sPose.quat //drone.p.quat

		// controller match {
		// 	case "v1" =>
		// 	case "v2" =>
		// 	case "dynamic" =>
		// }

		//moveStep2(p.x,p.y,p.z, q.x,q.y,q.z,q.w )

		val angles = q.toEuler()

		if( takingOff && p.y < 0.25f){
			thrust = 9.8f + 2.f
		} else if( takingOff ){
			thrust = 9.8f
			takingOff = false
		}

		sAcceleration.set( sPose.uu()*thrust )

		sPose.pos += sVelocity*dt
		sVelocity += (sAcceleration + g)*dt - sVelocity*.5f*dt 

		if( p.y < 0.f){
			sPose.pos.y = 0.f
			//vel.set(0.f,0.f,0.f)
		}

		body.pose = sPose

    // plot(sAcceleration.x)
    // plot2(expected_a.x)
    // plot3(sVelocity.x)
    // plot4(expected_v.x)

	}

	override def draw() = body.draw()



	override def move(lr:Float,fb:Float,ud:Float,rot:Float){
		if(!flying) return
		sPose.quat.fromEuler(fb*maxEuler,0.f,-lr*maxEuler)
		thrust = (9.8f + ud)/sPose.uu().y
	}


	override def takeOff() = {
		flying = true
		takingOff = true
	}
	override def land() = { sPose.quat.setIdentity(); flying = false; takingOff=false; thrust = 5.f}
	override def hover() = { sPose.quat.setIdentity(); val r = util.Randf(-0.1f,.1f); sVelocity.set(r(),r(),r())}

	
}