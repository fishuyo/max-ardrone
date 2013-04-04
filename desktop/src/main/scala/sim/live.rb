require 'java'

Seer = Java::com.fishuyo
Key = Seer.io.Keyboard
Pad = Seer.io.Trackpad

drone = Seer.drone.sim.Main.drone
#drone.drone.s.set(1.0,1.0,1.0  )
#drone.drone.p.pos.set(0,0,0)
#drone.velocity.set(0,0,0)

#drone.plot.color.set(4.8,5.3,1.3)

x=0
z=0
y=0
r=0

Key.clear()
Key.use()
Key.bind("j",lambda{x=-0.7;drone.move(x,z,y,r)})
Key.bind("l",lambda{x=0.7;drone.move(x,z,y,r)})
Key.bind("i",lambda{z=-0.7;drone.move(x,z,y,r)})
Key.bind("k",lambda{z=0.7;drone.move(x,z,y,r)})
Key.bindUp("j",lambda{x=0.0;drone.move(x,z,y,r)})
Key.bindUp("l",lambda{x=0.0;drone.move(x,z,y,r)})
Key.bindUp("i",lambda{z=0.0;drone.move(x,z,y,r)})
Key.bindUp("k",lambda{z=0.0;drone.move(x,z,y,r)})
Key.bind("y",lambda{y=5.0;drone.move(x,z,y,r)})
Key.bindUp("y",lambda{y=0.0;drone.move(x,z,y,r)})
Key.bind("h",lambda{y=-5.0;drone.move(x,z,y,r)})
Key.bindUp("h",lambda{y=0.0;drone.move(x,z,y,r)})
Key.bind("p",lambda{
	if drone.flying == true
		drone.land()
	else
		drone.takeOff()
	end
})

mx=0.0
my=0.0
Pad.bind( lambda{|i,f|
	if i == 2
		mx = mx + f[2]*0.05
		my = my + f[3]*-0.05
		drone.moveTo(mx,0.0,my,0.0)
		#drone.moveTo(2.0*f[0],1.0,-2.0*f[1],0.0)
	end
})

#drone.moveTo(1.0,1.0,1.0,0.0)
#drone.setMoveSpeed(5.0)
#drone.setMaxEuler(0.6)
#Seer.graphics.Shader[1]






