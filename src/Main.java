//Copyright 2019 Kevin Liu/Pegasis
//
//        Permission is hereby granted, free of charge, to any
//        person obtaining a copy of this software and associated
//        documentation files (the "Software"), to deal in the
//        Software without restriction, including without
//        limitation the rights to use, copy, modify, merge,
//        publish, distribute, sublicense, and/or sell copies of
//        the Software, and to permit persons to whom the Software
//        is furnished to do so, subject to the following
//        conditions:
//
//        The above copyright notice and this permission notice
//        shall be included in all copies or substantial portions
//        of the Software.
//
//        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF
//        ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
//        TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
//        PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
//        THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
//        DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
//        CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
//        CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
//        IN THE SOFTWARE.

import processing.sound.*;
import processing.core.*;
import processing.event.MouseEvent;
import processing.opengl.PGraphicsOpenGL;
import processing.video.*;

import java.awt.*;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.management.OperatingSystemMXBean;

public class Main extends PApplet {
    private static PApplet applet = null;   //save the reference of PApplet so inner claas can use it.
    private PFont mcFont;
    private static HashMap<String,PImage> guiTex=new HashMap<String,PImage>();  //a map that stores all the textures used by ui. Key is the name of the texture, value is the texture.
    private static HashMap<String,SoundFile> sounds=new HashMap<String,SoundFile>();  //a map that stores all the sounds. Key is the name of the sound, value is the sound.
    private static String[] directionMap={"+Z","-Z","+X","-X","-Y","+Y"};  //an array that maps an int to and direction(in string)
    private ArrayList<Level> allLevels = new ArrayList<Level>();   //an arraylist used to store all the levels
    private boolean playSound=false;  //is allowed to play sound
    private boolean playVideo=false;  //is allowed to play video
    private int frameTime=16;         //how many ms it takes to generate a frame.
    private Robot robot;              //utility for moving the mouse programically.

    public void settings() {
        fullScreen(P3D);  //set renderer to 3D mode
    }

    public void setup() {
        //some init work
        applet=this;
        background(0);
        BlockItemManager.init();
        Storage.getInstance().init();
        mcFont=createFont("Minecraft-Regular.otf",25,false);
        textFont(mcFont);
        surface.setTitle("KevCraft");
        ((PGraphicsOpenGL) g).textureSampling(2); //disable the auto scale on the image
        try {
            robot=new Robot();
        } catch (AWTException ignored) { }

        //load textures that are used in gui
        String[] guiList={
                "item_bar","list_items","item_bar_highlight","logo",
                "btn_normal","btn_hover","btn_pressed","ui_background",
                "btn_short_normal","btn_short_hover","btn_short_pressed",
                "alert_background"
        };
        for (String name:guiList){
            PImage tex=loadImage(name+".png");
            guiTex.put(name,tex);
        }

        //load sounds
        String[] soundList={"click"};
        if (playSound) {
            for (String name : soundList) {
                SoundFile sound = new SoundFile(applet, name + ".mp3");
                sounds.put(name, sound);
            }
        }

        //add all levels
        allLevels.add(new Level("Ms Harris Burton","Hi! Nice to meet you. " +
                "I heard about you a long time ago. Can you make a status for my cat using stone? " +
                "She just died a few days ago. I miss her so much. You saw my cat before, you " +
                "know how her looks like."));
        allLevels.add(new Level("Mr Frederick","Hi my friend! You may don't know me," +
                "but it doesn't matter. I just graduated from university, I want some cool stuff " +
                "in my back yard, can you do that?"));
        allLevels.add(new Level("Steve Jobs","After I died, the design of iPhone is " +
                "becoming shitier and shitier every year! I don't know wtf that Tim guy is doing. " +
                "You are the greatest artist, can you design a perfect iPhone and send it to Tim?"));
        allLevels.add(new Level("Rhonda","You still remember me? I'm Rhonda, your " +
                "ex-girlfriend. I think I meet the true love in my life, I really want to live with " +
                "him forever. I know you are a great artist. Can you design a romantic place that we can " +
                "live together? I'll pay you well."));
        allLevels.add(new Level("Mr António Guterres","Hello. I'm Secretary-General of " +
                "the United Nations. Through some serous research, we found that human is losing " +
                "interest to arts. You are the greatest artist in the world, can you build something " +
                "that can make people love arts like they do before?"));
        allLevels.add(new Level("Mark Watney","Hello there. I'm stucking on Mars now. Can " +
                "you build something to save me? My food will run out after 2 months."));


        //set the StartScreen as the first screen.
        ActivityManager.getInstance().add(new StartScreen());
    }


    //easy, don't need to explain
    public void draw() {
        int startTime=millis();
        background(0);
        ActivityManager.getInstance().draw();
        frameTime=millis()-startTime;
    }

    //class for a plane in 3D
    final class Plane{
        float a;
        float b;
        float c;
        float d;
        float distanceFix;
        float sqrt;

        //p1,p2,p3 is three point used to determine a plane, distanceFix changes the "right side of the plane"
        Plane(PVector p1,PVector p2,PVector p3,float distanceFix) {
            a = (p2.y-p1.y)*(p3.z-p1.z)-(p2.z-p1.z)*(p3.y-p1.y);
            b = (p2.z-p1.z)*(p3.x-p1.x)-(p2.x-p1.x)*(p3.z-p1.z);
            c = (p2.x-p1.x)*(p3.y-p1.y)-(p2.y-p1.y)*(p3.x-p1.x);
            d = -(a*p1.x+b*p1.y+c*p1.z);
            this.distanceFix=distanceFix;
            sqrt=sqrt(a*a+b*b+c*c);
        }

        //get distance from a point in 3d to this plane
        float distance(float px, float py, float pz){
            return distanceFix*(a*px+b*py+c*pz+d)/sqrt;
        }
    }

    //class for an item
    final static class Item{
        short id=0;
        short amount=0;
        PImage tex=null;

        //copy the item
        Item copy(){
            Item newItem=new Item();
            newItem.id=id;
            newItem.amount=amount;
            newItem.tex=tex;
            return newItem;
        }
    }

    //class for a block
    final static class Block {
        //posotion of the chunk that block in
        int chunkX;
        int chunkZ;
        //position of the block in the chunk
        int x;
        int y;
        int z;
        short id=0;
        short direction=4;
        boolean hasDirection = false;
        boolean isVisible = false;  //is the block in player's vision
        boolean isTransParent = false;
        short isChosen = -1;
        boolean[] sideDraw = {false,false,false,false,false,false};  //which side of the block is needed to draw
        BlockTexture tex=null;

        // get global position(local position+chunck position)
        final int getGlobalX(){
            return (chunkX<<4)+x;
        }
        final int getGlobalZ(){
            return (chunkZ<<4)+z;
        }
    }

    //chunk is a group of block
    final class Chunk{
        Block[][][] blocks=new Block[16][64][16];
        Chunk(int baseX, int baseZ){
            //put air every where
            for (int x=0;x<16;x++) for (int y=0;y<64;y++) for (int z=0;z<16;z++){
                blocks[x][y][z]=BlockItemManager.getBlock("air");
            }
            //create a stone platform
            if (baseX==0 && baseZ==0){
                for (int x=4;x<12;x++) for (int z=4;z<12;z++){
                    blocks[x][30][z]=BlockItemManager.getBlock("stone");
                }
            }
        }
    }

    //ui element, used to detect click
    class ClickArea implements UI{
        ButtonListener listener=null;
        int x;
        int y;
        int w;
        int h;
        boolean isHover=false;
        boolean isPressed=false;

        public ClickArea(int x,int y,int w,int h){
            this.x=x;
            this.y=y;
            this.w=w;
            this.h=h;
        }

        public void draw() {

        }

        //if mouse position is in the click area
        private boolean isInArea() {
            return mouseX>x-w/2 && mouseX<x+w/2 &&
                    mouseY>y-h/2 && mouseY<y+h/2;
        }

        public void mousePressed() {
            if (isInArea()){
                isPressed=true;
            }
        }

        public void mouseDragged() {

        }

        public void mouseReleased() {
            if (isPressed && isInArea() && listener!=null){
                if (playSound) {
                    sounds.get("click").play();
                }
                listener.onClick();
            }
            isPressed=false;
            isHover=false;
        }

        public void mouseMoved() {
            if (isInArea()){
                isHover=true;
            }else {
                isHover=false;
            }
        }
    }

    //long button class
    final class Button extends ClickArea{
        String text;
        Button(int x,int y,String text){
            super(x,y,width/3,width/30);
            this.text=text;
        }

        //easy to understand, don't need to explain
        public void draw() {
            pushStyle();
            PImage background;
            if (isPressed){
                background=guiTex.get("btn_pressed");
            } else if(isHover){
                background=guiTex.get("btn_hover");
            }else{
                background=guiTex.get("btn_normal");
            }
            imageMode(CENTER);
            image(background,x,y,w,h);
            textFont(mcFont);
            fill(255);
            textAlign(CENTER,CENTER);
            text(text,x,y-5);
            popStyle();
        }
    }

    //short button class
    final class ShortButton extends ClickArea{
        String text;
        ShortButton(int x,int y,String text){
            super(x,y,width/10,width/10*20/45);
            this.text=text;
        }

        //easy to understand, don't need to explain. like the long button, just use a shorter background image
        public void draw() {
            pushStyle();
            PImage background;
            if (isPressed){
                background=guiTex.get("btn_short_pressed");
            } else if(isHover){
                background=guiTex.get("btn_short_hover");
            }else{
                background=guiTex.get("btn_short_normal");
            }
            imageMode(CENTER);
            image(background,x,y,w,h);
            textFont(mcFont);
            fill(255);
            textAlign(CENTER,CENTER);
            text(text,x,y-5);
            popStyle();
        }
    }

    //button used in block shop to show blocks
    final class BlockButton extends ClickArea{
        PImage blockIcon;
        String blockName;
        int price;

        public BlockButton(int x, int y, int blockID, int price) {
            super(x, y, width/7,width/7);
            //get block info from block item manager
            blockIcon=BlockItemManager.getItem(blockID).tex;
            blockName=BlockItemManager.getName(blockID);
            this.price=price;
        }

        //easy to understand, don't need to explain
        public void draw() {
            pushStyle();
            fill(0,0);
            if (isPressed || isHover){
                stroke(255);
                strokeWeight(5);
            }else {
                noStroke();
            }
            rectMode(CENTER);
            rect(x,y,w,h);

            imageMode(CENTER);
            if (blockIcon!=null) {
                image(blockIcon, x, y - h / 5, w / 2, h / 2);
            }
            textAlign(CENTER,CENTER);
            fill(255);
            textFont(mcFont);
            text(blockName+"\n$"+price,x,y+h/4);
            popStyle();
        }
    }

    interface ButtonListener{
        void onClick();
    }

