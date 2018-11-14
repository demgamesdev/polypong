package com.demgames.polypong;

import java.util.ArrayList;
import java.util.Arrays;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.math.MathUtils;

public class ClassicGame extends ApplicationAdapter{
    //use of private etc is not consistently done
    private IGlobals globalVariables;

    //setup global variables
    public ClassicGame(IGlobals globalVariables_ ) {
        this.globalVariables=globalVariables_;
    }

    //declare renderer and world related stuff
    private SpriteBatch batch;
    private BitmapFont font;
    private int width, height;
    private ShapeRenderer shapeRenderer;
    private World world;
    private Box2DDebugRenderer debugRenderer;
    private Matrix4 debugMatrix;
    private OrthographicCamera camera;

    //declare global gamefield class
    GameField gameField;

    //define category and mask bits to decide which bodies collide with what
    final short CATEGORY_BORDER = 0x0001;
    final short CATEGORY_BALL = 0x0002;
    final short CATEGORY_BAT = 0x0004;
    final short CATEGORY_FIELDLINE = 0x0008;
    final short MASK_BORDER= CATEGORY_BALL | CATEGORY_BAT;
    final short MASK_BALL = CATEGORY_BORDER | CATEGORY_BALL | CATEGORY_BAT;
    final short MASK_BAT = CATEGORY_BORDER | CATEGORY_BALL | CATEGORY_BAT | CATEGORY_FIELDLINE;
    final short MASK_FIELDLINE  = CATEGORY_BAT;

    //global classes for balls, bats and arraylists for batch sending
    private Ball[] balls;
    private Bat myBat, otherBat;
    private ArrayList<Integer> sendBallKineticsAL=new ArrayList<Integer>(Arrays.asList(new Integer[]{}));
    private ArrayList<Integer> sendBallScreenChangeAL=new ArrayList<Integer>(Arrays.asList(new Integer[]{}));

    //global sendclasses
    private IGlobals.SendVariables.SendBallKinetics sendBallKinetics=new IGlobals.SendVariables.SendBallKinetics();
    private IGlobals.SendVariables.SendBallScreenChange sendBallScreenChange=new IGlobals.SendVariables.SendBallScreenChange();
    private IGlobals.SendVariables.SendBat sendBat=new IGlobals.SendVariables.SendBat();
    private IGlobals.SendVariables.SendScore sendScore=new IGlobals.SendVariables.SendScore();

    //class for touch input and gestures
    Touches touches;

    //ratio of box2d physics simulation in meters to displayed pixels, when -> box2d 1/PIXEL_TO_METERS else other way round
    private final float PIXELS_TO_METERS = 100f;

    //global zoomlevel
    private float zoomLevel=1;

