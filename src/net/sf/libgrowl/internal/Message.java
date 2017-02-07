package net.sf.libgrowl.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import net.sf.libgrowl.IResponse;

public abstract class Message implements IProtocol {

  private StringBuilder mBuffer;

  /**
   * container for all resources which need to be sent to Growl
   */
  private HashMap<String, byte[]> mResources = new HashMap<String, byte[]>();

  /**
   * name of the sending machine
   */
  private static String MACHINE_NAME;

  static {
    try {
      MACHINE_NAME = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  /**
   * platform version of the sending machine
   */
  private static String PLATFORM_VERSION = System.getProperty("os.version");

  /**
   * platform of the sending machine
   */
  private static String PLATFORM_NAME = System.getProperty("os.name");

  protected Message(final String messageType) {
    mBuffer = new StringBuilder();
    mBuffer.append(IProtocol.GNTP_VERSION).append(' ').append(messageType)
        .append(' ').append(IProtocol.ENCRYPTION_NONE).append(IProtocol.LINE_BREAK);
    header(HEADER_ORIGIN_MACHINE_NAME, MACHINE_NAME);
    header(HEADER_ORIGIN_SOFTWARE_NAME, "libgrowl");
    header(HEADER_ORIGIN_SOFTWARE_VERSION, "0.1");
    header(HEADER_ORIGIN_PLATFORM_NAME, PLATFORM_NAME);
    header(HEADER_ORIGIN_PLATFORM_VERSION, PLATFORM_VERSION);
  }

  protected void header(final String headerName,
      final boolean value) {
    header(headerName, value ? "True" : "False");
  }

  protected void header(final String headerName,
      final String value) {
    // filter out any \r\n in the header values
    mBuffer.append(headerName).append(": ").append(
        value.replaceAll(IProtocol.LINE_BREAK, "\n")).append(
        IProtocol.LINE_BREAK);
  }

  protected void header(final String headerName,
      final int value) {
    header(headerName, String.valueOf(value));
  }

  protected void lineBreak() {
    mBuffer.append(IProtocol.LINE_BREAK);
  }

  public int send(final String host, int port) {
    String response = null;
    try {
      String messageText = mBuffer.toString();
      // now start the communication
      final Socket socket = new Socket(host, port);
      socket.setSoTimeout(10000);
      final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      final OutputStream out = socket.getOutputStream();
      final OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
      writer.write(messageText);
      
      writeResources(out, writer);

      // always have a line break and an empty line at the message end
      writer.write(IProtocol.LINE_BREAK);
      
      writer.flush();
      System.out.println("------------------------");
      System.out.println(messageText);

      final StringBuilder buffer = new StringBuilder();
      String line = in.readLine();
      while (line != null && !line.isEmpty()) {
        buffer.append(line).append(IProtocol.LINE_BREAK);
        line = in.readLine();
      }
      response = buffer.toString();
      writer.close();
      out.close();
      in.close();
      socket.close();
      System.out.println("------------------------");
      System.out.println(response);
    } catch (UnknownHostException e) {
//      e.printStackTrace();
      return IResponse.ERROR;
    } catch (IOException e) {
//      e.printStackTrace();
      return IResponse.ERROR;
    }
    return getError(response);
  }

  /**
   * write the collected resources to the output stream
   * @throws IOException 
   */
  private void writeResources(OutputStream out, OutputStreamWriter writer) throws IOException {
	  for (Map.Entry<String, byte[]> entry : mResources.entrySet()) {
          writer.write(IProtocol.LINE_BREAK);
          
          final String id = entry.getKey();
          byte[] data = entry.getValue();
          if (data == null) {
            data = new byte[0];
          }
          
          writer.write(IProtocol.HEADER_IDENTIFIER+": "+id);
          writer.write(IProtocol.LINE_BREAK);
          writer.write(IProtocol.HEADER_LENGTH+": "+String.valueOf(data.length));
          writer.write(IProtocol.LINE_BREAK);
          
          // image data in bytes
          writer.write(IProtocol.LINE_BREAK);
          writer.flush();
          out.write(data);
          writer.write(IProtocol.LINE_BREAK);
      }
  }

  private int getError(final String response) {
    if (response == null) {
      return IResponse.ERROR;
    }
    if (response.contains("-OK")) {
      return IResponse.OK;
    }
    return IResponse.ERROR;
  }

  /**
   * add a resource to the internally remembered resources, which are
   * automatically added to the end of the message
   *
   * @param resourceId
   * @param data
   */
  protected void addResourceInternal(final String resourceId, final byte[] data) {
    mResources.put(resourceId, data);
  }
}