    //the start screen
    final class StartScreen implements Activity{
        //buttons
        Button craftBtn=new Button(width/2,height/2,"Craft");
        Button blockShopBtn =new Button(width/2,height/2+70,"Block Shop");
        Button inBoxBtn=new Button(width/2,height/2+140,"Inbox");
        Button instructionBtn=new Button(width/2,height/2+210,"Instruction");
        Button exitBtn=new Button(width/2,height/2+280,"Exit");

        //width and height of logo
        float logoW=width/3f*2;
        float logoH=logoW/1173f*289;

        Movie backGroundMv;

        //button listeners
        class GotoCraft implements ButtonListener{
            public void onClick() {
                ActivityManager.getInstance().replace(new Game());
            }
        }
        class GotoBlockShop implements ButtonListener{
            public void onClick() {
                ActivityManager.getInstance().add(new BlockShopScreen());
            }
        }
        class GotoInbox implements ButtonListener{
            public void onClick() {
                ActivityManager.getInstance().add(new InboxScreen());
            }
        }
        class GotoInstruction implements ButtonListener{
            public void onClick() {
                ActivityManager.getInstance().add(new InstructionScreen());
            }
        }
        class Exit implements ButtonListener{
            public void onClick() {
                exit();
            }
        }

        //easy to understand, don't need to explain
        public void setup() {
            imageMode(CENTER);
            craftBtn.listener=new GotoCraft();
            blockShopBtn.listener=new GotoBlockShop();
            inBoxBtn.listener=new GotoInbox();
            instructionBtn.listener=new GotoInstruction();
            exitBtn.listener=new Exit();
            if (playVideo) {
                //load and loop play the video
                backGroundMv = new Movie(applet, "mc.mp4");
                backGroundMv.loop();
            }
        }

        //easy to understand, don't need to explain
        public void draw() {
            pushStyle();
            if (playVideo) {
                image(backGroundMv, width/2, height/2, width, height);
            }else {
                background(255);
            }

            image(guiTex.get("logo"),width/2,height/5,logoW,logoH);

            craftBtn.draw();
            blockShopBtn.draw();
            inBoxBtn.draw();
            instructionBtn.draw();
            exitBtn.draw();

            if (playVideo) {
                //read next frame from the video
                backGroundMv.read();
            }
            popStyle();
        }


        public void resume() {
            imageMode(CENTER);
        }

        public void pause() {

        }

        public void mousePressed() {
            craftBtn.mousePressed();
            blockShopBtn.mousePressed();
            inBoxBtn.mousePressed();
            instructionBtn.mousePressed();
            exitBtn.mousePressed();
        }

        public void mouseDragged() {

        }

        public void mouseReleased() {
            craftBtn.mouseReleased();
            blockShopBtn.mouseReleased();
            inBoxBtn.mouseReleased();
            instructionBtn.mouseReleased();
            exitBtn.mouseReleased();
        }

        public void mouseMoved() {
            craftBtn.mouseMoved();
            blockShopBtn.mouseMoved();
            inBoxBtn.mouseMoved();
            instructionBtn.mouseMoved();
            exitBtn.mouseMoved();
        }

        public void mouseWheel(int wheelY) {

        }

        public void keyPressed() {

        }

        public void keyReleased() {

        }
    }

    //general alert dialog
    class Alert implements Overlay{
        String text;

        //width and height of the alert
        int alertW=width/2;
        int alertH=(int)(alertW/248f*166);

        ShortButton leftButton=null;
        ShortButton rightButton=null;

        //contructor for two-button alert
        public Alert(String text,String leftText,String rightText,ButtonListener leftListener,ButtonListener rightListener){
            this.text=text;

            leftButton = new ShortButton(width/2-alertW/4,height/2+alertH/4,leftText);
            rightButton = new ShortButton(width/2+alertW/4,height/2+alertH/4,rightText);
            leftButton.listener=leftListener;
            rightButton.listener=rightListener;
        }

        //constructor for single button alert
        public Alert(String text,String buttonText,ButtonListener buttonListener){
            this.text=text;

            leftButton = new ShortButton(width/2,height/2+alertH/4,buttonText);
            leftButton.listener=buttonListener;
        }

        public void setup() {

        }

        //easy to understand, don't need to explain
        public void draw() {
            rectMode(CENTER);
            imageMode(CENTER);
            fill(0,150);
            noStroke();
            rect(width/2,height/2,width,height);

            image(guiTex.get("alert_background"),width/2,height/2,alertW,alertH);
            fill(0);
            text(text,width/2,height/2-alertH/4,alertW-40,alertH);

            leftButton.draw();
            if (rightButton!=null) {
                rightButton.draw();
            }
        }

        public void resume() {

        }

        public void pause() {

        }

        public boolean mousePressed() {
            leftButton.mousePressed();
            if (rightButton!=null) {
                rightButton.mousePressed();
            }
            return true;
        }

        public boolean mouseDragged() {
            return true;
        }

        public boolean mouseReleased() {
            leftButton.mouseReleased();
            if (rightButton!=null) {
                rightButton.mouseReleased();
            }
            return true;
        }

        public boolean mouseMoved() {
            leftButton.mouseMoved();
            if (rightButton!=null) {
                rightButton.mouseMoved();
            }
            return true;
        }

        public boolean mouseWheel(int wheelY) {
            return true;
        }

        public boolean keyPressed() {
            return false;
        }

        public boolean keyReleased() {
            return false;
        }
    }

    //block shop screen
    final class BlockShopScreen implements Activity{
        PImage backgroundImg=guiTex.get("ui_background");
        ArrayList<BlockButton> blockButtons=new ArrayList<BlockButton>();
        ShortButton backBtn=new ShortButton(100,50,"< Back");

        //button listeners
        class Back implements ButtonListener{
            public void onClick() {
                ActivityManager.getInstance().back();
            }
        }
        class dropOverlay implements ButtonListener{
            public void onClick() {
                ActivityManager.getInstance().dropOverlay();
            }
        }
        class ConfirmBuyBlock implements ButtonListener{
            int id;
            public ConfirmBuyBlock(int id){
                this.id=id;
            }

            public void onClick() {
                //check if player have enough money
                if (Storage.getInstance().getMoney()>=100) {
                    ActivityManager.getInstance().addOverlay(new Alert("Do you really want to buy " + BlockItemManager.getName(id) + " ?\nThis will cost you $100.",
                            "Cancel", "Yes", new dropOverlay(), new BuyBlock(id)));
                }else {
                    ActivityManager.getInstance().addOverlay(new Alert("You don't have enough money to buy "+BlockItemManager.getName(id)+".",
                            "OK",new dropOverlay()));
                }
            }
        }
        class BuyBlock implements ButtonListener{
            int id;
            BuyBlock(int id){
                this.id=id;
            }

            public void onClick() {
                //minus 100 from player's pocket and reload the screen
                Storage.getInstance().setMoney(Storage.getInstance().getMoney()-100);
                Storage.getInstance().playerBoughtBlock(id);
                ActivityManager.getInstance().dropOverlay();
                ActivityManager.getInstance().replace(new BlockShopScreen());
            }
        }

        public void setup() {
            //get all the blocks from block item manager, put them in the array if player don't have it
            int x = 0;
            int y = 0;
            for (HashMap.Entry<Integer, String> entry : BlockItemManager.IDItemNameMap.entrySet()) {
                if (entry.getKey()!=0 && !Storage.getInstance().blockPlayerHas.contains(entry.getKey())) {
                    BlockButton button = new BlockButton((int) (width / 7f) + x * width / 7, 300 + y * width / 7, entry.getKey(), 100);
                    button.listener = new ConfirmBuyBlock(entry.getKey());
                    blockButtons.add(button);
                    x++;
                    if (x > 5) {
                        x = 0;
                        y++;
                    }
                }
            }

            backBtn.listener=new Back();
        }

        //easy to understand, don't need to explain
        public void draw() {
            //background
            tint(100);
            for (int x=0;x<width+50;x+=50) for (int y=0;y<height+50;y+=50){
                image(backgroundImg,x,y,50,50);
            }

            textAlign(CENTER,CENTER);
            textFont(mcFont,60);
            fill(255);
            text("Block Shop",width/2,90);
            textFont(mcFont,40);
            text("$ "+Storage.getInstance().getMoney(),width-150,90);

            clip(width/2,height/2+80,width,height-160);

            //draw all the buttons
            tint(255);
            for (BlockButton blockButton:blockButtons){
                blockButton.draw();
            }
            noClip();
            backBtn.draw();
        }

        public void resume() {

        }

        public void pause() {

        }

        public void mousePressed() {
            for (BlockButton blockButton:blockButtons){
                blockButton.mousePressed();
            }
            backBtn.mousePressed();
        }

        public void mouseDragged() {

        }

        public void mouseReleased() {
            for (BlockButton blockButton:blockButtons){
                blockButton.mouseReleased();
            }
            backBtn.mouseReleased();
        }

        public void mouseMoved() {
            for (BlockButton blockButton:blockButtons){
                blockButton.mouseMoved();
            }
            backBtn.mouseMoved();
        }

        public void mouseWheel(int wheelY) {
            for (BlockButton blockButton:blockButtons){
                blockButton.y+=wheelY*-20;
            }
        }

        public void keyPressed() {

        }

        public void keyReleased() {

        }
    }

    //class for inbox screen
    final class InboxScreen implements Activity{
        PImage backgroundImg=guiTex.get("ui_background");
        ShortButton backBtn=new ShortButton(100,50,"< Back");
        ShortButton receivedBtn =new ShortButton(width/4*3,height/2,"OK");

        //button listeners
        class Back implements ButtonListener{
            public void onClick() {
                ActivityManager.getInstance().back();
            }
        }
        class Received implements ButtonListener{
            public void onClick() {
                Storage.getInstance().setReceivedMoney(0);
            }
        }

        //easy to understand, don't need to explain
        public void setup() {
            backBtn.listener=new Back();
            receivedBtn.listener=new Received();
        }

        //easy to understand, don't need to explain
        public void draw() {
            pushStyle();

            tint(100);
            for (int x=0;x<width+50;x+=50) for (int y=0;y<height+50;y+=50){
                image(backgroundImg,x,y,50,50);
            }
            tint(255);

            textAlign(CENTER,CENTER);
            textFont(mcFont,60);
            fill(255);
            text("Inbox",width/2,90);

            //show the letter received
            textFont(mcFont,30);
            textAlign(LEFT,TOP);
            rectMode(CORNER);
            if (Storage.getInstance().getLevel() >= allLevels.size()) {
                text("Inbox is empty!", 100, 200, (width - 300) / 2, height - 200);
            } else {
                Level level = allLevels.get(Storage.getInstance().getLevel());
                text(level.name + ":\n\n" + level.detail, 100, 200, (width - 300) / 2, height - 200);
            }
            int receivedMoney=Storage.getInstance().getReceivedMoney();
            if (receivedMoney>0){
                text("You received"+":\n$"+receivedMoney,width/2+50,200,(width-300)/2,height-200);
                receivedBtn.draw();
            }

            backBtn.draw();
            popStyle();
        }

        public void resume() {

        }

        public void pause() {

        }

        public void mousePressed() {
            backBtn.mousePressed();
            receivedBtn.mousePressed();
        }

