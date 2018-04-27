// Java AWT Imports
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

// Java IO Imports
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.PrintWriter;

// Java Network Imports
import java.net.Socket;

// Java Utility Imports
import java.util.HashMap;
import java.util.Scanner;
import java.util.Arrays;


// BlueCove Imports
import javax.microedition.io.*;
import javax.bluetooth.*;


public class BluetoothServer implements Runnable
{
	
	// Variables...
	static NowPlayingConsumer consumer;
	static HashMap<String, BluetoothServer> servers = new HashMap<String, BluetoothServer>();
	StreamConnection connection;
	PrintWriter btout;
	NowPlayingListener listener;
	String devName;
	
	// Constructor...
	public BluetoothServer(StreamConnection connection)
	{
		this.connection = connection;
	}
	
	// Main method
	public static void main(String[] args) throws Exception
	{
		
		// Create the NowPlayingConsumer
		consumer = new NowPlayingConsumer("BluetoothServer");
		
		// Begin looking for connections from clients over bluetooth (BlueCove API)
		LocalDevice ldev  = LocalDevice.getLocalDevice();
		ldev.setDiscoverable(DiscoveryAgent.GIAC);
		UUID uuid = new UUID("00001101-0000-1000-8000-B10E70074AE1".replaceAll("\\Q-\\E",""), false);
		String url = "btspp://localhost:" + uuid.toString() + ";name=JavaBluetoothServer";
		StreamConnectionNotifier scn = (StreamConnectionNotifier)Connector.open(url);
		
		// Infinite loop to spin off handlers for devices as they connect
		while (true)
		{
			// Wait for and accept a connection
			StreamConnection connection = scn.acceptAndOpen();
			
			// Create a new instance to handle the connection (thread begins in the run() method)
			new Thread(new BluetoothServer(connection)).start();
		}
	}
	
	public void run()
	{
		try
		{
			// Set up I/O for the connection
			InputStream is = connection.openInputStream();
			OutputStream os = connection.openOutputStream();
			Scanner sc = new Scanner(is);
			btout = new PrintWriter(new OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8));
			
			// Wait for the device to report its name
			devName = sc.nextLine();
			
			// If a device with the name is already connected, disconnect it!
			BluetoothServer srv = servers.get(devName);
			if (srv != null)
				srv.stop();
			
			// Add this to the list of running connections
			servers.put(devName, this);
			
			// Set up the NowPlayingListener
			listener = new NowPlayingListener(){
				
				// Called when something new is playing
				public void nowPlayingSongSet(NowPlayingEvent ev)
				{
					
					// Send the event information over the connection
					System.out.println("Send: " + ev.toString());
					btout.println(ev.toString());
					btout.flush();
					
					// Send the event's image data over the connection
					BufferedImage img = ev.getImage();
					if (img != null)
						sendImage(img);
					else
						sendImage(new BufferedImage(16,16,BufferedImage.TYPE_INT_RGB));
					
				}
				
				// Called when nothing is playing anymore
				public void nowPlayingSongCleared(NowPlayingEvent ev)
				{
					
					// Send that nothing is playing anymore
					System.out.println("Send: nothingplaying");
					btout.println("nothingplaying");
					btout.flush();
					
				}
			};
			
			// Register the NowPlayingListener, and request a re-broadcast of the last event
			consumer.registerNowPlayingListener(listener);
			consumer.rebroadcastLastEvent();
			
			// Infinite loop to handle input from the client
			while (true)
			{
				// If there is data to process
				if (sc.hasNextLine())
				{
					
					// Read the data
					String input = sc.nextLine();
					System.out.println("Recieved '" + input + "'");
					
					// If the client requested that the volume be raised ...
					if (input.equals("volume up"))
					{
						// ... raise it.
						_extern("script/raisevolume.exe", false);
					}
					// If the client requested that the volume be lowered ...
					else if (input.equals("volume down"))
					{
						// ... lower it.
						_extern("script/lowervolume.exe", false);
					}
					
				}
				// If there is no data to process, sleep for a bit before checking again
				else
					Thread.sleep(150);
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}
	
	// Tells this instance to close its connection
	public void stop()
	{
		try
		{
			// Try to close the connection
			connection.close();
		}
		catch (Exception ex){}
		finally
		{
			// Regardless of whether or not that worked, unregister its NowPlayingListener
			consumer.unregisterNowPlayingListener(listener);
			System.out.println("Closed consumer for '" + devName + "'");
		}
	}
	
	// Sends an image over the connection
	// ... as a String ...
	// Needless to say, there are much more efficient ways to do this...
	public void sendImage(BufferedImage image)
	{
		
		// Get the image into a String
		System.out.println("Sending image...");
		int[] imgbuf = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
		String sstr = image.getWidth() + "," + image.getHeight() + ":" + Arrays.toString(imgbuf).replaceAll("\\[|\\]","");
		System.out.println("\tSending message...");
		
		// And send it!
		btout.println(sstr);
		btout.flush();
		System.out.println("\tMessage sent.");
		
	}
	
	// Run an external program (assumes that the specified program is a batch file by default)
	public static String _extern(String command, boolean reportOutput, String... args)
	{
		String[] cargs = new String[args.length+1];
		System.arraycopy(args, 0, cargs, 1, args.length);
		if (command.indexOf('.')==-1)
			command += ".bat";
		cargs[0] = command;
		try
		{
			System.err.print(Arrays.toString(cargs));
			Process proc = Runtime.getRuntime().exec(cargs);
			String output = "";
			if (reportOutput)
			{
				Scanner sc = new Scanner(proc.getInputStream());
				while (proc.isAlive() && sc.hasNextLine())
					output += sc.nextLine() + "\n";
			}
			proc.waitFor();
			System.err.println(" <- " + output.trim().replaceAll("\\Q\n\\E", " / "));
			return output.trim();
		}
		catch (Exception ex)
		{
			System.err.println("Error attempting to run " + command);
			ex.printStackTrace();
			return null;
		}
	}
}
