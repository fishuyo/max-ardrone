
/**
*
* fishuyo - 2013
*
*/

package com.fishuyo
package drone

import maths.Vec3
import maths.Quat
import spatial.Pose
import net.SimpleTelnetClient

import com.codeminders.ardrone._

import java.net._
import java.awt.image.BufferedImage


import scala.collection.mutable.Queue

class DroneControl(var ip:String="192.168.1.1") extends NavDataListener with DroneVideoListener {

  // current state flags
  var (connecting, ready, flying, navigating, goingHome) = (false,false,false,false,false)

  // javadrone api ARDrone
  var drone : ARDrone = _

  // holds last video frame
  var frame: BufferedImage = _

  // initial pose stored when first received tracking data
  var homePose = Pose()

  var dropped = 0
  private var lookingAt:Vec3 = null
  var waypoints = new Queue[(Float,Float,Float,Float)]

  //state for controller v1 (DroneControlv0 - v0.3)
	var dest = Vec3()
	var tPos = Vec3()
	var tVel = Vec3()
	var tAcc = Vec3()
	var tYaw = 0.f; var destYaw = 0.f

	//state for controller v2 (DroneControlv0.4)
	var destPose = Pose()
	var tPose = Pose()
  var tVel2 = Vec3()
  var tAcc2 = Vec3()
  var tJerk = Vec3()
  var euler = Vec3()  // relates to accel  -->  x'' = g*tan(theta)
  var angVel = Vec3()       // relates to jerk  --> x''' = g*sec(theta)^2
  var t0:Long = 0
  var (t,time1,time2,d0,neg) = (Vec3(),Vec3(),Vec3(),Vec3(),Array[Boolean](false,false,false))
  var expected_a = Vec3()
  var expected_v = Vec3()
  var (kp,kd,kdd) = (Vec3(1.85,8.55,1.85),Vec3(.75),Vec3(1))  // pdd equation constants

  // controller outputs (% of maximum)(delta relates to angVel and to jerk)
  var control = Vec3()  // x -> left/righ tilt, y -> up/down throttle, z -> forward/back tilt
  var rot = 0.f         // rotate ccw/cw speed

  // controller params
  var maxEuler = .6f  //(0 - .52 rad)
  var maxVert = 1000   //(200-2000 mm/s)
  var maxRot = 3.0f    //(.7 - 6.11 rad/s)
  var posThresh = .1f; var yawThresh = 10.f
	var moveSpeed = 1.f
  def setMoveSpeed(f:Float) = moveSpeed = f
	var vmoveSpeed = 1.f
	var rotSpeed = 1.f
	var smooth = false
  var rotFirst = false
  var look = false
  var useHover = true
  var patrol = false
  var switchRot = true

  // ARDrone state internal
  var gyroAngles = Vec3()
  var estimatedVelocity = Vec3() //from accelerometer?
  var altitude = 0.f
  var battery = 0
  var emergency = false
  var nd:NavData = _



  println("DroneControl version 0.4.0")
  

  //////////////////////////////////////////////////////////////////////
  // member functions
  //////////////////////////////////////////////////////////////////////

  def connect(){
    if( drone != null){
      println("Drone already connected.")
      return
    }else if( connecting ){
      return
    }
    connecting = true
    val _this = this
    val t = new Thread(){
      override def run(){
        try {
          val d = new ARDrone( InetAddress.getByName(ip), 1000, 1000 )
         	println("connecting to ARDrone at " + ip + " ..." )
          d.connect
          d.clearEmergencySignal
          d.waitForReady(3000)
          println("ARDrone connected and ready!")
          d.trim
          d.addImageListener(_this)
          d.addNavDataListener(_this)
          drone = d
          connecting = false
          ready = true

        } catch {
          case e: Exception => println("Drone connection failed."); e.printStackTrace 
          connecting = false
        }  
      }
    }
    t.start
  }

