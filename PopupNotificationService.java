// Java AWT Imports
import java.awt.image.BufferedImage;
import java.awt.event.WindowAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.MouseEvent;
import java.awt.FontMetrics;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.Color;

// Java Swing Imports
import javax.swing.JComponent;
import javax.swing.JFrame;


public class PopupNotificationService
{
	
	// Variables...
	static NowPlayingConsumer nplink;
	static JFrame notificationFrame;
	static Canvas canvas;
	
	// Final variables...
	static final Dimension SCREEN_DIMENSIONS = Toolkit.getDefaultToolkit().getScreenSize();
	static final int SCREEN_WIDTH = SCREEN_DIMENSIONS.width;
	static final int SCREEN_HEIGHT = SCREEN_DIMENSIONS.height;
	
	// Main method
	public static void main(String[] args)
	{
		try
		{
			// Create and set up the notification JFrame
			notificationFrame = new JFrame();
			notificationFrame.setSize(800, 300);
			notificationFrame.setUndecorated(true);
			
			// Create the Canvas element
			canvas = new Canvas(notificationFrame);
			
			// Add it to the JFrame
			notificationFrame.add(canvas);
			
			// ... and go back to setting up the notification JFrame
			notificationFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			notificationFrame.setLocation(SCREEN_WIDTH, (int)(SCREEN_HEIGHT*9/10.0-notificationFrame.getHeight()));
			notificationFrame.setAlwaysOnTop(true);
			notificationFrame.setFocusable(false);
			notificationFrame.setFocusableWindowState(false);
			notificationFrame.setBackground(new Color(0,0,0,0));
			
			// Create the NowPlayingConsumer and register a NowPlayingListener
			nplink = new NowPlayingConsumer("Popup Notification Service");
			nplink.registerNowPlayingListener(new NowPlayingAdapter(){
				
				// Called when something new is playing
				public void nowPlayingSongSet(NowPlayingEvent ev)
				{
					// Update the canvas element
					canvas.takeValuesFrom(ev);
					
					// And start a new thread on the canvas element (see Canvas.run())
					new Thread(canvas).start();
				}
				
			});
		}
		catch (Exception ex)
		{
			System.err.println("Fatal exception during initialization.");
			ex.printStackTrace();
			System.exit(0);
		}
	}
	
	// Utility function used to force a String into title case
	public static String titleCase(String str)
	{
		
		String ret = "";
		boolean caps = true;
		
		// For every character in the String
		for (char c : str.toCharArray())
		{
			
			// If it should be capitalized, capitalize it
			if (caps)
				ret += Character.toUpperCase(c);
			// Otherwise, make sure it isn't capitalized
			else
				ret += Character.toLowerCase(c);
			
			// If the character signals the end of a word, the next character should be capitalized
			if (c==' '||c=='-'||c=='/')
				caps = true;
			// and otherwise, it shouldn't be!
			else
				caps = false;
			
		}
		return ret;
		
	}
	
	// Utility function used to make an image match a given aspect ratio (by adding transparent borders)
	public static BufferedImage getImageWithAspectRatio(BufferedImage image, double aspectRatio)
	{
		
		if (image == null)
			return null;
		
		BufferedImage aspectRatioFixedImage = null;
		
		// If the image is taller than it should be ...
		if (image.getHeight() / (double)image.getWidth() > aspectRatio)
		{
			// ... create a new image with a width that matches the aspect ratio
			aspectRatioFixedImage = new BufferedImage((int)(image.getHeight() / aspectRatio), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			int swidth = (int)((image.getWidth() / (double) image.getHeight()) * aspectRatioFixedImage.getHeight());
			
			// and copy the old image to it
			aspectRatioFixedImage.getGraphics().drawImage(image, (aspectRatioFixedImage.getWidth() - swidth) / 2, 0, swidth, aspectRatioFixedImage.getHeight(), null);
		}
		// If the image is wider than it should be ...
		else if (image.getHeight() / (double)image.getWidth() < aspectRatio)
		{
			// ... create a new image with a height that matches the aspect ratio
			aspectRatioFixedImage = new BufferedImage(image.getWidth(), (int)(image.getWidth() * aspectRatio), BufferedImage.TYPE_INT_ARGB);
			int sheight = (int)((image.getHeight() / (double) image.getWidth()) * aspectRatioFixedImage.getWidth());
			
			// and copy the old image to it
			aspectRatioFixedImage.getGraphics().drawImage(image, 0, (aspectRatioFixedImage.getHeight() - sheight) / 2, aspectRatioFixedImage.getWidth(), sheight, null);
		}
		// Otherwise the image is good as-is ...
		else
		{
			// return it
			return image;
		}
		
		return aspectRatioFixedImage;
		
	}
}

// JComponent responsible for drawing the popup
class Canvas extends JComponent implements Runnable
{
	
