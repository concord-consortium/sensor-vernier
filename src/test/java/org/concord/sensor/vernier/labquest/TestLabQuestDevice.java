package org.concord.sensor.vernier.labquest;

import org.concord.sensor.device.ExampleSensorApp;
import org.concord.sensor.device.impl.DeviceID;
import org.junit.Test;

public class TestLabQuestDevice
{
	@Test
	public void testTemperatureAndLight(){
		ExampleSensorApp app = new ExampleSensorApp(){
			@Override
			public void setup() {
				// TODO Auto-generated method stub
				deviceId = DeviceID.VERNIER_LAB_QUEST;
			}
		};
		app.testAllConnectedProbes();
	}
}
