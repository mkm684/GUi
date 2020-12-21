import processing.core.*;
//import processing.Serial.*;
import sun.net.www.http.HttpClient;

import java.io.IOException;
import java.net.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.*;
import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import javax.servlet.http.HttpServletResponse;

// Processing plotting library
import grafica.*;
// Processing GUI library
import controlP5.*;


public class SerialRealTimePlot extends PApplet {

    public void settings() {
        size(plotWidth, plotHeight + nButtons*BtnHeight);
    }

    static public void main(String[] passedArgs) {
        String[] appletArgs = new String[] { "SerialRealTimePlot" };
        if (passedArgs != null) {
            PApplet.main(concat(appletArgs, passedArgs));
        } else {
            PApplet.main(appletArgs);
        }
    }

    private final int nChannels = 9;  // also number of plots
    private final int nLevels = 3;  // number of levels for plot split
    private final int nButtons = 3;  // also number of plots
    private final int nTextinput = 3;  // also number of plots

    private final int intSize = 4;  // bytes
    
    int firstPlotPos[] = {0, 0};
    int panelDim[] = {225, 140};
    int plotMargins = 60;
    int btnMargins = 25;
    
    private final int plotWidth = panelDim[0]*nChannels/nLevels + plotMargins*nLevels;
    private final int plotHeight = panelDim[1]*nChannels/nLevels + plotMargins*nLevels;
    
    private final int BtnWidth = plotWidth/2;
    private final int BtnHeight = 25;
    private final int nTextWidth = plotWidth/2;
    private final int nTextHeight = 25;
    
    // Array of points for nchannels
    private final List<Integer>[] channels = new List[nChannels];
    // Store last nChannels integers (1 per each channel)
    private final byte[] currentBuffer = new byte[nChannels * intSize];
    // Use this class to interpret bytes as integers
    private ByteBuffer byteBuffer;

    private final int nPoints = 250;  // num of points in each plot (generally affects resolution and speed)
    private int pointsCnt = 0;  // count each new point
    private final float scatterCoef = 5.0f;
    private final GPlot[] plots = new GPlot[nChannels];
    private boolean isPaused = false;

    // For x-ticks
    private long startTime;
    private long currentTime;
    private long currentTimePrev;

    // For benchmarking
    private final boolean runBench = false;
    private int cnt;
    private final int skipNFirstPoints = 100;
    private int skipNFirstPointsCnt = 0;
    
    int temp = 0;

    /*
     *  Use Processing GUI framework to add play/pause button. This is a pretty rich library
     *  so you can put some other useful elements
     */
    private ControlP5 cp5;
    URL url;
    HttpURLConnection con;


    public void setup() {

        // Create four plots to represent the 9 panels
        for (int i = 0; i < nChannels/nLevels; i++) {
        	for (int j = 0; j < nLevels; j++) {
	        	plots[nLevels*i+j] = new GPlot(this);
	        	plots[nLevels*i+j].setMar(plotMargins/2,plotMargins/2,plotMargins/2,plotMargins/2);
	            plots[nLevels*i+j].setPos(firstPlotPos[0] + j*(panelDim[0]+plotMargins), 
	            							firstPlotPos[1] + i*(plotMargins+panelDim[1]));
	            plots[nLevels*i+j].setDim(panelDim[0], panelDim[1]);
	            plots[nLevels*i+j].setAxesOffset(0);
	            plots[nLevels*i+j].setTicksLength(-4);
	            channels[nLevels*i+j] = new ArrayList<>();
        	}
        }

        PFont font = createFont("arial", 20);

        cp5 = new ControlP5(this);
        cp5.addButton("pauseBtn").setPosition(0, plotHeight).setSize(BtnWidth, BtnHeight);
        cp5.addButton("TBD").setPosition(0, plotHeight+BtnHeight).setSize(BtnWidth, BtnHeight);
        cp5.addButton("SetRTC").setPosition(0, plotHeight+2*BtnHeight).setSize(BtnWidth, BtnHeight);
        
        cp5.addTextfield("textInput_1", BtnWidth, plotHeight, nTextWidth, nTextHeight).setFont(font).setFocus(true).setColor(color(255, 0, 0));
        cp5.addTextfield("textInput_2", BtnWidth, plotHeight+nTextHeight, nTextWidth, nTextHeight).setFont(font).setFocus(true).setColor(color(255, 0, 0));
        cp5.addTextfield("textInput_3", BtnWidth, plotHeight+2*nTextHeight, nTextWidth, nTextHeight).setFont(font).setFocus(true).setColor(color(255, 0, 0));

        startTime = System.nanoTime();
        currentTimePrev = startTime;
    }


