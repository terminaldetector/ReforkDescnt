package com.terminaldetector.drmd.d6;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContactImpulseTest {
 @Test void contactStopsClosingAndReducesKineticEnergy(){
  var mass=new D6MassProperties();mass.addBlock(new Vec3(0,0,0),2);
  var body=new D6PhysicsBody().withMassProperties(mass).withLinearVelocity(new Vec3(3,-4,0));
  var offset=new Vec3(.5,-.5,0);double before=energy(body);
  assertTrue(body.contactImpulse(offset,new Vec3(0,1,0),.6)>0);
  assertTrue(body.velocityAtPoint(offset).y()>-1e-8);assertTrue(energy(body)<=before+1e-8);
 }
 @Test void separatingAndMasslessContactDoesNotPullBodyIntoFloor(){
  var mass=new D6MassProperties();mass.addBlock(new Vec3(0,0,0),1);
  var body=new D6PhysicsBody().withMassProperties(mass).withLinearVelocity(new Vec3(0,2,0));
  assertEquals(0,body.contactImpulse(new Vec3(0,-.5,0),new Vec3(0,1,0),.6));
  assertEquals(2,body.linearVelocity().y());
  assertEquals(0,new D6PhysicsBody().contactImpulse(new Vec3(0,0,0),new Vec3(0,1,0),.6));
 }
 private static double energy(D6PhysicsBody b){return .5*b.mass()*b.linearVelocity().lengthSquared()+.5*b.angularVelocity().dot(b.angularMomentum());}
}
