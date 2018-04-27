// Java AWT Imports
import java.awt.image.BufferedImage;

// Java IO Imports
import java.nio.file.Files;
import java.io.File;

// Java Network Imports
import java.net.URL;

// Java Utility Imports
import java.util.Scanner;
import java.util.Arrays;

// Java ImageIO Imports
import javax.imageio.ImageIO;


public class NetflixNowPlayingProvider
{
	
	// Final Variables...
	static final int 	NETFLIX_ALIVE_TIMEOUT			 = 45;	// Number of seconds since the last pulse before the video will be considered 'dead'.
	
	// Variables...
	static String showID = "?", currentID = "?", episodeTitle = "Unknown", showName = "Unknown", artPath = "", lastID = "?";
	static BufferedImage albumArt;
	static boolean lastIsPlaying;
	static int status = 1;
	
	static NowPlayingProvider providerConnection;
	
	// Main method
	public static void main(String[] args) throws InterruptedException
	{
		
		// Create the NowPlayingProvider
		providerConnection = new NowPlayingProvider("Netflix");
		
		while (true)
		{
			try
			{	
			
				// Figure out if anything is playing
				boolean isPlaying = isNetflixPlaying();
				
				// If something has changed
				if (isPlaying != lastIsPlaying || (isPlaying && !currentID.equals(lastID)))
				{
					
					// If something is playing
					if (isPlaying)
					{
						// Figure out what that something might be
						getNowPlayingNetflixShow();
						
						// and broadcast it!
						providerConnection.setNowPlaying(episodeTitle, showName, artPath);
					}
					// Otherwise, broadcast that nothing is playing
					else
						providerConnection.clearNowPlaying();
					
					// Keep track of the ID of what (if anything) is playing
					lastID = currentID;
				}
				
				// Keep track of whether or not anything is currently playing
				lastIsPlaying = isPlaying;

			}
			catch (Exception ex)
			{
				ex.printStackTrace();
			}
			
			// No need to check for updates too often...
			Thread.sleep(7500);
			
		}
	}
	
	// Get information on the currently playing show from Netflix's API (the ID should have already been determined in isNetflixPlaying())
	
	// <WARNING>
	//
	//	The API used in this method is not public, and I take no responsibility for any actions Netflix may take
	//	against your account for this program's use of the API.
	//
	//	API calls require session tokens from a logged-in account, and as such are identifiable as having originated
	//	from the user's account.
	//
	// </WARNING>
	
	static String lastApiURL = "";
	public static void getNowPlayingNetflixShow() throws Exception
	{
		
		System.out.println("Try to grab everything but Episode ID from shakti api...");
		System.out.println("\tEpisode ID = " + currentID);
		
		// Get the API URL
		String apiURL = "https://www.netflix.com/api/shakti/19592385/metadata?movieid=" + currentID;	// im pretty sure the numbers in this url are not unique to my netflix account, but this would be a good thing to check...
		
		// If the API URL hasn't changed, then the ID must not have changed either, and there's no reason to actually call the API
		if (apiURL.equals(lastApiURL))
			return;
		
		// Use curlfire to read the API using the user's Netflix cookies, duplicated from their Firefox profile
		File oFile = new File("output.txt");
		oFile.delete();
		String fileData = _extern("curlfire", true, "\"" + apiURL + "\"");
		
		System.out.println("Read api entry successfully");
		
		// Get the show name
		showName = fileData.split("\\Q\"type\":\\E")[1].split("\\Q\"title\":\"\\E")[1].split("\\Q\"\\E")[0];
		
		// Get the episode name
		try
		{
			// Try to parse the episode name
			episodeTitle = fileData.split("\\Q\"episodeId\":" + currentID + "\\E")[1].split("\\Q\"title\":\"\\E")[1].split("\\Q\"\\E")[0];
			System.out.println("\tEpisode Title = " + episodeTitle);
		}
		catch (Exception ex)
		{
			// If there was no episode, assume that this is a movie or a short, and that no real error has occurred
			System.out.println("This is either a movie or a short, yes?");
			currentID = "NO_ID_AVAILABLE";
			episodeTitle = showName;
			System.out.println("\tEpisode Title = " + episodeTitle);
			showName = "Netflix";
		}
		System.out.println("\tShow Name = " + showName);
		
		// Get the highest-resolution box-art image url
		String coverArtURL = fileData.split("\\Q\"boxart\":\\E")[1].split("\\Q\"url\":\"\\E")[1].split("\\Q\"\\E")[0];
		System.out.println("\tCover Art URL = " + coverArtURL);
		
		// Get the show ID (note that this differs from the episode ID!)
		showID = fileData.split("\\Q\"type\":\\E")[1].split("\\Q\"id\":\\E")[1].split("\\Q,\\E")[0];
		
		// Either read the box-art image from the cache
		// - or - read the box-art image from the url and cache it for future use
		File thumbFile = new File("thumb/" + showID + ".png");
		artPath = thumbFile.getAbsolutePath();
		if (thumbFile.exists())
		{
			System.out.println("\tthumb found in cache 'thumb/" + showID + ".png'");
		}
		else
		{
			BufferedImage artImg = ImageIO.read(new URL(coverArtURL));
			ImageIO.write(artImg, "png", new File("thumb/" + showID + ".png"));
			System.out.println("\tthumb written to cache 'thumb/" + showID + ".png");
		}
		
		// Remeber the API URL
		lastApiURL = apiURL;
		
	}

	// Determine whether or not Netflix is playing
	public static boolean isNetflixPlaying()
	{
		try
		{
			
			String fileData = "";
			
			// Use the AutoHotkey script to determine whether a window exists with a title of the format "Netflix Playback (<episodeID>)"
			File oFile = new File("netstate.txt");
			oFile.delete();
			_extern("script/netwindetect", false);
			
			// Need to cycle and grab the output manually because trying to grab it using the normal method fails due to some quirk in ahk output...
			int cycleCount = 0;
			do
			{
				if (oFile.exists())
				{
					byte[] filebytes = filebytes = Files.readAllBytes(oFile.toPath());
					fileData = new String(filebytes, "UTF-8");
				}
				cycleCount++;
				if (cycleCount > 10)
				{
					System.out.println("Aborting wait cycle!");
					return false;
				}
				if (fileData.indexOf("\n")!=-1)
						break;
				Thread.sleep(100);
			}
			while (true);
			
			// Read whether or not a match was found
			boolean playing = Boolean.parseBoolean(fileData.split(" ")[0]);
			
			// If a match was found, extract the episodeID
			if (playing)
				currentID = fileData.split("\\Q(\\E")[1].split("\\Q)\\E")[0];
			
			// Return whether or not a match was found
			return playing;
			
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		return false;
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

