package com.demgames.polypong;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;

public class MiscObjects {
    private IGlobals globalVariables;

    //global sendclasses
    private IGlobals.SendVariables.SendClass frequentSendClass=new IGlobals.SendVariables.SendClass();

    private IGlobals.SendVariables.SendBallKinetics sendBallKinetics=new IGlobals.SendVariables.SendBallKinetics();
    private IGlobals.SendVariables.SendBallScreenChange sendBallScreenChange=new IGlobals.SendVariables.SendBallScreenChange();
    private IGlobals.SendVariables.SendBallGoal sendBallGoal=new IGlobals.SendVariables.SendBallGoal();
    private IGlobals.SendVariables.SendBat sendBat=new IGlobals.SendVariables.SendBat();
    private IGlobals.SendVariables.SendScore sendScore=new IGlobals.SendVariables.SendScore();
    private IGlobals.SendVariables.SendConnectionState sendConnectionState=new IGlobals.SendVariables.SendConnectionState();

    private int myPlayerNumber;
    private float screenWidth, screenHeight,width, height;
    float zoomLevel;

    //class for touch input and gestures

    public Touches touches;

    MiscObjects(IGlobals globalVariables_ , int myPlayerNumber_, float width_, float height_) {
        this.globalVariables = globalVariables_;
        this.myPlayerNumber = myPlayerNumber_;
        this.width = width_;
        this.height = height_;
        this.touches=new Touches(2);

        this.screenWidth = globalVariables.getGameVariables().width;
        this.screenHeight = globalVariables.getGameVariables().height;

        this.zoomLevel = 1f;
    }

    /********* OTHER FUNCTIONS *********/
    //adjust camera for zooming


    //transform touch input for variable zoomlevel
    Vector2 transformZoom(Vector2 vec) {
        Vector2 camPos = new Vector2(0,-height+height/2*zoomLevel);
        vec.x*=zoomLevel;
        vec.y = - height + (vec.y + height) * zoomLevel;
        return(vec);
    }

    static float[] vecToFloatArray(Vector2[] vectorArray) {
        float[] floatArray = new float[vectorArray.length*2];
        for(int i=0;i<vectorArray.length;i++) {
            floatArray[2*i]=vectorArray[i].x;
            floatArray[2*i+1]=vectorArray[i].y;
        }
        return(floatArray);
    }

    static Vector2[] transformVectorArray(Vector2[] vectorArray, float scale, float degrees) {
        for(Vector2 vector : vectorArray) {
            vector.scl(scale).rotate(degrees);
        }
        return(vectorArray);
    }
    /********* SEND FUNCTIONS *********/

    void sendFieldChangeFunction(ArrayList<ClassicGameObjects.Ball> sendFieldChangeBallsAL) {
        if (sendFieldChangeBallsAL.size()>0) {
            sendBallScreenChange.myPlayerNumber=myPlayerNumber;
            sendBallScreenChange.ballNumbers = new Integer[sendFieldChangeBallsAL.size()];
            sendBallScreenChange.ballPlayerFields = new Integer[sendFieldChangeBallsAL.size()];
            sendBallScreenChange.ballPositions = new Vector2[sendFieldChangeBallsAL.size()];
            sendBallScreenChange.ballVelocities = new Vector2[sendFieldChangeBallsAL.size()];


            for (int i = 0; i < sendFieldChangeBallsAL.size(); i++) {
                sendBallScreenChange.ballNumbers[i] = sendFieldChangeBallsAL.get(i).ballNumber;
                sendBallScreenChange.ballPlayerFields[i] = sendFieldChangeBallsAL.get(i).tempPlayerField;
                sendBallScreenChange.ballPositions[i] = sendFieldChangeBallsAL.get(i).ballBody.getPosition();
                sendBallScreenChange.ballVelocities[i] = sendFieldChangeBallsAL.get(i).ballBody.getLinearVelocity();
                Gdx.app.debug("ClassicGame", "fieldchange of ball "+sendBallScreenChange.ballNumbers[i] +" sent");
            }
            globalVariables.getSettingsVariables().sendToAllClients(sendBallScreenChange,"tcp");
        }
    }

    void sendGoalFunction(ArrayList<Integer> sendGoalBallNumbersAL, int[] scores) {
        if (sendGoalBallNumbersAL.size()>0) {
            sendBallGoal.myPlayerNumber=myPlayerNumber;
            sendBallGoal.ballNumbers = sendGoalBallNumbersAL.toArray(new Integer[0]);
            sendBallGoal.playerScores=scores;
            //Gdx.app.debug("ClassicGame", "send ballgoal");
            globalVariables.getSettingsVariables().sendToAllClients(sendBallGoal,"tcp");
        }
    }

    void sendConnectionStateFunction() {
        sendConnectionState.myPlayerNumber=globalVariables.getSettingsVariables().myPlayerNumber;
        sendConnectionState.connectionState=globalVariables.getSettingsVariables().clientConnectionStates[globalVariables.getSettingsVariables().myPlayerNumber];
        globalVariables.getSettingsVariables().sendToAllClients(sendConnectionState,"tcp");
    }

