import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import processing.core.*;
import processing.event.KeyEvent;

public class QueasyCam {

    public boolean controllable;
    public float speed;
    public float sensitivity;
    public PVector position;
    public float pan;
    public float tilt;
    public PVector velocity;
    public float friction;
    public int frameTime=16;

    private PMatrix originalMatrix;
    private PApplet applet;
    private Robot robot;
    private PVector center;
    private PVector up;
    private PVector right;
    private PVector forward;
    private PVector target;
    private Point mouse;
    private Point prevMouse;
    private HashMap<Integer, Boolean> keys;

    public QueasyCam(PApplet applet){
        this.applet = applet;
        applet.registerMethod("draw", this);
        applet.registerMethod("keyEvent", this);
        try {
            robot = new Robot();
        } catch (Exception e){}

        controllable = true;
        speed = 6f;
        sensitivity = 1f;
        position = new PVector(0f, 65f, 0f);
        up = new PVector(0f, 1f, 0f);
        right = new PVector(1f, 0f, 0f);
        forward = new PVector(0f, 0f, 1f);
        velocity = new PVector(0f, 0f, 0f);
        pan = 0f;
        tilt = 0f;
        friction = 0.75f;
        keys = new HashMap<Integer, Boolean>();

        originalMatrix=applet.g.getMatrix();
        applet.perspective(PConstants.PI/3f, (float)applet.width/(float)applet.height, 1f, 10000f);
    }

    public void draw(){
        if (!controllable) return;

        mouse = MouseInfo.getPointerInfo().getLocation();
        if (prevMouse == null) prevMouse = new Point(mouse.x, mouse.y);

        int w = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width;
        int h = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height;

        if (mouse.x < 1 && (mouse.x - prevMouse.x) < 0){
            robot.mouseMove(w-2, mouse.y);
            mouse.x = w-2;
            prevMouse.x = w-2;
        }

        if (mouse.x > w-2 && (mouse.x - prevMouse.x) > 0){
            robot.mouseMove(2, mouse.y);
            mouse.x = 2;
            prevMouse.x = 2;
        }

        if (mouse.y < 1 && (mouse.y - prevMouse.y) < 0){
            robot.mouseMove(mouse.x, h-2);
            mouse.y = h-2;
            prevMouse.y = h-2;
        }

        if (mouse.y > h-1 && (mouse.y - prevMouse.y) > 0){
            robot.mouseMove(mouse.x, 2);
            mouse.y = 2;
            prevMouse.y = 2;
        }

        pan += PApplet.map(mouse.x - prevMouse.x, 0, applet.width, 0, PConstants.TWO_PI) * sensitivity;
        tilt += PApplet.map(mouse.y - prevMouse.y, 0, applet.height, 0, PConstants.PI) * sensitivity;
        tilt = clamp(tilt, -PConstants.PI/2.01f, PConstants.PI/2.01f);

        if (tilt == PConstants.PI/2) tilt += 0.001f;

        forward = new PVector(PApplet.cos(pan), PApplet.tan(tilt), PApplet.sin(pan));
        forward.normalize();
        PVector forwardNoY=new PVector(PApplet.cos(pan), 0, PApplet.sin(pan));
        right = new PVector(PApplet.cos(pan - PConstants.PI/2), 0, PApplet.sin(pan - PConstants.PI/2));

        target = PVector.add(position, forward);

        prevMouse = new Point(mouse.x, mouse.y);

        PVector newVelocity=new PVector();

        if (keys.containsKey(java.awt.event.KeyEvent.VK_A) && keys.get(java.awt.event.KeyEvent.VK_A)) newVelocity.add(right);
        if (keys.containsKey(java.awt.event.KeyEvent.VK_D) && keys.get(java.awt.event.KeyEvent.VK_D)) newVelocity.sub(right);
        if (keys.containsKey(java.awt.event.KeyEvent.VK_W) && keys.get(java.awt.event.KeyEvent.VK_W)) newVelocity.add(forwardNoY);
        if (keys.containsKey(java.awt.event.KeyEvent.VK_S) && keys.get(java.awt.event.KeyEvent.VK_S)) newVelocity.sub(forwardNoY);
        if (keys.containsKey(java.awt.event.KeyEvent.VK_SHIFT) && keys.get(java.awt.event.KeyEvent.VK_SHIFT)) newVelocity.add(up);
        if (keys.containsKey(java.awt.event.KeyEvent.VK_SPACE) && keys.get(java.awt.event.KeyEvent.VK_SPACE)) newVelocity.sub(up);

        newVelocity.normalize();
        newVelocity.mult(speed*frameTime/16);


        velocity.add(newVelocity);
        position.add(velocity);
        center = PVector.add(position, forward);
        applet.camera(position.x, position.y, position.z, center.x, center.y, center.z, up.x, up.y, up.z);

        velocity.mult(friction);

    }

    public void keyEvent(KeyEvent event){
        int keyCode = event.getKeyCode();

        switch (event.getAction()){
            case KeyEvent.PRESS:
                keys.put(keyCode, true);
                break;
            case KeyEvent.RELEASE:
                keys.put(keyCode, false);
                break;
        }
    }

    public void beginHUD()
    {
        applet.g.pushMatrix();
        applet.g.hint(applet.DISABLE_DEPTH_TEST);
        applet.g.resetMatrix();
        applet.g.applyMatrix(originalMatrix);
    }

    public void endHUD()
    {
        applet.g.hint(applet.ENABLE_DEPTH_TEST);
        applet.g.popMatrix();
    }

    private float clamp(float x, float min, float max){
        if (x > max) return max;
        if (x < min) return min;
        return x;
    }


}

