// Java AWT Imports
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.Color;

// Java IO Imports
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.File;

// Java Network Imports
import java.net.ServerSocket;
import java.net.Socket;

// Java Utility Imports
import java.util.ArrayList;
import java.util.Scanner;

// Javax ImageIO Imports
import javax.imageio.ImageIO;


// Creates 'NowPlayingProviderService's for any providers that attempt to connect
// Also starts a NowPlayingProviderConsumerServer
public class NowPlayingProviderServer
{
	
	// Variables...
	static ArrayList<NowPlayingProviderService> connectedServices = new ArrayList<NowPlayingProviderService>();
	static ArrayList<NowPlayingProviderConsumerService> connectedConsumerServices = new ArrayList<NowPlayingProviderConsumerService>();
	static NowPlayingProviderService lastRequester = null;
	
	// Main Method
	public static void main(String[] args) throws Exception
	{
		
		// Perform any needed initialization in the backend
		NowPlayingProviderBackend.init();
		
		// Start up the provider ServerSocket
		ServerSocket ss = new ServerSocket(53986);
		
		// Start up a NowPlayingProviderConsumerServer to handle consumer connections
		new Thread(new NowPlayingProviderConsumerServer()).start();
		
		// Infinite loop to handle provider connections
		while (true)
		{
			// Create a NowPlayingProviderService for the connection, and add it to the list of connected services
			connectedServices.add(new NowPlayingProviderService(ss.accept()));
		}
		
	}
}

// Creates 'NowPlayingProviderConsumerService's for any consumers that attempt to connect
class NowPlayingProviderConsumerServer implements Runnable
{
	public void run()
	{
		try
		{
			// Start up the consumer ServerSocket
			ServerSocket ss = new ServerSocket(53987);
			
			// Infinite loop to handle consumer connections
			while (true)
			{
				// Create a NowPlayingProviderConsumerService for the connection, and add it to the list of connected consumer services
				NowPlayingProviderServer.connectedConsumerServices.add(new NowPlayingProviderConsumerService(ss.accept()));
			}
			
		}
		catch (Exception ex)
		{
			throw new RuntimeException("[Critical] Error in consumer provider code!");
		}
	}
}

// Gets now playing info from a provider
class NowPlayingProviderService implements Runnable
{
	
	// Variables...
	String name;
	Socket socket;
	Scanner scanner;
	
	// Constructor...
	public NowPlayingProviderService(Socket socket)
	{
		try
		{
			// Connect and recieve the program's name
			this.socket = socket;
			this.scanner = new Scanner(socket.getInputStream(), "UTF-8");
			this.name = scanner.nextLine();
			
			// Start a new thread (thread begins in the run() method)
			new Thread(this).start();
		}
		catch (IOException ex)
		{
			System.out.println("Unable to start provider for '" + name + "'");
			ex.printStackTrace();
		}
	}
	public void run()
	{
		
		// Print the the provider is online
		System.out.println("Provider for " + name + " is online!");
		
		// Infinite loop to handle requests
		String request = "";
		while (true)
		{
			try
			{
				
				// Wait for the provider to have sent data
				while (!scanner.hasNext())
					Thread.sleep(750);
				
				// Get the data
				request = scanner.nextLine();
				
				// Split the data into its elements
				System.out.println("Handle request from " + name);
				String[] ps = request.split("\0");
				
				// If the data contained 3 elements, it is something new playing
				if (ps.length == 3)
				{
					
					// Mark that this provider is responsible for what is playing
					NowPlayingProviderServer.lastRequester = this;
					
					// Tell the backend to set now playing accordingly
					NowPlayingProviderBackend.setNowPlaying(name, ps[0], ps[1], ImageIO.read(new File(ps[2])), ps[2]);
					
				}
				// Otherwise, the provider tried to say nothing is playing anymore. If that provider was responsible for what was _actually_ playing ...
				else if (NowPlayingProviderServer.lastRequester == this)
				{
					// ... tell the backend to clear now playing
					NowPlayingProviderBackend.clearNowPlaying();
				}
			}
			catch (Exception ex)
			{
				System.err.println("Error while dealing with '" + request + "' for '" + name + "'");
				ex.printStackTrace();
			}
		}
	}
}

