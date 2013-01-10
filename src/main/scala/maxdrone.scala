/**
* 
* Max external for piloting ARDrone using the JavaDrone api
*
* fishuyo - 2012
*
*/

import com.fishuyo.maths.Vec3
import com.fishuyo.net.SimpleTelnetClient

import com.codeminders.ardrone._

import com.cycling74.max._
import com.cycling74.jitter._
import MaxObject._

import java.net._
import java.awt.image.BufferedImage

import org.apache.log4j._

import scala.collection.mutable.Queue

class DroneControl extends MaxObject with NavDataListener with DroneVideoListener {

  var ip = "192.168.1.1"
  declareAttribute("ip")

  private var connecting = false
  private var ready = false
  private var navigating = false
  private var goingHome = false
  private var drone : ARDrone = _
  private var dropped = 0

  private var frame: BufferedImage = _
  private var mat: JitterMatrix = _

  private var home:(Float,Float,Float,Float) = null
  private var pos = Vec3(0)
  private var vel = Vec3(0)
  private var accel = Vec3(0)
  private var dest = Vec3(0)
  private var tilt = Vec3(0.f)
  private var (ud,r) = (0.f,0.f)

  private var yaw = 0.f
  private var destYaw = 0.f
  private var yawVel = 0.f
  private var lookingAt:Vec3 = null

  private var nd:NavData = _

  var waypoints = new Queue[(Float,Float,Float,Float)]
  
  var yawThresh = 20.f
  declareAttribute("yawThresh")
  var posThresh = .33f
  declareAttribute("posThresh")
  var velThresh = .1f
  declareAttribute("velThresh")
  var moveSpeed = .1f 			//horizontal movement speed default
  declareAttribute("moveSpeed")
  var vmoveSpeed = .1f 			//vertical movement speed default
  declareAttribute("vmoveSpeed")
  var rotSpeed = .9f 			//rotation speed default
  declareAttribute("rotSpeed")
  var smooth = false
  declareAttribute("smooth")
  var rotFirst = true
  declareAttribute("rotFirst")
  var look = false
  declareAttribute("look")
  var useHover = true
  declareAttribute("useHover")
  var patrol = false
  declareAttribute("patrol")

  //navdata values
  var flying = false
  declareAttribute("flying")
  var altitude = 0.f
  declareAttribute("altitude")
  var battery = 0
  declareAttribute("battery")
  var pitch = 0.f
  declareAttribute("pitch")
  var roll = 0.f
  declareAttribute("roll")
  var yaww = 0.f
  declareAttribute("yaww")
  var vx = 0.f
  declareAttribute("vx")
  var vy = 0.f
  declareAttribute("vy")
  var vz = 0.f
  declareAttribute("vz")

  var qSize = 0

  post("DroneControl version 0.3.3")
  

  def connect(){
    if( drone != null){
      post("Drone already connected.")
      return
    }else if( connecting ){
      return
    }
    connecting = true
    val _this = this
    val t = new Thread(){
      override def run(){
        try {
          val d = new ARDrone( InetAddress.getByName(ip) )
         	post("connecting to ARDrone at " + ip + " ..." )
          d.connect
          d.clearEmergencySignal
          d.waitForReady(3000)
          post("ARDrone connected and ready!")
          d.trim
          d.addImageListener(_this)
          d.addNavDataListener(_this)
          drone = d
          connecting = false
          ready = true

        } catch {
          case e: Exception => post("Drone connection failed."); e.printStackTrace 
          connecting = false
        }  
      }
    }
    t.start
  }

  def disconnect(){
    if( drone == null){
      post("Drone not connected.")
      return
    }
    if( flying ) drone.land
    drone.disconnect
    Thread.sleep(100)
    drone = null
    ready = false
    post("Drone disconnected.") 
  }

  def clearEmergency(){
    if( drone == null){
      post("Drone not connected.")
      return
    }
    drone.clearEmergencySignal
  }

