package dev.sablelife.module;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

final class PhysicalMotionMathTest {
    @Test void dampingNeverReversesOrCrossesItsDirectionalThreshold(){
        var random=new Random(71);
        for(int i=0;i<10000;i++){
            double threshold=random.nextDouble()*128,speed=random.nextDouble()*200-50,strength=random.nextDouble()*100;
            double after=speed-MotionForceMath.brake(speed,threshold,strength);
            if(speed<=threshold)assertEquals(speed,after,0);
            else{assertTrue(after>=threshold);assertTrue(after<=speed);}
        }
    }
    @Test void zeroStrengthIsExactlyInactiveAndThresholdIsNotACruiseTarget(){
        assertEquals(0,MotionForceMath.brake(100,0,0));
        assertEquals(0,MotionForceMath.brake(1,2,100));
        double v=20;for(int i=0;i<300;i++)v-=MotionForceMath.brake(v,2,50);
        assertEquals(2,v,1e-8);
    }
    @Test void independentAxesCannotAddLinearKineticEnergy(){
        var random=new Random(72);
        for(int i=0;i<10000;i++){
            double speed=random.nextDouble()*80-40,threshold=random.nextDouble()*15,strength=random.nextDouble()*100;
            double after=speed-Math.signum(speed)*MotionForceMath.brake(Math.abs(speed),threshold,strength);
            assertTrue(after*after<=speed*speed+1e-8);
        }
    }
    @Test void speedCeilingOnlyRestrictsAccelerationAndBrakesWithoutOvershooting(){
        assertEquals(1,MotionForceMath.impulse(0,10,1,false,true,16,true));
        assertEquals(10,MotionForceMath.impulse(15,10,100,false,true,16,true));
        assertEquals(0,MotionForceMath.impulse(20,10,100,false,true,16,false));
        assertEquals(-40,MotionForceMath.impulse(20,10,100,false,true,16,true));
        assertEquals(5,MotionForceMath.impulse(0,10,100,true,false,16,true));
        assertEquals(100,MotionForceMath.impulse(0,10,100,false,false,16,true));
    }
    @Test void invalidPhysicalInputsCannotGenerateAnImpulse(){
        assertEquals(0,MotionForceMath.impulse(Double.NaN,1,100,false,false,1,true));
        assertEquals(0,MotionForceMath.impulse(0,0,100,false,false,1,true));
        assertEquals(0,MotionForceMath.impulse(0,1,Double.POSITIVE_INFINITY,false,false,1,true));
    }
}