        public void mouseDragged() {

        }

        public void mouseReleased() {
            backBtn.mouseReleased();
            receivedBtn.mouseReleased();
        }

        public void mouseMoved() {
            backBtn.mouseMoved();
            receivedBtn.mouseMoved();
        }

        public void mouseWheel(int wheelY) {

        }

        public void keyPressed() {

        }

        public void keyReleased() {

        }
    }

    //instruction screen
    final class InstructionScreen implements Activity{
        PImage backgroundImg=guiTex.get("ui_background");
        ShortButton backBtn=new ShortButton(100,50,"< Back");

        class Back implements ButtonListener{
            public void onClick() {
                ActivityManager.getInstance().back();
            }
        }
        public void setup() {
            backBtn.listener=new Back();
        }

        //easy to understand, don't need to explain
        public void draw() {
            pushStyle();

            tint(100);
            for (int x=0;x<width+50;x+=50) for (int y=0;y<height+50;y+=50){
                image(backgroundImg,x,y,50,50);
            }

            textAlign(CENTER,CENTER);
            textFont(mcFont,60);
            fill(255);
            text("Instruction",width/2,90);

            textFont(mcFont,30);
            text("Movement:\n" +
                            "Use WASD to control moving forward, left, backward and right.\n" +
                            "Use space to go up and left shift to go down.\n" +
                            "Use E to open inventory and ESC to open game menu.\n\n" +
                            "You are the greatest artist in the world, master at building things using blocks.\n" +
                            "People will find you for creating art for them.\n" +
                            "Once you done, you will get payed. The amount of payment depends on customer's satisfaction.\n" +
                            "Always check your inbox. You will receive request and payment from there.\n" +
                            "You can buy new blocks in block shop using money.",
                    width / 2, height / 2 + 45);

            tint(255);
            backBtn.draw();

            popStyle();
        }

        public void resume() {

        }

        public void pause() {

        }

        public void mousePressed() {
            backBtn.mousePressed();
        }

        public void mouseDragged() {

        }

        public void mouseReleased() {
            backBtn.mouseReleased();
        }

        public void mouseMoved() {
            backBtn.mouseMoved();
        }

        public void mouseWheel(int wheelY) {

        }

        public void keyPressed() {

        }

        public void keyReleased() {

        }
    }

    //in game class
    final class Game implements Activity {
        HashSet<Integer> keyPressedSet = new HashSet<Integer>(); //a site that keeps all they key currently pressed
        Camera cam;  //camera
        PlayerMovementController controller;  //player controller
        Chunk[][] activeChunks=new Chunk[5][5];  //chunks that is loaded in memory
        int chunkOffsetX=-2;
        int chunkOffsetZ=-2;
        int cpuUsage;
        int maxRam;
        int usedRam;
        Thread getUsageThread=new Thread(new getUsage());
        Thread generateTerrainThread=new Thread(new GenerateTerrain());
        ExecutorService cullingThreadPool = Executors.newFixedThreadPool(8);

        PVector lookingAt=new PVector();  //direction player looking at
        PVector vectorH=new PVector();  //tempory variable
        Plane[] planes=new Plane[4];  //four planes that made frustum
        Block blockChosen=null;
        ItemBar itemBar=new ItemBar();
        ItemList itemList=new ItemList();
        Player player;
        boolean debugEnabled=true;
        boolean itemListShowing=false;
        boolean gameMenuShowing=false;
        int ignoreMouseMovement=0;

        final public class getUsage implements Runnable {
            OperatingSystemMXBean operatingSystemMXBean =
                    (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

            public void run() {
                //get cpu and ram usage every 0.25 sec
                while(!Thread.currentThread().isInterrupted()) {
                    cpuUsage=(int)(operatingSystemMXBean.getProcessCpuLoad()*100);
                    maxRam=(int)(Runtime.getRuntime().totalMemory()/1024/1024);
                    usedRam=maxRam-(int)(Runtime.getRuntime().freeMemory()/1024/1024);
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ignored) {}
                }
            }
        }
        final public class GenerateTerrain implements Runnable{
            public void run() {
                //generate terrain
                while(!Thread.currentThread().isInterrupted()) {
                    for(int chunkX=0;chunkX<5;chunkX++)for(int chunkZ=0;chunkZ<5;chunkZ++) {
                        if (activeChunks[chunkX][chunkZ]==null){
                            activeChunks[chunkX][chunkZ]=new Chunk(chunkX+chunkOffsetX,chunkZ+chunkOffsetZ);
                        }
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) { }
                    }
                }
            }
        }

        //set a block in the world with chunk pos and block's local pos
        private void setBlock(Block block,int chunkX,int chunkZ ,int x,int y,int z){
            activeChunks[chunkX-chunkOffsetX][chunkZ-chunkOffsetZ].blocks[x][y][z]=block;
        }

        //set a block in the world with block's world pos
        private void globalSetBlock(Block block,int x,int y,int z){
            int chunkX=floor((x+0.5f)/16f);
            int chunkZ=floor((z+0.5f)/16f);
            int localX=(int)mod(x+0.5f,16);
            int localZ=(int)mod(z+0.5f,16);
            int localY=(int)(y+0.5f);

            setBlock(block,chunkX,chunkZ,localX,localY,localZ);
        }

        //get a block in the world by global pos
        private Block globalGetBlock(float x, float y, float z){
            int chunkX=floor((x+0.5f)/16f);
            int chunkZ=floor((z+0.5f)/16f);
            int localX=(int)mod(x+0.5f,16);
            int localZ=(int)mod(z+0.5f,16);
            int localY=(int)(y+0.5f);
            try {
                Block b=activeChunks[chunkX-chunkOffsetX][chunkZ-chunkOffsetZ].blocks[localX][localY][localZ];
                b.x=localX;
                b.y=localY;
                b.z=localZ;
                b.chunkX=chunkX;
                b.chunkZ=chunkZ;
                return b;
            }catch (Exception ignored){
                return null;
            }
        }


        //item bar overlay class
        class ItemBar implements Overlay{
            int highlightedItem=0;

            public void draw() {
                cam.beginHUD();
                //background
                imageMode(CENTER);
                int w = width/3;
                int h = (int)(w/180f*20);
                image(guiTex.get("item_bar"),width/2,height-h/2,w,h);

                //items in item bar
                float space=w/9f;
                float sideLength=space*0.7f;
                for (int i=0;i<9;i++){
                    if (player.heldItems[i]!=null) {
                        image(player.heldItems[i].tex, width / 2 - w / 2 + space / 2 + i * space, height - h / 2, sideLength, sideLength);
                    }
                }

                //show the highlighted item
                image(guiTex.get("item_bar_highlight"),width/2-w/2+space/2+highlightedItem*space,height-h/2,space,space);

                imageMode(CORNER);
                cam.endHUD();
            }

            public void setup() {

            }

            public void resume() {

            }

            public void pause() {

            }

            public boolean mousePressed() {
                return false;
            }

            public boolean mouseDragged() {
                return false;
            }

            public boolean mouseReleased() {
                return false;
            }

            public boolean mouseMoved() {
                return false;
            }

            //change highlighted item using mouse wheel
            public boolean mouseWheel(int y){
                highlightedItem+=y;
                if (highlightedItem>8){
                    highlightedItem-=9;
                }else if (highlightedItem<0){
                    highlightedItem+=9;
                }
                return true;
            }

            //use e to open inventory
            public boolean keyPressed() {
                if (key=='e' || key=='E'){
                    return true;
                }else{
                    int num=-1;
                    try{
                        num=Integer.parseInt(key+"");
                    }catch (Exception ignored){ }
                    if (num>=1 && num<=9){
                        highlightedItem=num-1;
                        return true;
                    }
                }
                return false;
            }

            public boolean keyReleased() {
                if (key=='e' || key=='E'){
                    itemListShowing=true;
                    ActivityManager.getInstance().addOverlay(itemList);
                    return true;
                }
                return false;
            }
        }

        //game menu overlay
        class GameMenu implements Overlay{
            //buttons
            Button resumeButton=new Button(width/2,height/2-70,"Resume");
            Button finishButton=new Button(width/2,height/2,"Finish This Art");
            Button back2StartButton=new Button(width/2,height/2+70,"Quit to Start Menu");
            Button exitButton=new Button(width/2,height/2+140,"Exit the Game");

            //button listeners
            class Resume implements ButtonListener{
                public void onClick() {
                    gameMenuShowing=false;
                    ignoreMouseMovement++;
                    noCursor();
                    robot.mouseMove(width/2,height/2);
                    ActivityManager.getInstance().dropOverlay();
                }
            }
            class CloseAlert implements ButtonListener{
                public void onClick() {
                    ActivityManager.getInstance().dropOverlay();
                }
            }
            class Finish implements ButtonListener{
                public void onClick() {
                    //back to start screen and add money to player
                    ActivityManager.getInstance().replace(new StartScreen());
                    ActivityManager.getInstance().addOverlay(new Alert("Your art is delivered to the customer.","OK",new CloseAlert()));
                    int received=(int)random(0,100);
                    Storage.getInstance().setMoney(Storage.getInstance().getMoney()+received);
                    Storage.getInstance().setReceivedMoney(received);
                    Storage.getInstance().setLevel(Storage.getInstance().getLevel()+1);
                }
            }
            class Back2Start implements ButtonListener{
                public void onClick() {
                    ActivityManager.getInstance().replace(new StartScreen());
                }
            }
            class Exit implements ButtonListener{
                public void onClick() {
                    exit();
                }
            }

            //easy to understand, don't need to explain
            public void setup() {
                cursor();
                gameMenuShowing=true;
                resumeButton.listener=new Resume();
                finishButton.listener=new Finish();
                back2StartButton.listener=new Back2Start();
                exitButton.listener=new Exit();
            }

            //easy to understand, don't need to explain
            public void draw() {
                cam.beginHUD();
                pushStyle();
                rectMode(CORNER);
                fill(0,150);
                rect(0,0,width,height);

                fill(255);
                textFont(mcFont,40);
                textAlign(CENTER,CENTER);
                text("Game Menu",width/2,height/4);

                resumeButton.draw();
                finishButton.draw();
                back2StartButton.draw();
                exitButton.draw();

                popStyle();
                cam.endHUD();
            }

            public void resume() {

            }

            public void pause() {

            }

            public boolean mousePressed() {
                resumeButton.mousePressed();
                finishButton.mousePressed();
                back2StartButton.mousePressed();
                exitButton.mousePressed();
                return true;
            }

            public boolean mouseDragged() {
                return true;
            }

            public boolean mouseReleased() {
                resumeButton.mouseReleased();
                finishButton.mouseReleased();
                back2StartButton.mouseReleased();
                exitButton.mouseReleased();
                return true;
            }

            public boolean mouseMoved() {
                resumeButton.mouseMoved();
                finishButton.mouseMoved();
                back2StartButton.mouseMoved();
                exitButton.mouseMoved();
                return true;
            }

            public boolean mouseWheel(int wheelY) {
                return false;
            }