    //stuff for potential use
    private long frameNumber=0;
    private int sendFrameSkip=1;
    private long currentMillis=System.currentTimeMillis();

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);

        //get width and height of display
        width = Gdx.graphics.getWidth();
        height = Gdx.graphics.getHeight();

        //set fov of camera to display
        camera = new OrthographicCamera(width, height);

        //set position to middle of normal screen
        camera.position.set(0, -height/2, 0);
        camera.update();

        //copy camera to debugmatrix for synchronous displaying of elements
        debugMatrix=new Matrix4(camera.combined);
        debugMatrix.scale(PIXELS_TO_METERS, PIXELS_TO_METERS,1);
        debugRenderer=new Box2DDebugRenderer();
        //shaperenderer for rendering shapes duuh
        shapeRenderer = new ShapeRenderer();
        //set font and batch for drawing fonts and textures
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        font.getData().setScale(2f);

        world=new World(new Vector2(0f,0f),true);

        //only to finger gestures
        touches=new Touches(2);

        gameField=new GameField();

        //initialize balls currently at beginning always on serverside -> player 0
        balls=new Ball[globalVariables.getGameVariables().numberOfBalls];
        for(int i=0;i<balls.length;i++) {
            balls[i]= new Ball(new Vector2(globalVariables.getGameVariables().ballsPositions[i].x*width/PIXELS_TO_METERS,
                    globalVariables.getGameVariables().ballsPositions[i].y*height/PIXELS_TO_METERS),new Vector2(0,0),(1+globalVariables.getGameVariables().ballsSizes[i])*width/50/PIXELS_TO_METERS,
                    i,0,10);
        }

        //initialize bats
        myBat= new Bat(globalVariables.getSettingsVariables().myPlayerScreen);
        otherBat = new Bat((globalVariables.getSettingsVariables().myPlayerScreen+1)%2);

        world.setContactListener(new ContactListener() {
            @Override
            public void beginContact(Contact contact) {

            }

            @Override
            public void endContact(Contact contact) {
            }

            @Override
            public void preSolve(Contact contact, Manifold oldManifold) {
            }

            @Override
            public void postSolve(Contact contact, ContactImpulse impulse) {
            }
        });
    }

    //executed when closed i think
    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        world.dispose();
        debugRenderer.dispose();
    }

    @Override
    public void render() {
        //background
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        //touch input checking
        touches.checkTouches();
        touches.checkZoomGesture();

        //do physics calculations of balls
        for (Ball ball : balls) {
            ball.doPhysics();
        }

        //do physics calculations of bats
        myBat.doPhysics(touches.touchPos[0],0);
        otherBat.doPhysics(new Vector2(globalVariables.getGameVariables().batPositions[otherBat.batPlayerField].x * width,
                globalVariables.getGameVariables().batPositions[otherBat.batPlayerField].y * height),globalVariables.getGameVariables().batOrientations[otherBat.batPlayerField]);

        //step world one timestep further, ideally for 60fps, maybe needs to be adapted variably for same speeds etc
        world.step(1/60f, 2,2);

        //send everything
        sendBallPlayerScreenChange(sendBallScreenChangeAL);
        sendBall(sendBallKineticsAL);
        sendBatFunction(myBat);
        sendBallKineticsAL=new ArrayList<Integer>(Arrays.asList(new Integer[]{}));
        sendBallScreenChangeAL=new ArrayList<Integer>(Arrays.asList(new Integer[]{}));

        //draw everything
        drawShapes();

        //update last touch to current
        touches.updateLasts();
        frameNumber++;
    }

    /********* SEND FUNCTIONS *********/

    //pretty self explanatory
    void sendBall(ArrayList<Integer> AL) {
        if (AL.size()>0) {
            sendBallKinetics.ballNumbers = AL.toArray(new Integer[0]);
            sendBallKinetics.ballPlayerFields = new int[AL.size()];
            sendBallKinetics.ballPositions = new Vector2[AL.size()];
            sendBallKinetics.ballVelocities = new Vector2[AL.size()];


            for (int i = 0; i < AL.size(); i++) {
                sendBallKinetics.ballPlayerFields[i] = globalVariables.getSettingsVariables().myPlayerScreen;
                sendBallKinetics.ballPositions[i] = new Vector2(balls[sendBallKinetics.ballNumbers[i]].ballBody.getPosition().x / width * PIXELS_TO_METERS,
                        balls[sendBallKinetics.ballNumbers[i]].ballBody.getPosition().y / height * PIXELS_TO_METERS);
                sendBallKinetics.ballVelocities[i] = new Vector2(balls[sendBallKinetics.ballNumbers[i]].ballBody.getLinearVelocity().x / width * PIXELS_TO_METERS,
                        balls[sendBallKinetics.ballNumbers[i]].ballBody.getLinearVelocity().y / height * PIXELS_TO_METERS);
            }

            globalVariables.getNetworkVariables().connectionList.get(0).sendUDP(sendBallKinetics);
            //Gdx.app.debug("ClassicGame", "ball "+Integer.toString(theBall.ballNumber)+" sent");
        }
    }

    void sendBallPlayerScreenChange(ArrayList<Integer> AL) {
        if (AL.size()>0) {
            sendBallScreenChange.ballNumbers = AL.toArray(new Integer[0]);
            sendBallScreenChange.ballPlayerFields = new int[AL.size()];
            sendBallScreenChange.ballPositions = new Vector2[AL.size()];
            sendBallScreenChange.ballVelocities = new Vector2[AL.size()];


            for (int i = 0; i < AL.size(); i++) {
                sendBallScreenChange.ballPlayerFields[i] = (globalVariables.getSettingsVariables().myPlayerScreen + 1) % 2;
                sendBallScreenChange.ballPositions[i] = new Vector2(balls[sendBallScreenChange.ballNumbers[i]].ballBody.getPosition().x / width * PIXELS_TO_METERS,
                        balls[sendBallScreenChange.ballNumbers[i]].ballBody.getPosition().y / height * PIXELS_TO_METERS);
                sendBallScreenChange.ballVelocities[i] = new Vector2(balls[sendBallScreenChange.ballNumbers[i]].ballBody.getLinearVelocity().x / width * PIXELS_TO_METERS,
                        balls[sendBallScreenChange.ballNumbers[i]].ballBody.getLinearVelocity().y / height * PIXELS_TO_METERS);
            }
            globalVariables.getNetworkVariables().connectionList.get(0).sendTCP(sendBallScreenChange);
        }
    }

    void sendBatFunction(Bat theBat) {
        sendBat.batPlayerField =globalVariables.getSettingsVariables().myPlayerScreen;
        sendBat.batPosition=new Vector2(theBat.batBody.getPosition().x / width * PIXELS_TO_METERS,theBat.batBody.getPosition().y / height * PIXELS_TO_METERS);
        sendBat.batOrientation=theBat.batBody.getAngle();
        globalVariables.getNetworkVariables().connectionList.get(0).sendUDP(sendBat);
    }



    /********* OTHER FUNCTIONS *********/

    //adjust camera for zooming
    private boolean zoom (float originalDistance, float currentDistance){
        float newZoomLevel=zoomLevel+(originalDistance-currentDistance)/5000;
        if(newZoomLevel<=2.0f && newZoomLevel>=1.0f) {
            zoomLevel=newZoomLevel;

        } else if(newZoomLevel>2.0f) {
            zoomLevel=2.0f;
        } else if(newZoomLevel<1.0f) {
            zoomLevel=1.0f;
        }
        camera.zoom=zoomLevel;
        camera.position.set(0,-height+height/2*zoomLevel,0);
        camera.update();

        return false;
    }

    //transform touch input for variable zoomlevel
    Vector2 transformZoom(Vector2 vec) {
        Vector2 camPos = new Vector2(0,-height+height/2*zoomLevel);
        vec.x*=zoomLevel;
        vec.y = - height + (vec.y + height) * zoomLevel;
        return(vec);
    }

    void drawShapes() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeType.Filled);

        //field background
        shapeRenderer.setColor(89/255f, 89/255f, 89/255f, 1);
        shapeRenderer.rect(-width/2, -height, width, height*2);

        //field line
        shapeRenderer.setColor(128/255f,143/255f,133/255f,1);
        shapeRenderer.rect(-width/2, -5, width, 10);

        shapeRenderer.end();

        myBat.display();
        otherBat.display();

        for(Ball ball : balls) {
            ball.display();
        }

        touches.drawTouchPoints();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        //show fps
        font.draw(batch,"fps: "+Float.toString(Gdx.graphics.getFramesPerSecond()), width /3, -20);

        batch.end();

        //uncomment for box2d bodies to be shown
        //debugRenderer.render(world,camera.combined.cpy().scale(PIXELS_TO_METERS,PIXELS_TO_METERS,1));
    }

    /********* CLASSES *********/

    //create class Ball
    class Ball {
        private Body ballBody;

        private float ballRadius;

        private float[] ballColor =new float[3];
        private int ballNumber;

        //arraylist with max length for balltrace
        private boundedArrayList<Vector2> ballPositionArrayList;
        private int ballPositionArrayListLength;
        private int ballPositionFrameSkip=4;
        private long ballUpdateCounter=0;

        //constructor for Ball
        Ball(Vector2 position_, Vector2 velocity_, float radius_,int ballNumber_,int playerScreen_, int ballPositionArrayListLength_) {


            this.ballRadius =radius_;
            this.ballNumber=ballNumber_;
            this.ballPositionArrayListLength=ballPositionArrayListLength_;

            //physics stuff
            BodyDef ballBodyDef= new BodyDef();
            this.ballPositionArrayList = new boundedArrayList(ballPositionArrayListLength);

            //set bodytype
            if(globalVariables.getSettingsVariables().myPlayerScreen==0) {
                ballBodyDef.type = BodyDef.BodyType.DynamicBody;
            } else {
                ballBodyDef.type = BodyDef.BodyType.KinematicBody;
            }
            //better collisions for fast moving objects
            ballBodyDef.bullet=true;

            ballBodyDef.position.set(position_);
            this.ballBody = world.createBody(ballBodyDef);

            CircleShape ballShape = new CircleShape();
            ballShape.setPosition(new Vector2(0,0));
            ballShape.setRadius(ballRadius);

            FixtureDef ballFixtureDef = new FixtureDef();
            ballFixtureDef.shape= ballShape;
            //density for physics calculations
            ballFixtureDef.density=1f;
            //set collision filters
            ballFixtureDef.filter.categoryBits = CATEGORY_BALL;
            ballFixtureDef.filter.maskBits = MASK_BALL;

            this.ballBody.createFixture(ballFixtureDef);
            ballShape.dispose();

            this.ballBody.setLinearVelocity(velocity_);
        }

        void doPhysics() {
            //if ball on my screen apply forces etc. else update position and velocity by the ones stored globally and received from other player
            if (globalVariables.getGameVariables().ballsPlayerScreens[this.ballNumber] == globalVariables.getSettingsVariables().myPlayerScreen) {
                this.checkPlayerFieldContains();
                //Gdx.app.debug("ClassicGame", "ball "+Integer.toString(balls[i].ballNumber)+" computed");
                this.ballBody.setType(BodyDef.BodyType.DynamicBody);
                for (int j = 0; j < touches.maxTouchCount; j++) {
                    if (touches.isTouched[j]) {
                        if (globalVariables.getGameVariables().attractionState) {
                            //attraction
                            this.ballBody.applyForceToCenter(touches.touchPos[j].cpy().scl(1 / PIXELS_TO_METERS).sub(balls[this.ballNumber].ballBody.getPosition()), true);
                        }
                    }
                }
                if(globalVariables.getGameVariables().gravityState) {
                    //gravity
                    this.ballBody.applyForceToCenter(new Vector2(0,-(this.ballBody.getPosition().y+height/PIXELS_TO_METERS)*1f), true);
                }

            } else {
                //Gdx.app.debug("ClassicGame", "ball "+Integer.toString(balls[i].ballNumber)+" NOT computed");
                if (frameNumber % sendFrameSkip == 0) {
                    this.ballBody.setType(BodyDef.BodyType.KinematicBody);

                    this.ballBody.setTransform(new Vector2(globalVariables.getGameVariables().ballsPositions[this.ballNumber].x * width / PIXELS_TO_METERS,
                            globalVariables.getGameVariables().ballsPositions[this.ballNumber].y * height / PIXELS_TO_METERS), 0);
                    this.ballBody.setLinearVelocity(new Vector2(globalVariables.getGameVariables().ballsVelocities[this.ballNumber].x * width / PIXELS_TO_METERS,
                            globalVariables.getGameVariables().ballsVelocities[this.ballNumber].y * height / PIXELS_TO_METERS));

                    frameNumber = 0;
                }
            }
            if (ballUpdateCounter%this.ballPositionFrameSkip==0) {
                //add position for balltrace
                this.ballPositionArrayList.addLast(this.ballBody.getPosition().cpy().scl(PIXELS_TO_METERS));
            }
            this.ballUpdateCounter++;
        }

        void display() {
            //color depending on playerfield
            if (globalVariables.getGameVariables().ballsPlayerScreens[ballNumber]==0) {
                this.ballColor[0] =62/255f;
                this.ballColor[1]=143/255f;
                this.ballColor[2]=215/255f;

            } else {
                this.ballColor[0] =186/255f;
                this.ballColor[1]=106/255f;
                this.ballColor[2]=133/255f;
            }
            shapeRenderer.begin(ShapeType.Filled);
            shapeRenderer.setColor(this.ballColor[0], this.ballColor[1], this.ballColor[2], 1f);

            //display trace
            for(Vector2 pos : this.ballPositionArrayList) {
                shapeRenderer.circle(pos.x, pos.y,5);
                //Gdx.app.debug("ClassicGame", "pos x " +Float.toString(pos.x)+" y "+ Float.toString(pos.y));
            }
            shapeRenderer.circle(this.ballBody.getPosition().x*PIXELS_TO_METERS,this.ballBody.getPosition().y*PIXELS_TO_METERS, this.ballRadius *PIXELS_TO_METERS, 30);
            shapeRenderer.end();
        }

        void checkPlayerFieldContains() {
            //TODO generalize to more players
            //check in which field ball is contained
            if(gameField.playerFieldPolygon[(globalVariables.getSettingsVariables().myPlayerScreen+1)%2].contains(this.ballBody.getPosition().cpy().scl(PIXELS_TO_METERS))) {
                sendBallScreenChangeAL.add(this.ballNumber);
                //Gdx.app.debug("ClassicGame", "ball "+Integer.toString(this.ballNumber)+" on other screen");
            } else {
                sendBallKineticsAL.add(this.ballNumber);
            }
        }

    }

    class Bat {
        private Body batBody;
        private int batPlayerField;
        private int batWidth = 300;
        private int batHeight = 70;
        private Vector2 newPos;
        Bat(int batPlayerField_) {
            this.batPlayerField =batPlayerField_;
            BodyDef bodyDef= new BodyDef();
            if(batPlayerField_==globalVariables.getSettingsVariables().myPlayerScreen) {
                bodyDef.type= BodyDef.BodyType.DynamicBody;
                bodyDef.position.set(new Vector2(0,-height*0.8f).scl(1/PIXELS_TO_METERS));
            } else {
                bodyDef.type= BodyDef.BodyType.KinematicBody;
                bodyDef.position.set(new Vector2(0,height*0.8f).scl(1/PIXELS_TO_METERS));
            }

            this.batBody=world.createBody(bodyDef);
            PolygonShape batShape= new PolygonShape();
            batShape.setAsBox(this.batWidth/2/PIXELS_TO_METERS,this.batHeight/2/PIXELS_TO_METERS);
            FixtureDef batFixtureDef= new FixtureDef();
            //batFixtureDef.restitution=0f;
            batFixtureDef.shape = batShape;
            batShape.dispose();
            batFixtureDef.density=1f;
            batFixtureDef.friction=10f;
            batFixtureDef.filter.categoryBits = CATEGORY_BAT;
            batFixtureDef.filter.maskBits = MASK_BAT;
            this.batBody.createFixture(batFixtureDef);
            this.batBody.setLinearDamping(100);
            this.batBody.setAngularDamping(100);

            this.newPos = batBody.getPosition();
            globalVariables.getGameVariables().batPositions[this.batPlayerField]=new Vector2(this.batBody.getPosition().x/width*PIXELS_TO_METERS,this.batBody.getPosition().y/height*PIXELS_TO_METERS);
        }

        void doPhysics(Vector2 position, float orientation) {
            //similar to ball if my bat then physics else only change position etc.
            if(this.batPlayerField ==globalVariables.getSettingsVariables().myPlayerScreen) {
                //update new position if touch inside my field
                if(gameField.playerFieldPolygon[globalVariables.getSettingsVariables().myPlayerScreen].contains(position)) {
                    this.newPos = position;
                }
                //force to physically move to touched position
                Vector2 forceVector = this.newPos.cpy().scl(1/PIXELS_TO_METERS).sub(this.batBody.getPosition());
                //torque to set orientation
                float torque = -(this.batBody.getAngle()-orientation)*5000f;
                //forceVector.scl(1/forceVector.len());
                forceVector.scl(5000f);
                forceVector.sub(this.batBody.getLinearVelocity().cpy().scl(0));
                this.batBody.applyForceToCenter(forceVector,true);
                this.batBody.applyTorque(torque,true);
            } else {
                this.batBody.setTransform(new Vector2(globalVariables.getGameVariables().batPositions[this.batPlayerField].x*width/PIXELS_TO_METERS,
                        globalVariables.getGameVariables().batPositions[this.batPlayerField].y*height/PIXELS_TO_METERS),globalVariables.getGameVariables().batOrientations[this.batPlayerField]);
                //this.batBody.setLinearVelocity(new Vector2(globalVariables.getGameVariables().batPositions[this.batPlayerField].x*width/PIXELS_TO_METERS,globalVariables.getGameVariables().batPositions[this.batPlayerField].y*height/PIXELS_TO_METERS));

            }

        }

        void display() {
            //rotate and translate needed to properly display bat with orientation
            shapeRenderer.setColor(196/255f, 106/255f, 78/255f, 1);
            shapeRenderer.begin(ShapeType.Filled);
            shapeRenderer.identity();
            shapeRenderer.translate(this.batBody.getPosition().x*PIXELS_TO_METERS, this.batBody.getPosition().y*PIXELS_TO_METERS,0);
            shapeRenderer.rotate(0, 0,1, this.batBody.getAngle()* (180f/MathUtils.PI));
            shapeRenderer.rect(-this.batWidth/2, -this.batHeight/2, this.batWidth, this.batHeight);
            shapeRenderer.identity();
            //shapeRenderer.rotate(0, 0,1, -this.batBody.getAngle()* (180f/MathUtils.PI));
            //shapeRenderer.translate(-this.batBody.getPosition().x*PIXELS_TO_METERS, -this.batBody.getPosition().y*PIXELS_TO_METERS,0);
            shapeRenderer.end();
        }

    }

    class Touches {
        private int maxTouchCount;
        private Vector2[] touchPos;
        private Vector2[] lastTouchPos;
        private Vector2[] startTouchPos;
        private boolean[] isTouched;
        private boolean[] lastIsTouched;

        Touches(int maxTouchCount_) {
            this.maxTouchCount=maxTouchCount_;
            this.touchPos=new Vector2[maxTouchCount];
            this.lastTouchPos=new Vector2[maxTouchCount];
            this.startTouchPos=new Vector2[maxTouchCount];
            this.isTouched=new boolean[maxTouchCount];
            this.lastIsTouched=new boolean[maxTouchCount];

            for (int i=0;i<maxTouchCount;i++) {
                this.touchPos[i]=new Vector2(0,-height/2);
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
                    this.touchPos[i]=transformZoom(new Vector2(Gdx.input.getX(i)-width/2,-Gdx.input.getY(i)));
                } else {
                    this.isTouched[i] = false;
                }
                if(!this.lastIsTouched[i] && this.isTouched[i]) {
                    this.startTouchPos[i]=this.touchPos[i];
                }
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
                    camera.zoom=zoomLevel;
                    camera.position.set(0,-height+height/2*zoomLevel,0);
                    camera.update();
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
        void drawTouchPoints() {
            shapeRenderer.begin(ShapeType.Filled);
            for(int i = 0; i < this.maxTouchCount; i++) {
                if (this.isTouched[i]) {
                    shapeRenderer.setColor(0, 1, 0, 0.5f);
                    shapeRenderer.circle(this.touchPos[i].x,this.touchPos[i].y, 30, 100);
                    if(i>0) {
                        shapeRenderer.setColor(0, 1, 0, 1);
                        shapeRenderer.line(this.touchPos[i-1].x,this.touchPos[i-1].y,this.touchPos[i].x,this.touchPos[i].y);
                    }
                }
            }
            shapeRenderer.end();
        }
    }

    class GameField{
        private Body leftBorderBody,bottomBorderBody,rightBorderBody,topBorderBody,midLineBody;
        private Polygon[] playerFieldPolygon;
        private Vector2[] gameFieldVertices;
        GameField() {
            //TODO generalize to more players
            this.gameFieldVertices = new Vector2[6];
            this.playerFieldPolygon = new Polygon[2];
            for (int i=0;i<2;i++) {
                this.playerFieldPolygon[i]=new Polygon();
            }

            float rotatePlayerScreenDegrees=0;
            for (int i=0;i<2;i++) {
                if(globalVariables.getSettingsVariables().myPlayerScreen==i) {
                    rotatePlayerScreenDegrees = i * 180;
                }
            }

            //TODO eliminate tunneling outside field
            //following has to be done cleaner with one set of vertices and rotations

            this.gameFieldVertices[0] = new Vector2(-width/2,-height).rotate(rotatePlayerScreenDegrees);
            this.gameFieldVertices[1] = new Vector2(width/2,-height).rotate(rotatePlayerScreenDegrees);
            this.gameFieldVertices[2] = new Vector2(width/2,0).rotate(rotatePlayerScreenDegrees);
            this.gameFieldVertices[3] = new Vector2(width/2,height).rotate(rotatePlayerScreenDegrees);
            this.gameFieldVertices[4] = new Vector2(-width/2,height).rotate(rotatePlayerScreenDegrees);
            this.gameFieldVertices[5] = new Vector2(-width/2,0).rotate(rotatePlayerScreenDegrees);

            this.playerFieldPolygon[0] = new Polygon(new float[]{gameFieldVertices[0].x, gameFieldVertices[0].y, gameFieldVertices[1].x, gameFieldVertices[1].y, gameFieldVertices[2].x,
                    gameFieldVertices[2].y, gameFieldVertices[5].x, gameFieldVertices[5].y});
            this.playerFieldPolygon[1] = new Polygon(new float[]{gameFieldVertices[3].x, gameFieldVertices[3].y, gameFieldVertices[4].x, gameFieldVertices[4].y, gameFieldVertices[5].x,
                    gameFieldVertices[5].y, gameFieldVertices[2].x, gameFieldVertices[2].y});

            Vector2 [] borderVertices=new Vector2[4];
            borderVertices[0]=new Vector2(-width/2/PIXELS_TO_METERS,-height/PIXELS_TO_METERS);
            borderVertices[1]=new Vector2(width/2/PIXELS_TO_METERS,-height/PIXELS_TO_METERS);
            borderVertices[2]=new Vector2(width/2/PIXELS_TO_METERS,height/PIXELS_TO_METERS);
            borderVertices[3]= new Vector2(-width/2/PIXELS_TO_METERS,height/PIXELS_TO_METERS);

            final EdgeShape leftBorderShape,bottomBorderShape,rightBorderShape,topBorderShape, midLineShape;
            leftBorderShape=new EdgeShape();
            bottomBorderShape=new EdgeShape();
            rightBorderShape= new EdgeShape();
            topBorderShape=new EdgeShape();
            midLineShape=new EdgeShape();

            bottomBorderShape.set(borderVertices[0],borderVertices[1]);
            rightBorderShape.set(borderVertices[1],borderVertices[2]);
            topBorderShape.set(borderVertices[2],borderVertices[3]);
            leftBorderShape.set(borderVertices[3],borderVertices[0]);
            midLineShape.set(this.gameFieldVertices[2].cpy().scl(1/PIXELS_TO_METERS), this.gameFieldVertices[5].cpy().scl(1/PIXELS_TO_METERS));

            BodyDef borderBodyDef= new BodyDef();
            borderBodyDef.type = BodyDef.BodyType.StaticBody;
            borderBodyDef.position.set(0,0);
            FixtureDef borderFd=new FixtureDef();
            borderFd.restitution = 0.7f;
            borderFd.filter.categoryBits=CATEGORY_BORDER;
            borderFd.filter.maskBits=MASK_BORDER;
            FixtureDef lineFd=new FixtureDef();
            lineFd.filter.categoryBits=CATEGORY_FIELDLINE;
            lineFd.filter.maskBits=MASK_FIELDLINE;


            leftBorderBody = world.createBody(borderBodyDef);
            bottomBorderBody= world.createBody(borderBodyDef);
            rightBorderBody= world.createBody(borderBodyDef);
            topBorderBody=world.createBody(borderBodyDef);
            midLineBody=world.createBody(borderBodyDef);

            borderFd.shape = leftBorderShape;
            leftBorderBody.createFixture(borderFd);

            borderFd.shape = bottomBorderShape;
            bottomBorderBody.createFixture(borderFd);

            borderFd.shape = rightBorderShape;
            rightBorderBody.createFixture(borderFd);

            borderFd.shape = topBorderShape;
            topBorderBody.createFixture(borderFd);

            lineFd.shape = midLineShape;
            midLineBody.createFixture(lineFd);

            leftBorderShape.dispose();
            bottomBorderShape.dispose();
            rightBorderShape.dispose();
            topBorderShape.dispose();
            midLineShape.dispose();
        }
    }

    //custom arraylist with maximum elements, first one is kicked out if max is reached
    class boundedArrayList<T> extends ArrayList<T> {
        private int maxSize;
        public boundedArrayList(int size)
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
