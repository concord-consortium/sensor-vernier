package org.concord.sensor.vernier.labquest;

import org.concord.sensor.device.impl.JavaDeviceFactory;
import org.concord.sensor.test.TestInterfaceManager;
import org.junit.Test;

public class TestLabQuestDevice
{
	@Test
	public void testTemperatureAndLight(){
		TestInterfaceManager.testTemperatureAndLight(JavaDeviceFactory.VERNIER_LAB_QUEST);
	}
}
