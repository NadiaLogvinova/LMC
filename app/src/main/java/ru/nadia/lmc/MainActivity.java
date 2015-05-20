package ru.nadia.lmc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {

	View[] myView = new View[19];
    String[] adrForGet;
    Device device;
    
    public static String[] ip = {"192.168.1.250"};
    public static int[] port_station = {500, 18};
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Button butConnect = (Button) findViewById(R.id.connecting);
        butConnect.setOnClickListener(this);
        
        Button butSet = (Button) findViewById(R.id.setFormat);
        butSet.setTag(R.id.READ_ADDRESS, "M100");
		butSet.setTag(R.id.WRITE_ADDRESS, "M100");
		butSet.setOnClickListener(this);
         
		myView[0] = (Button) findViewById(R.id.size1);
		myView[1] = (Button) findViewById(R.id.size2);
		myView[2] = (Button) findViewById(R.id.size3);
		myView[3] = (Button) findViewById(R.id.size4);
		myView[4] = (Button) findViewById(R.id.size5);
		myView[5] = (Button) findViewById(R.id.size6);
		myView[6] = (Button) findViewById(R.id.size7);
		myView[7] = (Button) findViewById(R.id.size8);
		myView[8] = (Button) findViewById(R.id.size9);
		myView[9] = (Button) findViewById(R.id.PX1);
		myView[10] = (Button) findViewById(R.id.PX2);
		myView[11] = (Button) findViewById(R.id.PX3);
		myView[12] = (Button) findViewById(R.id.PD);
		myView[13] = (TextView) findViewById(R.id.textView1);
		myView[14] = (TextView) findViewById(R.id.textView2);
		myView[15] = (TextView) findViewById(R.id.textView3);
		myView[16] = (TextView) findViewById(R.id.textView4);
		myView[17] = (TextView) findViewById(R.id.textView5);
		myView[18] = (TextView) findViewById(R.id.textView6);
		
		
		Resources res = getResources();
		String[] readAdressArray = res.getStringArray(R.array.read_adress_array);
		String[] writeAdressArray = readAdressArray;
		
		for (int i = 0; i < myView.length; i++) {
			if (myView[i] instanceof Button) {
				myView[i].setTag(R.id.READ_ADDRESS, readAdressArray[i]);
				myView[i].setTag(R.id.WRITE_ADDRESS, writeAdressArray[i]);
				myView[i].setOnClickListener(this);
			} else {
				myView[i].setTag(R.id.READ_ADDRESS, readAdressArray[i]);
			}
		}
		
        //Create adress array for method getValue()
        adrForGet = new String[myView.length];
        int i = 0;
        for (View a: myView) {
        	if (a instanceof Button) {
        		adrForGet[i] = (String) ((Button)a).getTag(R.id.READ_ADDRESS);
        	} else if (a instanceof TextView) {
        		adrForGet[i] = (String) ((TextView)a).getTag(R.id.READ_ADDRESS);
			}	
        	i++;
        }          
    }
    
    public void onResume() {
        super.onResume();   
        
        device = new Device(MainActivity.ip, MainActivity.port_station);
    	new ConnectAndInitTask().execute(getApplicationContext());
    	if (device.getOnline() == false) {
    		Toast.makeText(this, R.string.connection_NOT_established, Toast.LENGTH_SHORT).show();
    	}
    	Thread myThread = new Thread(new Runnable() {
    		@Override
    		public void run() {
    			while (true) {    
    				if (device.getOnline() == true) {
    					new Refresh().execute(getApplicationContext());
    				} else {
        				//NOP 
	                }
    				try {
    					Thread.sleep(5000);
    				} catch (InterruptedException e) {
                        e.printStackTrace();
                    }
    			}    
    		}
    	});
    	myThread.start();
    }
    
    public void onPause() {
    	super.onPause();
    	
    	if (device.getOnline() == true) {
    		device.disconnect();
    	}
    }
    
    public void onStop() {
    	super.onStop();
    	
    	if (device.getOnline() == true) {
    		device.disconnect();
       	}
    }
    
    public void onDestroy() {
    	super.onDestroy();

    	if (device.getOnline() == true) {
    		device.disconnect();
       	}
    }
    

	public void onClick(View v) {
			switch (v.getId()) {
			case R.id.connecting: new ConnectAndInitTask().execute(getApplicationContext());
								if (device.getOnline() == true) {
									Toast.makeText(MainActivity.this, R.string.connection_established, Toast.LENGTH_SHORT).show();
								} else {
									Toast.makeText(MainActivity.this, R.string.connection_NOT_established, Toast.LENGTH_SHORT).show();
								}
								break;
			case R.id.setFormat: 	if (device.getOnline() == true) {
										String adr = (String) v.getTag(R.id.WRITE_ADDRESS);
										new SetMarkerTask(adr, true).execute(getApplicationContext());
									} else {
										Toast.makeText(MainActivity.this, R.string.impossible_set_format, Toast.LENGTH_LONG).show();
									}
			 						break;
			default:			String old_size = String.valueOf(((TextView) v).getText());
								String adr = (String) ((Button) v).getTag(R.id.WRITE_ADDRESS);		
								changeSize(old_size, adr);
		}					 	
	}
	
	
	public void changeSize(final String old_sz, final String adr_wr) {    //current value view, address to write new value
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		final EditText input = new EditText(this); 
		
		input.setHint(R.string.only_numbers_0_9);
		input.setInputType(InputType.TYPE_CLASS_NUMBER);
		
		if (!(input.getText()).equals(R.string.hint))
		{ 
			input.setText("");
		} else {
			input.setText(old_sz);
		}

		
//		input.setFilters(new InputFilter[] {
//			new InputFilter() {
//				@Override
//				public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
//					if(source.equals("")){ // for backspace
//						return source;
//					}
//					if(source.toString().matches("[0-9]+")){
//						return source;
//					}
//					return "";
//				}
//			}
//		});

		alert.setView(input);
		
		
		alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int whichButton) {
			if(!old_sz.equals(String.valueOf(input.getText()))) {    //convert CharSequence to int
				if (device.getOnline() == true) {
					int[] new_sz_int = new int[]{Integer.parseInt(String.valueOf(input.getText()))}; 
					new SetValueTask(new String[]{adr_wr}, new int[]{32}, new_sz_int).execute(getApplicationContext());
				} else {
					Toast.makeText(MainActivity.this, R.string.connection_NOT_established, Toast.LENGTH_SHORT).show();
				}	
			} else {
				//NOP	
			} 
		}
		});

		alert.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				//NOP
			}
		});

		alert.show();
	}
	
	public void cellInit(int[] init) {
		int i = 0;
		for(View a: myView) {
			((TextView) a).setText(String.valueOf(init[i]));
			i++;
		}
	} 
	
	private class ConnectAndInitTask extends AsyncTask<Object, Object, int[]> {
		@Override
		protected int[] doInBackground(Object...obj) {
			device.connect();
			if (device.getOnline() == true) {
				return device.getValue(adrForGet, new int[]{8});
			} else {
				return new int[] {0};
			}
		}
		
		protected void onPostExecute(int[] result) {
			if (result[0] != 0) {
				cellInit(result);
			} 
       }
	}	
	
	private class Refresh extends AsyncTask<Object, Object, int[]> {
		@Override
		protected int[] doInBackground(Object...obj) {	
			return device.getValue(adrForGet, new int[]{8});
		}
		
		protected void onPostExecute(int[] result) {
			cellInit(result);
       }	
	}	
	
	
	private class SetValueTask extends AsyncTask<Object, Object, int[]> {
		String[] adr;
		int[] size;
		int[] value;
		
		SetValueTask(String[] adr, int[] size, int[] value) {
			this.adr = adr;
			this.size = size;
			this.value = value;
		}
		
		@Override
		protected int[] doInBackground(Object...obj) {			
			device.setValue(adr, size, value);
			return device.getValue(adrForGet, new int[]{8});
		}	
		
		protected void onPostExecute(int[] result) {
			cellInit(result);
       }
	}
	
	private class SetMarkerTask extends AsyncTask<Object, Object, int[]> {
		String adr;
		boolean rs;
		
		SetMarkerTask(String adr, boolean rs) {
			this.adr = adr;
			this.rs = rs;
		}
		@Override
		protected  int[] doInBackground(Object...obj) {			
			device.set_resetM(adr, rs);
			return device.getValue(adrForGet, new int[]{8});
		}	
		
		protected void onPostExecute(int[] result) {
			cellInit(result);
       }
	}	
}