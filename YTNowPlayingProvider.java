// Java AWT Imports
import java.awt.image.BufferedImage;

// Java IO Imports
import java.nio.file.Files;
import java.io.File;

// Java Network Imports
import javax.net.ssl.HttpsURLConnection;
import java.net.URLEncoder;
import java.net.URL;

// Java Utility Imports
import java.util.Scanner;
import java.util.Arrays;

// Java ImageIO Imports
import javax.imageio.ImageIO;


public class YTNowPlayingProvider
{
	
	// Final variables...
	static final int 	YT_ALIVE_TIMEOUT			 = 45;	// Number of seconds since the last pulse before the video will be considered 'dead'.
	
	// Variables...
	static String artist = "Unknown", song = "Untitled", thumbLoc = null, lastString = null;
	static BufferedImage albumArt;
	static int status = 1;
	
	static NowPlayingProvider providerConnection;
	
	// Main method
	public static void main(String[] args) throws InterruptedException
	{
		
		// Create the NowPlayingProvider
		providerConnection = new NowPlayingProvider("Youtube Music Firefox");
		
		while (true)
		{
			try
			{	
				
				// Get the song that's currently playing
				getNowPlayingYoutubeSong();
				
				// If something has changed
				if (!(artist+song+thumbLoc+paused).equals(lastString))
				{
					
					// If a video is playing
					if (!paused)
					{
						// Try to grab album art for it
						getAlbumArt();
						// and broadcast it!
						providerConnection.setNowPlaying(song, artist, thumbLoc);
					}
					// Otherwise, broadcast that nothing is playing
					else
						providerConnection.clearNowPlaying();
					
					// Keep track of what's currently playing
					lastString = artist + song + thumbLoc + paused;
				}
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
			}
			finally
			{
				// No need to check for updates too often...
				Thread.sleep(7500);
			}
		}
	}
	
	// Wrapper for getStringImage() that calls it with the proper request String
	public static BufferedImage getAlbumArt()
	{
		if (artist.equals("Unknown"))
			return getStringImage(song + " album art", false, null);
		return getStringImage(artist + " " + song + " album art", false, null);
	}
	
	// Try to get an image representing the given String
	
	// Search order:
	// 	1) Local thumbnail cache
	// 	2) iTunes API (skipped in some circumstances)
	// 	3) qwant API (if not disabled) (skipped in some circumstances)
	// 	4) YouTube video thumbnail
	
