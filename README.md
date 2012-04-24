# groovy-libpusher

Plain old Groovy library for the pusher REST API.

This is slight re-organization of [grails-libpusher](https://github.com/mostblind/grails-libpusher) by [Michael Pangopoulos](https://github.com/mostblind).
    
and is provided under the same license (MIT).

## Usage

You need a Pusher account.  Please register at http://pusher.com and obtain your credentials for use in the library.

    @Grab(group='com.zenuevo.pusher', module='groovy-libpusher', version='1.0')
    @GrabExclude('xerces:xercesImpl')
    
    import com.zenuevo.pusher.PusherLib 
    import groovy.json.JsonBuilder
    
    def pusherURL = "api.pusherapp.com"
    def appId =  /* your pusher app id */
    def appKey = /* your pusher app key */
    def secret = /* your pusher secret */
    
    def pusher = new PusherLib(pusherURL,appId, appKey, secret) 


    def builder = JsonBuilder.newInstance()
    def root = builder {
      sender "Fred"
      message "Hello from Groovy"
    }

    pusher.triggerPush("demo-channel", "chat-message", builder.toString())
    
    
If you are logged in to your pusher account and looking at the debug console, you'll see your message appear there after running the above. (the debug console needs to be open before you run the script, or you won't see it.)

The library also supports generation of the auth string so you can, for example, implement a pusher authorization endpoint.  It might look something like this for a presence channel:

    ... snip ...

    String socketID = request.getParameter("socket_id")
    String channelName = request.getParameter("channel_name")

    def user = ...

    JsonBuilder channelDataBuilder = JsonBuilder.newInstance()
    def cd = channelDataBuilder {
      user_id "${user.primaryKey}"
      user_info {
        name user.firstName
      }
    }

    String authString = pusher.genAuthString(socketID, channelName, channelDataBuilder.toString())

    JsonBuilder jsonBuilder = JsonBuilder.newInstance()
    def xhrResponse = jsonBuilder {
      auth authString
      channel_data cdBuilder.toString()
    }

    response.setContentType("application/json")
    response.getWriter().print(jsonBuilder.toString())
    


The library is posted in Sonatype's OSS Maven repository and should normally be promoted up to Maven Central, so you can also depend on groovy-libpusher in

### Maven

    <dependency>
      <groupId>com.zenuevo.pusher</groupId>
      <artifactId>groovy-libpusher</artifactId>
      <version>1.0</version>
    </dependency>
    
### Gradle

    dependencies{
        ...
        compile 'com.zenuevo.pusher:groovy-libpusher:1.0'
        ...
    }

## Notes

The library depends on [http-builder](http://groovy.codehaus.org/modules/http-builder/) which has many dependencies of its own.  You may find it necessary to exclude some transitive dependencies, as shown in the Grape example, above.

