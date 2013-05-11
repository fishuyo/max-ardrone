/**
* 
* Max external for piloting ARDrone using the JavaDrone api
*
* fishuyo - 2012
*
*/

import com.fishuyo.maths.Vec3
import com.fishuyo.maths.Quat
import com.fishuyo.spatial.Pose
import com.fishuyo.net.SimpleTelnetClient

import com.codeminders.ardrone._

import com.cycling74.max._
import com.cycling74.jitter._
import MaxObject._

import java.net._
import java.awt.image.BufferedImage

import scala.collection.mutable.Queue

class DroneControl extends MaxObject with NavDataListener with DroneVideoListener {

  // config params
  declareAttribute("ip")
  declareAttribute("yawThresh")
  declareAttribute("posThresh")
  declareAttribute("velThresh")
  declareAttribute("moveSpeed")
  declareAttribute("vmoveSpeed")
  declareAttribute("rotSpeed")
  declareAttribute("smooth")
  declareAttribute("rotFirst")
  declareAttribute("lookAtDest")
  declareAttribute("useHover")
  declareAttribute("patrol")
  declareAttribute("switchRot")

  // status
  declareAttribute("emergency")
  declareAttribute("altitude")
  declareAttribute("battery")

  // default ip
  var ip = "192.168.1.1"

  // current state flags
  var (connecting, ready, flying, navigating, goingHome) = (false,false,false,false,false)

  // javadrone api ARDrone
  var drone : ARDrone = _

  // holds last video frame
  var frame: BufferedImage = _

  // initial pose stored when first received tracking data
  var homePose = Pose()

  // number of frames receiving the same position
  var dropped = 0

  // navigation state
  var lookAtDest = false
  var isLookingAt = false
  var lookingAt:Vec3 = null
  var waypoints = new Queue[(Float,Float,Float,Float)]

  //state for controller v1 (DroneControlv0 - v0.3)
  var destPose = Pose()
  var tPose = Pose()
  var tVel = Vec3()
  var tAcc = Vec3()
  var tYaw = 0.f; var destYaw = 0.f
  var err=Vec3()
  var d_err=Vec3()
  var kpdd_xy=Vec3(.5f,10.f,0)
  var kpdd_z=Vec3(1.f,.1f,0)
  var rotK = Vec3(.318f,.1f,0)

  // controller outputs (% of maximum)(delta relates to angVel and to jerk)
  var control = Vec3()  // x -> left/right tilt, y -> forward/back tilt, z -> up/down throttle
  var rot = 0.f         // rotate ccw/cw speed

  // controller params
  var maxEuler = .6f  //(0 - .52 rad)
  var maxVert = 1000   //(200-2000 mm/s)
  var maxRot = 3.0f    //(.7 - 6.11 rad/s)
  var posThresh = .1f  // threshold distance from destination or waypoint
  var yawThresh = 10.f // threshold rotation from destination yaw
  var moveSpeed = .3f  // planar move speed multiplier
  def setMoveSpeed(f:Float) = moveSpeed = f
  var vmoveSpeed = .6f // vertical move speed multiplier
  def setVMoveSpeed(f:Float) = vmoveSpeed = f
  var rotSpeed = 1.f  // rotational speed multiplier
  var smooth = false  // 
  var rotFirst = false  // rotate first before other movement
  var useHover = true  // use the hover command when not reaching destination
  var patrol = false   // loop waypoints
  var switchRot = true  // change the direction of rotation command (if tracker data reversed)

  // ARDrone state internal
  var gyroAngles = Vec3()
  var estimatedVelocity = Vec3() //from accelerometer?
  var altitude = 0.f
  var battery = 0
  var emergency = false
  var nd:NavData = _

  var qSize = 0
  
  private var mat: JitterMatrix = _


  println("DroneControl version 0.4.2")
  

  //////////////////////////////////////////////////////////////////////
  // member functions
  //////////////////////////////////////////////////////////////////////

  def setPIDxy( p1:Float, d1:Float ) = kpdd_xy.set(p1,d1,0)
  def setPIDz( p1:Float, d1:Float ) = kpdd_z.set(p1,d1,0)
  def setPIDrot( p1:Float) = kpdd_z.set(p1,0,0)

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

  def lookAt( x:Float, y:Float, z:Float ){
    isLookingAt = true
    lookAtDest = false
    lookingAt = Vec3(x,y,z)
  }
  def dontLook(){
    isLookingAt = false
    lookAtDest = false
  }