	static boolean qwantAPIdisabled = true;	// either way itunes api is used if possible (note: this should probably stay disabled... the qwant api is unofficial and rate-limiting sticks for a week or more in my experience)
	public static BufferedImage getStringImage(String str, boolean allowSubpar, String data)
	{
		BufferedImage img = null;
		int retrycount = 1;
		
		// Remove any slashes from the string (API restrictions)
		str = str.replaceAll("\\Q/\\E","").replaceAll("\\Q\\\\E","");
		
		// (1) Read and return the cached thumbnail if it exists
		try
		{
			File thumbFile = new File("thumb/" + str + ".jpg");
			thumbLoc = thumbFile.getAbsolutePath();
			img = ImageIO.read(thumbFile);
			System.out.println("read cached thumbnail.");
			return img;
		}
		catch (Exception ex4){}
		
		// (*) If the artist was parsed as "Various Artists", then we're best off sticking to the YouTube video thumbnail
		if (str.indexOf("Various Artists") != -1)	// Dont even bother in this case, almost always returns inaccurate results compared to the thumbnail
		{
			File file = new File("thumb/" + str + ".jpg");
			try{ImageIO.write(thumbnailImage, "jpg", file);}catch(Exception ex2){}
			thumbLoc = file.getAbsolutePath();
			return thumbnailImage;
		}
		
		// (2) Try to grab album art from the iTunes API (succeeds for most songs; returns 500px (?) square images)
		if (!allowSubpar)	// should only check on the first pass.
		{
			try
			{
				// Use the iTunes API to search for a song match
				HttpsURLConnection connection = (HttpsURLConnection)new URL("https://itunes.apple.com/search?term=" + URLEncoder.encode(str.replaceAll("\\Q album art\\E",""),"UTF-8")).openConnection();
				connection.connect();
				Scanner sc = new Scanner(connection.getInputStream());
				String idata = "";
				while (sc.hasNext())
				{
					idata += sc.nextLine() + "\n";
				}
				
				// Try to grab a 500px image for the top result
				String iurl = idata.split("\\QartworkUrl100\":\"\\E")[1].split("\\Q\"\\E")[0].replaceAll("\\Q100x100bb\\E","500x500bb");
				System.out.println("grab image for '" + str + "' from external source '" + iurl + "'");
				img = ImageIO.read(new URL(iurl));
				
				// If the image was read successfully, add it to the local cache
				if (img != null)
				{
					File file = new File("thumb/" + str + ".jpg");
					ImageIO.write(img, "jpg", file);
					thumbLoc = file.getAbsolutePath();
					
					// and return it
					return img;
				}
				else
					System.out.println("\tFail.");
			}
			catch (ArrayIndexOutOfBoundsException aex){}
			catch (Exception ex){ex.printStackTrace();}
		}
		
		// (4) If the qwant API is disabled and the earlier steps failed (we fell through to here) ...
		if (qwantAPIdisabled)
		{
			// ... write the YouTube video thumbnail to the local cache
			File file = new File("thumb/" + str + ".jpg");
			try{ImageIO.write(thumbnailImage, "jpg", file);}catch(Exception ex2){}
			thumbLoc = file.getAbsolutePath();
			
			// and return it
			return thumbnailImage;
		}
		
		// (3) OTHERWISE, time to use qwant's (unofficial) API (oh boy)
		try
		{
			// If this is the first iteration of the qwant search
			if (data == null)
			{
				// Try to get search results from qwant's API
				HttpsURLConnection connection = (HttpsURLConnection)new URL("https://api.qwant.com/api/search/images?count=5&offset=" + retrycount + "&q=" + URLEncoder.encode(str,"UTF-8")).openConnection();
				Scanner sc = null;
				
				// Try to establish a connection
				try
				{
					connection.connect();
					sc = new Scanner(connection.getInputStream());
				}
				catch (Exception ex)
				{
					System.err.println("Fatal.");
					ex.printStackTrace();
					
					// If we recieved HTTP Error 429, we're being rate-limited;
					// 	disable the API to prevent any further damage
					if (ex.getMessage().indexOf("429")!=-1)
					{
						System.err.println("Disabling Qwant API (rate-limited)");
						qwantAPIdisabled = true;
						return getStringImage(str, true, null);
					}
					return null;
				}
				
				// Try to read the response data
				data = "";
				while (sc.hasNext())
				{
					data += sc.nextLine() + "\n";
				}
				
				// If the response indicated there was an error, (you could probably guess by now) we're being rate-limited;
				// 	disable the API to prevent any further damage
				if (data.indexOf("error")!=-1)
				{
					System.err.println("Disabling Qwant api (being rate-limited?)");
					qwantAPIdisabled = true;
					return getStringImage(str, true, null);
				}
			}
			
			// While we haven't hit the max retry count and we still haven't gotten an image
			while (retrycount <= 5 && img == null)
			{
				try
				{
					// Get the next element in the search results
					String url = data.split("\\Q\"media\":\"\\E")[retrycount-1].split("\\Q\"\\E")[0].replaceAll("\\Q\\/\\E", "/");
					System.out.println("grab image for '" + str + "' from external source '" + url + "'");
					
					// Try to grab the image if it's square OR we're allowing subpar image searches (square = much more likely to be album art)
					if (allowSubpar || img.getWidth()==img.getHeight())	// square images only
						img = ImageIO.read(new URL(url));
						
					// If the image wasn't read ...
					if (img == null)
					{
						// ... wait a bit
						retrycount++;
						Thread.sleep(2000);	// don't spam their API!
					}
					// Otherwise, write the image to the local cache!
					else
					{
						ImageIO.write(img, "jpg", new File("thumb/" + str + ".jpg"));
					}
				}
				catch (Exception ex3)
				{
					// If there was an exception, wait a bit and try the next result
					retrycount++;
					try{Thread.sleep(500);}catch(Exception ex1){System.out.println("\tFail.");}
				}
			}
		}
		catch (Exception ex2){}
		
		// If we hit the Qwant API's max retry count or just otherwise still don't have an image ...
		if (retrycount > 5 || img == null)
		{
			// If we weren't allowing subpar images this round, look again but allow them
			if (!allowSubpar)
				return getStringImage(str, true, data);
			
			// (4) Otherwise, return the YouTube video thumbnail
			if (allowSubpar && thumbnailImage != null)
				return thumbnailImage;
			
			// If it falls through to here, we have 0 viable images somehow
			throw new RuntimeException("IMAGE_READ_RETRY_COUNT_TOO_HIGH");
		}
		
		// If we got here somehow, we must have an image by way of the if statement above, so return it!
		return img;
	}
	
