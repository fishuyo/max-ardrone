require 'java'

#### import packages ####
module M
  include_package "com.fishuyo.io"
  include_package "com.fishuyo.maths"
  include_package "com.fishuyo.spatial"
  include_package "com.fishuyo.graphics"
  include_package "com.fishuyo.util"
  include_package "com.fishuyo.drone.sim"
end

class Object
  class << self
    alias :const_missing_old :const_missing
    def const_missing c
      M.const_get c
    end
  end
end
###########################

$simControl = Main.simControl
$simDrone = Main.simDrone

$simDrone.sPose.setIdentity() #pos.set(0,0,0)
Main.traces[0].color2.set(0,1,0)

######## Drone Control Config #########

$simControl.setMoveSpeed(1.0)
#$simControl.setMaxEuler(0.3)

########### Keyboard input #############
x=0
z=0
y=0
r=0

Keyboard.clear()
Keyboard.use()
Keyboard.bind("f", lambda{ $simControl.toggleFly() })

Keyboard.bind("j", lambda{ x=-0.7; $simControl.move(x,z,y,r) })
Keyboard.bind("l", lambda{ x=0.7; $simControl.move(x,z,y,r) })
Keyboard.bind("i", lambda{ z=-0.7; $simControl.move(x,z,y,r) })
Keyboard.bind("k", lambda{ z=0.7; $simControl.move(x,z,y,r) })
Keyboard.bind("y", lambda{ y=5.0; $simControl.move(x,z,y,r) })
Keyboard.bind("h", lambda{ y=-5.0; $simControl.move(x,z,y,r) })

Keyboard.bindUp("j", lambda{ x=0.0; $simControl.move(x,z,y,r) })
Keyboard.bindUp("l", lambda{ x=0.0; $simControl.move(x,z,y,r) })
Keyboard.bindUp("i", lambda{ z=0.0; $simControl.move(x,z,y,r) })
Keyboard.bindUp("k", lambda{ z=0.0; $simControl.move(x,z,y,r) })
Keyboard.bindUp("y", lambda{ y=0.0; $simControl.move(x,z,y,r) })
Keyboard.bindUp("h", lambda{ y=0.0; $simControl.move(x,z,y,r) })

Keyboard.bind("g", lambda{ Main.togglePlotFollow() })
Keyboard.bind("n", lambda{ 
	r = Randf.apply(-2.0,2.0,false)
	$simControl.addWaypoint(r[],1.0,r[],0)
})


######## Trackpad gesture input #########

mx=0.0
my=0.0
mz=0.0
Trackpad.clear()
Trackpad.connect()
Trackpad.bind( lambda{|i,f|           # i -> number of fingers detected
							     # f -> array of (x,y,dx,dy)
	
	# use two fingers to change destination on xz plane
	if i == 2					 
		mx = mx + f[2]*0.05
		mz = mz + f[3]*-0.05
		$simControl.moveTo(mx,my,mz,0.0)
	# use three fingers to change destination on xy plane
	elsif i == 3					
		mx = mx + f[2]*0.05
		my = my + f[3]*0.05
		$simControl.moveTo(mx,my,mz,0.0)
	end
	if mx > 6.0 then mx = 6.0
	elsif mx < -6.0 then mx = -6.0 end
	if my > 6.0 then my = 6.0
	elsif my < 0.0 then my = 0.0 end
	if mz > 6.0 then mz = 6.0
	elsif mz < -6.0 then mz = -6.0 end
})


######## Step function called each frame #######

def step(dt)

	$simControl.kpdd_xy.set(0.5,10.0,0)
	# New step function
	#$simControl.step2( $simDrone.sPose )

	# DroneControl v0.4 step function
	pos = $simDrone.sPose.pos
	$simControl.step( pos.x,pos.y,pos.z,0.0 )
	

	# add data points to plots
	Main.plots[0].apply($simDrone.sAcceleration.x)
	Main.plots[1].apply($simControl.expected_a.x)
	Main.plots[2].apply($simDrone.sVelocity.x)
	Main.plots[3].apply($simControl.expected_v.x)
	d0 = $simControl.d0.x
	Main.plots[4].apply($simDrone.sPose.pos.x-d0)
	Main.plots[5].apply($simControl.expected_x.x-d0)

	# add Vec3 point to 3d trace of drones position
	Main.traces[0].apply($simDrone.sPose.pos)

	# have plots follow camera
	if Main.plotsFollowCam
		i=0
		Main.plots.foreach do |p|
			pos = Camera.nav.pos + Camera.nav.uf()*1.5
			j = i/2
			pos += Camera.nav.ur()*(j*0.6-1.0)
			pos += Camera.nav.uu()*0.5

			p.pose.pos.lerpTo( pos, 0.1)
			p.pose.quat.slerpTo( Camera.nav.quat, 0.1)
			i += 1
		end
	end
end






