// Java AWT Imports
import java.awt.image.BufferedImage;
import java.awt.Graphics;
import java.awt.Color;

// Java IO Imports
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.File;

// Java Utility Imports
import java.util.Scanner;
import java.util.Arrays;

// Java ImageIO Imports
import javax.imageio.ImageIO;

// JNA Imports
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.win32.*;
import com.sun.jna.*;


public class WindowsThemeController
{
	
	// Files used to store colored versions of the desktop wallpaper
	static final File maskedBgFile1		= new File(  "MASKEDBG.bmp" );
	static final File maskedBgFile2		= new File( "TMASKEDBG.bmp" );
	
	// Files used to backup the initial wallpaper location
	static final File realbgStoreFile	= new File(  "BGBACKUP.ini" );
	
	// Constants used to control the creation of the color palette passed off to Windows
	static final int	TBCOLOR_PALETTE_SIZE 		 = 8;	// DO NOT CHANGE THIS VALUE!
	static final int	BASE_COLOR_POSITION	 		 = 3;	// 0-7 inclusive
	static final int 	START_COLOR_POSITION		 = 4;	// 0-7 inclusive
	static final int 	ACCENT_COLOR_POSITION		 = 5;	// 0-7 inclusive
	
	// Variables...
	static NowPlayingConsumer nplink;
	static BufferedImage bgimg;
	static File bgFile, maskedBgFile = maskedBgFile1;
	
	// Used to modify background image on-the-fly
	public static interface User32 extends Library
	{
		User32 INSTANCE = (User32)Native.loadLibrary("user32",User32.class,W32APIOptions.DEFAULT_OPTIONS);
		boolean SystemParametersInfo(int a, int b, String str, int c);
	}
	
