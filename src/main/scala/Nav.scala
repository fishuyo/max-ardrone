/* Port of Allocore al_Pose.hpp originally by
	Wesley Smith, 2010, wesley.hoke@gmail.com
	Lance Putnam, 2010, putnam.lance@gmail.com
	Graham Wakefield, 2010, grrrwaaa@gmail.com
	Pablo Colapinto, 2010, wolftype@gmail.com
*/


package com.fishuyo
package spatial

import maths._

object Pose {
	def apply( pos:Vec3=Vec3(0), quat:Quat=Quat(1,0,0,0)) = new Pose(pos,quat)
}
/** Class Pose represents a position and orientation in 3d space */
class Pose( var pos:Vec3=Vec3(0), var quat:Quat=Quat(1,0,0,0) ){
  
  def vec = pos

  /** translate and rotate pose by another pose */
  def *(p:Pose) = new Pose( pos+p.pos, quat*p.quat)
  def *=(p:Pose) = { pos += p.pos; quat *= p.quat; this }

  def set(p:Pose) = { pos.set(p.pos); quat.set(p.quat)}

  //return Azimuth Elevation and distance to point v
  //def getAED(v:Vec3): (Float,Float,Float) = {}

  def getUnitVectors():(Vec3,Vec3,Vec3) = (quat.toX, quat.toY, quat.toZ)
  def getDirVectors():(Vec3,Vec3,Vec3) = (quat.toX, quat.toY, -quat.toZ)
  def ur() = quat.toX
  def uu() = quat.toY
  def uf() = -quat.toZ

  def setIdentity = { pos.zero(); quat.setIdentity() }

  /** return linear interpolated Pose from this to p by amount d*/
  def lerp(p:Pose, d:Float) = new Pose( pos.lerp(p.pos, d), quat.slerp(p.quat, d) )


}

/** Pose that moves through space */
class Nav( p:Vec3=Vec3(0) ) extends Pose(p) {
	var smooth = 0.f
	var scale = 1.f
	var vel = Vec3(0); var velS = Vec3(0)
	var angVel = Vec3(0); var angVelS = Vec3(0)
	var turn = Vec3(0); var nudge = Vec3(0)
	var mUR = Vec3(0); var mUU = Vec3(0); var mUF = Vec3(0)

	def view(eu:(Float,Float,Float)){ view(Quat().fromEuler(eu)) }
	def view(q:Quat){ quat = q; updateDirVectors() }

	def velPose() = new Pose( velS, Quat().fromEuler(angVelS) )

	def stop() = {
		vel.zero; velS.zero;
		angVel.zero; angVelS.zero;
		turn.zero; nudge.zero;
		updateDirVectors()
	}

	def moveToOrigin() = {
		quat.setIdentity; pos.zero
		stop
	}

	def lookAt( p: Vec3, amt:Float=1.f) = {

	}
	def goTo( p:Vec3, amt:Float=1.f) = {
		val dir = (p - pos).normalize
	}

	def updateDirVectors() = { quat = quat.normalize; mUR = ur(); mUU = uu(); mUF = uf() }

	def step( dt:Float ) = {
		scale = dt
		val amt = 1.f - smooth

		//smooth velocities
		velS = velS.lerp( vel*dt + nudge, amt )
		angVelS = angVelS.lerp( angVel*dt + turn, amt )

		nudge.zero; turn.zero

		//rotate
		quat *= Quat().fromEuler(angVelS)
		updateDirVectors()

		//move
		pos.x += velS dot Vec3( mUR.x, mUU.x, mUF.x)
		pos.y += velS dot Vec3( mUR.y, mUU.y, mUF.y)
		pos.z += velS dot Vec3( mUR.z, mUU.z, mUF.z)

	}
}


