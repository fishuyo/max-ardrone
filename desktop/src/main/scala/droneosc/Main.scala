package com.fishuyo
package drone
package DroneControlOSC

import maths._
import io._
import dynamic._

import de.sciss.osc._
import scala.collection.mutable.Map


object Main extends App {

  val control = new DroneControl

  val live = new Ruby("src/main/scala/droneosc/droneosc.rb")

  DroneOSC.dump = true
  DroneOSC.listen()

}

object DroneOSC{

	var dump = false
	val sync = new AnyRef
	var callbacks = Map[String,(Float*)=>Unit]()
	//var callbacks2 = Map[String,(Float,Float)=>Unit]()

	def clear() = callbacks.clear()
	def bind( s:String, f:(Float*)=>Unit) = callbacks += s -> f

	def listen(port:Int=8000){
		import Main.control

		val cfg         = UDP.Config()
	  cfg.localPort   = port  // 0x53 0x4F or 'SO'
	  val rcv         = UDP.Receiver( cfg )

	  def f(s:String)(v:Float*) = {println(s)}

	  if( dump ) rcv.dump( Dump.Both )
	  rcv.action = {
	  	case (Message("/connect", ip:String), _) => control.ip = ip; control.connect()
	  	case (Message("/disconnect"), _) => control.disconnect
	  	case (Message("/takeOff"), _) => control.takeOff
	  	case (Message("/land"), _) => control.land
	  	case (Message("/toggleFly"), _) => control.toggleFly
	  	case (Message("/move",a:Float,b:Float,c:Float,d:Float), _) => control.move(a,b,c,d)
	  	case (Message("/moveTo",a:Float,b:Float,c:Float,d:Float), _) => control.moveTo(a,b,c,d)

	  	case (Message("/quit"), _) => println("quit.."); rcv.close(); sys.exit(0);
	    // case (Message( name, vals @ _* ), _) =>
	    //   callbacks.getOrElse(name, f(name)_ )(vals.asInstanceOf[Seq[Float]]:_*)
	     
	    case (p, addr) => println( "Ignoring: " + p + " from " + addr )
	  }
	  rcv.connect()
	}
}



