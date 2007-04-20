/**
 * 
 */
package org.concord.sensor.vernier;

import java.util.StringTokenizer;

import org.concord.sensor.ExperimentConfig;
import org.concord.sensor.ExperimentRequest;
import org.concord.sensor.SensorConfig;
import org.concord.sensor.SensorRequest;
import org.concord.sensor.device.DeviceReader;
import org.concord.sensor.device.DeviceService;
import org.concord.sensor.device.impl.AbstractStreamingSensorDevice;
import org.concord.sensor.device.impl.SerialPortParams;
import org.concord.sensor.device.impl.StreamingBuffer;
import org.concord.sensor.impl.ExperimentConfigImpl;
import org.concord.sensor.impl.Vector;
import org.concord.sensor.serial.SensorSerialPort;
import org.concord.sensor.serial.SerialException;

/**
 * @author scott
 *
 */
public class LabProSensorDevice extends AbstractStreamingSensorDevice
{
    final static SerialPortParams serialPortParams = 
    	new SerialPortParams(SensorSerialPort.FLOWCONTROL_NONE,
    			38400, 8, 1, 0);
	
    public final static int [] CHANNELS = {1,2,3,4,11,12};
    
	protected final byte [] buf = new byte [1024];
	
	protected LabProProtocol protocol;
	
    public LabProSensorDevice()
    {
    	deviceLabel = "LP";
    	protocol = new LabProProtocol(this);
    	
    }
    
	/* (non-Javadoc)
	 * @see org.concord.sensor.device.impl.AbstractSensorDevice#getSerialPortParams()
	 */
	protected SerialPortParams getSerialPortParams()
	{
		return serialPortParams;
	}

