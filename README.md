Please submit your issues on [Ant-Media-Server](https://github.com/ant-media/Ant-Media-Server)

# LiveVideoBroadcaster
Native Android app that can broadcast and play live video via RTMP - It is developed by [Ant Media](http://antmedia.io)

## How to Develop a Live Streaming Mobile App in 3 Steps?
Developing a live streaming mobile app is not a very quick and easy job if you develop every piece on your own. It requires extensive knowledge about video streaming etc.  so you may prefer to find some libraries and samples that makes this job easy. Yeah in this post, we are going to tell how to develop a live streaming app by providing sample codes on Github.

Let's make it easy, you can develop a live streaming app in 3 general steps.

### Step 1: Get a Media Server

First of all, a live stream should be sent to a media server that can distributes the live stream to subscribers in real time so a media server should be running in somewhere like Amazon AWS, Google Cloud, IBM Bluemix or in any other infrastructure.  You may even use your own local computer to first test the app.

Anyway, there are media servers you can purchase licence, fortunately you do not need to pay anything and you can download Ant Media Server Community Edition at antmedia.io. Ant Media Server can distribute live stream in RTMP, RTSP and HLS formats. Moreover, it records live streams in MP4 format. It means your live streams and recorded streams can play in both all mobile and desktop browsers.

After you download Ant Media Server, extract it and run the start.sh file in your computer.
```
./start.sh
```
When you run this command, Ant Media Server starts with printing some logs. At this stage, please learn your computer IP address and save it somewhere. We will use this address later.

### Step 2: Broadcast Live Stream from Your Mobile Device

Yeah at this stage needs very specific knowledge luckily, we make it so simple that you do not need to know about encoding and packaging, you just need to call some functions. Nevertheless, I should say some technical issues. Here they are...

H264 Hardware Encoders are used to encode the camera preview. It runs on Android 4.3 and above. Using hardware encoders make the mobile app very small and efficient.
RTMP protocol is used to send the live stream to Media Server. We have used librtmp library to handle this issue.
FLV format is used to send live stream to Media Server via RTMP protocol.
This technical details are enough at this stage. So let's make hands dirty.

Please clone this repository https://github.com/ant-media/LiveVideoBroadcaster

git clone https://github.com/ant-media/LiveVideoBroadcaster.git

After you clone the sample, please open the project with Android Studio and write your own media server IP address to RTMP_BASE_URL field on MainActivity.java file.

Build and Run the app, you need to see below screen

![](http://antmedia.io/wp-content/uploads/2017/04/Screenshot_2017-04-16-17-06-22-e1492352365617.png)

Click Live Video Broadcaster button and you should see this screen.

![](http://antmedia.io/wp-content/uploads/2017/04/record-e1492352687883.png)



Write a stream name like "test" to edit text and press the button. You should see below screen.

![](http://antmedia.io/wp-content/uploads/2017/04/broadcastig-e1492352769543.png)


Right now, you are broadcasting live stream to media server. Any subscriber can now watch  your live stream via media server.

### Step 3: Play Live Stream On your Mobile Device

Playing a live stream on your android is not a hard thing to accomplish if your media server supports, RTSP or HLS. Fortunately, Ant Media Server supports both of them however, if you want to have low latency in live stream, it would be better to use RTMP so that we have added RTMP play functionality to ExoPlayer, moreover this player is already integrated to our sample. You just need to run LiveVideoBroadcaster app on any other android device.

When you run it, please click the Live Video player at this time. You should see a black screen like below.

![](http://antmedia.io/wp-content/uploads/2017/04/Screenshot_2017-04-16-17-08-13-e1492352875527.png)

Write down the same stream name you have used in Step 2 and press Play button. You should now watch the live stream that your other Android device broadcasts on your device.

![](http://antmedia.io/wp-content/uploads/2017/04/Screenshot_2017-04-16-17-08-56-1-e1492352925734.png)



Congratulations, that's all. If you have any question or need to get support (private or enterprise) please contact us antmedia.io.