// Sends now playing info to a consumer
class NowPlayingProviderConsumerService
{
	
	// Variables...
	String name;
	Socket socket;
	Scanner scanner;
	PrintWriter writer;

	// Constructor...
	public NowPlayingProviderConsumerService(Socket socket)
	{
		
		try
		{	
			// Connect and recieve the program's name
			this.socket = socket;
			this.scanner = new Scanner(socket.getInputStream());
			this.name = scanner.nextLine();
			this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
			System.out.println("Consumer for " + name + " has connected!");
			
			// Send the consumer the current now playing info
			sendNowPlayingInfo();	
		}
		catch (IOException ex)
		{
			System.out.println("Unable to start consumer provider for '" + name + "'");
			ex.printStackTrace();
		}
	}
	
	// Send the current now playing info to the consumer
	public void sendNowPlayingInfo()
	{
		
		// Get the master color as a String
		String colorString = NowPlayingProviderBackend.colorToString(NowPlayingProviderBackend.masterColor);
		
		// Send the appropriate message
		if (NowPlayingProviderBackend.masterState == 1)
			writer.println("nothinghere\0" + colorString);
		else
			writer.println(NowPlayingProviderBackend.masterName + "\0" + NowPlayingProviderBackend.masterTitle + "\0" + NowPlayingProviderBackend.masterSubtext + "\0" + NowPlayingProviderBackend.masterImagePath + "\0" + colorString);
		
		// Flush the stream to ensure the message actually sent (instead of just being put in a buffer somewhere)
		writer.flush();
		
	}
}

class NowPlayingProviderBackend
{
	
	static
	{
		try
		{
			File rootFile = new File("C:\\ProgramData\\NowPlayingProvider\\");
			if (!rootFile.exists())
				rootFile.mkdir();
		}
		catch (Exception ex)
		{
			System.err.println("[Error] Error Creating Root File Directory!");
			ex.printStackTrace();
		}
	}
	
	// Files used to store now playing info (for any programs that read from files instead of using consumers)
	static final File masterColorFile	= new File( "C:\\ProgramData\\NowPlayingProvider\\DOMINANTCOLOR.songinfo" );
	static final File stateFile			= new File( "C:\\ProgramData\\NowPlayingProvider\\PLAYBACKSTATE.songinfo" );
	static final File subtextFile		= new File( "C:\\ProgramData\\NowPlayingProvider\\ARTISTNAME.songinfo" );
	static final File titleFile			= new File( "C:\\ProgramData\\NowPlayingProvider\\SONGTITLE.songinfo" );
	static final File thumbPathFile		= new File( "C:\\ProgramData\\NowPlayingProvider\\THUMBPATH.songinfo" );
	
	// The color to be sent when nothing is playing
	static final Color	PAUSED_COLOR			 	 = new Color(60, 60, 60);
	
	// More variables...
	static Color masterColor = PAUSED_COLOR;
	static String masterTitle = "No Title Provided", masterSubtext = "No Subtext Provided", masterName = "Unknown Service", masterImagePath = null;
	static BufferedImage masterImage = new BufferedImage(1,1,3);
	static int masterState;	// 0 if something is playing, 1 otherwise
	
	static void init()
	{
		// Any needed initialization code would go here.
	}
	
	// Called when there is something new playing
	public static void setNowPlaying(String pname, String title, String subtext, BufferedImage img, String imgpath)
	{
		
		// Gather the needed data
		masterColor = getMasterColorFrom(img);
		masterImagePath = imgpath;
		masterImage = img;
		masterTitle = title;
		masterSubtext = subtext;
		masterState = 0;
		masterName = pname;
		
		// Write it out to the given files
		writeFile(titleFile, title);
		writeFile(subtextFile, subtext);
		writeFile(thumbPathFile, imgpath);
		writeFile(stateFile, "0");
		writeFile(masterColorFile, colorToString(masterColor));
		
		// And notify all consumers
		for (NowPlayingProviderConsumerService consumerService : NowPlayingProviderServer.connectedConsumerServices)
			consumerService.sendNowPlayingInfo();
		
	}
	
