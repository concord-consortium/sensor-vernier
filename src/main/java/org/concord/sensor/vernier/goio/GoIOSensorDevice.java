package org.concord.sensor.vernier.goio;

import org.concord.sensor.ExperimentConfig;
import org.concord.sensor.ExperimentRequest;
import org.concord.sensor.SensorConfig;
import org.concord.sensor.device.DeviceReader;
import org.concord.sensor.device.impl.AbstractSensorDevice;
import org.concord.sensor.device.impl.SerialPortParams;
import org.concord.sensor.goio.jna.GoIOLibrary;
import org.concord.sensor.goio.jna.GoIOSensor;
import org.concord.sensor.impl.ExperimentConfigImpl;
import org.concord.sensor.vernier.VernierSensor;
import org.concord.sensor.vernier.VernierSensorDevice;

//JVDH API
//     Start here, look at LabQuestSensorDevice.java
//     and                 VernierSensor, SensorID
public class GoIOSensorDevice extends AbstractSensorDevice implements
		VernierSensorDevice 
{
	GoIOLibrary goio;
	private String errorMessage;
	private GoIOSensor currentGoDevice;
		
	public GoIOSensorDevice() {
    	deviceLabel = "GIO";
    	goio = new GoIOLibrary();
    	
    	// TODO add some exception handling here for when the native library can't be loaded
    	goio.initLibrary();
	}
	
	@Override
	public void log(String message) {
		super.log(message);
	}
	
	@Override
	protected SerialPortParams getSerialPortParams() {
		return null;
	}

	@Override
	protected boolean initializeOpenPort(String portName) {
		return false;
	}

	/**
	 * Because we are using a api that abstracts the usb and/or serial port connection
	 * we override this to initialize the api instead of opening the port. 
	 * 
	 * 
	 * @see org.concord.sensor.device.impl.AbstractSensorDevice#openPort()
	 */
	@Override
	public void open(String portParams) {
		if(goio == null){
			return;
		}
		
		if(goio.init() != 0) {
			errorMessage = "Can't init go_io, You have another program using the Go device\n";
		}		
	}

	/**
	 * We don't have a port, but openPort is called by the auto configure code.
	 * 
	 * @see org.concord.sensor.device.impl.AbstractSensorDevice#openPort()
	 */
	protected boolean openPort()
	{
		return goio != null;
	}
		
	public void close()
	{
		// already closed
		if(goio == null){
			return;
		}

		// TODO if we have open sensor handles we need to close them
		if(currentGoDevice != null){
			currentGoDevice.close();
			currentGoDevice = null;
		}
		
		goio.uninit();
		goio = null;
	}

	
	public boolean canDetectSensors() {		
		return true;
	}

	@Override
	protected boolean hasNonAutoIdSensors() {
		// FIXME they are non autoid sensors that can be used with the golink
		//   and old projects used them.  However the current AbstractSensorDevice code doesn't
		//   handle this case particularly well, so for now we will return false here
		return false;
	}
	
	public ExperimentConfig configure(ExperimentRequest request) {
		return autoIdConfigure(request);
	}

	public ExperimentConfig getCurrentConfig() {
		if(currentGoDevice == null){
			currentGoDevice = openGoDevice();
		}

		if(currentGoDevice == null) {
			// FIXME this should probably return something else
			return null;
		}

		ExperimentConfigImpl expConfig = new ExperimentConfigImpl();
		
		// FIXME this should use the device name based on what is attached 
		expConfig.setDeviceName(currentGoDevice.getDeviceLabel());

		expConfig.setExactPeriod(true);
		expConfig.setPeriod((float)currentGoDevice.getMeasurementPeriod());
		expConfig.setDataReadPeriod(expConfig.getPeriod());

		SensorConfig [] sensorConfigs = new SensorConfig[1];
		expConfig.setSensorConfigs(sensorConfigs);

		int channelType = VernierSensor.CHANNEL_TYPE_ANALOG;
		if(currentGoDevice.isGoMotion()) {
			channelType = VernierSensor.CHANNEL_TYPE_DIGITAL;
		}
		VernierSensor sensor = new VernierSensor(this, devService, 0, channelType);

		sensor.setupSensor(currentGoDevice.getAttachedSensorId(), null);
		sensorConfigs[0] = sensor;
		
		expConfig.setValid(true);
		
		return expConfig;
	}

	protected GoIOSensor openGoDevice() {
		GoIOSensor gSensor = goio.getFirstSensor(); 
		
		if(gSensor == null){
			return null;			
		}
		
		// This will lock the device to this thread
		// In the sensor-native code we then unlock it.  It isn't clear why, 
		// perhaps so any thread can access it.  A more safe approach would be to use
		// the a single thread delegator to force all access on one thread.
		gSensor.open();
		
		return gSensor;
	}
	
	public String getErrorMessage(int error) {
		if(errorMessage == null){
			return "Unkown Error";
		}
		
		return errorMessage;
	}

	public String getVendorName() {
		return "Vernier";		
	}

	public String getDeviceName() {
		// TODO this should change after the device is detected to represent the actual device
		return "GoIO";
	}

	public int read(float[] values, int offset, int nextSampleOffset,
			DeviceReader reader) {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean start() {
		// TODO Auto-generated method stub
		return false;
	}

	public void stop(boolean wasRunning) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isAttached() {
		return goio.isGoDeviceAttached();
	}
}