	// Main method
	public static void main(String[] args)
	{
	
		try
		{
			
			// Modify the registry to force all new instances of explorer.exe to launch as seperate processes
			// (This way, we can use taskkill to kill the background instance of explorer that is responsible for rendering the desktop without affecting other explorer instances.)
			_extern("script/spinit", false);
			
			// Load the current background image (as well as creating a backup / restoring from backup if necessary)
			bgimg = loadBgImage();
			
			// Create the NowPlayingConsumer ...
			nplink = new NowPlayingConsumer("Windows Theme Controller");
			
			// ... and register a NowPlayingListener
			nplink.registerNowPlayingListener(new NowPlayingListener(){
				
				// Called when something new is playing
				public void nowPlayingSongSet(NowPlayingEvent ev)
				{
					// Set the background image based on the event
					trySetBgImage(ev);
					
					// Generate a color palette based upon the event's dominant color
					Color[] palette = generatePalette(ev.getDominantColor(), BASE_COLOR_POSITION, TBCOLOR_PALETTE_SIZE);
					
					// Set the theme color, and attempt to apply it by forcing the desktop's instance of explorer.exe to restart
					_extern("script/tbcolor", false, paletteString(palette), colorString(palette[START_COLOR_POSITION]), colorString(palette[ACCENT_COLOR_POSITION]));
					_extern("script/refreshcolors", false);
		
				}
				
				// Called when nothing is playing anymore
				public void nowPlayingSongCleared(NowPlayingEvent ev)
				{
					
					// If the background was loaded (normally OR from a backup) successfully at the beginning of the program ...
					if (bgimg != null)
					{
						// ... restore it!
						User32.INSTANCE.SystemParametersInfo(0x0014, 0, bgFile.getAbsolutePath(), 1);
					}
					
					// Generate a color palette based upon the event's dominant color
					Color[] palette = generatePalette(ev.getDominantColor(), BASE_COLOR_POSITION, TBCOLOR_PALETTE_SIZE);
					
					// Set the theme color, and attempt to apply it by forcing the desktop's instance of explorer.exe to restart
					_extern("script/tbcolor", false, paletteString(palette), colorString(palette[START_COLOR_POSITION]), colorString(palette[ACCENT_COLOR_POSITION]));
					_extern("script/refreshcolors", false);
					
				}
				
			});
		}
		catch (Exception ex)
		{
			System.err.println("Fatal exception during initializtion.");
			ex.printStackTrace();
			System.exit(0);
		}
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
	
	// Load the current background image (as well as creating a backup / restoring from backup if necessary)
	public static BufferedImage loadBgImage()
	{
		try
		{
			// Get the current background
			bgFile = new File(_extern("script/getbg", true));
			
			// If the background is one of the files that the program uses to store re-colored backgrounds ...
			if (bgFile.getAbsolutePath().equalsIgnoreCase(maskedBgFile1.getAbsolutePath()) || bgFile.getAbsolutePath().equalsIgnoreCase(maskedBgFile2.getAbsolutePath()))
			{
				// ... then the program must have crashed / been exited with a masked background still active.
				
				// Try to restore the last known "real" background
				bgFile = new File(new Scanner(realbgStoreFile).nextLine());
				System.out.println("[Info] Restored last known wallpaper image from " + bgFile.getAbsolutePath());
				
			}
			// Otherwise, back-up the background image in case the program crashes / is closed with a masked background active
			else
				writeFile(realbgStoreFile, bgFile.getAbsolutePath());
			
			// Read & return the image
			return ImageIO.read(bgFile);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		return null;
	}
	
	// Sets the background image based upon the given NowPlayingEvent
	public static void trySetBgImage(NowPlayingEvent ev)
	{
		
		// If the background was read properly
		if (bgimg != null)
		{
			// Toggle between using maskedBgFile1 and maskedBgFile2 (the background will not update properly without changing file location every update)
			if (maskedBgFile == maskedBgFile1)
				maskedBgFile = maskedBgFile2;
			else
				maskedBgFile = maskedBgFile1;
			
			try
			{
				// Mask the background with the event's dominant color
				ImageIO.write(maskWithColor(bgimg, ev.getDominantColor()), "bmp", maskedBgFile);
				
				// and update the Windows background image
				User32.INSTANCE.SystemParametersInfo(0x0014, 0, maskedBgFile.getAbsolutePath(), 1);
			}
			catch (IOException ex)
			{
				System.err.println("Unable to set background :(");
				ex.printStackTrace();
			}
		}
	}
	
	// Masks an image with a color
	public static BufferedImage maskWithColor(BufferedImage src, Color color)
	{
		// Create a copy of the image
		BufferedImage retImg = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics gx = retImg.getGraphics();
		
		// Copy over the original image
		gx.drawImage(src,0,0,null);
		
		// Cover it with a transparent layer of the color
		gx.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),80));
		gx.fillRect(0, 0, retImg.getWidth(), retImg.getHeight());
		
		// Cover it with a transparent black layer
		gx.setColor(new Color(0,0,0,210));
		gx.fillRect(0, 0, retImg.getWidth(), retImg.getHeight());
		
		// Cover it with another transparent layer of the color
		gx.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),40));
		gx.fillRect(0, 0, retImg.getWidth(), retImg.getHeight());
		
		// Clean-up and return the image
		gx.dispose();
		return retImg;
	}
	
	// Generates a palette to be used by Windows based upon the given parameters
	public static Color[] generatePalette(Color color, int centerPosition, int size)
	{
		// Create the palette
		Color[] palette = new Color[size];
		
		// Set the 'center' color
		palette[centerPosition] = color;
		
		// Fill in all the colors after the 'center' with a progressively brighter version of the base color
		for(int i=centerPosition-1;i>=0;i--)
			palette[i]=palette[i+1].brighter();
		
		// Fill in all the colors before the 'center' with a progressively darker version of the base color
		for(int i=centerPosition+1;i<palette.length;i++)
			palette[i]=palette[i-1].darker();
		
		// Return the palette
		return palette;
	}
	
	// Formats a color palette as a String
	public static String paletteString(Color[] palette)
	{
		String ret = "";
		for (Color c:palette)
			ret += String.format("%02X", c.getRed())+String.format("%02X", c.getGreen())+String.format("%02X", c.getBlue()) + "00";
		return ret;
	}
	
	// Formats a color as a String
	public static String colorString(Color color)
	{
		return String.format("%02X", color.getBlue())+String.format("%02X", color.getGreen())+String.format("%02X", color.getRed());
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
}