	// Called when there is nothing playing anymore
	public static void clearNowPlaying()
	{
		
		// Gather the needed data
		masterColor = PAUSED_COLOR;
		masterState = 1;
		
		// Write it out to the given files
		writeFile(stateFile, "1");
		writeFile(masterColorFile, PAUSED_COLOR.getRed() + "," + PAUSED_COLOR.getGreen() + "," + PAUSED_COLOR.getBlue());
		
		// And notify all consumers
		for (NowPlayingProviderConsumerService consumerService : NowPlayingProviderServer.connectedConsumerServices)
			consumerService.sendNowPlayingInfo();
		
	}
	
	// Turn a Color into a String
	public static String colorToString(Color color)
	{
		return color.getRed() + "," + color.getGreen() + "," + color.getBlue();
	}
	
	// Write a single String to the given File
	public static void writeFile(File file, String data)
	{
		try
		{
			PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file),java.nio.charset.StandardCharsets.UTF_8));
			out.print(data);
			out.close();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}
	
	// Create a nice color from the given image (rawMasterColor stores the un-modified color)
	static Color rawMasterColor = Color.DARK_GRAY;
	public static Color getMasterColorFrom(BufferedImage img)
	{
		
		// Get the image's average color
		Color col = new Color(areaAvg(img,img.getWidth()/2,img.getHeight()/2,img.getWidth(),img.getHeight()));
		rawMasterColor = col;
		
		// Break down the color into its HSB components
		float[] hsb = Color.RGBtoHSB(col.getRed(),col.getGreen(),col.getBlue(),null);
		
		// If the color is very dark, raise its brightness to a set minimum and lower its saturation.
		if (hsb[2] < 0.01)
		{
			hsb[2] = 0.2f;
			hsb[1] = hsb[1]/10f;
		}
		
		// Enforce a maximum brightness
		else if (hsb[2] < 0.6)
		{
			hsb[2] = 0.6f;
		}
		
		// Boost the saturation of colors that aren't extremely unsaturated (the more un-saturated a color is, the more its saturation will be boosted)
		if (hsb[1] > 0.05)
		{
			hsb[1] = (1f+hsb[1])/2f;
		}
		
		// Re-combine and return the color
		col = new Color(Color.HSBtoRGB(hsb[0],hsb[1],hsb[2]));
		return col;
		
	}
	
	// Get the average color over a region of an image
	// img = the image to average color over, (sx,sy) = the center of the region about which the sample is taken, [xm,ym] = the total [width,height] of the sample
	public static int areaAvg(BufferedImage img, int sx, int sy, int xm, int ym)
	{
		
		// Get the image's width, height
		int width = img.getWidth();
		int height = img.getHeight();
		
		// Get the image's pixel contents as an int[]
		BufferedImage i2 = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
		i2.getGraphics().drawImage(img,0,0,null);
		int[] imgbuf = ((DataBufferInt)i2.getRaster().getDataBuffer()).getData();
		
		// Initialize the component accumulators
		int b = 0;
		int g = 0;
		int r = 0;
		
		// Calculate the required constants
		int lowxbound = sx-xm/2;
		int highxbound = sx+xm/2;
		int lowybound = sy-ym/2;
		int highybound = sy+ym/2;
		int lowzbound = lowxbound+lowybound*width;
		int highzbound = highxbound+highybound*width;
		int z_add = width-xm;
		int regionsize = xm*ym;
		
		// Sanity-check the bounds, return a fully transparent black color if the checks fail
		if (lowxbound < 0 || highxbound > width)
			return 0;
		if (lowybound < 0 || highybound > height)
			return 0;
		
		// Accumulate the color components of all pixels in the given range
		int z = lowzbound;
		for (int y = lowybound; y < highybound; y++)
		{
			for (int x = lowxbound; x < highxbound; x++)
			{
				int c = imgbuf[z];
				b += (c&0xFF);
				g += ((c>>8)&0xFF);
				r += ((c>>16)&0xFF);
				z++;
			}
			z += z_add;
		}
		
		// Divide components by the number of pixels iterated over
		r/=regionsize;
		g/=regionsize;
		b/=regionsize;
		
		// Re-combine them and return the result
		return r<<16 | g<<8 | b;
		
	}
}