            public boolean keyPressed() {
                if (key==ESC){
                    return true;
                }
                return false;
            }

            public boolean keyReleased() {
                if (key==ESC){
                    gameMenuShowing=false;
                    ignoreMouseMovement++;
                    noCursor();
                    robot.mouseMove(width/2,height/2);
                    ActivityManager.getInstance().dropOverlay();
                    return true;
                }
                return false;
            }
        }

        class ItemList implements Overlay{
            Item[][] items=new Item[9][5];
            Item itemOnMouse=null;
            int w=width/2;
            int h=(int)(w/195f*136);
            //left-top pos of the main item list
            float listStartX=width/2-(w/2*0.89f);
            float space=(w*0.83f)/9;
            float listStartY=height/2-(h/2*0.72f);
            float sideLength=space*0.7f;

            //y pos of the single line in the item list
            float listStartY2=listStartY+6*sideLength+h*0.14f;

            public void setup() {
                cursor();
                //add the block player has to the list
                int x = 0;
                int y = 0;
                for (int id:Storage.getInstance().blockPlayerHas) {
                    if (id!=0) {
                        items[x][y]=BlockItemManager.getItem(id);
                        x++;
                        if (x > 8) {
                            x = 0;
                            y++;
                        }
                    }
                }
            }

            public void draw() {
                cam.beginHUD();
                pushStyle();
                textFont(mcFont,25);
                textAlign(LEFT,BOTTOM);
                rectMode(CORNER);
                //background
                fill(0,150);
                rect(0,0,width,height);
                image(guiTex.get("list_items"),width/2-w/2,height/2-h/2,w,h);

                //draw item in the list1
                for (int x=0;x<9;x++) for (int y=0;y<5;y++){
                    if (items[x][y]!=null) {
                        image(items[x][y].tex, listStartX + space * x, listStartY + space * y, sideLength, sideLength);
                    }
                }

                //draw item in the list2
                for (int i=0;i<9;i++){
                    if (player.heldItems[i]!=null){
                        image(player.heldItems[i].tex,listStartX+space*i,listStartY2,sideLength,sideLength);
                    }
                }

                text("Item List",listStartX,listStartY-30);

                if (itemOnMouse!=null){
                    image(itemOnMouse.tex,mouseX-sideLength/2,mouseY-sideLength/2,sideLength,sideLength);
                }
                popStyle();
                cam.endHUD();
            }


            public void resume() {

            }

            public void pause() {

            }

            //easy to understand, don't need to explain
            public boolean mousePressed() {
                int clickedX=0;
                int clickedY=0;
                int clickedPlace=-1; //0: list   1:bar
                for (int x=0;x<9;x++) for (int y=0;y<5;y++){
                    if (mouseX > listStartX + space * x &&
                            mouseX < listStartX + space * x + space &&
                            mouseY > listStartY + space * y &&
                            mouseY < listStartY + space * y + space ) {
                        clickedX=x;
                        clickedY=y;
                        clickedPlace=0;
                        break;
                    }
                }

                if (clickedPlace==-1){
                    for (int i=0;i<9;i++){
                        if (mouseX > listStartX + space * i &&
                                mouseX < listStartX + space * i + space &&
                                mouseY > listStartY2 &&
                                mouseY < listStartY2 + space) {
                            clickedX=i;
                            clickedPlace=1;
                        }
                    }
                }

                if (clickedPlace==0){
                    itemOnMouse=null;
                    if (items[clickedX][clickedY]!=null){
                        itemOnMouse=items[clickedX][clickedY].copy();
                    }
                }else if(clickedPlace==1){
                    Item temp=itemOnMouse;
                    itemOnMouse=player.heldItems[clickedX];
                    player.heldItems[clickedX]=temp;
                }

                return true;
            }

            public boolean mouseDragged() {
                return true;
            }

            public boolean mouseReleased() {
                return true;
            }

            public boolean mouseMoved() {
                return true;
            }

            public boolean mouseWheel(int y){
                return false;
            }

            public boolean keyPressed() {
                if (key=='e' || key=='E'){
                    return true;
                }
                return false;
            }

            //use e to quit
            public boolean keyReleased() {
                if (key=='e' || key=='E'){
                    itemOnMouse=null;
                    itemListShowing=false;
                    ignoreMouseMovement++;
                    noCursor();
                    robot.mouseMove(width/2,height/2);
                    ActivityManager.getInstance().dropOverlay();
                    return true;
                }
                return false;
            }
        }

        public void setup() {
            pushMatrix();
            player=new Player(new PVector(0,-3300,0));  //give the player init pos of 0,33,0
            controller=new PlayerMovementController(player);  //give controller the player to control
            cam = new Camera(player);  //give the camera the player to follow
            textureMode(NORMAL);
            noCursor();
            //start threads
            generateTerrainThread.start();
            getUsageThread.start();

            strokeWeight(0.04f);
            ActivityManager.getInstance().addOverlay(itemBar);

            robot.mouseMove(width/2,height/2);
            ignoreMouseMovement++;
        }