  def disconnect(){
    if( drone == null){
      println("Drone not connected.")
      return
    }
    if( flying ) drone.land
    drone.disconnect
    Thread.sleep(100)
    drone = null
    ready = false
    println("Drone disconnected.") 
  }

  def clearEmergency(){
    if( drone == null){
      println("Drone not connected.")
      return
    }
    drone.clearEmergencySignal
  }

  def trim() = drone.trim
  
  def takeOff(){
    if( drone == null){
      println("Drone not connected.")
      return
    }
    drone.takeOff
    flying = true
  }

  def land(){
    if( drone == null){
      println("Drone not connected.")
      return
    }
    drone.land
    flying = false
  }

  def toggleFly = {
    if( flying ) land
    else takeOff
  }

  def playLed(anim:Int, freq:Float, dur:Int){
    if( drone == null){
      println("Drone not connected.")
      return
    }
    drone.playLED(anim, freq, dur)
  }

  def dance(anim:Int, dur:Int){
    if( drone == null){
      println("Drone not connected.")
      return
    }
    drone.playAnimation(anim, dur)
  }

  def move( lr: Float, fb: Float, udv: Float, rv: Float ){
    if( drone == null){
      println("Drone not connected.")
      return
    }
    navigating=false
    drone.move(lr,fb,udv,rv) 
  }

  def moveTo( x:Float,y:Float,z:Float,w:Float ){
    if( goingHome ) return
    dest = Vec3(x,y,z)
    destPose = Pose(dest, Quat())
    destYaw = w
    while( destYaw < -180.f ) destYaw += 360.f
    while( destYaw > 180.f ) destYaw -= 360.f
    navigating = true
  }

  def hover = { navigating=false; drone.hover }

  def addWaypoint( x:Float,y:Float,z:Float,w:Float ) = {
    if( waypoints.size > 1000 ) waypoints.clear
    waypoints.enqueue((x,y,z,w))
    navigating = true
  }
  def nextWaypoint(){
    if( waypoints.isEmpty || drone == null) return
    drone.playLED(4,10,1)
    val (x,y,z,w) = waypoints.dequeue
    moveTo(x,y,z,w)
    if( patrol ) waypoints.enqueue((x,y,z,w))
  }
  def clearWaypoints() = waypoints.clear

  // used to set ARDrone config options
  def setConfigOption(name:String,value:String){
    drone.setConfigOption(name,value)
  }
  def setMaxEuler(v:Float){
    maxEuler = v
    setConfigOption("control:euler_angle_max",v.toString)
  }
  def setMaxVertical(v:Int){
    maxVert = v
    setConfigOption("control:control_vz_max",v.toString)
  } 
  def setMaxRotation(v:Float){
    maxRot = v
    setConfigOption("control:control_yaw",v.toString)
  }

  // debug stuff
  // def logger( l:String="INFO" ){
  //   var v = Level.INFO
  //   l.toUpperCase match {
  //     case "WARN" => v = Level.WARN
  //     case "DEBUG" => v = Level.DEBUG
  //     case _ => v = Level.INFO
  //   }
  //   Logger.getRootLogger.setLevel( v )
  // }

  def getVersion(){ println("version: " + drone.getDroneVersion() )}

  def debug(){
    println( "ready: " + ready)
    println("flying: " + flying)
    println("navigating: " + navigating)
    println("pos: " + tPos.x + " " + tPos.y + " " + tPos.z + " " + tYaw)
    println("dest: " + dest.x + " " + dest.y + " " + dest.z + " " + destYaw)
    println("tracked vel: " + tVel.x + " " + tVel.y + " " + tVel.z )
    println("tracked accel: " + tAcc.x + " " + tAcc.y + " " + tAcc.z)
    //println("internal vel: " + vx + " " + vy + " " + vz)
    //println("move: " + tilt.x + " " + tilt.y + " " + ud + " " + r)
    //println("pitch roll yaw: " + pitch + " " + roll + " " + yaww)
    println("altitude: " + altitude)
    println("battery: " + battery)
    //if( drone != null ) qSize = drone.queueSize
    //println("command queue size: " + qSize)
  }

