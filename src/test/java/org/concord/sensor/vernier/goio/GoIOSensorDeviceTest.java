package org.concord.sensor.vernier.goio;

import org.concord.sensor.device.SensorDeviceTest;


public class GoIOSensorDeviceTest extends SensorDeviceTest {
	public void setup(){
		device = new GoIOSensorDevice();
	}
}