	// Get information from the song (or video...) currently playing in YouTube
	static String lastId = "";
	static boolean doUpdateState;
	static BufferedImage thumbnailImage;
	static boolean paused = true;
	public static void getNowPlayingYoutubeSong(File... exclusions) throws Exception	// make this return a File[] of exclusions and call it in a loop instead of recursively (stackoverflows are bad)
	{
		
		doUpdateState = false;
		
		// Find the firefox caching directory
		File firefoxCacheDir = new File(System.getProperty("user.home") + "\\AppData\\Local\\Mozilla\\Firefox\\Profiles");	// chrome dir is Local\\Google\\Chrome\\User Data\\Default\\Cache (nvm, chrome doesn't seem to cache youtube stats api accesses there...)
		firefoxCacheDir = new File(firefoxCacheDir.listFiles()[0].getAbsolutePath() + "\\cache2\\entries");
		
		// Find the most recent non-excluded file
		long mtime = 0;
		File bestMatch = null;
		for (File f : firefoxCacheDir.listFiles())
		{
			long time = f.lastModified();
			if (time > mtime && !Arrays.asList(exclusions).contains(f))
			{
				bestMatch = f;
				mtime = time;
			}
		}
		
		// If there are no matches, there's nothing more to do here
		if (bestMatch == null)
			return;
		
		// Read the best match
		byte[] filebytes = Files.readAllBytes(bestMatch.toPath());
		String fileData = new String(filebytes,"UTF-8");
		
		// Figure out its age
		long age = System.currentTimeMillis()-mtime;
		
		// Check the file for cached calls to YouTube's stat-gathering API
		if (fileData.indexOf("www.youtube.com/api/stats/")!=-1)
		{
			
			// If we are in here, then the file contains a record of a call to YouTube's stat-gathering API!
			
			// If the file is older than the live-timeout or the API call indicates that the video has been paused ...
			if (age > YT_ALIVE_TIMEOUT*1000 || fileData.indexOf("&state=paused")!=-1)
			{
				// ... return that nothing is playing anymore
				doUpdateState = false;
				System.err.println("\tVideo has not pulsed recently (it's not playing anymore)");
				if (!song.equalsIgnoreCase("No Song Detected"))
				{
					// Differentiate between a paused video and an inactive video
					// 	(todo: remove this if it's not being used anywhere?)
					if (fileData.indexOf("&state=paused")!=-1)
					{
						paused = true;
						doUpdateState = true;	// need to force an update
						return;
					}
					else
					{
						lastId = "";
						song = "No Song Detected".toUpperCase();
						artist = "";
					}
					System.err.println("No song playing.");
					paused = true;
				}
				paused = true;
				return;
			}
			
			boolean skiplitoggle = false;
			
			// If the video WAS paused... well, now its not, so make sure we are going to send an update to the NowPlayingProvider server
			if (paused)
			{
				paused = false;
				doUpdateState = true;	// need to force an update
				skiplitoggle = true;
				lastId = "";
			}
			
			// (todo: is this necessary? last I remember it was, which is somewhat odd...)
			doUpdateState = true;
			
			// Grab the video ID
			String videoId = fileData.split("\\Q&docid=\\E")[1].split("\\Q&\\E")[0];
			
			// If the ID is unchanged, then there's nothing to update (unless the video is now paused... skiplitoggle handles that case)
			if (videoId.equals(lastId))
			{
				if (!skiplitoggle)
					doUpdateState = false;
				return;
			}
			
			// Keep track of the ID
			lastId = videoId;
			
			// Printouts and stuff
			System.out.println("Song Info:");
			System.out.println("\tid = " + videoId);
			
			// Grab video metadata from YouTube's oembed API
			URL url = new URL("https://www.youtube.com/oembed?url=http://www.youtube.com/watch?v=" + videoId + "&format=json");
			String data = new Scanner(url.openStream()).nextLine();
			String title = data.split("\\Qtitle\":\"\\E")[1].split("\\Q\",\\E|\\Q\"}\\E")[0].replaceAll("\\Q\\\"\\E","");
			System.out.println("\ttitle = " + title);
			String author = "Unknown";
			String song = "Untitled";
			
			// Try to split the formatted title up into a song and and author (can't really help it if someone decided to list them in the less common order, but that doesn't happen too often)
			String sft = songFormat(title);
			String[] parts = sft.split("\\Q-\\E|\\Q:\\E|\\Q\\u2013\\E|\\Q|\\E");
			
			// Only split into parts by hyphen if there's nothing else to split by (hyphens sometimes appear legitimitely in artist/song names)
			if (sft.indexOf("-") != -1 && (sft.indexOf(":") != -1 || sft.indexOf("\\u2013") != -1 || sft.indexOf("|") != -1))
				parts = sft.split("\\Q:\\E|\\Q\\u2013\\E|\\Q|\\E");
			
			// If there are two elements remaining, one is the song and the other is the author
			if (parts.length == 2)
			{
				song = parts[1].trim();
				author = parts[0].trim();
			}
			// Otherwise, just say we only have a song ( :shrug: )
			else
				song = parts[0].trim();
			
			// If the user who posted the video is of the form "<something here> - Topic", then "<something here>" will almost always be the correct artist name
			String astring = data.split("\\Qauthor_name\":\"\\E")[1].split("\\Q\"\\E")[0];
			if (astring.indexOf(" - Topic")!=-1)
				author = astring.split("\\Q - Topic\\E")[0].trim();
			else if (author.equals("Unknown"))
				author = astring.trim();
			
			// Sometimes the title failed to split above, but we now have the author from the "<> - Topic" method above, so we can make sure the author isn't included as part of the detected song name now
			if (song.indexOf(author)==0)
				song = song.substring(author.length(),song.length()).trim();
			
			System.out.println("\tsong = " + song);
			System.out.println("\tauthor = " + author);
			YTNowPlayingProvider.song = unidecode(song);
			YTNowPlayingProvider.artist = unidecode(author);
			
			// Read the YouTube video thumbnail image
			thumbnailImage = ImageIO.read(new URL(data.split("\\Qthumbnail_url\":\"\\E")[1].split("\\Q\"\\E")[0].replaceAll("\\Q\\/\\E","/")));
			
			// todo: do some processing on the thumbnail image to detect if it was created by YT automatically based on square album art, then strip the junk to recreate the original album art (only need to do this if the thumbnail image is chosen, so this comment probably shouldn't be located here...)
			
		}
		// If we get here, this file doesn't contain a call to the YouTube stats API ...
		else
		{
			// ... so if we have reached files that are too old ...
			if (age > Math.max(30000,YT_ALIVE_TIMEOUT*1000))
			{
				// ... and the video isn't paused, because if it is then we should just wait until it un-pauses, and have nothing useful to add ...
				if (!paused)
				{
					// ... mark that nothing is playing anymore
					lastId = "";
					song = "No Song Detected".toUpperCase();
					artist = "";
					System.err.println("No song playing.");
					doUpdateState=true;	// need to force an update
					paused = true;
				}
				return;
			}
			
			// Otherwise we haven't reached files that are too old yet
			
			// Update the file exclusion list to include the file we just checked
			File[] newExclusions = new File[exclusions.length+1];
			System.arraycopy(exclusions, 0, newExclusions, 0, exclusions.length);
			newExclusions[exclusions.length] = bestMatch;
			
			// And look again.
			getNowPlayingYoutubeSong(newExclusions);
		}
	}
	