  def trim() = drone.trim
  
  def takeOff(){
    if( drone == null){
      post("Drone not connected.")
      return
    }
    drone.takeOff
  }
  
  def land(){
    if( drone == null){
      post("Drone not connected.")
      return
    }
    drone.land
  }

  def reboot() = {
    val t = new Thread(){
      override def run(){
        try{
          post("Sending reboot...")
          if( drone != null ) disconnect
          val c = new SimpleTelnetClient(ip)
          c.send("reboot")
          c.disconnect
          post("Rebooting ARDrone. ")
        } catch {
          case e: Exception => post("Reboot failed.") 
        }
      }
    }
    t.start
  }

  def telnet(command:String) = {
    val t = new Thread(){
      override def run(){
        post("Sending telnet command: " + command)
        val c = new SimpleTelnetClient(ip)
        c.send(command)
        c.disconnect
      }
    }
    t.start
  }

  def playLed(anim:Int, freq:Float, dur:Int){
    if( drone == null){
      post("Drone not connected.")
      return
    }
    drone.playLED(anim, freq, dur)
  }

  def dance(anim:Int, dur:Int){
    if( drone == null){
      post("Drone not connected.")
      return
    }
    drone.playAnimation(anim, dur)
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

  def toggleFly = {
	  if( flying ) land
	  else takeOff
	  //post( "fly: " + flying )
  }

  def move( lr: Float, fb: Float, udv: Float, rv: Float ){
    if( drone == null){
      post("Drone not connected.")
      return
    }
    navigating=false
    drone.move(lr,fb,udv,rv) 
  }

  def moveTo( x:Float,y:Float,z:Float,w:Float ){
    if( goingHome ) return
    dest = Vec3(x,y,z)
    destYaw = w
    while( destYaw < -180.f ) destYaw += 360.f
    while( destYaw > 180.f ) destYaw -= 360.f
    navigating = true
  }

  def lookAt( x:Float, y:Float, z:Float){
    lookingAt = Vec3(x,y,z)
  }
  def dontLookAt(){
    lookingAt = null
  }

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

  def setHome( x:Float,y:Float,z:Float,w:Float ) = home = (x,y,z,w)
  def goHome() = {
    val (x,y,z,w) = home
    moveTo(x,y+1.f,z,w)
    posThresh = .07f
    smooth = true
    goingHome = true
  }
  def cancelHome() = {
    posThresh = .33f
    smooth = false
    goingHome = false
  }

  def hover = { navigating=false; drone.hover }

  def step(x:Float,y:Float,z:Float,w:Float ){
    if( home == null ) home = (x,y,z,w)
    if( !ready || !flying || !navigating ) return

    var hover = useHover
    tilt.zero
    ud = 0.f
    r = 0.f

    var p = Vec3(x,y,z)
    var v = p - pos

    if( v.x == 0.f && v.y == 0.f && v.z == 0.f ){
      dropped += 1
      if(dropped > 5){
        post("lost tracking!!!") // TODO
      }
    }else dropped = 0

    accel = v - vel
    vel.set( v )
    pos.set(p)

    var oldYaw = yaw
    yaw = w; while( yaw < -180.f ) yaw += 360.f; while( yaw > 180.f) yaw -= 360.f
    yawVel = yaw - oldYaw
    if( yawVel > 180.f) yawVel -= 360.f
    if( yawVel < -180.f) yawVel += 360.f

    //if look always look where it's going
    if(look) destYaw = math.atan2(dest.z - pos.z, dest.x - pos.x).toFloat.toDegrees + 90.f
    else if( lookingAt != null ) destYaw = math.atan2(lookingAt.z - pos.z, lookingAt.x - pos.x).toFloat.toDegrees + 90.f
    while( destYaw < -180.f ) destYaw += 360.f
    while( destYaw > 180.f ) destYaw -= 360.f

    var estYaw = yaw + yawVel
    var dw = destYaw - estYaw
    if( dw > 180.f ) dw -= 360.f 
    if( dw < -180.f ) dw += 360.f 
    if( math.abs(dw) > yawThresh ){ 
      hover = false
      r = -rotSpeed
      if( dw < 0.f) r = rotSpeed
      if( smooth ) r *= math.abs(dw) / 180.f
      //drone.move(0,0,0,r)
      //return
    }

    val dir = (dest - (pos+vel))
    val dp = dir.mag
    if( dp  > posThresh ){
      hover = false
      val cos = math.cos(estYaw.toRadians)
      val sin = math.sin(estYaw.toRadians)
      val d = dir.normalize
      ud = d.y * vmoveSpeed

      //assumes drone oriented 0 degrees looking down negative z axis, positive x axis to its right
      tilt.y = (d.x*sin + d.z*cos).toFloat * moveSpeed //forward backward tilt
      tilt.x = (d.x*cos - d.z*sin).toFloat * moveSpeed //left right tilt
      if( smooth && dp < 1.f ){
        tilt *= dp
        ud *= dp
      }         
      
    } else if( vel.mag > velThresh){
      hover = false
      val cos = math.cos(estYaw.toRadians)
      val sin = math.sin(estYaw.toRadians)
      ud = -vel.y
      tilt.y = (-vel.x*sin - vel.z*cos).toFloat //forward backward tilt
      tilt.x = (-vel.x*cos + vel.z*sin).toFloat //left right tilt

    }else if( goingHome && vel.mag < .05f ){
      drone.land
      posThresh = .33f
      smooth = false
      goingHome = false
      return
    }else nextWaypoint

    if(hover) drone.hover
    else if(rotFirst && r != 0.f) drone.move(0,0,0,r)
    else drone.move(tilt.x,tilt.y,ud,r)
    
  }

  def logger( l:String="INFO" ){
    var v = Level.INFO
    l.toUpperCase match {
      case "WARN" => v = Level.WARN
      case "DEBUG" => v = Level.DEBUG
      case _ => v = Level.INFO
    }
    Logger.getRootLogger.setLevel( v )
  }

  def debug(){
    post( "ready: " + ready)
    post("flying: " + flying)
    post("navigating: " + navigating)
    post("pos: " + pos.x + " " + pos.y + " " + pos.z + " " + yaw)
    post("dest: " + dest.x + " " + dest.y + " " + dest.z + " " + destYaw)
    post("tracked vel: " + vel.x + " " + vel.y + " " + vel.z + " " + yawVel)
    post("tracked accel: " + accel.x + " " + accel.y + " " + accel.z)
    post("internal vel: " + vx + " " + vy + " " + vz)
    post("move: " + tilt.x + " " + tilt.y + " " + ud + " " + r)
    post("pitch roll yaw: " + pitch + " " + roll + " " + yaww)
    post("altitude: " + altitude)
    post("battery: " + battery)
    if( drone != null ) qSize = drone.queueSize
    post("command queue size: " + qSize)
  }

  def navDataReceived(nd:NavData){
    flying = nd.isFlying
    altitude = nd.getAltitude
    battery = nd.getBattery
    pitch = nd.getPitch
    roll = nd.getRoll
    yaww = nd.getYaw
    vx = nd.getVx
    vy = nd.getLongitude
    vz = nd.getVz
    //qSize = drone.queueSize
  }

  def frameReceived(startX:Int, startY:Int, w:Int, h:Int, rgbArray:Array[Int], offset:Int, scansize:Int){
    if( frame == null ) frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    frame.setRGB(startX, startY, w, h, rgbArray, offset, scansize)
  }

  def connectVideo() = drone.connectVideo();
  override def bang(){
    if( frame != null ){
      if( mat == null ) mat = new JitterMatrix
      mat.copyBufferedImage(frame)
      outlet(0,"jit_matrix",mat.getName())
    }else post("no frames from drone received yet.")
  }

  override def notifyDeleted(){
    disconnect
  }

}