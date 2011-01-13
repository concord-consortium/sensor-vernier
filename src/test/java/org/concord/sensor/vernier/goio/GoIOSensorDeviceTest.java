package org.concord.sensor.vernier.goio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.swing.JOptionPane;

import org.concord.sensor.ExperimentConfig;
import org.concord.sensor.SensorConfig;
import org.concord.sensor.SensorRequest;
import org.concord.sensor.device.SensorDeviceTest;
import org.concord.sensor.impl.ExperimentRequestImpl;
import org.concord.sensor.impl.SensorRequestImpl;
import org.junit.Test;


public class GoIOSensorDeviceTest extends SensorDeviceTest {
	public void setup(){
		device = new GoIOSensorDevice();
	}
	
	@Test
	public void testRepeatGetCurrentConfig(){
		JOptionPane.showMessageDialog(null, "Attach the " + getDeviceLabel() +
			" and no sensor");

		prepareDevice();
		
		ExperimentConfig experimentConfig = device.getCurrentConfig();
		assertNotNull("Non null experiment config", experimentConfig);
		assertEquals("Unknown sensor type", SensorConfig.QUANTITY_UNKNOWN,
				experimentConfig.getSensorConfigs()[0].getType());

		byte ddsChecksum = ((GoIOSensorDevice)device).currentGoDevice.getDDSCheckSum();
		
		// The force sensor is used here because it is a smart sensor that has a DDS record
		// stored in the sensor.  The temperature sensor does not have a DDS record.
		JOptionPane.showMessageDialog(null, "Attach the " + getDeviceLabel() +
		" and a force sensor");
		
		experimentConfig = device.getCurrentConfig();
		assertNotNull("Non null experiment config", experimentConfig);
		assertEquals("Force sensor", SensorConfig.QUANTITY_FORCE,
				experimentConfig.getSensorConfigs()[0].getType());
		
		// however I believe the goio sdk will not have loaded in the correct calibration
		byte ddsChecksum2 = ((GoIOSensorDevice)device).currentGoDevice.getDDSCheckSum();
		assertTrue("Checksum of no device should be different than with a device", 
				ddsChecksum != ddsChecksum2);
	}	

	@Test
	public void testRawVoltage2Collection() throws InterruptedException{
		// This isn't a good test because the 10V channel doesn't report a 
		// very different value from the 5V channel.  This test would require
		// the actual header.
		JOptionPane.showMessageDialog(null, "Attach the " + getDeviceLabel() +
		" and a temperature sensor");

		prepareDevice();

		ExperimentRequestImpl experimentRequest = new ExperimentRequestImpl();
		experimentRequest.setPeriod(0.1f);
		SensorRequestImpl sensorRequest = new SensorRequestImpl();
		experimentRequest.setSensorRequests(new SensorRequest[] {sensorRequest});
		sensorRequest.setType(SensorConfig.QUANTITY_RAW_VOLTAGE_2);

		ExperimentConfig experimentConfig = device.configure(experimentRequest);
		assertNotNull("Non null experiment config", experimentConfig);
		assertTrue("Correctly configured a raw voltage sensor", experimentConfig.isValid());

		assertTrue("Device started correctly", device.start());
		
		float[] values = new float[10000];		
		int count = device.read(values, 0, 1, null);
		assertTrue("Read doesn't return error", count >=0);

		Thread.sleep(500);
		count = device.read(values, 0, 1, null);
		assertTrue("Read got some valid values", count > 0);		
		assertTrue("Voltage value is floating", values[0] >= 2 && values[0] <= 3);
				
		device.stop(true);				
	}

}
