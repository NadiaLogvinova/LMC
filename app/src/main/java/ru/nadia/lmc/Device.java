package ru.nadia.lmc;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;


public class Device implements Driver {
	private String IP;
	private int portNumber;
	private int stationNumber;
	private Socket socket;
	private boolean online = false;
	private InputStream in;
	private OutputStream out;
	private static final byte START_COMMAND           = 0x02;  		
	private static final byte STOP_COMMAND            = 0x03;	
	private static final byte[] WRITE_COMMAND         = new byte[] {0x31, 0x37};	//command "write" = '17' (2 bytes)
	//private static final byte WRITE2_COMMAND 		  = 0x37;	
	private static final byte[] READ_COMMAND          = new byte[] {0x31, 0x35};	//command "read" = '15' (2 bytes)
	private static final byte[]  RS_MARKER			  = new byte[] {0x31, 0x30};   //command "set or reset marker" = '10' (2 bytes)
	//private static final byte RS2_MARKER			  = 0x30;
	private static final byte SET_MARKER			  = 0x33;   
	private static final byte RESET_MARKER			  = 0x34;
	private static final byte  MARKER_CODE 			= 0x4d; //code of letter "M" for rs-request
	//LENGTH = quantity of bytes in request
	//the request length without adress-cells and data-cells
	private static final int  LENGTH_WITHOUT_ADRESS_DATA = 10;  
	private static final int  LENGTH_LETTER_ADRESS    = 3;      //length for letter part of adress
	private static final int  LENGTH_NUMBER_ADRESS    = 4;      //length for number part of adress
	private static final int  LENGTH_DATA			  = 4;      //length of one data-cell
	private static final int  LENGTH_ADRESS			  = LENGTH_LETTER_ADRESS + LENGTH_NUMBER_ADRESS;
	//Calculate length of one adress-cell + one data-cell in the set-request
	private static final int  LENGTH_ADRESS_DATA_SET  = LENGTH_LETTER_ADRESS + LENGTH_NUMBER_ADRESS + LENGTH_DATA; 	
	private static final int  START_LETTER_ADRESS = 7;		//the start for letter part of first adress-cell in request 
	//Calculate the start for number part of first adress-cell in requests 
	private static final int  START_NUMBER_ADRESS = START_LETTER_ADRESS + LENGTH_LETTER_ADRESS;	
	private static final int  START_NUMBER_ADRESS_RS = 7; 
	//Calculate the start for first adress-cell in set-request
	private static final int  START_DATA = START_NUMBER_ADRESS + LENGTH_NUMBER_ADRESS;
	private static final int  LENGTH_RS_REQUEST = 14; //length of rs-request for marker
	
	//length of answer on get-request without data-cells
	private static final int  LENGTH_ANSWER_WITHOUT_DATA = 9;
	private static final int  START_DATA_ANSWER = 6; //start for first data-cell in answer on get-request
	
	public Device(String[] param1, int[] param2) {
		IP = param1[0];
		portNumber = param2[0];
		stationNumber = param2[1];
	}

	@Override
	public void setValue(String[] adress, int[] size, int[] value) {
		int count = adress.length; 
		byte[] set_request = new byte[LENGTH_WITHOUT_ADRESS_DATA + LENGTH_ADRESS_DATA_SET*count];
		//We are filling array for set-request
		initStartSlaveCommandStop(set_request, WRITE_COMMAND);            
		initCount(set_request, count);	
		initLetterPartOfAdress(set_request, adress,LENGTH_LETTER_ADRESS, count, LENGTH_ADRESS_DATA_SET, START_LETTER_ADRESS);
	  	//We are filling number part for adress-cells (4*count bytes) and data-cells (4*count bytes)
		int[] number = getNumberPartAdress(adress, count);
		initNumberPartAdressOrData(set_request, count, number, LENGTH_NUMBER_ADRESS, LENGTH_ADRESS_DATA_SET, START_NUMBER_ADRESS);
		initNumberPartAdressOrData(set_request, count, value, LENGTH_DATA, LENGTH_ADRESS_DATA_SET, START_DATA);  
		//We are calculating CheckSumm (excluding last three cells) and filling the "CheckSumm" cells (2 bytes);
	  	int checkSum = lrc(Arrays.copyOf(set_request, set_request.length - 3));    		
	  	initCheckSum(set_request, checkSum);
	  	//We are sending the data in the port and getting the data from the port
	  	myWrite(out, set_request);     
	  	myRead(in);
	}