  // drone navdata and video callbacks
  def navDataReceived(nd:NavData){
    flying = nd.isFlying
    altitude = nd.getAltitude
    battery = nd.getBattery
    gyroAngles.x = nd.getPitch
    gyroAngles.z = nd.getRoll
    gyroAngles.y = nd.getYaw
    estimatedVelocity.x = nd.getVx
    estimatedVelocity.y = nd.getLongitude
    estimatedVelocity.z = nd.getVz
    emergency = drone.isEmergencyMode()
    //qSize = drone.queueSize
  }

  // called when video frame received
  def frameReceived(startX:Int, startY:Int, w:Int, h:Int, rgbArray:Array[Int], offset:Int, scansize:Int){
    if( frame == null || frame.getWidth != w || frame.getHeight != h ) frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    frame.setRGB(startX, startY, w, h, rgbArray, offset, scansize)
  }

  def selectCameraMode( mode: Int){
    var m = mode % 4
    m match {
      case 0 => drone.selectVideoChannel( ARDrone.VideoChannel.HORIZONTAL_ONLY )
      case 1 => drone.selectVideoChannel( ARDrone.VideoChannel.VERTICAL_ONLY )
      case 2 => drone.selectVideoChannel( ARDrone.VideoChannel.HORIZONTAL_IN_VERTICAL )
      case 3 => drone.selectVideoChannel( ARDrone.VideoChannel.VERTICAL_IN_HORIZONTAL )
    }
  }

  // telnet commands
  def reboot() = {
    val t = new Thread(){
      override def run(){
        try{
          println("Sending reboot...")
          if( drone != null ) disconnect
          val c = new SimpleTelnetClient(ip)
          c.send("reboot")
          c.disconnect
          println("Rebooting ARDrone. ")
        } catch {
          case e: Exception => println("Reboot failed.") 
        }
      }
    }
    t.start
  }

  def telnet(command:String) = {
    val t = new Thread(){
      override def run(){
        println("Sending telnet command: " + command)
        val c = new SimpleTelnetClient(ip)
        c.send(command)
        c.disconnect
      }
    }
    t.start
  }



  ////////////////////////////////////////////////////////////////////////////
	// Trajectory Calculation for STEP 2.0
  ////////////////////////////////////////////////////////////////////////////

  // acceleration piecewise function
  def acurve(t1:Float,t2:Float,neg:Boolean)(t:Float):Float = {
    var ret=0.f
    if(t < t1) ret = math.sin(math.Pi/t1*t).toFloat
    else if( t > t2) ret = -math.sin(math.Pi/t1*(t-t2)).toFloat
    else ret = 0.f
    ret *= math.tan(maxEuler).toFloat*9.8f
    if(neg) -ret else ret
  }
  // velocity piecewise function
  def vcurve(t1:Float,t2:Float,neg:Boolean)(t:Float):Float = {
    var ret=0.f
    if(t < t1) ret = t1/math.Pi.toFloat*(1.f - math.cos(math.Pi/t1*t).toFloat)  
    else if( t > t2) ret = t1/math.Pi.toFloat*(1.f + math.cos(math.Pi/t1*(t-t2)).toFloat)
    else ret = 2*t1/math.Pi.toFloat
    ret *= math.tan(maxEuler).toFloat*9.8f
    if(neg) -ret else ret
  }
  // position piecewise function
  def dcurve(t1:Float,t2:Float,neg:Boolean)(t:Float):Float = {
    var ret=0.f
    if(t < t1) ret = t1/math.Pi.toFloat*(t - t1/math.Pi.toFloat*math.sin(math.Pi/t1*t).toFloat)  
    else if( t > t2) ret = t1/math.Pi.toFloat*(t + t1/math.Pi.toFloat*math.sin(math.Pi/t1*(t-t2)).toFloat) + t1/math.Pi.toFloat*(t2-t1)
    else ret = t1/math.Pi.toFloat*(2*t - t1)
    ret *= math.tan(maxEuler).toFloat*9.8f
    if(neg) -ret else ret
  }