    public void draw() {
        currentTime = System.nanoTime();

        // Benchmark - how many points in 1 second
        if ( runBench ) {
            cnt += channels[0].size();
            if (currentTime - startTime >= 1e9) {
                println(cnt);
                cnt = 0;
                startTime = currentTime;
            }
            // Controlling whether values are correct when benchmarking
            if ( ++skipNFirstPointsCnt == skipNFirstPoints ) {
                System.out.printf("A: %4d\tB: %4d\n", channels[0].get(0), channels[1].get(0));
            }
        }
        else {

            /*
             *  No need to redraw during the pause. But we continue to stamp points in the background
             *  to provide an instant resuming
             */
            if (!isPaused) {
                for (GPlot plot : plots) {
                    plot.beginDraw();
                    plot.drawBackground();
                    plot.drawBox();
                    plot.drawXAxis();
                    plot.drawYAxis();
                    plot.drawTopAxis();
                    plot.drawRightAxis();
                    plot.drawTitle();
                    plot.getMainLayer().drawPoints();
                    plot.endDraw();
                }

            }
            
            // demo data TBD from Ethernet. 
      	  	for (int i = 0; i < channels.length; i++) {
	      		  channels[i].add(temp);
      	  	}
      	  	temp++;

            /*
             *  Append all points accumulated between 9 consecutive screen updates
             *  Instead of putting all these accumulated points at the one x-tick we evenly scatter them
             *  a little bit with a 'scatterCoef' to avoid gaps between points.
             */
            for (int i = 0; i < channels[0].size(); i++, pointsCnt++) {
                for (int j = 0; j < plots.length; j++) {
                    plots[j].addPoint((currentTimePrev
                                          + ((currentTime - currentTimePrev) * scatterCoef * i / channels[j].size())
                                          - startTime)
                                          / 1e9f,
                            channels[j].get(i));
                }
                if (pointsCnt > nPoints) {
                    for (GPlot plot : plots) {
                        plot.removePoint(0);
                    }
                }
            }
            currentTimePrev = currentTime;
        }

        // Free dynamic buffers
        for (List<Integer> channel : channels) {
            channel.clear();
        }
        
        // try to connect to server.
		try {
			  url = new URL("http://192.168.0.26/test");
			  con = (HttpURLConnection) url.openConnection();
			  con.setConnectTimeout(30000);
			  con.setRequestMethod("GET");
			  con.setDoOutput(true);
		} catch (Exception ex) {
			  ex.printStackTrace();		                                                                                                                                                                                                                                                                                                                                                                                                                   
		}
        
//		try {
//		  int status = con.getResponseCode();
//		  InputStreamReader streamReader;	
//		  System.out.println(status);
//
//		  if (status > 299) {
//			  	streamReader = new InputStreamReader(con.getErrorStream());
//		  } else {
//				BufferedReader in;
//				streamReader = new InputStreamReader(con.getInputStream());
//				in = new BufferedReader(streamReader);
//				String inputLine;
//				StringBuffer content = new StringBuffer();
//				content.append("test");
//				while ((inputLine = in.readLine()) != null) {
//				    content.append(inputLine);
//				}
//				System.out.println(content);
//				in.close();
//		  }
//		  con.disconnect();
//		} catch (Exception ex) {
//		  ex.printStackTrace();		                                                                                                                                                                                                                                                                                                                                                                                                                   
//		}
        
//        try {
//            DatagramSocket serverSocket = new DatagramSocket(81);
//            byte[] receiveData = new byte[8];
//            String sendString = "polo";
//            byte[] sendData = sendString.getBytes("UTF-8");
//
//            System.out.printf("Listening on udp:%s:%d%n",
//                    InetAddress.getLocalHost().getHostAddress(), 81);     
//            DatagramPacket receivePacket = new DatagramPacket(receiveData,
//                               receiveData.length);
//
//            while(true)
//            {
//            	  System.out.println("going to recieve: ");
//                  serverSocket.receive(receivePacket);
//                  String sentence = new String( receivePacket.getData(), 0,
//                                     receivePacket.getLength() );
//                  System.out.println("RECEIVED: " + sentence);
//                  // now send acknowledgement packet back to sender     
//                  DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
//                       receivePacket.getAddress(), receivePacket.getPort());
//                  serverSocket.send(sendPacket);
//            }
//          } catch (IOException e) {
//                  System.out.println(e);
//          }
    }


    /*
     *  We use this separate event to read bytes from the serial port. 'currentBuffer' is used to store raw
     *  bytes and 'byteBuffer' to convert them into 2 4-byte integers (little endian format). As 'serialEvent'
     *  triggers more frequent than screen update event we need to store several values between 2 updates.
     *  So we use dynamic arrays for this purpose.
     *
     *  Also there are always a chance to get in during a 'wrong' byte: not a first one of a whole integer or
     *  just channels are swapped. In this case simply restart the sketch or implement some sort of syncing
     *  mechanism (e.g. check decoded values).
     */
//    public void serialEvent(Serial s) {
//    	  String inString  = s.readStringUntil ( '\n' );
//    	  int buff[] = {
//    			  Integer.parseInt(inString.substring(inString.indexOf("a") + 1, inString.indexOf("b"))),
//    			  Integer.parseInt(inString.substring(inString.indexOf("b") + 1, inString.indexOf("c")))
//    	  };
//    	  for (int i = 0; i < channels.length; i++) {
//    		  channels[i].add(buff[i]);
//    	  }
//    }
    /*
     *  Our button automatically binds itself to the function with matching name
     */
    public void pauseBtn() {
        isPaused = !isPaused;
    }
    
}