	/**
	 * @see org.concord.sensor.device.impl.AbstractSensorDevice#initializeOpenPort(java.lang.String)
	 */
	protected boolean initializeOpenPort(String portName)
	{
		try {
			protocol.wakeUp();
			protocol.reset();
		} catch (SerialException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}

	/**
	 * @see org.concord.sensor.device.impl.AbstractSensorDevice#isAttachedInternal(java.lang.String)
	 */
	protected boolean isAttachedInternal(String portLabel)
	{
		try {
			if(port == null || !port.isOpen()){
				return false;
			}
			
			// wakeup again incase this is called directly			
			protocol.wakeUp();
			protocol.requestSystemStatus();
			
			//read result
			float [] values = new float[18];
			int count = readValues(values);
			if(count != 17){
				if(count >= 0){
					log("wrong number of values returned for system status ret: " +
							count);
				}
				return false;
			}

			if(round(values[LabProProtocol.SYS_STATUS.CONST_8888]) != 8888){
				log("system status has wrong constent vlaue: " + 
						values[LabProProtocol.SYS_STATUS.CONST_8888]);
				return false;
			}
						
		} catch (SerialException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	/**
	 * This takes away any floating point drift that would mess up a basic
	 * cast to int.  For example a float could be turn out to be 0.99999
	 * and a basic cast to int will return 0 instead of the desired 1.
	 * @param value
	 * @return
	 */
	public final static int round(float value)
	{
		if(value >= 0){
			return (int)(value+0.5f);
		} else {
			return (int)(value-0.5f);
		}
	}
	
	/**
	 * @see org.concord.sensor.device.SensorDevice#canDetectSensors()
	 */
	public boolean canDetectSensors()
	{
		return true;
	}
	
    /**
     * @see org.concord.sensor.device.SensorDevice#configure(org.concord.sensor.ExperimentRequest)
     */
    public ExperimentConfig configure(ExperimentRequest request) 
    {
    	return autoIdConfigure(request);
    }


	/**
	 * @see org.concord.sensor.device.SensorDevice#getCurrentConfig()
	 */
	public ExperimentConfig getCurrentConfig()
	{
	    ExperimentConfigImpl expConfig = new ExperimentConfigImpl();
	    expConfig.setDeviceName("LabPro");
	    
		// read the sensor ids of each of the ports to see what is
		// attached.  
	    Vector sensorConfigVect = new Vector();
	    
		try {
			protocol.wakeUp();

			// send the read command
			float channelStatus [] = new float[3]; 
			
			for(int i=0; i<CHANNELS.length; i++){
				protocol.requestChannelStatus(CHANNELS[i], 0);

				int count = readValues(channelStatus);
				if(count < 3){
					// there was an error
					log("error reading channel status from device chan: " + 
							CHANNELS[i]);
					continue;
				} 
				
				int sensorId = round(channelStatus[0]);
				log("chan: " + CHANNELS[i] + " sens id: " + sensorId);
				
				// only add the sensor if we can identify that it is really 
				// there.  There might be some trick we can use to see if 
				// something is attached, by reading a value and seeing if it
				// is different than the baseline value.
				if(sensorId <= 0){
					continue;
				}
						
				LabProSensor sensorConfig = new LabProSensor(this, devService);
				
				// translate the vernier id to the SenorConfig id
				int ret = sensorConfig.translateSensor(sensorId, null);
				sensorConfigVect.add(sensorConfig);
			}
		} catch (SerialException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		SensorConfig [] sensorConfigArr = 
			new SensorConfig[sensorConfigVect.size()];
		Vector.copyArray(sensorConfigVect.items, 0, sensorConfigArr, 
				0, sensorConfigVect.size());
		expConfig.setSensorConfigs(sensorConfigArr);

	    expConfig.setExactPeriod(true);

	    return expConfig;
	}

	/* (non-Javadoc)
	 * @see org.concord.sensor.device.SensorDevice#getErrorMessage(int)
	 */
	public String getErrorMessage(int error)
	{
		// TODO Auto-generated method stub
		return null;
	}

	float [] dataValues = new float[2];
	
	protected int streamRead(float[] values, int offset, int nextSampleOffset, 
			DeviceReader reader, StreamingBuffer streamingBuffer)
	{
		// read all the bytes from the port and look for the 
		// {} blocks
		try {
			int count;
			int numSamples = 0;
			while(true ){
				count = readValues(dataValues, streamingBuffer);
				if(count <= 0){
					break;
				}
				values[offset + nextSampleOffset * numSamples] = dataValues[0];
				numSamples++;
			}

			// The readValues method should have updated the 
			// processedBytes field of the streamingBuffer
			
			return numSamples;
			// the problem with this approach is that we are goint to eat
			// up bytes from the serial port before the whole thing is 
			// ready.
			
			// so we need to store the read bytes into a buffer
			// that we then reuse.
			
		} catch (SerialException e) {
			e.printStackTrace();
		}
		
		return 0;
	}

	/**
	 * @see org.concord.sensor.device.SensorDevice#start()
	 */
	public boolean start()
	{
		try {
			protocol.wakeUp();
			protocol.reset();

			// collected data from channel one using the 
			// auto id operation
			protocol.channelSetup(1, 1);
			
			// start the collection
			// sample once every 0.5 seconds
			// use realtime mode (-1)
			// start sampling now (0)
			protocol.dataCollectionSetup(0.5f,-1,0);
			
			return super.start();
		} catch (SerialException e) {
			e.printStackTrace();
		}
		
		return false;
	}

	/**
	 * @see org.concord.sensor.device.SensorDevice#stop(boolean)
	 */
	public void stop(boolean wasRunning)
	{
		try {
			// send a wakeup just in case
			protocol.wakeUp();
			
			// send the reset
			protocol.reset();
		} catch (SerialException e) {
			e.printStackTrace();
		}
		
	}

	protected int readValues(float [] values, StreamingBuffer sb)
    	throws SerialException
	{
		// read one byte at a time until we get to the closing bracket
		// this is not the best way to do this, but it work without blocking
		// regardless about how the readBytes on the port is implemented
		// The timeout here should be set depending on how long it takes
		// the LabPro to get back to us with the first byte
		// check that the first byte is a }		
		if((sb.totalBytes - sb.processedBytes) < 2){
			return 0;
		}
		
		byte currentByte = sb.buf[sb.processedBytes];
		if(currentByte != '{'){
			log("First byte isn't { instead it is: " + (char)currentByte);
			return -1;
		}

		int off = sb.processedBytes + 1;
			
		while(off < sb.totalBytes && currentByte != '\n'){
			currentByte = sb.buf[off++];
		}

		if(currentByte != '\n'){
			// we didn't find the ending char, so we only read part 
			// of a packet
			// leave the processedBytes alone, so we can read them all again
			// the next time
			return 0;
		}
		
		// we should now have a buffer with a string in it from 0-off
		// I don't know if this will work in waba
		String result = 
			new String(sb.buf, sb.processedBytes, off-sb.processedBytes);

		// now we have to use basic string parsing because waba and java don't 
		// share the tokenizer
		// but sense we are in a crunch lets just use the java conventions
		// and deal with the waba stuff when we need it.
		int count = 0;
		StringTokenizer toks = new StringTokenizer(result, "{},");
		while(toks.hasMoreTokens() && count < values.length){
			String numberStr = toks.nextToken();
			float number = Float.parseFloat(numberStr);
			values[count] = number;
			count++;
		}
		
		sb.processedBytes = off;
		return count;
	}
	
	/**
	 * This read the string return values of the LabPro
	 * These values look like:
	 * {  +2.40000E+01, -9.99900E+02, -9.99900E+02 }
	 * @param values
	 * @return -1 if there is an error, otherwise returns the number of values
	 * @throws SerialException 
	 */	
	protected int readValues(float [] values)
		throws SerialException
	{
		// read one byte at a time until we get to the closing bracket
		// this is not the best way to do this, but it work without blocking
		// regardless about how the readBytes on the port is implemented
		// The timeout here should be set depending on how long it takes
		// the LabPro to get back to us with the first byte
		// check that the first byte is a }		
		int ret = 0;
		int off = 0;
		
		ret = port.readBytes(buf, off, 1, 1000);
		if(ret != 1){
			log("Didn't get any bytes from device ret: " + ret);
			return -1;
		}
		byte currentByte = buf[0];

		if(currentByte != '{'){
			log("First byte isn't { instead it is: " + (char)currentByte);
			return -1;
		}
		off = 1;
		
		while(ret == 1 && currentByte != '\n'){
			ret = port.readBytes(buf, off, 1, 100);
			currentByte = buf[off++];
		}
					
		if(ret != 1 || currentByte != '\n'){
			// we got an error 
			log("error reading values ret: " + ret + 
					" lastB: " + (char)currentByte);
			return -1;
		}
		
		// we should now have a buffer with a string in it from 0-off
		// I don't know if this will work in waba
		String result = new String(buf, 0, off);

		// now we have to use basic string parsing because waba and java don't 
		// share the tokenizer
		// but sense we are in a crunch lets just use the java conventions
		// and deal with the waba stuff when we need it.
		int count = 0;
		StringTokenizer toks = new StringTokenizer(result, "{},");
		while(toks.hasMoreTokens() && count < values.length){
			String numberStr = toks.nextToken();
			numberStr = numberStr.trim();
			if(numberStr.length() == 0) {
				continue;
			}
			float number = Float.parseFloat(numberStr);
			values[count] = number;
			count++;
		}
		
		return count;
	}

	/* (non-Javadoc)
	 * @see org.concord.sensor.device.impl.AbstractStreamingSensorDevice#getStreamBufferSize()
	 */
	protected int getStreamBufferSize()
	{
		return 1024;
	}

	SensorSerialPort getPort()
	{
		return port;
	}
	
	DeviceService getDeviceService()
	{
		return devService;
	}
	
	/**
	 * Increase the visibility of the log by overriding it here
	 * that  
	 * 
	 * @see org.concord.sensor.device.impl.AbstractSensorDevice#log(java.lang.String)
	 */
	protected void log(String message)
	{
	    // TODO Auto-generated method stub
	    super.log(message);
	}
	
}
