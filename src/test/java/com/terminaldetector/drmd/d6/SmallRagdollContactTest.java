package com.terminaldetector.drmd.d6;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmallRagdollContactTest {
	@Test void validSmallOrbInertiaRetainsItsInverseAtBothSupportedSmallScales(){
		for(double scale:new double[]{.0625,.125,1,16}){
			double mass=6*scale*scale*scale,half=.43*scale,inertia=(2.0/3)*mass*half*half;
			var matrix=D6Mat3.diagonal(inertia,inertia,inertia);var inverse=matrix.inverse();assertNotNull(inverse,"valid isotropic inertia at scale="+scale);
			assertEquals(1,inverse.transform(new Vec3(inertia,0,0)).x(),1e-12);
		}
	}
	@Test void actualSmallMassPointContactStopsClosingWithoutLosingItsAngularResponse(){
		double scale=.125,mass=6*scale*scale*scale,half=.43*scale,inertia=(2.0/3)*mass*half*half;
		assertTrue(inertia*inertia*inertia<1e-12,"negative control: old absolute cutoff rejected this real valid rig");
		var body=new D6PhysicsBody().withMass(mass).withInertia(D6Mat3.diagonal(inertia,inertia,inertia))
				.withLinearVelocity(new Vec3(35,0,0)).withAngularMomentum(new Vec3(0,0,-12*inertia));
		var offset=new Vec3(half,0,0);var normal=new Vec3(-1,0,0);
		assertEquals(1/mass,body.pointImpulseDenominator(offset,normal),1e-10);assertEquals(12,body.angularVelocity().length(),1e-12);
		double before=energy(body);assertTrue(body.contactImpulse(offset,normal,.72)>0);
		assertEquals(0,body.linearVelocity().x(),1e-10);assertTrue(body.velocityAtPoint(offset).dot(normal)>=-1e-10);assertTrue(energy(body)<=before+1e-10);
	}
	@Test void conditioningDecisionIsRelativeToMatrixScale(){
		for(double scale:new double[]{1e-100,1,1e100}){
			var matrix=D6Mat3.diagonal(2*scale,3*scale,7*scale);var inverse=matrix.inverse();assertNotNull(inverse);
			var result=inverse.transform(matrix.transform(new Vec3(1,-2,3)));assertTrue(result.minus(new Vec3(1,-2,3)).length()<1e-11);
			assertNull(D6Mat3.diagonal(1e-14*scale,scale,scale).inverse(),"same relative near-singularity stays refused");
		}
		assertNull(D6Mat3.ZERO.inverse());assertNull(D6Mat3.diagonal(Double.NaN,1,1).inverse());
	}
	private static double energy(D6PhysicsBody b){return .5*b.mass()*b.linearVelocity().lengthSquared()+.5*b.angularVelocity().dot(b.angularMomentum());}
}