  // for a given distance solve for t1 and t2 
  def solver(d:Float):(Float,Float) = {
  	var t1 = 0.f
  	var t2 = 0.f
  	val maxA = math.tan(maxEuler).toFloat*9.8f

		if( d/moveSpeed < moveSpeed*math.Pi.toFloat/(2*maxA)){
			t1 = math.sqrt(d*math.Pi/(2*maxA)).toFloat
			t2 = t1
		}else{
	    t1 = moveSpeed*math.Pi.toFloat/(2*math.tan(maxEuler).toFloat*9.8f)  // ---> d = 2*t1*t2*a/pi, vmax = 2*t1*a/pi
	    t2 = d / moveSpeed
		}
    (t1,t2)
  }

  ////////////////////////////////////////////////////////////////////////////
  // STEP 2.0
  ////////////////////////////////////////////////////////////////////////////
  def step2(x:Float,y:Float,z:Float,qx:Float,qy:Float,qz:Float,qw:Float){
    step2( Pose(Vec3(x,y,z),Quat(qw,qx,qy,qz)))
  }
  
  def step2(p:Pose){

    if( !flying || !navigating ){ return } //navmove(0.f,0.f,0.f,0.f); return }

    // calculate time since last step
    val t1 = System.currentTimeMillis()
    val dt = (t1 - t0) / 1000.f
    t0 = t1
    //println("dt: "+dt)

    // current pose and velocity
    //val p = Pose(Vec3(x,y,z),Quat(qw,qx,qy,qz))
    val v = (p.pos - tPose.pos) / dt

    // update tracked state
    val a = (v - tVel2) / dt
    tJerk = (a - tAcc2) / dt
    tAcc2.set(a)
    tVel2.set(v)
    tPose.set(p)

    // get euler angles from quaternion
    val e = Vec3()
    e.set( tPose.quat.toEuler )
    angVel = (e - euler) / dt
    euler.set(e)

    // calculate distance to dest and trajectory
    val dir = destPose.pos - tPose.pos
    val dist = dir.mag
    if( dist > posThresh){
      
      for( i <- (0 until 3)){

        if( i == 1){ // use simple method for y direction
          expected_a(i) = dir.normalize().y

        } else if( math.abs(dir(i)) < posThresh){    // if close enough in x or z stop moving
          t(i) = 0.f
          expected_a(i) = 0.f
          //this.hover()

        } else if( t(i) == 0.f ){ //calculate new trajectory
          d0(i) = tPose.pos(i)
          if( dir(i) < 0.f) neg(i) = true
          else neg(i) = false
          val times = solver(math.abs(dir(i)))
          time1(i) = times._1
          time2(i) = times._2
          println( "new trajectory!!!!!!!!!!!! " + i + " " + times._1 + " " + times._2 )
          t(i) += dt
        }else{

          // calculate desired acceleration value based on the differences of expected vs actual positions, velocities, and accelerations
          val d = dcurve(time1(i),time2(i),neg(i))(t(i))
          val dd = vcurve(time1(i),time2(i),neg(i))(t(i))
          expected_v(i) = dd
          val ddd = acurve(time1(i),time2(i),neg(i))(t(i))

          // desiredAccel = k1 * ∆pos + k2 * ∆vel + k3 * ∆accel
          expected_a(i) = kp(i)*((d+d0(i))-tPose.pos(i)) + kd(i)*(dd-v(i)) + kdd(i)*(ddd-a(i))
          t(i) += dt

          // if time greater than calculated trajectory clear trajectory
          if(t(i) > (time2(i) + time1(i))){
            t(i) = 0.f
            expected_a(i) = 0.f
            //navigating = false
            //move(0.f,0.f,0.f,0.f)
            //this.hover()
          }
        }

      }

      // cos(yaw) / sin(yaw)
      val cos = math.cos(euler.y).toFloat
      val sin = math.sin(euler.y).toFloat

      // calculate desired euler angles for desired accelerations
      control.x = math.atan((expected_a.x*cos - expected_a.z*sin) / (expected_a.y+9.8f)).toFloat
      control.y = math.atan((expected_a.x*sin + expected_a.z*cos) / (expected_a.y+9.8f)).toFloat
      control.z = expected_a.y * vmoveSpeed; //expected_a.y

      //recalculate control x y tilts as percentage of maxEuler angle
      control.x = control.x / maxEuler
      control.y = control.y / maxEuler

      // limit or desaturate control params
      if( control.x > 1.f || control.x < -1.f){ control.x /= math.abs(control.x); control.y /= math.abs(control.x) }
      if( control.y > 1.f || control.y < -1.f){ control.y /= math.abs(control.y); control.x /= math.abs(control.y) }
      if( control.z > 1.f) control.z = 1.f
      else if( control.z < -1.f) control.z = -1.f
      if( rot > 1.f) rot = 1.f
      else if( rot < -1.f) rot = -1.f

      // send move command to drone
      drone.move(control.x,control.y,control.z,rot)
    } 
  }

