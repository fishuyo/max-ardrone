/**
* 
* Max external for piloting ARDrone using the JavaDrone api
*
* fishuyo - 2012
*
*/

import com.fishuyo.maths.Vec3

import com.codeminders.ardrone._

import com.cycling74.max._
import com.cycling74.jitter._
import MaxObject._

import java.net._
import java.awt.image.BufferedImage

class DroneControl extends MaxObject with NavDataListener with DroneVideoListener {

  var ip = "192.168.3.1"
  declareAttribute("ip")

  private var ready = false
  private var navigating = false
  private var drone : ARDrone = _

  private var frame: BufferedImage = _
  private var mat: JitterMatrix = _

  private var pos = Vec3(0)
  private var vel = Vec3(0)
  private var dest = Vec3(0)

  private var yaw = 0.f
  private var destYaw = 0.f

  private var nd:NavData = _
  
  var yawThresh = 20.f
  declareAttribute("yawThresh")
  var posThresh = .33f
  declareAttribute("posThresh")
  var moveSpeed = .1f 			//horizontal movement speed default
  declareAttribute("moveSpeed")
  var vmoveSpeed = .1f 			//vertical movement speed default
  declareAttribute("vmoveSpeed")
  var rotSpeed = .9f 			//rotation speed default
  declareAttribute("rotSpeed")

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
  

  def connect( ) = {
    try {
      drone = new ARDrone( InetAddress.getByName(ip) )
     	post("connecting to ARDrone at " + ip + " ..." )
      drone.connect
      post("ARDrone Connected.")
      drone.clearEmergencySignal
      drone.waitForReady(5000)
      post("ARDrone ready!")
      drone.trim
      drone.addImageListener(this)
      drone.addNavDataListener(this)
      ready = true

    } catch {
      case e: Exception => ouch("Drone connection failed..."); e.printStackTrace 
    }
  }

  def disconnect() = { if( flying ) drone.land; drone.disconnect; Thread.sleep(100); drone = null; post("Drone disconnected.") }
  def clearEmergency() = drone.clearEmergencySignal
  def trim() = drone.trim
  def takeOff() = { drone.takeOff; flying = true}
  def land() = { drone.land; flying = false}

  def playLed = drone.playLED(1, 10, 5 )

  def toggleFly = {
	  if( flying ) land
	  else takeOff
	  //post( "fly: " + flying )
  }

  def move( lr: Float, fb: Float, ud: Float, r: Float ) = { navigating=false; drone.move(lr,fb,ud,r) }

  def moveTo( x:Float,y:Float,z:Float,w:Float ) = {
    dest = Vec3(x,y,z)
    destYaw = w
    while( destYaw < -180.f ) destYaw += 360.f
    while( destYaw > 180.f ) destYaw -= 360.f
    navigating = true
  }

  def hover = { navigating=false; drone.hover }

  def step(x:Float,y:Float,z:Float,w:Float ){

    if( !ready || !flying || !navigating ) return

    val p = Vec3(x,y,z)
    vel = p - pos

    pos = p;
    yaw = w; while( yaw < -180.f ) yaw += 360.f; while( yaw > 180.f) yaw -= 360.f

    var dw = destYaw - yaw
    if( dw > 180.f ) dw -= 360.f 
    if( dw < -180.f ) dw += 360.f 
    if( math.abs(dw) > yawThresh ){
      var r = -rotSpeed //.3f
      if( dw < 0.f) r = rotSpeed //.3f
      drone.move(0,0,0,r)
      return
    }
    //println( "diff in yaw: " + dw )

    val dir = (dest - (pos+vel))
    val dp = dir.mag
    val cos = math.cos(w.toRadians)
    val sin = math.sin(w.toRadians)
    val d = (dest - pos).normalize
    var ud = d.y * vmoveSpeed //.1f

    //assumes drone oriented looking down negative z axis, positive x axis to its right
    var fb = d.x*sin + d.z*cos //-d.x*cos - d.z*sin  //d.x*cos - d.z*sin
    var lr = d.x*cos - d.z*sin //-d.x*sin + d.z*cos  //-d.x*sin - d.z*cos
    fb = fb * moveSpeed //.1f
    lr = lr * moveSpeed //.1f
    //println("dp: " + dp + "  "+lr+" "+fb+" "+ud)
    if( dp  > posThresh ){
      drone.move(lr.toFloat,fb.toFloat,ud,0)
    }else {
      drone.hover
    }
    //println( dp )
  }

  def debug(){
    post( "ready: " + ready)
    post("flying: " + flying)
    post("navigating: " + navigating)
    post("pos: " + pos.x + " " + pos.y + " " + pos.z + " " + yaw)
    post("dest: " + dest.x + " " + dest.y + " " + dest.z + " " + destYaw)

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
  }

  def frameReceived(startX:Int, startY:Int, w:Int, h:Int, rgbArray:Array[Int], offset:Int, scansize:Int){
    if( frame == null ) frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    frame.setRGB(startX, startY, w, h, rgbArray, offset, scansize)
  }

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