	@Override
	public int[] getValue(String[] adress, int[] size) {
		int count = adress.length;
		byte[] get_request = new byte[LENGTH_WITHOUT_ADRESS_DATA + LENGTH_ADRESS*count];
		//We are filling array for request
		initStartSlaveCommandStop(get_request, READ_COMMAND);
		initCount(get_request, count);
		initLetterPartOfAdress(get_request, adress,LENGTH_LETTER_ADRESS, count, LENGTH_ADRESS, START_LETTER_ADRESS);
		//We are filling number part for adress-cells (4*count bytes)
		int[] number = getNumberPartAdress(adress, count);
		initNumberPartAdressOrData(get_request, count, number, LENGTH_NUMBER_ADRESS, LENGTH_ADRESS, START_NUMBER_ADRESS);
		//We are calculating CheckSumm (excluding last three cells) and filling the "CheckSumm" cells (2 bytes)
		int checkSum = lrc(Arrays.copyOf(get_request, get_request.length - 3));
		initCheckSum(get_request, checkSum);
		//We are sending the data in the port
	    myWrite(out, get_request);			
		//We are getting the data from the buffered port
		BufferedInputStream bin = new BufferedInputStream(in);
		bin.mark(LENGTH_ANSWER_WITHOUT_DATA + LENGTH_DATA*count + 1);
		myRead(bin);
		int[] newData = new int[count];
		try {
			bin.reset(); 
			byte[] get_answer = new byte[bin.available()];
			bin.read(get_answer);
			//We are translating any 4 bytes (starting with 6-th bytes) in int
			for(int i = 0; i < count; i++) {
				String hexString = new String(get_answer, START_DATA_ANSWER + LENGTH_DATA*i, LENGTH_DATA);
				newData[i] = Integer.parseInt(hexString, 16);
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			bin = null;
		}
		return newData;
	}

	@Override
	public void connect() {
		try {
			socket = new Socket(IP, portNumber);
			out = socket.getOutputStream();	
			in = socket.getInputStream();
			online = true;
		} catch (IOException e) {
			e.printStackTrace();
			online = false;
		}
		
	}

	@Override
	public void disconnect() {
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			online = false;
		}
	}
	
	public boolean getOnline() {
		return online;
	}
	
	protected void set_resetM(String adress, boolean rs) {
		byte[] rs_request = new byte[LENGTH_RS_REQUEST];
		//We are filling array for request
		initStartSlaveCommandStop(rs_request, RS_MARKER);             
		//set or reset cell M
		if(rs == true) {
			rs_request[5] = SET_MARKER; 
		} else {
			rs_request[3] = RESET_MARKER; 
		}
	    //We are coding "M" how 4d
		rs_request[6] = MARKER_CODE; 
		//We are filling number part for adress-cells (4*count bytes)
		String intString = adress.substring(1);
	  	if(intString.length() < 4) {
			intString = zeroString(4 - intString.length()) + intString;
		}
	  	for (int j = 0; j < LENGTH_NUMBER_ADRESS; j++) {
  			rs_request[START_NUMBER_ADRESS_RS + j] = (byte) intString.charAt(j);
  		} 	
		//We are calculating CheckSumm (excluding last three cells) and filling the "CheckSumm" cells (2 bytes)
	  	int checkSum = lrc(Arrays.copyOf(rs_request, rs_request.length - 3));  
	  	initCheckSum(rs_request, checkSum);
	  	//We are sending the data in the port and getting the data from the port
	  	myWrite(out, rs_request);
	  	myRead(in);
	}
	
