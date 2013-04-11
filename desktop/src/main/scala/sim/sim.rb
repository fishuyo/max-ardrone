require 'java'

#### Globals / package variables ####

$Seer = Java::com.fishuyo
$Vec3 = $Seer.maths.Vec3
$Camera = $Seer.graphics.Camera
Key = $Seer.io.Keyboard
Pad = $Seer.io.Trackpad

$Main = $Seer.drone.sim.Main
$simControl = $Main.simControl
$simDrone = $Main.simDrone

######## Drone Control Config #########

#$simControl.setMoveSpeed(5.0)
#$simControl.setMaxEuler(0.6)

########### Keyboard input #############
x=0
z=0
y=0
r=0

Key.clear()
Key.use()
Key.bind("f", lambda{ $simControl.toggleFly() })

Key.bind("j", lambda{ x=-0.7; $simControl.move(x,z,y,r) })
Key.bind("l", lambda{ x=0.7; $simControl.move(x,z,y,r) })
Key.bind("i", lambda{ z=-0.7; $simControl.move(x,z,y,r) })
Key.bind("k", lambda{ z=0.7; $simControl.move(x,z,y,r) })
Key.bind("y", lambda{ y=5.0; $simControl.move(x,z,y,r) })
Key.bind("h", lambda{ y=-5.0; $simControl.move(x,z,y,r) })

Key.bindUp("j", lambda{ x=0.0; $simControl.move(x,z,y,r) })
Key.bindUp("l", lambda{ x=0.0; $simControl.move(x,z,y,r) })
Key.bindUp("i", lambda{ z=0.0; $simControl.move(x,z,y,r) })
Key.bindUp("k", lambda{ z=0.0; $simControl.move(x,z,y,r) })
Key.bindUp("y", lambda{ y=0.0; $simControl.move(x,z,y,r) })
Key.bindUp("h", lambda{ y=0.0; $simControl.move(x,z,y,r) })

Key.bind("g", lambda{ $Main.togglePlotFollow() })


######## Trackpad gesture input #########

mx=0.0
my=0.0
mz=0.0
Pad.clear()
Pad.connect()
Pad.bind( lambda{|i,f|           # i -> number of fingers detected
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

	# New step function
	#$simControl.step2( $simDrone.sPose )

	# DroneControl v0.4 step function
	pos = $simDrone.sPose.pos
	$simControl.step( pos.x,pos.y,pos.z,0.0 )
	

	# add data points to plots
	$Main.plots[0].apply($simDrone.sAcceleration.x)
	$Main.plots[1].apply($simControl.expected_a.x)
	$Main.plots[2].apply($simDrone.sVelocity.x)
	$Main.plots[3].apply($simControl.expected_v.x)

	# add Vec3 point to 3d trace of drones position
	$Main.traces[0].apply($simDrone.sPose.pos)

	# have plots follow camera
	if $Main.plotsFollowCam
		i=0
		$Main.plots.foreach do |p|
			pos = $Camera.nav.pos + $Camera.nav.uf()*1.5
			j = i/2
			pos += $Camera.nav.ur()*(j*0.6-1.0)
			pos += $Camera.nav.uu()*0.5

			p.pose.pos.lerpTo( pos, 0.1)
			p.pose.quat.slerpTo( $Camera.nav.quat, 0.1)
			i += 1
		end
	end
end