        //+Z -Z +X -X -Y(top) +Y(bottom)
        //draw a block in given pos
        final void texturedCube(BlockTexture btex,int x,int y,int z,boolean[] sideDraw,int direction) {
            float nx=x-0.5f;
            float px=x+0.5f;
            float ny=y-0.5f;
            float py=y+0.5f;
            float nz=z-0.5f;
            float pz=z+0.5f;

            PImage tex=btex.tex;
            PImage topTex=btex.topTex;
            PImage btmTex=btex.btmTex;

            if (direction==4){
                //+Z
                if (sideDraw[0]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, ny, pz, 0, 0);
                    vertex(px, ny, pz, 1, 0);
                    vertex(px, py, pz, 1, 1);
                    vertex(nx, py, pz, 0, 1);
                    endShape();
                }
                //-Z
                if (sideDraw[1]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(px, ny, nz, 0, 0);
                    vertex(nx, ny, nz, 1, 0);
                    vertex(nx, py, nz, 1, 1);
                    vertex(px, py, nz, 0, 1);
                    endShape();
                }
                //+X
                if (sideDraw[2]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(px, ny, pz, 0, 0);
                    vertex(px, ny, nz, 1, 0);
                    vertex(px, py, nz, 1, 1);
                    vertex(px, py, pz, 0, 1);
                    endShape();
                }
                //-X
                if (sideDraw[3]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, ny, nz, 0, 0);
                    vertex(nx, ny, pz, 1, 0);
                    vertex(nx, py, pz, 1, 1);
                    vertex(nx, py, nz, 0, 1);
                    endShape();
                }
                //-Y Top
                if (sideDraw[4]){
                    beginShape(QUADS);
                    texture(topTex);
                    vertex(nx, ny, nz, 0, 0);
                    vertex(px, ny, nz, 1, 0);
                    vertex(px, ny, pz, 1, 1);
                    vertex(nx, ny, pz, 0, 1);
                    endShape();
                }
                //+Y Bottom
                if (sideDraw[5]){
                    beginShape(QUADS);
                    texture(btmTex);
                    vertex(nx, py, pz, 0, 0);
                    vertex(px, py, pz, 1, 0);
                    vertex(px, py, nz, 1, 1);
                    vertex(nx, py, nz, 0, 1);
                    endShape();
                }
            } else if (direction==0){
                //+Z
                if (sideDraw[0]){
                    beginShape(QUADS);
                    texture(topTex);
                    vertex(nx, ny, pz, 0, 0);
                    vertex(px, ny, pz, 1, 0);
                    vertex(px, py, pz, 1, 1);
                    vertex(nx, py, pz, 0, 1);
                    endShape();
                }
                //-Z
                if (sideDraw[1]){
                    beginShape(QUADS);
                    texture(btmTex);
                    vertex(px, ny, nz, 0, 0);
                    vertex(nx, ny, nz, 1, 0);
                    vertex(nx, py, nz, 1, 1);
                    vertex(px, py, nz, 0, 1);
                    endShape();
                }
                //+X
                if (sideDraw[2]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(px, ny, pz, 1, 0);
                    vertex(px, ny, nz, 1, 1);
                    vertex(px, py, nz, 0, 1);
                    vertex(px, py, pz, 0, 0);
                    endShape();
                }
                //-X
                if (sideDraw[3]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, ny, nz, 0, 1);
                    vertex(nx, ny, pz, 0, 0);
                    vertex(nx, py, pz, 1, 0);
                    vertex(nx, py, nz, 1, 1);
                    endShape();
                }
                //-Y Top
                if (sideDraw[4]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, ny, nz, 1, 1);
                    vertex(px, ny, nz, 0, 1);
                    vertex(px, ny, pz, 0, 0);
                    vertex(nx, ny, pz, 1, 0);
                    endShape();
                }
                //+Y Bottom
                if (sideDraw[5]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, py, pz, 0, 0);
                    vertex(px, py, pz, 1, 0);
                    vertex(px, py, nz, 1, 1);
                    vertex(nx, py, nz, 0, 1);
                    endShape();
                }
            }else if(direction==1){
                //+Z
                if (sideDraw[0]){
                    beginShape(QUADS);
                    texture(btmTex);
                    vertex(nx, ny, pz, 0, 0);
                    vertex(px, ny, pz, 1, 0);
                    vertex(px, py, pz, 1, 1);
                    vertex(nx, py, pz, 0, 1);
                    endShape();
                }
                //-Z
                if (sideDraw[1]){
                    beginShape(QUADS);
                    texture(topTex);
                    vertex(px, ny, nz, 0, 0);
                    vertex(nx, ny, nz, 1, 0);
                    vertex(nx, py, nz, 1, 1);
                    vertex(px, py, nz, 0, 1);
                    endShape();
                }
                //+X
                if (sideDraw[2]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(px, ny, pz, 0, 1);
                    vertex(px, ny, nz, 0, 0);
                    vertex(px, py, nz, 1, 0);
                    vertex(px, py, pz, 1, 1);
                    endShape();
                }
                //-X
                if (sideDraw[3]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, ny, nz, 1, 0);
                    vertex(nx, ny, pz, 1, 1);
                    vertex(nx, py, pz, 0, 1);
                    vertex(nx, py, nz, 0, 0);
                    endShape();
                }
                //-Y Top
                if (sideDraw[4]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, ny, nz, 0, 0);
                    vertex(px, ny, nz, 1, 0);
                    vertex(px, ny, pz, 1, 1);
                    vertex(nx, ny, pz, 0, 1);
                    endShape();
                }
                //+Y Bottom
                if (sideDraw[5]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, py, pz, 1, 1);
                    vertex(px, py, pz, 0, 1);
                    vertex(px, py, nz, 0, 0);
                    vertex(nx, py, nz, 1, 0);
                    endShape();
                }
            }else if(direction==2){
                //+Z
                if (sideDraw[0]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, ny, pz, 0, 1);
                    vertex(px, ny, pz, 0, 0);
                    vertex(px, py, pz, 1, 0);
                    vertex(nx, py, pz, 1, 1);
                    endShape();
                }
                //-Z
                if (sideDraw[1]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(px, ny, nz, 1, 0);
                    vertex(nx, ny, nz, 1, 1);
                    vertex(nx, py, nz, 0, 1);
                    vertex(px, py, nz, 0, 0);
                    endShape();
                }
                //+X
                if (sideDraw[2]){
                    beginShape(QUADS);
                    texture(topTex);
                    vertex(px, ny, pz, 0, 0);
                    vertex(px, ny, nz, 1, 0);
                    vertex(px, py, nz, 1, 1);
                    vertex(px, py, pz, 0, 1);
                    endShape();
                }
                //-X
                if (sideDraw[3]){
                    beginShape(QUADS);
                    texture(btmTex);
                    vertex(nx, ny, nz, 0, 0);
                    vertex(nx, ny, pz, 1, 0);
                    vertex(nx, py, pz, 1, 1);
                    vertex(nx, py, nz, 0, 1);
                    endShape();
                }
                //-Y Top
                if (sideDraw[4]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, ny, nz, 0, 1);
                    vertex(px, ny, nz, 0, 0);
                    vertex(px, ny, pz, 1, 0);
                    vertex(nx, ny, pz, 1, 1);
                    endShape();
                }
                //+Y Bottom
                if (sideDraw[5]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, py, pz, 0, 1);
                    vertex(px, py, pz, 0, 0);
                    vertex(px, py, nz, 1, 0);
                    vertex(nx, py, nz, 1, 1);
                    endShape();
                }
            }else if(direction==3){
                //+Z
                if (sideDraw[0]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, ny, pz, 1, 0);
                    vertex(px, ny, pz, 1, 1);
                    vertex(px, py, pz, 0, 1);
                    vertex(nx, py, pz, 0, 0);
                    endShape();
                }
                //-Z
                if (sideDraw[1]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(px, ny, nz, 0, 1);
                    vertex(nx, ny, nz, 0, 0);
                    vertex(nx, py, nz, 1, 0);
                    vertex(px, py, nz, 1, 1);
                    endShape();
                }
                //+X
                if (sideDraw[2]){
                    beginShape(QUADS);
                    texture(btmTex);
                    vertex(px, ny, pz, 0, 0);
                    vertex(px, ny, nz, 1, 0);
                    vertex(px, py, nz, 1, 1);
                    vertex(px, py, pz, 0, 1);
                    endShape();
                }
                //-X
                if (sideDraw[3]){
                    beginShape(QUADS);
                    texture(topTex);
                    vertex(nx, ny, nz, 0, 0);
                    vertex(nx, ny, pz, 1, 0);
                    vertex(nx, py, pz, 1, 1);
                    vertex(nx, py, nz, 0, 1);
                    endShape();
                }
                //-Y Top
                if (sideDraw[4]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, ny, nz, 1, 0);
                    vertex(px, ny, nz, 1, 1);
                    vertex(px, ny, pz, 0, 1);
                    vertex(nx, ny, pz, 0, 0);
                    endShape();
                }
                //+Y Bottom
                if (sideDraw[5]){
                    beginShape(QUADS);
                    texture(tex);
                    vertex(nx, py, pz, 1, 0);
                    vertex(px, py, pz, 1, 1);
                    vertex(px, py, nz, 0, 1);
                    vertex(nx, py, nz, 0, 0);
                    endShape();
                }
            }

        }

        public void draw() {
            pushStyle();

            noStroke();
            scale(100);

            final PVector smallCamPos=player.globalPos.copy().mult(0.01f);

            int blockCullingStart=millis();
            //frustum culling start-------------------------------------------------------------------------
            float V=PI/3-0.2f;
            float H=2*atan(tan(V/2)*(float)width/(float)height)-0.2f;

            lookingAt.x=PApplet.cos(controller.pan);
            lookingAt.y=PApplet.tan(controller.tilt);
            lookingAt.z=PApplet.sin(controller.pan);
            lookingAt.normalize();
            vectorH.x=PApplet.cos(controller.pan+PI/2);
            vectorH.z=PApplet.sin(controller.pan+PI/2);
            vectorH.normalize();
            PVector vectorH2lookingAt=vecRotate(lookingAt,vectorH,PI/2);

            PVector borderL=vecRotate(lookingAt,vectorH2lookingAt,H/2);
            PVector borderLB=vecRotate(borderL,vectorH,V/2).add(smallCamPos);
            PVector borderLT=vecRotate(borderL,vectorH,-V/2).add(smallCamPos);
            PVector borderR=vecRotate(lookingAt,vectorH2lookingAt,-H/2);
            PVector borderRB=vecRotate(borderR,vectorH,V/2).add(smallCamPos);
            PVector borderRT=vecRotate(borderR,vectorH,-V/2).add(smallCamPos);


            planes[0]=new Plane(smallCamPos,borderLB,borderLT,-1);
            planes[1]=new Plane(smallCamPos,borderRB,borderRT,1);
            planes[2]=new Plane(smallCamPos,borderLT,borderRT,-1);
            planes[3]=new Plane(smallCamPos,borderLB,borderRB,1);

            final int[] drawingFaces = {0};
            final AtomicInteger done = new AtomicInteger();
            int supposedDone=0;
            for(int chunkX=0;chunkX<5;chunkX++) for(int chunkZ=0;chunkZ<5;chunkZ++) if (activeChunks[chunkX][chunkZ]!=null) {
                final Chunk chunk=activeChunks[chunkX][chunkZ];
                final int finalChunkX=chunkX;
                final int finalChunkZ=chunkZ;
                supposedDone++;
                cullingThreadPool.execute(new Runnable() {
                    public void run() {
                        Block block;
                        int x;
                        int y;
                        int z;

                        for (x=0;x<16;x++) for (y=0;y<64;y++) for (z=0;z<16;z++){
                            block = chunk.blocks[x][y][z];
                            block.isVisible = true;
                            for (Plane plane : planes) {
                                if (plane.distance(((finalChunkX + chunkOffsetX) << 4) + x, -y, ((finalChunkZ + chunkOffsetZ) << 4) + z) < -0.87f) {
                                    block.isVisible = false;
                                    break;
                                }
                            }
                        }
                        done.incrementAndGet();
                    }
                });
            }
            while (done.get() < supposedDone){
                delay(1);
            }
            //frustum culling end -----------------------------------------------------------------------------


            //update player viewing angle
            if (ignoreMouseMovement>0){
                ignoreMouseMovement--;
            }else if (!itemListShowing && !gameMenuShowing) {
                controller.viewMove(mouseX - width / 2, mouseY - height / 2);
                robot.mouseMove(width / 2, height / 2);
            }


            //surface culling start ----------------------------------------------------------------------------
            done.set(0);
            supposedDone=0;
            for(int chunkX=0;chunkX<5;chunkX++)for(int chunkZ=0;chunkZ<5;chunkZ++) if (activeChunks[chunkX][chunkZ]!=null) {
                final Chunk chunk=activeChunks[chunkX][chunkZ];
                final int finalChunkX=chunkX;
                final int finalChunkZ=chunkZ;
                supposedDone++;
                cullingThreadPool.execute(new Runnable() {
                    public void run() {
                        final Block[] comparedBlocks=new Block[6];
                        Block block;
                        int x;
                        int y;
                        int z;
                        for (x=0;x<16;x++) for (y=0;y<64;y++) for (z=0;z<16;z++){
                            block = chunk.blocks[x][y][z];

                            if (!block.isVisible || block.isTransParent) {
                                continue;
                            }

                            for (int i=0;i<6;i++){
                                comparedBlocks[i]=null;
                            }

                            if (z + 1 != 16) {
                                comparedBlocks[0]=chunk.blocks[x][y][z + 1];
                            }else if (finalChunkZ+1<5 && activeChunks[finalChunkX][finalChunkZ + 1]!=null){
                                comparedBlocks[0]=activeChunks[finalChunkX][finalChunkZ + 1].blocks[x][y][0];
                            }
                            if (z - 1 != -1) {
                                comparedBlocks[1] = chunk.blocks[x][y][z - 1];
                            } else if(finalChunkZ-1> -1 && activeChunks[finalChunkX][finalChunkZ - 1]!=null){
                                comparedBlocks[1] = activeChunks[finalChunkX][finalChunkZ - 1].blocks[x][y][15];
                            }
                            if (x + 1 != 16) {
                                comparedBlocks[2] = chunk.blocks[x + 1][y][z];
                            } else if (finalChunkX+1<5 && activeChunks[finalChunkX + 1][finalChunkZ]!=null){
                                comparedBlocks[2] = activeChunks[finalChunkX + 1][finalChunkZ].blocks[0][y][z];
                            }
                            if (x - 1 != -1) {
                                comparedBlocks[3] = chunk.blocks[x - 1][y][z];
                            } else if (finalChunkX-1 > -1 && activeChunks[finalChunkX - 1][finalChunkZ]!=null){
                                comparedBlocks[3] = activeChunks[finalChunkX - 1][finalChunkZ].blocks[15][y][z];
                            }
                            if (y + 1 != 64) {
                                comparedBlocks[4] = chunk.blocks[x][y + 1][z];
                            }
                            if (y - 1 != -1) {
                                comparedBlocks[5] = chunk.blocks[x][y - 1][z];
                            }

                            for (int i=0;i<6;i++){
                                if (comparedBlocks[i]!=null){
                                    if (!comparedBlocks[i].isVisible){
                                        block.sideDraw[i]=false;
                                    }else {
                                        block.sideDraw[i]=comparedBlocks[i].isTransParent;
                                    }
                                }
                            }

                            if (block.sideDraw[0] && smallCamPos.z < z + ((finalChunkZ + chunkOffsetZ) << 4) + 0.5) {
                                block.sideDraw[0] = false;
                            }
                            if (block.sideDraw[1] && smallCamPos.z > z + ((finalChunkZ + chunkOffsetZ) << 4) - 0.5) {
                                block.sideDraw[1] = false;
                            }
                            if (block.sideDraw[2] && smallCamPos.x < x + ((finalChunkX + chunkOffsetX) << 4) + 0.5) {
                                block.sideDraw[2] = false;
                            }
                            if (block.sideDraw[3] && smallCamPos.x > x + ((finalChunkX + chunkOffsetX) << 4) - 0.5) {
                                block.sideDraw[3] = false;
                            }
                            if (block.sideDraw[4] && -smallCamPos.y < y + 0.5) {
                                block.sideDraw[4] = false;
                            }
                            if (block.sideDraw[5] && -smallCamPos.y > y - 0.5) {
                                block.sideDraw[5] = false;
                            }

                            for (int i = 0; i < 6; i++) {
                                if (block.sideDraw[i]) {
                                    drawingFaces[0]++;
                                }
                            }

                        }
                        done.incrementAndGet();
                    }
                });
            }
            while (done.get() < supposedDone){
                delay(1);
            }
            int blockCulling=millis()-blockCullingStart;
            //surface culling end -------------------------------------------------------------------------------


            //detect which block player is looking at start ----------------------------------------------------
            if (blockChosen!=null) blockChosen.isChosen=-1;
            blockChosen=null;
            PVector detectChosenSegment=lookingAt.copy().mult(0.2f);
            for (PVector detectChosen=new PVector();detectChosen.magSq()<75;detectChosen.add(detectChosenSegment)){
                Block block=globalGetBlock(detectChosen.x+smallCamPos.x,-(detectChosen.y+smallCamPos.y),detectChosen.z+smallCamPos.z);
                if (block!=null && !block.isTransParent){
                    block.isChosen=0;
                    int blockX=(block.chunkX<<4)+block.x;
                    int blockZ=(block.chunkZ<<4)+block.z;
                    int blockY=block.y;

                    float[] toEachSide=new float[6];
                    toEachSide[0]=detectChosen.z+smallCamPos.z-blockZ;
                    toEachSide[1]=blockZ-(detectChosen.z+smallCamPos.z);
                    toEachSide[2]=detectChosen.x+smallCamPos.x-blockX;
                    toEachSide[3]=blockX-(detectChosen.x+smallCamPos.x);
                    toEachSide[4]=blockY+(detectChosen.y+smallCamPos.y);
                    toEachSide[5]=-(detectChosen.y+smallCamPos.y)-blockY;

                    float biggest=0;
                    for (int i=0;i<6;i++){
                        if (toEachSide[i]>biggest){
                            biggest=toEachSide[i];
                            block.isChosen=(short)i;
                        }
                    }

                    blockChosen=block;
                    break;
                }
            }
            //detect which block player is looking at end ----------------------------------------------------------

            int renderingStart=millis();
            //render blocks start ------------------------------------------------------------------
            Chunk chunk;
            Block block;
            if (drawingFaces[0]!=0) {
                for (int chunkX = 0; chunkX < 5; chunkX++)
                    for (int chunkZ = 0; chunkZ < 5; chunkZ++)
                        if (activeChunks[chunkX][chunkZ] != null) {
                            chunk = activeChunks[chunkX][chunkZ];
                            int x;
                            int y;
                            int z;
                            for (x = 0; x < 16; x++) for (y = 0; y < 64; y++) for (z = 0; z < 16; z++) {
                                block = chunk.blocks[x][y][z];
                                if (block.isVisible && !block.isTransParent) {
                                    if (block.isChosen!=-1){
                                        stroke(0);
                                    }else {
                                        noStroke();
                                    }
                                    texturedCube(block.tex, ((chunkOffsetX + chunkX) << 4) + x, -y, ((chunkOffsetZ + chunkZ) << 4) + z, block.sideDraw,block.direction);
                                }
                            }
                        }
            }
            //render blocks end -----------------------------------------------------------------------
            int rendering=millis()-renderingStart;


            //control player movement
            int leftRight=0;
            if(keyPressedSet.contains(java.awt.event.KeyEvent.VK_A)){
                leftRight--;
            }
            if (keyPressedSet.contains(java.awt.event.KeyEvent.VK_D)){
                leftRight++;
            }
            int frontBehind=0;
            if(keyPressedSet.contains(java.awt.event.KeyEvent.VK_W)){
                frontBehind++;
            }
            if (keyPressedSet.contains(java.awt.event.KeyEvent.VK_S)){
                frontBehind--;
            }
            int upDown=0;
            if(keyPressedSet.contains(java.awt.event.KeyEvent.VK_SPACE)){
                upDown++;
            }
            if (keyPressedSet.contains(java.awt.event.KeyEvent.VK_SHIFT)){
                upDown--;
            }
            controller.move(frontBehind,leftRight,upDown);
            controller.update();

            cam.draw();

            //HUD start --------------------------------------------------------------------
            cam.beginHUD();
            pushStyle();
            textFont(mcFont,25);
            textAlign(LEFT,TOP);
            if (debugEnabled) {
                //Debug Info
                fill(255);
                StringBuilder debugText = new StringBuilder();
                debugText.append("FPS: ").append(floor(frameRate)).append("\n");
                debugText.append("CPU Usage: ").append(cpuUsage).append("%  RAM Usage: ").append(usedRam).append("MB/").append(maxRam).append("MB\n");
                debugText.append("X: ").append((int) (controller.pos.x / 100)).append("  Y: ").append(-(int) (controller.pos.y / 100)).append("  Z: ").append((int) (controller.pos.z / 100)).append("\n");
                debugText.append("chunkX: ").append(floor(smallCamPos.x / 16f)).append("  chunkZ: ").append(floor(smallCamPos.z / 16f)).append("  localX: ").append((int) mod(smallCamPos.x, 16)).append("  localZ: ").append((int) mod(smallCamPos.z, 16)).append("\n");
                if (blockChosen != null) {
                    debugText.append("Chosen block: ").append(BlockItemManager.getName(blockChosen.id)).append("  [").append(blockChosen.x).append(", ").append(blockChosen.y).append(", ").append(blockChosen.z).append("] ").append("\n");
                    debugText.append("id: ").append(blockChosen.id).append("  direction: ").append(directionMap[blockChosen.direction]).append("  chosen direction: ").append(directionMap[blockChosen.isChosen]).append("\n");
                }
                debugText.append("drawingFaces: ").append(drawingFaces[0]).append("\n");
                debugText.append("blockCulling: ").append(blockCulling).append("ms").append("\n");
                debugText.append("rendering: ").append(rendering).append("ms").append("\n");
                debugText.append("dmouseX: ").append(mouseX-pmouseX).append("  dmouseY: ").append(mouseY-pmouseY).append("\n");
                text(debugText.toString(), 10, 30);
            }

            //Cursor
            fill(255,200);
            beginShape();
            vertex(width/2-2,height/2-15);
            vertex(width/2+2,height/2-15);
            vertex(width/2+2,height/2-2);
            vertex(width/2+15,height/2-2);
            vertex(width/2+15,height/2+2);
            vertex(width/2+2,height/2+2);
            vertex(width/2+2,height/2+15);
            vertex(width/2-2,height/2+15);
            vertex(width/2-2,height/2+2);
            vertex(width/2-15,height/2+2);
            vertex(width/2-15,height/2-2);
            vertex(width/2-2,height/2-2);
            endShape();
            cam.endHUD();
            popStyle();

            popStyle();
            //HUD end ----------------------------------------------------------------
        }


        public void resume() {

        }

        public void pause() {
            popMatrix();
        }

        public void mousePressed() {
            if (blockChosen!=null){
                if (mouseButton==LEFT) {
                    //replace the block with air
                    setBlock(BlockItemManager.getBlock("air"), blockChosen.chunkX, blockChosen.chunkZ, blockChosen.x, blockChosen.y, blockChosen.z);
                }else {
                    //calculate which surface the player is choosing
                    int newBlockX=blockChosen.getGlobalX();
                    int newBlockY=blockChosen.y;
                    int newBlockZ=blockChosen.getGlobalZ();
                    if (blockChosen.isChosen==0){
                        newBlockZ++;
                    }else if(blockChosen.isChosen==1){
                        newBlockZ--;
                    }else if (blockChosen.isChosen==2){
                        newBlockX++;
                    }else if(blockChosen.isChosen==3){
                        newBlockX--;
                    }else if (blockChosen.isChosen==4){
                        newBlockY--;
                    }else if(blockChosen.isChosen==5){
                        newBlockY++;
                    }

                    //put a new block there
                    Block newBlock=globalGetBlock(newBlockX,newBlockY,newBlockZ);
                    if (newBlock != null && player.heldItems[itemBar.highlightedItem]!=null && newBlock.id == 0) {
                        newBlock=BlockItemManager.getBlock(player.heldItems[itemBar.highlightedItem].id);
                        if (newBlock.hasDirection && blockChosen.isChosen<4){
                            newBlock.direction=blockChosen.isChosen;
                        }
                        globalSetBlock(newBlock,newBlockX,newBlockY,newBlockZ);
                    }
                }
            }
        }

        public void mouseDragged() {
        }

        public void mouseReleased() {

        }

        public void mouseMoved() {

        }

        public void mouseWheel(int y){

        }

        public void keyPressed() {
            keyPressedSet.add(keyCode);
        }

        public void keyReleased() {
            if (key==ESC){
                ActivityManager.getInstance().addOverlay(new GameMenu());
            }
            keyPressedSet.remove(keyCode);
        }
    }

    //entity class
    abstract class Entity{
        int health;
        PVector globalPos;
        float diameter;
        float height;
        int onFire=0;
        PVector faceDirection=new PVector();
    }

    //player class
    class Player extends Entity{
        int hunger;
        Item[] heldItems=new Item[9];
        Player(PVector pos){
            globalPos=pos;
            health=20;
            hunger=20;
            height=1.8f;
            diameter=0.8f;
        }
    }

    //a class used to manage all the resources about blocks and items
    final static class BlockItemManager {
        static HashMap<Integer,String> IDItemNameMap =new HashMap<Integer,String>(); //a ID<->ItemName map
        static HashMap<String,Integer> itemNameIDMap =new HashMap<String,Integer>(); //a ItemName<->ID map
        static HashMap<Integer,BlockTexture> IDBlockTexMap=new HashMap<Integer,BlockTexture>(); //a ID<->BlockTexture map
        static HashMap<Integer,PImage> IDItemTexMap=new HashMap<Integer,PImage>();  //a ID<->ItemTexture map

        private static HashMap<String,PImage> textures=new HashMap<String,PImage>(); //a filename<->texture map
        static String[] allTexList={"dirt","grass_side",
                "grass_top","bedrock","stone","leaves","wood_top",
                "wood_side","item_bar","list_items",
                "brick","diamond block","emerald block","gold block","iron block",
                "lapis block","redstone block","cloth_1","cloth_2","cloth_3","cloth_4",
                "cloth_5","cloth_6","cloth_7","cloth_8","cloth_9","cloth_10","cloth_11",
                "cloth_12","cloth_13","cloth_14","cloth_15",
                "birch wood","brick stone","coal ore","diamond ore","emerald ore",
                "gold ore","gravel","hellrock","hellsand","ice","iron ore","lapis ore",
                "moss stone","redstone ore","sand","tnt_bottom","tnt_side","tnt_top","wood board"

        };

        private BlockItemManager(){

        }

        //add all the blocks and items and generate item icon
        static void init(){
            IDItemNameMap.put(0,"air");
            IDItemNameMap.put(1,"stone");
            IDItemNameMap.put(2,"grass block");
            IDItemNameMap.put(3,"dirt");
            IDItemNameMap.put(7,"bedrock");
            IDItemNameMap.put(17,"wood");
            IDItemNameMap.put(18,"leaves");
            IDItemNameMap.put(20,"brick");
            IDItemNameMap.put(21,"diamond block");
            IDItemNameMap.put(22,"emerald block");
            IDItemNameMap.put(23,"gold block");
            IDItemNameMap.put(24,"iron block");
            IDItemNameMap.put(25,"lapis block");
            IDItemNameMap.put(26,"redstone block");
            IDItemNameMap.put(27,"cloth_1");
            IDItemNameMap.put(28,"cloth_2");
            IDItemNameMap.put(29,"cloth_3");
            IDItemNameMap.put(30,"cloth_4");
            IDItemNameMap.put(31,"cloth_5");
            IDItemNameMap.put(32,"cloth_6");
            IDItemNameMap.put(33,"cloth_7");
            IDItemNameMap.put(34,"cloth_8");
            IDItemNameMap.put(35,"cloth_9");
            IDItemNameMap.put(36,"cloth_10");
            IDItemNameMap.put(37,"cloth_11");
            IDItemNameMap.put(38,"cloth_12");
            IDItemNameMap.put(39,"cloth_13");
            IDItemNameMap.put(40,"cloth_14");
            IDItemNameMap.put(41,"cloth_15");
            IDItemNameMap.put(42,"birch wood");
            IDItemNameMap.put(43,"brick stone");
            IDItemNameMap.put(44,"coal ore");
            IDItemNameMap.put(45,"diamond ore");
            IDItemNameMap.put(46,"emerald ore");
            IDItemNameMap.put(47,"gold ore");
            IDItemNameMap.put(48,"gravel");
            IDItemNameMap.put(49,"hellrock");
            IDItemNameMap.put(50,"hellsand");
            IDItemNameMap.put(51,"ice");
            IDItemNameMap.put(52,"iron ore");
            IDItemNameMap.put(53,"lapis ore");
            IDItemNameMap.put(54,"moss stone");
            IDItemNameMap.put(55,"redstone ore");
            IDItemNameMap.put(56,"sand");
            IDItemNameMap.put(57,"tnt");
            IDItemNameMap.put(58,"wood board");
            for(Map.Entry<Integer, String> entry : IDItemNameMap.entrySet()){
                itemNameIDMap.put(entry.getValue(), entry.getKey());
            }

            for (String name:allTexList){
                PImage tex=applet.loadImage(name+".png");
                textures.put(name,tex);
            }
            IDBlockTexMap.put(0,null);
            IDBlockTexMap.put(1,new BlockTexture(textures.get("stone")));
            IDBlockTexMap.put(2,new BlockTexture(textures.get("grass_side"),textures.get("grass_top"),textures.get("dirt")));
            IDBlockTexMap.put(3,new BlockTexture(textures.get("dirt")));
            IDBlockTexMap.put(7,new BlockTexture(textures.get("bedrock")));
            IDBlockTexMap.put(17,new BlockTexture(textures.get("wood_side"),textures.get("wood_top")));
            IDBlockTexMap.put(18,new BlockTexture(textures.get("leaves")));
            IDBlockTexMap.put(20,new BlockTexture(textures.get("brick")));
            IDBlockTexMap.put(21,new BlockTexture(textures.get("diamond block")));
            IDBlockTexMap.put(22,new BlockTexture(textures.get("emerald block")));
            IDBlockTexMap.put(23,new BlockTexture(textures.get("gold block")));
            IDBlockTexMap.put(24,new BlockTexture(textures.get("iron block")));
            IDBlockTexMap.put(25,new BlockTexture(textures.get("lapis block")));
            IDBlockTexMap.put(26,new BlockTexture(textures.get("redstone block")));
            IDBlockTexMap.put(27,new BlockTexture(textures.get("cloth_1")));
            IDBlockTexMap.put(28,new BlockTexture(textures.get("cloth_2")));
            IDBlockTexMap.put(29,new BlockTexture(textures.get("cloth_3")));
            IDBlockTexMap.put(30,new BlockTexture(textures.get("cloth_4")));
            IDBlockTexMap.put(31,new BlockTexture(textures.get("cloth_5")));
            IDBlockTexMap.put(32,new BlockTexture(textures.get("cloth_6")));
            IDBlockTexMap.put(33,new BlockTexture(textures.get("cloth_7")));
            IDBlockTexMap.put(34,new BlockTexture(textures.get("cloth_8")));
            IDBlockTexMap.put(35,new BlockTexture(textures.get("cloth_9")));
            IDBlockTexMap.put(36,new BlockTexture(textures.get("cloth_10")));
            IDBlockTexMap.put(37,new BlockTexture(textures.get("cloth_11")));
            IDBlockTexMap.put(38,new BlockTexture(textures.get("cloth_12")));
            IDBlockTexMap.put(39,new BlockTexture(textures.get("cloth_13")));
            IDBlockTexMap.put(40,new BlockTexture(textures.get("cloth_14")));
            IDBlockTexMap.put(41,new BlockTexture(textures.get("cloth_15")));
            IDBlockTexMap.put(42,new BlockTexture(textures.get("birch wood")));
            IDBlockTexMap.put(43,new BlockTexture(textures.get("brick stone")));
            IDBlockTexMap.put(44,new BlockTexture(textures.get("coal ore")));
            IDBlockTexMap.put(45,new BlockTexture(textures.get("diamond ore")));
            IDBlockTexMap.put(46,new BlockTexture(textures.get("emerald ore")));
            IDBlockTexMap.put(47,new BlockTexture(textures.get("gold ore")));
            IDBlockTexMap.put(48,new BlockTexture(textures.get("gravel")));
            IDBlockTexMap.put(49,new BlockTexture(textures.get("hellrock")));
            IDBlockTexMap.put(50,new BlockTexture(textures.get("hellsand")));
            IDBlockTexMap.put(51,new BlockTexture(textures.get("ice")));
            IDBlockTexMap.put(52,new BlockTexture(textures.get("iron ore")));
            IDBlockTexMap.put(53,new BlockTexture(textures.get("lapis ore")));
            IDBlockTexMap.put(54,new BlockTexture(textures.get("moss stone")));
            IDBlockTexMap.put(55,new BlockTexture(textures.get("redstone ore")));
            IDBlockTexMap.put(56,new BlockTexture(textures.get("sand")));
            IDBlockTexMap.put(57,new BlockTexture(textures.get("tnt_side"),textures.get("tnt_top"),textures.get("tnt_bottom")));
            IDBlockTexMap.put(58,new BlockTexture(textures.get("wood board")));

            for(Map.Entry<Integer, String> entry : IDItemNameMap.entrySet()){
                if (entry.getKey()!=0) {
                    IDItemTexMap.put(entry.getKey(), getBlockIcon(entry.getKey()));
                }
            }
        }

        //get name based on id
        static String getName(int id){
            return IDItemNameMap.get(id);
        }

        //get block by id
        static Block getBlock(int id){
            Block block=new Block();
            block.id=(short) id;
            block.tex=IDBlockTexMap.get(id);
            if (id==0){
                block.isTransParent=true;
            }
            if (id==17){
                block.hasDirection=true;
            }
            return block;
        }

        //get block by name
        static Block getBlock(String name){
            return getBlock(itemNameIDMap.get(name));
        }

        //get item by id
        static Item getItem(int id){
            Item item=new Item();
            item.id=(short) id;
            item.amount=1;
            item.tex=IDItemTexMap.get(id);
            return item;
        }

        //get item by name
        static Item getItem(String name){
            return getItem(itemNameIDMap.get(name));
        }

        //render blockicon by id
        static PGraphics getBlockIcon(int id){
            PImage topTex=IDBlockTexMap.get(id).topTex;
            PImage tex=IDBlockTexMap.get(id).tex;

            PGraphics pg = applet.createGraphics(100, 100,P3D);
            pg.beginDraw();
            pg.textureMode(NORMAL);
            pg.ortho(-50, 50, -50, 50);
            pg.noStroke();
            pg.translate(50, 50, -100);
            pg.rotateX(-PI/6);
            pg.rotateY(-PI/4);

            pg.scale(30);

            pg.beginShape(QUADS);
            pg.texture(topTex);

            pg.vertex(-1, -1, -1, 0, 0);
            pg.vertex( 1, -1, -1, 1, 0);
            pg.vertex( 1, -1,  1, 1, 1);
            pg.vertex(-1, -1,  1, 0, 1);

            pg.endShape();


            pg.beginShape(QUADS);
            pg.texture(tex);
            pg.vertex(-1, -1,  1, 0, 0);
            pg.vertex( 1, -1,  1, 1, 0);
            pg.vertex( 1,  1,  1, 1, 1);
            pg.vertex(-1,  1,  1, 0, 1);

            pg.vertex( 1, -1,  1, 0, 0);
            pg.vertex( 1, -1, -1, 1, 0);
            pg.vertex( 1,  1, -1, 1, 1);
            pg.vertex( 1,  1,  1, 0, 1);

            pg.endShape();
            pg.endDraw();
            return pg;
        }
    }

    //block texture class, easy to understand, don't need to explain
    static class BlockTexture{
        PImage tex;
        PImage topTex;
        PImage btmTex;

        BlockTexture(PImage tex){
            this.tex=tex;
            this.topTex=tex;
            this.btmTex=tex;
        }

        BlockTexture(PImage tex, PImage topTex){
            this.tex=tex;
            this.topTex=topTex;
            this.btmTex=topTex;
        }

        BlockTexture(PImage tex, PImage topTex, PImage btmTex){
            this.tex=tex;
            this.topTex=topTex;
            this.btmTex=btmTex;
        }
    }

    //avtivity(page/screen) manager class
    final static class ActivityManager {
        private ArrayList<Activity> activityStack = new ArrayList<Activity>();
        private ArrayList<Overlay> overlayStack = new ArrayList<Overlay>();
        private static ActivityManager instance;

        private ActivityManager() {

        }

        static ActivityManager getInstance() {
            if (instance == null) instance = new ActivityManager();
            return instance;
        }

        //add an activity to the stack
        void add(Activity activity) {
            if (activityStack.size() != 0) {
                activityStack.get(activityStack.size() - 1).pause();
            }
            activityStack.add(activity);
            activity.setup();
        }

        //add an overlay
        void addOverlay(Overlay overlay){
            overlayStack.add(overlay);
            overlay.setup();
        }

        //replace current activity with a new one
        public void replace(Activity activity) {
            if (activityStack.size() != 0) {
                activityStack.get(activityStack.size() - 1).pause();
                activityStack.remove(activityStack.size() - 1);
                overlayStack.clear();

                activityStack.add(activity);
                activity.setup();
            }
        }

        //back to pervous activity
        public void back() {
            if (activityStack.size() != 0) {
                activityStack.get(activityStack.size() - 1).pause();
                activityStack.remove(activityStack.size() - 1);
                overlayStack.clear();

                activityStack.get(activityStack.size() - 1).resume();
            }
        }

        //drop the overlay at the top
        void dropOverlay(){
            if (overlayStack.size() != 0) {
                overlayStack.get(overlayStack.size() - 1).pause();
                overlayStack.remove(overlayStack.size() - 1);
            }
        }

        //draw the activity at the top of the stack and all the overlay
        void draw() {
            activityStack.get(activityStack.size() - 1).draw();
            for (Overlay overlay:overlayStack){
                overlay.draw();
            }
        }

        //distribute this event to overlay first, if overlay doesn't use it, distribute it to the activity
        void mousePressed() {
            boolean used=false;
            for (int i = overlayStack.size() - 1; i >= 0; i--) {
                if (overlayStack.get(i).mousePressed()) {
                    used = true;
                    break;
                }
            }
            if (!used){
                activityStack.get(activityStack.size() - 1).mousePressed();
            }
        }

        //distribute this event to overlay first, if overlay doesn't use it, distribute it to the activity
        void mouseDragged() {
            boolean used=false;
            for (int i = overlayStack.size() - 1; i >= 0; i--) {
                if (overlayStack.get(i).mouseDragged()) {
                    used = true;
                    break;
                }
            }
            if (!used){
                activityStack.get(activityStack.size() - 1).mouseDragged();
            }
        }

        //distribute this event to overlay first, if overlay doesn't use it, distribute it to the activity
        void mouseReleased() {
            boolean used=false;
            for (int i = overlayStack.size() - 1; i >= 0; i--) {
                if (overlayStack.get(i).mouseReleased()) {
                    used = true;
                    break;
                }
            }
            if (!used){
                activityStack.get(activityStack.size() - 1).mouseReleased();
            }
        }

        //distribute this event to overlay first, if overlay doesn't use it, distribute it to the activity
        void mouseWheel(MouseEvent event) {
            boolean used = false;
            for (int i = overlayStack.size() - 1; i >= 0; i--) {
                if (overlayStack.get(i).mouseWheel(event.getCount())) {
                    used = true;
                    break;
                }
            }

            if (!used) {
                activityStack.get(activityStack.size() - 1).mouseWheel(event.getCount());
            }
        }

        //distribute this event to overlay first, if overlay doesn't use it, distribute it to the activity
        void mouseMoved() {
            boolean used = false;
            for (int i = overlayStack.size() - 1; i >= 0; i--) {
                if (overlayStack.get(i).mouseMoved()) {
                    used = true;
                    break;
                }
            }

            if (!used) {
                activityStack.get(activityStack.size() - 1).mouseMoved();
            }
        }

        //distribute this event to overlay first, if overlay doesn't use it, distribute it to the activity
        void keyPressed() {
            boolean used=false;
            for (int i=overlayStack.size()-1;i>=0;i--){
                if (overlayStack.get(i).keyPressed()){
                   used = true;
                   break;
                }
            }

            if (!used) {
                activityStack.get(activityStack.size() - 1).keyPressed();
            }
        }

        //distribute this event to overlay first, if overlay doesn't use it, distribute it to the activity
        void keyReleased() {
            boolean used=false;
            for (int i=overlayStack.size()-1;i>=0;i--){
                if (overlayStack.get(i).keyReleased()){
                    used = true;
                    break;
                }
            }

            if (!used) {
                activityStack.get(activityStack.size() - 1).keyReleased();
            }
        }
    }

    //class for save and load data
    final static class Storage{
        static Storage instance=null;
        ArrayList<Integer> data=new ArrayList<Integer>();
        ArrayList<Integer> blockPlayerHas=new ArrayList<Integer>();

        private Storage(){

        }

        public static Storage getInstance(){
            if (instance==null){
                instance=new Storage();
            }
            return instance;
        }

        //get and save all sorts of data
        int getMoney(){
            return data.get(0);
        }

        void setMoney(int num){
            data.set(0,num);
            saveData();
        }

        int getLevel(){
            return data.get(1);
        }

        void setLevel(int num){
            data.set(1,num);
            saveData();
        }

        int getReceivedMoney(){
            return data.get(2);
        }

        void setReceivedMoney(int num){
            data.set(2,num);
        }

        void playerBoughtBlock(int id){
            blockPlayerHas.add(id);
            saveData();
        }

        //if data file doesn't exist, create one, else, load from file
        void init(){
            try {
                data=loadInts("data/player_data.txt");
            }catch (Exception ignored){ }

            if (data.size()==0){
                data.add(0);
                data.add(0);
                data.add(0);
            }

            try {
                blockPlayerHas = loadInts("data/block_player_has.txt");
            }catch (Exception ignored) {}

            if (blockPlayerHas.size()==0){
                blockPlayerHas.add(1);
                blockPlayerHas.add(2);
                blockPlayerHas.add(3);
                blockPlayerHas.add(17);
                blockPlayerHas.add(18);
                blockPlayerHas.add(20);
            }

            saveData();
        }

        //save int arraylist
        private void saveInts(ArrayList<Integer> array,String fileName){
            String[] strArray=new String[array.size()];
            for (int i=0;i<array.size();i++){
                strArray[i]=String.valueOf(array.get(i));
            }

            applet.saveStrings(fileName,strArray);
        }

        //load int arraylist
        private ArrayList<Integer> loadInts(String fileName){
            String[] strArray=applet.loadStrings(fileName);
            ArrayList<Integer> array=new ArrayList<Integer>();
            for (int i=0;i<strArray.length;i++){
                array.add(Integer.parseInt(strArray[i]));
            }

            return array;
        }

        private void saveData(){
            saveInts(data,"data/player_data.txt");
            saveInts(blockPlayerHas,"data/block_player_has.txt");
        }
    }

    public interface UI {
        void draw();

        void mousePressed();

        void mouseDragged();

        void mouseReleased();

        void mouseMoved();
    }

    public interface Activity {
        void setup();

        void draw();

        void resume();

        void pause();

        void mousePressed();

        void mouseDragged();

        void mouseReleased();

        void mouseMoved();

        void mouseWheel(int wheelY);

        void keyPressed();

        void keyReleased();
    }

    public interface Overlay {
        void draw();

        void setup();

        void resume();

        void pause();

        //true: used  false:unused
        boolean mousePressed();

        boolean mouseDragged();

        boolean mouseReleased();

        boolean mouseMoved();

        boolean mouseWheel(int wheelY);

        boolean keyPressed();

        boolean keyReleased();
    }

    //this class control the movement of the player
    final class PlayerMovementController {
        //variable name explains itself
        Player player;
        PVector pos;
        PVector lookingAt;
        float speed=6f;
        float friction = 0.75f;
        float pan = 0f;
        float tilt = 0f;
        float sensitivity = 1f;
        private PVector up = new PVector(0,1,0);
        private PVector right = new PVector(1,0,0);
        private PVector forward = new PVector(0,0,1);
        PVector velocity = new PVector();

        PlayerMovementController(Player player){
            this.player=player;
            this.pos=player.globalPos;
            this.lookingAt=pos.copy().add(new PVector(1,0,0));
        }

        //update the player pos
        void update(){
            pos.add(velocity);
            velocity.mult(friction);

            player.globalPos=pos;
            player.faceDirection=forward;
        }

        //move player
        //             1    -1        -1    1      1   -1
        void move(int frontBehind,int leftRight,int upDown){
            forward = new PVector(PApplet.cos(pan), PApplet.tan(tilt), PApplet.sin(pan));
            forward.normalize();
            PVector forwardNoY=new PVector(PApplet.cos(pan), 0, PApplet.sin(pan));
            right = new PVector(PApplet.cos(pan - PConstants.PI/2), 0, PApplet.sin(pan - PConstants.PI/2));

            PVector newVelocity=new PVector();

            if (leftRight==-1) newVelocity.add(right);
            if (leftRight==1) newVelocity.sub(right);
            if (frontBehind==1) newVelocity.add(forwardNoY);
            if (frontBehind==-1) newVelocity.sub(forwardNoY);
            if (upDown==-1) newVelocity.add(up);
            if (upDown==1) newVelocity.sub(up);
            newVelocity.normalize();
            newVelocity.mult(speed*frameTime/16);
            velocity.add(newVelocity);
        }

        //move view when mouse moves
        void viewMove(int dx,int dy){
            pan += PApplet.map(dx, 0, applet.width, 0, PConstants.TWO_PI) * sensitivity;
            tilt += PApplet.map(dy, 0, applet.height, 0, PConstants.PI) * sensitivity;
            tilt = clamp(tilt, -PConstants.PI/2.01f, PConstants.PI/2.01f);
        }

        private float clamp(float x, float min, float max){
            if (x > max) return max;
            if (x < min) return min;
            return x;
        }
    }

    //class for camera, easy to understand, don't need to explain
    final class Camera{
        Entity entity;
        private PMatrix originalMatrix;
        Camera(Entity entity){
            this.entity=entity;
            originalMatrix=g.getMatrix();
            applet.perspective(PI/3f, (float)width/height, 1, 10000);
        }

        void draw(){
            camera(entity.globalPos.x, entity.globalPos.y, entity.globalPos.z,
                    entity.faceDirection.x+entity.globalPos.x, entity.faceDirection.y+entity.globalPos.y, entity.faceDirection.z+entity.globalPos.z, 0, 1, 0);
        }

        void beginHUD()
        {
            applet.g.pushMatrix();
            applet.g.hint(applet.DISABLE_DEPTH_TEST);
            applet.g.resetMatrix();
            applet.g.applyMatrix(originalMatrix);
        }

        void endHUD()
        {
            applet.g.hint(applet.ENABLE_DEPTH_TEST);
            applet.g.popMatrix();
        }
    }

    //easy to understand
    final class Level{
        String name;
        String detail;
        Level(String name, String detail){
            this.name=name;
            this.detail=detail;
        }
    }

    //easy to understand
    final class Letter{
        String name;
        String text;
        Letter(String name, String text){
            this.name=name;
            this.text=text;
        }
    }

    public void mousePressed() {
        ActivityManager.getInstance().mousePressed();
    }

    public void mouseDragged() {
        ActivityManager.getInstance().mouseDragged();
    }

    public void mouseReleased() {
        ActivityManager.getInstance().mouseReleased();
    }

    public void mouseMoved() {
        ActivityManager.getInstance().mouseMoved();
    }

    public void mouseWheel(MouseEvent event){
        ActivityManager.getInstance().mouseWheel(event);
    }

    public void keyPressed() {
        ActivityManager.getInstance().keyPressed();
        if (key==ESC){
            key=0;
        }
    }

    public void keyReleased() {
        ActivityManager.getInstance().keyReleased();
        if (key==ESC){
            key=0;
        }
    }

    private float mod(float n1,float n2){
        float result=n1%n2;
        if (result<0){
            result=n2+result;
        }
        return result;
    }

    private PVector vecRotate(PVector original, PVector axis, float angle){
        float c=cos(angle);
        float s=sin(angle);
        float x=axis.x;
        float y=axis.y;
        float z=axis.z;
        float old_x=original.x;
        float old_y=original.y;
        float old_z=original.z;
        float new_x = (x*x*(1-c)+c)*old_x+(x*y*(1-c)-z*s)*old_y+(x*z*(1-c)+y*s)*old_z;
        float new_y = (y*x*(1-c)+z*s)*old_x+(y*y*(1-c)+c)*old_y+(y*z*(1-c)-x*s)*old_z;
        float new_z = (x*z*(1-c)-y*s)*old_x+(y*z*(1-c)+x*s)*old_y+(z*z*(1-c)+c)*old_z;
        return new PVector(new_x,new_y,new_z);
    }

    public static void main(String... args) {
        PApplet.main("Main");
    }
}