	// Performs various formatting operations on the given String (see comments in-method)
	// Really gross, but it works pretty well.
	// (At the very least, it fixes the vast majority of titles properly, and only messes up in a select few cases)
	public static String songFormat(String str)
	{
		// These phrases / symbols are almost never relevant to the actual title/author of the currently playing song, so remove them to clean things up.
		// Note: does not remove all parenthesis, just ones with nothing left inside them after the 'undesireable' phrases are dealt with
		String ret = str.replaceAll("\\QAudio\\E","").replaceAll("\\QOfficial Video\\E","").replaceAll("\\Qlyrics\\E","").replaceAll("\\QLyrics\\E","").replaceAll("\\QOfficial Audio\\E","").replaceAll("\\QOFFICIAL VIDEO\\E","").replaceAll("\\QAcoustic\\E","").replaceAll("\\QLyric Video\\E","").replaceAll("\\QVideo Version\\E","").replaceAll("\\QVideo\\E","").replaceAll("\\QOfficial Music \\E","").replaceAll("\\Q*\\E","").replaceAll("\\Q()\\E","").replaceAll("\\Q[]\\E","").trim();
		
		// This next section is doing two things:
		//	1) removing useless information between parenthesis BUT
		//	2) retaining information about edits / remixes
		//		note that by default information about covers is NOT retained (reasoning that covers are closer to the source material than edits / remixes), but this can easily be changed below
		//		additionally, to simplify things a bit, the section below also attempts to remove information on any 'featured' artists
		
		String realRet = "";
		boolean inparen = false, autoKillChar = false;
		String exclStr = "";
		int ucount = 1;
		for (int ind = 0; ind < ret.length(); ind++)
		{
			char c = ret.charAt(ind);
			
			// If a parenthesis grouping is being opened, track what's inside it
			if (c=='['||c=='(')
			{
				exclStr = "";
				inparen = true;
			}
			
			// Keeping track of the characters in the parenthesis grouping if we're in one
			if (inparen)
				exclStr += c;
			// Otherwise, if we encounter a '|' and have already found a viable split of the title ...
			else if (c=='|' && ucount>1)
			{
				// ... assume everything after the '|' is useless descriptive junk
				autoKillChar = true;
			}
			// Otherwise, if we're still looking at the String ...
			else if (!autoKillChar)
			{
				
				// Add the character in question to the output
				realRet += c;
				
				// Check to see whether or not the character indicates a viable split of the title ...
				// 	1) If there is a '-' with a space on at least one side (otherwise its most likely a hyphenated word/name)
				if (c=='-' && ((ind>=1 && ret.charAt(ind-1)==' ') || (ind < ret.length()-1 && ret.charAt(ind+1)==' ')))
					ucount++;
				// 	2) If there is a '|' or ':' character
				else if (c=='|' || c==':')
					ucount++;
				//	3) If there is an EN-dash (yes, I've seen this happen :< )
				else if (c=='3' && (ind >= "\\u2013".length() && ret.substring(ind-"\\u2013".length(),ind).equals("\\u2013")))	// 1. eww; 2. idk if that substring is correct
					ucount++;
					
			}
			
			// If a parenthesis grouping is being closed, figure out whether or not to include it:
			if (c==']'||c==')')
			{
				String test = exclStr.toLowerCase();
				// Include the contents of the parenthesis grouping if they are describing an edit or a remix
				if ((test.contains("edit")||test.contains("remix")) && !autoKillChar)
					realRet += exclStr;
				inparen = false;
			}
			
			// If the output looks like it's about to contain featured artist information:
			String test = realRet.toLowerCase();
			if (test.indexOf("ft.")!=-1 || test.indexOf("feat.")!=-1)
			{
				// Remove it and stop looking at the rest of the String
				realRet = realRet.replaceAll("\\Qfeat.\\E","").replaceAll("\\Qft.\\E","");
				autoKillChar = true;
			}
			
		}
		return realRet.trim();
	}
	
	// Replaces instances of \\u#### in a String with the appropriate Unicode characters
	public static String unidecode(String str)
	{
		String ret = "";
		boolean inchecku = false;
		for (int i = 0; i < str.length(); i++)
		{
			char c = str.charAt(i);
			if (c == '\\')
				inchecku = true;
			else if (inchecku)
				if (c == 'u' || c == 'U')
				{
					String uval = "";
					for (int n = 0; n < 4; n++)
					{
						i++;
						uval += str.charAt(i);
					}
					ret += (char)Integer.parseInt(uval,16);
					inchecku = false;
				}
				else
				{
					ret += "\\"+c;
					inchecku = false;
				}
			else
				ret += c;
			
		}
		return ret.replaceAll("\\Q\\/\\E","/").trim();
	}
	
}