	// Final variables...
	static final int SCREEN_WIDTH = PopupNotificationService.SCREEN_WIDTH;
	static final int SCREEN_HEIGHT = PopupNotificationService.SCREEN_HEIGHT;
	
	// Variables...
	JFrame frame;
	FontMetrics fm, fm2;
	Color masterColor;
	String masterTitle, masterSubtext;
	BufferedImage masterImage;
	
	// Constructor...
	public Canvas(JFrame frame)
	{
		
		this.frame = frame;
		
		// Re-paint immediately upon creation (to grab FontMetrics objects)
		frame.repaint();
		
	}
	
	// Updates the Canvas with the values from a NowPlayingEvent
	public void takeValuesFrom(NowPlayingEvent ev)
	{
		
		// Grab the title/subtext and make them title-case
		masterTitle = PopupNotificationService.titleCase(ev.getTitle());
		masterSubtext = PopupNotificationService.titleCase(ev.getSubtext());
		
		// Grab the dominant color
		masterColor = ev.getDominantColor();
		
		// Grab the image and make it fit a square aspect ratio
		masterImage = PopupNotificationService.getImageWithAspectRatio(ev.getImage(), 1);
		
	}
	
	// Where the actual drawing of the component happens
	public void paint(Graphics g)
	{
		
		// Background color (darkest)
		Color pb = masterColor.darker().darker().darker().darker();
		g.setColor(new Color(pb.getRed(),pb.getGreen(),pb.getBlue(),240));
		g.fillRect(0, 0, getWidth(), getHeight());
		
		// Lighter background color
		pb = masterColor.darker().darker();
		g.setColor(new Color(pb.getRed(),pb.getGreen(),pb.getBlue(),160));
		g.fillRect(10, 10, getWidth()-2*10, getHeight()-2*10);
		
		// Drawing the image
		if (masterImage != null)
			g.drawImage(masterImage, 15, 15, 270, 270, null);
		
		// Getting ready to draw the title
		g.setFont(g.getFont().deriveFont(85f));
		g.setColor(masterColor);
		fm = g.getFontMetrics();
		
		// Figuring out text positioning
		int xn = 270+15+25;
		int cy = 150/2 - (fm.getAscent()+fm.getDescent())/2 + fm.getAscent();
		
		// Drawing the title
		g.drawString(masterTitle, xn, cy);
		
		// Getting ready to draw the subtext
		g.setColor(masterColor.darker());
		g.setFont(g.getFont().deriveFont(60f));
		fm2 = g.getFontMetrics();
		
		// Drawing the subtext
		g.drawString(masterSubtext, xn, cy + fm2.getHeight());
		
	}
	
	public void run()
	{
		try
		{
			
			// Make the frame visible
			frame.setVisible(true);
			
			// Repaint to get the FontMetrics if they haven't already been stored
			if (fm == null)
				repaint();
			while (fm == null) { try{ Thread.sleep(100); } catch(Exception ex){} }
			
			// Adjust the frame's size based upon the width of the title/subtext
			frame.setSize(270+15+25+Math.max(fm.stringWidth(masterTitle)+25,fm2.stringWidth(masterSubtext)+25), frame.getHeight());
			frame.repaint();
			
			// Calculate values to be used in the animation
			int width = frame.getWidth();
			int targetMove = (int)(width+SCREEN_HEIGHT*(1-9/10.0));
			int change = (targetMove)/25;
			int xloc = frame.getLocation().x, yloc = frame.getLocation().y;
			
			// Slide the frame onto the screen
			while (xloc > SCREEN_WIDTH-targetMove)
			{
				xloc = Math.max(xloc - change, SCREEN_WIDTH-targetMove);
				frame.setLocation(xloc, yloc);
				Thread.sleep(15);
			}
			
			// Pause for a bit
			Thread.sleep(2400);
			
			// Slide the frame off of the screen
			while (xloc < SCREEN_WIDTH)
			{
				xloc = Math.min(xloc + change, SCREEN_WIDTH);
				frame.setLocation(xloc, yloc);
				Thread.sleep(15);
			}
			
		}
		catch (Exception ex)
		{
			frame.setLocation(SCREEN_WIDTH, frame.getLocation().y);
		}
		finally
		{
			// Make the frame invisible
			frame.setVisible(false);
		}
	}
}
