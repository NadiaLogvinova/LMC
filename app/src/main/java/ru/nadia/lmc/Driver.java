package ru.nadia.lmc;

import java.io.IOException;

public interface Driver {
	public void setValue(String[] adress, int[] size, int[] value);
	public int[] getValue(String[] adress, int[] size);
	public void connect() throws IOException;
	public void disconnect();
} 