	private int lrc(byte[] dataBuffer) {
		 int LRC = 0;
	     for (int i = 0; i < dataBuffer.length; i++) {
	         LRC = (LRC + dataBuffer[i]) & 0xFF; 
	     }
	     return LRC;
	}
	
	private  byte[] getLetterCode(char letter) {
		byte[] letterCode1 = new byte[3];
		switch (letter) {
		case 'C': letterCode1[0] = 0x30; letterCode1[1] = 0x31; letterCode1[2] = 0x3C; break;
		case 'D': letterCode1[0] = 0x30; letterCode1[1] = 0x31; letterCode1[2] = 0x3D; break;
		case 'M': letterCode1[0] = 0x30; letterCode1[1] = 0x31; letterCode1[2] = 0x37; break;
		default: break;
		}
		return letterCode1;
	}
	
	private char[] intToTwoBytes(int count) { //int->hex->two code bytes
		char[] hexCount = new char[2];
		if(count < 10) {
			hexCount[0] = '0';
			hexCount[1] = Character.forDigit(count, 10);
		} else {
			hexCount = Integer.toHexString(count).toUpperCase().toCharArray(); //int->hexString->charArray
		}
		return hexCount;
	}
	
	private String zeroString(int count) {
			String str = new String();
			for (int j = 0; j < count; j++) {
				str += "0";
			}
		return str;
	}
	
	private void myWrite(OutputStream myOut, byte[] arr) {
		try {
			myOut.write(arr);
			myOut.flush();
		} catch (IOException e1) {
			e1.printStackTrace();
		} 	
	}
	
	private void myRead(InputStream myIn) {
		byte b = 0;
		try {
			while(b != STOP_COMMAND) {
				b = (byte)myIn.read();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		} 
	}
	
	private void initStartSlaveCommandStop(byte[] request, byte[] command) {
		request[0] = START_COMMAND;                
		request[request.length-1] = STOP_COMMAND;      
		//�station ("slave") (2 bytes)
		char[] hexStation = intToTwoBytes(stationNumber);
		request[1] = (byte) hexStation[0];                         
		request[2] = (byte) hexStation[1];
		//command (2 bytes)
		request[3] = command[0];                         
		request[4] = command[1]; 
	}		
	
	private void initCount(byte[] request, int count) {
		char[] hexCount = intToTwoBytes(count);
		request[5] = (byte) hexCount[0];
		request[6] = (byte) hexCount[1];
	}
	
	 //We are filling letter part for adress-cells (3*count bytes)
	private void initLetterPartOfAdress(byte[] request, String[] adress, int lenLetterAdr, int count, int lenAdrData, int startLetterAdr) {
		byte[] letterCode = new byte[lenLetterAdr];
		for (int i = 0; i < count; i++) {
			letterCode = getLetterCode(adress[i].charAt(0));
			for (int j = 0; j < lenLetterAdr; j++) {
				request[lenAdrData*i + startLetterAdr + j] = letterCode[j];	
			}
		} 	
	}
	
	private int[] getNumberPartAdress(String[] adr, int count) {
		int[] arr = new int[count];
		for (int i = 0; i < count; i++) {
			arr[i] = Integer.parseInt(adr[i].substring(1));
		}
		return arr;
	}
	
	private void initNumberPartAdressOrData(byte[] request, int count, int[] arr, int lenNumberOrData, int lenAdrAndData, int start ) {
		for (int i = 0; i < count; i++) {
	  		String hexString = Integer.toHexString(arr[i]).toUpperCase(); //string->int->hexString
	  		if (hexString.length() < lenNumberOrData) {
				hexString = zeroString(lenNumberOrData - hexString.length()) + hexString;
			}
	  		for (int j = 0; j < lenNumberOrData; j++) {
	  			request[lenAdrAndData*i + start + j] = (byte) hexString.charAt(j);
	  		}
	  	}
	}
	 
	private void initCheckSum(byte[] request, int Sum) {
		char[] hexCount = intToTwoBytes(Sum);
	  	request[request.length-3] = (byte) hexCount[0];
	  	request[request.length-2] = (byte) hexCount[1];
	}
}
