package org.concord.sensor.vernier.labquest;

import java.util.ArrayList;

import org.concord.sensor.ExperimentConfig;
import org.concord.sensor.ExperimentRequest;
import org.concord.sensor.SensorConfig;
import org.concord.sensor.device.DeviceReader;
import org.concord.sensor.device.impl.AbstractSensorDevice;
import org.concord.sensor.device.impl.SerialPortParams;
import org.concord.sensor.impl.ExperimentConfigImpl;
import org.concord.sensor.labquest.jna.GSensorDDSMem;
import org.concord.sensor.labquest.jna.LabQuest;
import org.concord.sensor.labquest.jna.LabQuestException;
import org.concord.sensor.labquest.jna.LabQuestLibrary;
import org.concord.sensor.labquest.jna.NGIOLibrary;
import org.concord.sensor.labquest.jna.NGIOSourceCmds;
import org.concord.sensor.vernier.VernierSensor;
import org.concord.sensor.vernier.VernierSensorDevice;

public class LabQuestSensorDevice extends AbstractSensorDevice
	implements VernierSensorDevice
{	
	private static final double SPEED_OF_SOUND_M_PER_USEC = 343.0/1000000.0;
	private LabQuestLibrary labQuestLibrary;
	private LabQuest labQuest;
	int [] pMeasurementsBuf = new int [1000];
	long [] pTimestampsBuf = new long [1000];
	private float lastMDValue = 0;
	
	public LabQuestSensorDevice() {
    	deviceLabel = "LQ";
    	
    	labQuestLibrary = new LabQuestLibrary();
    	labQuestLibrary.init();
		
    	Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run() {
				if(labQuest == null){
					return;
				}
				
				System.err.println("Closing LabQuestSensorDevice.");

				// Try to make sure the labquest is closed
				// If this not done then the usb has to disconnect before running again
				// And it might even cause a crash on windows.				
				close();
			}
		});    	

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
		try {
			String firstDeviceName = labQuestLibrary.getFirstDeviceName();
			if(firstDeviceName == null){
				return;
			}
			labQuest = labQuestLibrary.openDevice(firstDeviceName);
			labQuest.acquireExclusiveOwnership();
		} catch (LabQuestException e) {
			closeAfterException(e);
		}
	}
	
	public void close()
	{
		// already closed
		if(labQuest == null){
			return;
		}
		
		try {
			labQuest.close();
			labQuest = null;
		} catch (LabQuestException e) {
			e.printStackTrace();
			// set it to null even if we have an exception because that is the 
			// indication that the device is not attached
			labQuest = null;
		}
	}
	
	/**
	 * We don't have a port, but openPort is called by the auto configure code.
	 * 
	 * @see org.concord.sensor.device.impl.AbstractSensorDevice#openPort()
	 */
	protected boolean openPort()
	{
		return labQuest != null;
	}
	
	public boolean isAttached()
	{
		return labQuest != null;
	}
	
	public ExperimentConfig getCurrentConfig() {
		ExperimentConfigImpl expConfig = new ExperimentConfigImpl();
		expConfig.setDeviceName("LabQuest");

		// read the sensor ids of each of the ports to see what is
		// attached.  
		ArrayList sensorConfigList = new ArrayList();

		try {
			for(byte i=1; i<7; i++){
				int sensorId = -1;
				sensorId = labQuest.getSensorId(i);

				// only add the sensor if we can identify that it is really 
				// there.  There might be some trick we can use to see if 
				// something is attached, by reading a value and seeing if it
				// is different than the baseline value.
				if(sensorId <= 0){
					continue;
				}

				GSensorDDSMem sensorDDSMem = null;
				if(sensorId >= 20){
					labQuest.ddsMemReadRecord(NGIOSourceCmds.CHANNEL_ID_ANALOG1, false);
					sensorDDSMem = labQuest.ddsMemGetRecord(NGIOSourceCmds.CHANNEL_ID_ANALOG1);
				}


				int channelType = VernierSensor.CHANNEL_TYPE_ANALOG;
				if(i >= 5){
					channelType = VernierSensor.CHANNEL_TYPE_DIGITAL;
				}

				VernierSensor sensorConfig = 
					new VernierSensor(LabQuestSensorDevice.this, devService, (int)i,
							channelType);

				// translate the vernier id to the SenorConfig id
				sensorConfig.setupSensor(sensorId, null);
				sensorConfigList.add(sensorConfig);
			}

			int numSensors = sensorConfigList.size();
			if(numSensors == 0){
				expConfig.setSensorConfigs(null);			
			} else {
				SensorConfig [] sensorConfigArr = 
					(SensorConfig[]) sensorConfigList.toArray(
							new SensorConfig[sensorConfigList.size()]);
				expConfig.setSensorConfigs(sensorConfigArr);
			}

			expConfig.setExactPeriod(true);
		} catch (LabQuestException e) {
			if(expConfig != null){
				// If we got an exception then we want the caller to know this as soon
				// as possible. 
				expConfig = null;
			}
			closeAfterException(e);
		}

		return expConfig;
	}

	public String getErrorMessage(int error) {
		return "Unknown LabQuest Error";
	}

	public boolean start() {
		try {
			// get the current channel from the currentConfig
			SensorConfig [] sensors = currentConfig.getSensorConfigs();

			float period = currentConfig.getPeriod();
			labQuest.setMeasurementPeriod((byte) -1, period);

			int mask = 0;
			for (SensorConfig sensorConfig : sensors) {
				int channel = sensorConfig.getPort();
				// clear any previous data 
				labQuest.clearIO((byte)channel);
				mask = mask | (1 << channel);

				VernierSensor vSensor = (VernierSensor) sensorConfig;
				if(vSensor.getVernierProbeType() == VernierSensor.kProbeTypeMD){
					labQuest.setSamplingMode((byte)channel, 
							NGIOSourceCmds.SAMPLING_MODE_PERIODIC_MOTION_DETECT);
					labQuest.setMeasurementPeriod((byte)channel, period);
				}

			}

			// need to set the channel mask based on the current configuration
			labQuest.setSensorChannelEnableMask(mask);
			labQuest.startMeasurements();
			return true;
		} catch (LabQuestException e) {
			closeAfterException(e);
			return false;
		}				
	}
	
	/**
	 * This call could be slow.  It seems to require several back and forth communications to the
	 * device, however it might also be the case that library is keeping all the data in its
	 * internal buffer and the device is just streaming it.   In that case this should be fast.  
	 * 
	 * @see org.concord.sensor.device.SensorDevice#read(float[], int, int, org.concord.sensor.device.DeviceReader)
	 */
	public int read(float[] values, int offset, int nextSampleOffset,
			DeviceReader reader) 
	{
		try {
			// get the current channels from the currentConfig
			SensorConfig [] sensors = currentConfig.getSensorConfigs();

			// We go through all of the sensors first to see how much data is available
			// then we only read out the min available, so when it is combined 
			// all the channels have the same length.
			int minAvailable = Integer.MAX_VALUE;
			for (int sensorIndex = 0; sensorIndex<sensors.length; sensorIndex++){
				VernierSensor sensorConfig = (VernierSensor) sensors[sensorIndex];
				int channel = sensorConfig.getPort();
				int available = labQuest.getNumberOfMeasurementsAvailable((byte)channel);
				if(sensorConfig.getVernierProbeType() == VernierSensor.kProbeTypeMD){
					// this is a motion sensor, it has 2 measurements (ping and echo) 
					// for each value.  
					available = available / 2;
				}

				if(available < minAvailable){
					minAvailable = available;
				}
			}					

			assert(minAvailable != Integer.MAX_VALUE);

			int mask = 0;
			int numMeasurements = 0;
			for (int sensorIndex = 0; sensorIndex<sensors.length; sensorIndex++){
				VernierSensor sensorConfig = (VernierSensor) sensors[sensorIndex];
				int channel = sensorConfig.getPort();
				if(sensorConfig.getVernierProbeType() == VernierSensor.kProbeTypeAnalog5V){
					numMeasurements = labQuest.readRawMeasurementsAnalog((byte)channel, 
							pMeasurementsBuf, minAvailable);
					for(int i=0; i<numMeasurements; i++){

						float calibratedData = Float.NaN;
						if(sensorConfig.getCalibration() != null){
							float voltage = labQuest.convertToVoltage((byte)channel, 
									pMeasurementsBuf[i], GSensorDDSMem.kProbeTypeAnalog5V);
							calibratedData = sensorConfig.getCalibration().calibrate(voltage);
						} else {
							calibratedData = labQuest.calibrateData2(
									(byte)channel, pMeasurementsBuf[i]);
						}
						values[offset + sensorIndex + i*nextSampleOffset] = calibratedData;
					}
				} else if(sensorConfig.getVernierProbeType() == VernierSensor.kProbeTypeMD){
					int numReadings = labQuest.readRawMeasurementsMotion((byte)channel, 
							pMeasurementsBuf, pTimestampsBuf, minAvailable*2);

					// match up ping/echo pairs and throw out false echo pairs.  
					// fill any false echos with the previous value.
					numMeasurements = computeDistanceMeasurements(numReadings,
							values, offset + sensorIndex, nextSampleOffset);
				}
			}

			return numMeasurements;
		} catch (LabQuestException e) {
			closeAfterException(e);
			return -1;
		} 
	}

	private int computeDistanceMeasurements(int numReadings,
			float[] values, int startIndex, int nextSampleOffset) {
		
		long echo;
		int numValues = 0;
		long ping = -1;
		
		// the last value is recorded in case we get a false ping.  In that case we just replay
		// the last good value.  This keeps the data going at a fixed period.
		// the last value is a field in case we get a false ping as the last reading in this
		// method call.  With it a being a field we can continue later. 		
		
		for(int i=0; i<numReadings; i++){
			switch(pMeasurementsBuf[i]){
			case NGIOLibrary.MEAS_MOTION_DETECTOR_PING:
				ping = pTimestampsBuf[i];
				break;
			case NGIOLibrary.MEAS_MOTION_DETECTOR_ECHO:
				echo = pTimestampsBuf[i];
				lastMDValue = (float)(0.5*((double)(echo - ping))*SPEED_OF_SOUND_M_PER_USEC);
				values[startIndex + numValues*nextSampleOffset] = lastMDValue;
				numValues ++;
				// set the ping to -1 so we can detect errors more easily
				ping = -1;
				break;
			case NGIOLibrary.MEAS_MOTION_DETECTOR_FALSE_ECHO:
				// use the last value, this will give us 0 for the first time it is collected
				values[startIndex + numValues*nextSampleOffset] = lastMDValue;
				numValues ++;
				ping = -1;
				break;
			}
		}
				
		return numValues;
	}			

	public void stop(boolean wasRunning) 
	{
		try {					
			// get the current channel from the currentConfig
			labQuest.stopMeasurements();					
		} catch (LabQuestException e) {
			closeAfterException(e);
		}
	}

	public ExperimentConfig configure(ExperimentRequest request) {
    	return autoIdConfigure(request);
	}

	/**
	 * This device only supports usb, so it needs no serial port
	 * params.
	 * 
	 * @see org.concord.sensor.device.impl.AbstractSensorDevice#getSerialPortParams()
	 */
	protected SerialPortParams getSerialPortParams() {
		return null;
	}

	/**
	 * We don't really have a port so nothing needs to be done here.
	 * 
	 * @see org.concord.sensor.device.impl.AbstractSensorDevice#initializeOpenPort(java.lang.String)
	 */
	@Override
	protected boolean initializeOpenPort(String portName) {
		return false;
	}

	public boolean canDetectSensors() {
		return true;
	}

	/**
	 * Increase the visibility of the log by overriding it here
	 * that  
	 * 
	 * @see org.concord.sensor.device.impl.AbstractSensorDevice#log(java.lang.String)
	 */
	public void log(String message)
	{
	    super.log(message);
	}

	private void closeAfterException(LabQuestException e) {
		e.printStackTrace();

		if(labQuest != null){
			try {
				labQuest.close();
			} catch (LabQuestException e1) {
				// an error closing, for now assume it was closed anyhow
				// it might be that we need to tell the user to un plug and
				// replug the device.
				e1.printStackTrace();
				labQuest = null;
			}
		}
	}
}
