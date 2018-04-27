// Java AWT Imports
import java.awt.image.BufferedImage;

// Java IO Imports
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

// Java Network Imports
import java.net.Socket;


// Handles a NowPlayingProvider provider connection
public class NowPlayingProvider
{
	
	// Variables...
	static Socket socket;
	static PrintWriter out;
	
	// Constructor (opens a connection to the NowPlayingProvider server)
	public NowPlayingProvider(String name)
	{
		try
		{
			// Open the connection and set up the PrintWriter
			out = new PrintWriter(new OutputStreamWriter((socket = new Socket("localhost", 53986)).getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
			
			// Send the program's name
			out.println(name);
			out.flush();	
		}
		catch (Exception ex)
		{
			System.err.println("ERROR INITIALIZING NOWPLAYINGPROVIDER BRIDGE! [is the server running?]");
		}
	}
	
	// Tell the server that something new is playing
	public void setNowPlaying(String title, String subtext, String imgpath)
	{
		out.println(title + "\0" + subtext + "\0" + imgpath);
		out.flush();
	}
	
	// Tell the server that whatever was playing has stopped
	public void clearNowPlaying()
	{
		out.println("clearnp");
		out.flush();
	}
}
