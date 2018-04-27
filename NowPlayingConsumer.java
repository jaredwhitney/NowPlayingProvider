// Java AWT Imports
import java.awt.image.BufferedImage;
import java.awt.Color;

// Java IO Imports
import java.io.PrintWriter;
import java.io.File;

// Java Network Imports
import java.net.Socket;

// Java Utility Imports
import java.util.ArrayList;
import java.util.Scanner;

// Java ImageIO Imports
import javax.imageio.ImageIO;


// Provides information on the current now playing state
public class NowPlayingConsumer implements Runnable
{
	
	// Variables...
	private ArrayList<NowPlayingListener> listeners = new ArrayList<NowPlayingListener>();
	private Scanner sc;
	private NowPlayingEvent lastEvent;
	private boolean lastWasClearEvent;
	
	// Constructor (opens a connection to the NowPlayingProvider server)
	public NowPlayingConsumer(String name)
	{
		try
		{
			// Open the connection
			Socket s = new Socket("localhost", 53987);
			
			// Send the program's name
			PrintWriter out = new PrintWriter(s.getOutputStream());
			out.println(name);
			out.flush();
			
			// Set up the Scanner
			sc = new Scanner(s.getInputStream(), "UTF-8");
			
			// Start a new thread to listen for broadcasts (thread begins in the run() method)
			new Thread(this).start();
		}
		catch (Exception ex)
		{
			throw new RuntimeException("UNABLE TO REGISTER NOWPLAYINGCONSUMER (make sure the server is running?)");
		}
	}
	
	// Registers a NowPlayingListener
	public void registerNowPlayingListener(NowPlayingListener listener)
	{
		listeners.add(listener);
	}
	
	// Unregisters a NowPlayingListener
	public void unregisterNowPlayingListener(NowPlayingListener listener)
	{
		listeners.remove(listener);
	}
	
	// Handle broadcasts as they arrive
	public void run()
	{
		while (true)
		{
			// Get a broadcast
			String str = sc.nextLine();
			
			// Split the data into its elements
			String[] parts = str.split("\0");
			
			// Determine whether something new is playing, or nothing is playing
			// If nothing is playing ...
			if (parts[0].equals("nothinghere"))
			{
				// ... package that as a NowPlayingEvent
				NowPlayingEvent ev = new NowPlayingEvent(null, null, null, null, colorParse(parts[1]), str);
				
				// ... keep track of it
				lastEvent = ev;
				lastWasClearEvent = true;
				
				// ... and notify all registered 'NowPlayingListener's
				for (NowPlayingListener listener : listeners)
					listener.nowPlayingSongCleared(ev);
			}
			// Otherwise, something new is playing ...
			else
			{
				// ... package that as a NowPlayingEvent
				NowPlayingEvent ev = new NowPlayingEvent(parts[0], parts[1], parts[2], parts[3], colorParse(parts[4]), str);
				
				// ... keep track of it
				lastEvent = ev;
				lastWasClearEvent = false;
				
				// ... and notify all registered 'NowPlayingListener's
				for (NowPlayingListener listener : listeners)
					listener.nowPlayingSongSet(ev);
			}
		}
	}
	
	// Re-broadcast the last event that was caught
	public void rebroadcastLastEvent()
	{
		if (lastWasClearEvent)
			for (NowPlayingListener listener : listeners)
				listener.nowPlayingSongCleared(lastEvent);
		else
			for (NowPlayingListener listener : listeners)
				listener.nowPlayingSongSet(lastEvent);
	}
	
	// Converts a String into a Color
	private static Color colorParse(String colorString)
	{
		String[] parts = colorString.split("\\Q,\\E");
		return new Color(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
	}
	
}

// Represents information about a change in the now playing state
class NowPlayingEvent
{
	
	// Variables...
	String providerName, title, subtext, imagePath, requestString;
	BufferedImage image;
	Color dominantColor;
	
	// Constructor...
	public NowPlayingEvent(String providerName, String title, String subtext, String imagePath, Color dominantColor, String requestString)
	{
		this.providerName = providerName;
		this.title = title;
		this.subtext = subtext;
		this.imagePath = imagePath;
		this.dominantColor = dominantColor;
		this.requestString = requestString;
	}
	
	// Getters...
	public String getProvider()
	{
		return providerName;
	}
	public String getTitle()
	{
		return title;
	}
	public String getSubtext()
	{
		return subtext;
	}
	public String getImagePath()
	{
		return imagePath;
	}
	public Color getDominantColor()
	{
		return dominantColor;
	}
	
	// Load the BufferedImage from the specified path, if it hasn't already been loaded; then return it
	public BufferedImage getImage()
	{
		if (image == null)
			if (imagePath == null)
				return null;
			else
			{
				try
				{
					BufferedImage oimg = ImageIO.read(new File(imagePath));
					image = new BufferedImage(oimg.getWidth(), oimg.getHeight(), BufferedImage.TYPE_INT_RGB);
					image.getGraphics().drawImage(oimg,0,0,null);
					return image;
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
					return null;
				}
			}
		return image;
	}
	
	@Override
	public String toString()
	{
		return requestString;
	}
	
}

// Interface defining a NowPlayingListener
interface NowPlayingListener
{
	public void nowPlayingSongSet(NowPlayingEvent ev);
	public void nowPlayingSongCleared(NowPlayingEvent ev);
}

// Analagous to NowPlayingListener
abstract class NowPlayingAdapter implements NowPlayingListener
{
	public void nowPlayingSongSet(NowPlayingEvent ev){}
	public void nowPlayingSongCleared(NowPlayingEvent ev){}
}
