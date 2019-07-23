package utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import peer.PeerInterface;

public class Utils {
	
	public static boolean isPowerOfTwo(int x){
	 while (((x % 2) == 0) && x > 1){
	   x /= 2;
	 }
	 return (x == 1);
	}
	
	public static boolean lookupUtil(Pair<Integer, PeerInterface> n, int k, Pair<Integer, PeerInterface> f){
		return lookupUtil(n.first, k, f.first);
	}
	
	/**
	 * Returns true if k is between x and y, on the chord
	 * @param x
	 * @param k
	 * @param y
	 * @return
	 */
	public static boolean lookupUtil(int x, int k, int y){
        if (y < k) {
            return x < k && x > y;
        } else {
        	return (x > k && x > y) || (x < k && x < y);
        }
	}
	
	public static String getLocalIP() throws SocketException{
		Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
		while(e.hasMoreElements()){
			NetworkInterface n = (NetworkInterface) e.nextElement();
			Enumeration<InetAddress> ee = n.getInetAddresses();
			while (ee.hasMoreElements())
			{
				InetAddress i = (InetAddress) ee.nextElement();
				if(!i.isLoopbackAddress()){
					String address = i.getHostAddress();
					if(address.split("\\.").length == 4){
						//means it's an ipv4 address
						System.out.println("Utils getLocalIP(): " + address);
						return address;
					}
				}
			}
		}
		throw new SocketException("Unable to retrieve local IP.");
	}
	
	public static int generateFileId(String hash_input){
		return Math.abs(hash_input.hashCode());
	}
	
  
  /**
   * ecrypts data using key
   *
   * @param data
   * @param key
   * @return
   * @throws Exception
   */
  public static byte[] encrypt(byte[] data, SecretKey key) throws Exception {
      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE, key);
      byte[] encrypted = cipher.doFinal(data);
      return encrypted;
  }
  
  /**
   * Decrypts encryptedData using key
   *
   * @param encryptedData
   * @param key
   * @return
   * @throws Exception
   */
  public static byte[] decrypt(byte[] encryptedData, SecretKey key) throws Exception {
      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
      cipher.init(Cipher.DECRYPT_MODE, key);
      byte[] decrypted = cipher.doFinal(encryptedData);
      return decrypted;        
  }
  /**
   * http://stackoverflow.com/questions/2939218/getting-the-external-ip-address-in-java
   * @return
   * @throws IOException 
   */
  public static String getExternalIp() throws IOException{
  	URL whatismyip = new URL("http://checkip.amazonaws.com");
		BufferedReader in = new BufferedReader(new InputStreamReader(
		                whatismyip.openStream()));

		String ip = in.readLine(); //you get the IP as a String
		return ip;
  }
}
