package com.zenuevo.pusher

import groovyx.net.http.RESTClient
import java.security.InvalidKeyException
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import static groovyx.net.http.ContentType.URLENC

//Copyright (c) 2010-2012 Zenuevo, LLC.  Michael Pangopoulos
//
//Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
//The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
/**
 * Class to send messages to Pusher's REST API. Plain groovy.
 *
 * Please set pusherApplicationId, pusherApplicationKey, pusherApplicationSecret accordingly
 * before sending any request.
 *
 * @author Kirk Stork, Zenuevo, LLC.  Adapted from https://github.com/mostblind/grails-libpusher. 
 * @author Michael Pangopoulos (based on http://tinyurl.com/2urje4s)
 */
class PusherLib {

  final private String pusherHost;
  final private String pusherApplicationId;
  final private String pusherApplicationKey;
  final private String pusherApplicationSecret;


  public PusherLib(String pusherURL, String appId, String appKey, String appSecret = null) {
    pusherHost = pusherURL
    pusherApplicationId = appId
    pusherApplicationKey = appKey
    pusherApplicationSecret = appSecret
  }

  private def byteArrayToString(byte[] data) {
    BigInteger bigInteger = new BigInteger(1, data)
    String hash = bigInteger.toString(16)
    // Zero pad it
    while (hash.length() < 64) {
      hash = "0" + hash
    }
    return hash
  }

  /**
   * Returns a HMAC/SHA256 representation of the given string
   * @param data
   * @return
   */
  private def hmacsha256Representation(String data) {
    try {
      final SecretKeySpec signingKey = new SecretKeySpec(pusherApplicationSecret.getBytes(), "HmacSHA256")

      final Mac mac = Mac.getInstance("HmacSHA256")
      mac.init(signingKey)

      byte[] digest = mac.doFinal(data.getBytes("UTF-8"))
      return byteArrayToString(digest)
    } catch (InvalidKeyException e) {
      throw new RuntimeException("Invalid key exception while converting to HMac SHA256")
    }
  }

  private String md5Digest(String msg) {
    MessageDigest digest = MessageDigest.getInstance("MD5")
    digest.update(msg.bytes)
    BigInteger bi = new BigInteger(1, digest.digest())
    String md = bi.toString(16).padLeft(32,"0")
    return md
  }
  /**
   * Build query string that will be appended to the URI and HMAC/SHA256 encoded
   * @param eventName
   * @param jsonData
   * @param socketID
   * @return
   */
  private def buildQuery(String eventName, def jsonData, String socketID) {
    StringBuffer buffer = new StringBuffer()
    buffer.append("auth_key=")
    buffer.append(pusherApplicationKey)
    buffer.append("&auth_timestamp=")
    buffer.append(System.currentTimeMillis() / 1000)
    buffer.append("&auth_version=1.0")
    buffer.append("&body_md5=")
    buffer.append(md5Digest(jsonData))
    buffer.append("&name=")
    buffer.append(eventName)
    if (!socketID.isEmpty()) {
      buffer.append("&socket_id=")
      buffer.append(socketID)
    }
    return buffer.toString()
  }

  /**
   * Build path of the URI that is also required for Authentication
   * @param channelName
   * @return
   */
  private buildURIPath(String channelName) {
    StringBuffer buffer = new StringBuffer()
    //Application ID
    buffer.append("/apps/")
    buffer.append(pusherApplicationId)
    //Channel name
    buffer.append("/channels/")
    buffer.append(channelName)
    //Event
    buffer.append("/events")
    //Return content of buffer
    return buffer.toString()
  }

  /**
   * Build authentication signature to assure that our event is recognized by Pusher
   * @param uriPath
   * @param query
   * @return
   */
  private def buildAuthenticationSignature(String uriPath, String query) {
    StringBuffer buffer = new StringBuffer()
    buffer.append("POST\n")
    buffer.append(uriPath)
    buffer.append("\n")
    buffer.append(query)
    String h = buffer.toString()
    return hmacsha256Representation(h)
  }

  /**
   * Build URI where request is send to
   * @param uriPath
   * @param query
   * @param signature
   * @return
   */
  private def buildURI(String uriPath, String query, String signature) {
    StringBuffer buffer = new StringBuffer()
    buffer.append("http://")
    buffer.append(pusherHost)
    buffer.append(uriPath)
    buffer.append("?")
    buffer.append(query)
    buffer.append("&auth_signature=")
    buffer.append(signature)
    return buffer.toString()
  }

  /**
   * Delivers a message to the Pusher API without providing a socket_id
   * @param channel
   * @param event
   * @param jsonData
   * @return HttpResponse status
   */
  def triggerPush(String channel, String event, String jsonData) {
    triggerPush(channel, event, jsonData, "")
  }

  /**
   * Delivers a message to the Pusher API
   * @param channel
   * @param event
   * @param jsonData
   * @param socketId
   * @return HttpResponse status
   */
  def triggerPush(String channel, String event, String jsonData, String socketId) {

    def uriPath = buildURIPath(channel)
    def query = buildQuery(event, jsonData, socketId)
    def signature = buildAuthenticationSignature(uriPath, query)
    def uri = buildURI(uriPath, query, signature)

    def pusher = new RESTClient(uri)

    try {
      def response = pusher.post(
          requestContentType: URLENC,
          body: jsonData)
      response.status
    } catch (Exception e) {
      log.error("Pusher request failed!")
      null
    }
  }

  /**
     * Generate the authorization string required for private channels
     * @param socketId
     * @param channel
     * @param channelData Optional stringified json when using presence channel
     * @return String signed code
     */  def genAuthString(String socketId, String channel, String channelData = null) {
    def authToken = socketId + ':' + channel

    if (channelData) {
      authToken += ":${channelData}"
    }

    pusherApplicationKey + ':' + hmacsha256Representation(authToken)
  }

}