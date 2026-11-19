package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;

/** Continuous translation of the existing conservative part box, at its current orientation. */
public final class RagdollSweep {
	private RagdollSweep() {}
	public record Hit(double fraction,Vec3 normal) {}

	/** Earliest entering contact, or null. Existing initial penetration remains the adapter's job. */
	public static Hit firstContact(Vec3 from,Vec3 to,Vec3 half,Vec3 obstacleMin,Vec3 obstacleMax){
		if(!finite(from)||!finite(to)||!finite(half)||!finite(obstacleMin)||!finite(obstacleMax)
				||half.x()<0||half.y()<0||half.z()<0
				||obstacleMin.x()>=obstacleMax.x()||obstacleMin.y()>=obstacleMax.y()||obstacleMin.z()>=obstacleMax.z())
			throw new IllegalArgumentException("invalid ragdoll sweep");
		double[] start={from.x(),from.y(),from.z()},end={to.x(),to.y(),to.z()};
		double[] low={obstacleMin.x()-half.x(),obstacleMin.y()-half.y(),obstacleMin.z()-half.z()};
		double[] high={obstacleMax.x()+half.x(),obstacleMax.y()+half.y(),obstacleMax.z()+half.z()};
		if(start[0]>low[0]&&start[0]<high[0]&&start[1]>low[1]&&start[1]<high[1]&&start[2]>low[2]&&start[2]<high[2])return null;
		double enter=Double.NEGATIVE_INFINITY,leave=Double.POSITIVE_INFINITY;Vec3 normal=null;
		for(int axis=0;axis<3;axis++){
			double delta=end[axis]-start[axis];
			if(delta==0){
				// Touching a parallel face is a graze, not entering solid volume.
				if(start[axis]<=low[axis]||start[axis]>=high[axis])return null;
				continue;
			}
			double a=(low[axis]-start[axis])/delta,b=(high[axis]-start[axis])/delta;
			if(a>b){double swap=a;a=b;b=swap;}
			if(a>enter){
				enter=a;double sign=delta>0?-1:1;
				normal=axis==0?new Vec3(sign,0,0):axis==1?new Vec3(0,sign,0):new Vec3(0,0,sign);
			}
			leave=Math.min(leave,b);if(enter>=leave)return null;
		}
		return normal!=null&&enter>=0&&enter<=1&&leave>0?new Hit(enter,normal):null;
	}

	private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
}