    void sendFrequentFunction(ArrayList<ClassicGameObjects.Ball> sendBallsAL, ClassicGameObjects.Bat bat) {
        sendBat.myPlayerNumber=myPlayerNumber;
        sendBat.batPlayerField =myPlayerNumber;
        sendBat.batPosition=bat.batBody.getPosition();
        sendBat.batOrientation=bat.batBody.getAngle();

        if (sendBallsAL.size()>0) {
            sendBallKinetics.myPlayerNumber=myPlayerNumber;
            sendBallKinetics.ballNumbers = new Integer[sendBallsAL.size()];
            sendBallKinetics.ballPlayerFields = new Integer[sendBallsAL.size()];
            sendBallKinetics.ballPositions = new Vector2[sendBallsAL.size()];
            sendBallKinetics.ballVelocities = new Vector2[sendBallsAL.size()];


            for (int i = 0; i < sendBallsAL.size(); i++) {
                sendBallKinetics.ballNumbers[i] = sendBallsAL.get(i).ballNumber;
                sendBallKinetics.ballPlayerFields[i] = myPlayerNumber;
                sendBallKinetics.ballPositions[i] = sendBallsAL.get(i).ballBody.getPosition();
                sendBallKinetics.ballVelocities[i] = sendBallsAL.get(i).ballBody.getLinearVelocity();
            }
            //Gdx.app.debug("ClassicGame", "ball "+Integer.toString(theBall.ballNumber)+" sent");
            frequentSendClass.sendObjects = new Object[]{sendBat,sendBallKinetics};
        } else {
            frequentSendClass.sendObjects = new Object[]{sendBat};
        }

        globalVariables.getSettingsVariables().sendToAllClients(frequentSendClass,"udp");
    }

    class Touches {
        int maxTouchCount;
        Vector2[] touchPos;
        private Vector2[] lastTouchPos;
        private Vector2[] startTouchPos;
        boolean[] isTouched;
        private boolean[] lastIsTouched;

        Touches(int maxTouchCount_) {
            this.maxTouchCount=maxTouchCount_;
            this.touchPos=new Vector2[maxTouchCount];
            this.lastTouchPos=new Vector2[maxTouchCount];
            this.startTouchPos=new Vector2[maxTouchCount];
            this.isTouched=new boolean[maxTouchCount];
            this.lastIsTouched=new boolean[maxTouchCount];

            for (int i=0;i<maxTouchCount;i++) {
                this.touchPos[i]=new Vector2(0,-height*0.9f);
                this.lastTouchPos[i]=touchPos[i];
                this.startTouchPos[i]=touchPos[i];
                this.isTouched[i]=false;
                this.lastIsTouched[i]=false;
            }
        }

        //check for touches
        void checkTouches() {
            for(int i=0;i<this.maxTouchCount;i++) {
                if (Gdx.input.isTouched(i)) {
                    this.isTouched[i] = true;
                    this.touchPos[i]=transformZoom(new Vector2((Gdx.input.getX(i)/screenWidth-0.5f) *width,-Gdx.input.getY(i)/screenHeight*height));
                } else {
                    this.isTouched[i] = false;
                }
                if(!this.lastIsTouched[i] && this.isTouched[i]) {
                    this.startTouchPos[i]=this.touchPos[i];
                }
            }
        }

        private void zoom (float originalDistance, float currentDistance){
            float newZoomLevel=zoomLevel+(originalDistance-currentDistance)/5;
            if(newZoomLevel<=2.0f && newZoomLevel>=1.0f) {
                zoomLevel=newZoomLevel;

            } else if(newZoomLevel>2.0f) {
                zoomLevel=newZoomLevel;//2.0f;
            } else if(newZoomLevel<1.0f) {
                zoomLevel=1.0f;
            }
        }

        //check for zoom gesture
        void checkZoomGesture() {
            if(this.isTouched[0] && this.isTouched[1]) {
                zoom(this.startTouchPos[0].cpy().sub(this.startTouchPos[1]).len(),this.touchPos[0].cpy().sub(this.touchPos[1]).len());
            }

            if(!this.isTouched[0] || !this.isTouched[1]) {
                if(zoomLevel != 2.0 || zoomLevel != 1.0) {
                    //continuously update camera
                    zoomLevel=MathUtils.round(zoomLevel);
                }

            }
        }

        void updateLasts() {
            for(int i=0;i<this.maxTouchCount;i++) {
                this.lastTouchPos[i]=this.touchPos[i];
                this.lastIsTouched[i]=this.isTouched[i];
            }
        }

        //show where screen is touched
        void drawTouchPoints(ShapeRenderer shapeRenderer) {

            for(int i = 0; i < this.maxTouchCount; i++) {
                if (this.isTouched[i]) {
                    shapeRenderer.setColor(0, 1, 0, 0.5f);
                    shapeRenderer.circle(this.touchPos[i].x,this.touchPos[i].y, width/100, 20);
                    if(i>0) {
                        //shapeRenderer.setColor(0, 1, 0, 1);
                        //shapeRenderer.line(this.touchPos[i-1].x,this.touchPos[i-1].y,this.touchPos[i].x,this.touchPos[i].y);
                    }
                }
            }
        }
    }

    //custom arraylist with maximum elements, first one is kicked out if max is reached
    static class BoundedArrayList<T> extends ArrayList<T> {
        private int maxSize;
        public BoundedArrayList(int size)
        {
            this.maxSize = size;
        }

        public void addLast(T e)
        {
            this.add(e);
            if(this.size() > this.maxSize)
                this.remove(0);
        }
    }


}