  def moveTo( x:Float,y:Float,z:Float,qx:Float,qy:Float,qz:Float,qw:Float ){
    moveTo( Pose(Vec3(x,y,z),Quat(qw,qx,qy,qz)) )
  }
  def moveTo( x:Float,y:Float,z:Float,w:Float=0.f ){
    moveTo( Pose(Vec3(x,y,z),Quat().fromEuler((0.f,w*math.Pi.toFloat/180.f,0.f)) ) )
  }
  def moveTo( p:Pose ){
    if( goingHome ) return
    destPose = Pose(p)
    //destYaw = w
    //while( destYaw < -180.f ) destYaw += 360.f
    //while( destYaw > 180.f ) destYaw -= 360.f
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
  // Step v1
  ////////////////////////////////////////////////////////////////////////////  
  def step(x:Float,y:Float,z:Float,qx:Float,qy:Float,qz:Float,qw:Float){
    step( Pose(Vec3(x,y,z),Quat(qw,qx,qy,qz)))
  }  
  def step(x:Float,y:Float,z:Float,w:Float=0.f ){
    step( Pose(Vec3(x,y,z),Quat().fromEuler((0.f,w*math.Pi.toFloat/180.f,0.f))) )
  }

  def step(p:Pose){
    if( homePose == null ) homePose = Pose(p)
    if( !flying || !navigating ) return

    var hover = useHover
    var switchRot = false

    var pos = p.pos
    tVel = pos - tPose.pos

    if( tVel.x == 0.f && tVel.y == 0.f && tVel.z == 0.f ){
      dropped += 1
      if(dropped > 5){
        println("lost tracking or step size too short!!!") // TODO
        drone.hover
        return
      }
    }else dropped = 0

    tPose.set(p)

    var destRotVec = Vec3()
    var rotVec = p.uf().normalize()

    //if look always look where it's going
    if(lookAtDest) destRotVec.set( (destPose.pos - pos).normalize )//Quat().fromEuler((0.f,-1.f*(math.atan2(destPose.pos.z - pos.z, destPose.pos.x - pos.x).toFloat + math.Pi.toFloat/2.f),0.f))
    else if( isLookingAt ) destRotVec.set( (lookingAt - pos).normalize ) //Quat().fromEuler((0.f,-1.f*(math.atan2(lookingAt.z - pos.z, lookingAt.x - pos.x).toFloat + math.Pi.toFloat/2.f),0.f))
    else destRotVec.set( destPose.uf().normalize )

    // yaw error
    val w = p.quat.toEuler()._2
    var dw = math.acos((destRotVec dot rotVec)).toFloat
    dw *= (if( (destRotVec cross rotVec).y > 0.f ) -1.f else 1.f)

    rot = 0.f
    if( math.abs(dw) > (yawThresh*math.Pi/180.f) ){ 
      hover = false
      if( !switchRot ) rot = -rotSpeed else rot = rotSpeed
      rot *= dw * rotK.x
      if( math.abs(rot) > 1.f) rot = rot / math.abs(rot)

    }

    // position error
    val dir = (destPose.pos - tPose.pos)
    val dist = dir.mag
    d_err = dir - err
    err.set(dir)

    control.zero
    if( dist  > posThresh ){
      hover = false
      val cos = math.cos(w)
      val sin = math.sin(w)

      val d = err*kpdd_xy.x + d_err*kpdd_xy.y
      val ud = (err.y*kpdd_z.x + d_err.y*kpdd_z.y) * vmoveSpeed

      //assumes drone oriented 0 degrees looking down negative z axis, (-90 degrees)positive x axis to its right, 
      control.y = (d.x*sin + d.z*cos).toFloat * moveSpeed //forward backward control
      control.x = (d.x*cos - d.z*sin).toFloat * moveSpeed //left right control

      control.z = ud

      if( math.abs(control.x) > 1.f) control.x = control.x / math.abs(control.x)
      if( math.abs(control.y) > 1.f) control.y = control.y / math.abs(control.y)
      if( math.abs(control.z) > 1.f) control.z = control.z / math.abs(control.z)
      
    }else nextWaypoint

    if(hover) drone.hover()
    else if(rotFirst && rot != 0.f) drone.move(0,0,0,rot)
    else drone.move(control.x,control.y,control.z,rot)      
  }


  def getPos(){ outlet(1, Array[Atom](Atom.newAtom("pos"),Atom.newAtom(tPose.pos.x),Atom.newAtom(tPose.pos.y),Atom.newAtom(tPose.pos.z))) }
  def getVel(){ outlet(1, Array[Atom](Atom.newAtom("vel"),Atom.newAtom(tVel.x),Atom.newAtom(tVel.y),Atom.newAtom(tVel.z))) }
  def getGyroAngles(){ outlet(1, Array[Atom](Atom.newAtom("gyroAngles"),Atom.newAtom(gyroAngles.x),Atom.newAtom(gyroAngles.y),Atom.newAtom(gyroAngles.z))) }

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