  ////////////////////////////////////////////////////////////////////////////
	// Step v1
  ////////////////////////////////////////////////////////////////////////////	
	def step(x:Float,y:Float,z:Float,w:Float ){
    if( homePose == null ) homePose = Pose(Vec3(x,y,z),Quat())
    if( !flying || !navigating ) return

    var tilt = Vec3(0.f)
    var (ud,r) = (0.f,0.f)
    var hover = useHover
    var switchRot = false

    var p = Vec3(x,y,z)
    tVel = p - tPos

    if( tVel.x == 0.f && tVel.y == 0.f && tVel.z == 0.f ){
      dropped += 1
      if(dropped > 5){
        println("lost tracking or step size too short!!!") // TODO
        drone.hover
        return
      }
    }else dropped = 0

    tPos.set(p)
    tYaw = w; while( tYaw < -180.f ) tYaw += 360.f; while( tYaw > 180.f) tYaw -= 360.f

    //if look always look where it's going
    //if(look) destYaw = math.atan2(dest.pos.z - pos.z, dest.pos.x - tPos.x).toFloat.toDegrees + 90.f
    //else if( lookingAt != null ) destYaw = math.atan2(lookingAt.z - tPos.z, lookingAt.x - tPos.x).toFloat.toDegrees + 90.f
    
    while( destYaw < -180.f ) destYaw += 360.f
    while( destYaw > 180.f ) destYaw -= 360.f

    var dw = destYaw - tYaw
    if( dw > 180.f ) dw -= 360.f 
    if( dw < -180.f ) dw += 360.f

    if( math.abs(dw) > yawThresh ){ 
      hover = false
      if( !switchRot ) r = -rotSpeed else r = rotSpeed
      if( dw < 0.f) r *= -1.f
      if( smooth ) r *= dw / 180.f
      //drone.move(0,0,0,r)
      //return
    }

    val dir = (dest - (tPos+tVel))
    val dp = dir.mag
    if( dp  > posThresh ){
      hover = false
      val cos = math.cos(w.toRadians)
      val sin = math.sin(w.toRadians)
      val d = (dest - tPos).normalize
      ud = d.y * vmoveSpeed

      //assumes drone oriented 0 degrees looking down negative z axis, positive x axis to its right
      tilt.y = (d.x*sin + d.z*cos).toFloat * moveSpeed //forward backward tilt
      tilt.x = (d.x*cos - d.z*sin).toFloat * moveSpeed //left right tilt
      if( smooth ) {
        tilt *= dp
        if( tilt.x > 1.f || tilt.y > 1.f) tilt = tilt.normalize       
      }
      
    }else nextWaypoint

    if(hover) drone.hover()
    else if(rotFirst && r != 0.f) drone.move(0,0,0,r)
    else drone.move(tilt.x,tilt.y,ud,r)      
  }

}