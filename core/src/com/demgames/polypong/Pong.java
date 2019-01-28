package com.demgames.polypong;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.PolygonSprite;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
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

import java.util.ArrayList;
import java.util.Arrays;

public class Pong extends ApplicationAdapter{
    //use of private etc is not consistently done
    private IGlobals globalVariables;

    //setup global variables
    public Pong(IGlobals globalVariables_ ) {
        this.globalVariables=globalVariables_;
    }

    //declare renderer and world related stuff
    private SpriteBatch spriteBatch;
    private PolygonSpriteBatch polygonSpriteBatch;
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

    //global classes for balls, bats and arraylists for spriteBatch sending
    private Ball[] balls;
    private Bat myBat, otherBat;
    private ArrayList<Integer> sendBallKineticsAL=new ArrayList<Integer>(Arrays.asList(new Integer[]{}));
    private ArrayList<Integer> sendBallScreenChangeAL=new ArrayList<Integer>(Arrays.asList(new Integer[]{}));
    private ArrayList<Integer> sendBallGoalAL=new ArrayList<Integer>(Arrays.asList(new Integer[]{}));

    //global sendclasses
    private IGlobals.SendVariables.SendBallKinetics sendBallKinetics=new IGlobals.SendVariables.SendBallKinetics();
    private IGlobals.SendVariables.SendBallScreenChange sendBallScreenChange=new IGlobals.SendVariables.SendBallScreenChange();
    private IGlobals.SendVariables.SendBallGoal sendBallGoal=new IGlobals.SendVariables.SendBallGoal();
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
        //set font and spriteBatch for drawing fonts and textures
        spriteBatch = new SpriteBatch();
        polygonSpriteBatch = new PolygonSpriteBatch();
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
                    globalVariables.getGameVariables().ballsPositions[i].y*height/PIXELS_TO_METERS),new Vector2(10,10),(1+globalVariables.getGameVariables().ballsSizes[i])*width/50/PIXELS_TO_METERS,
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
        spriteBatch.dispose();
        font.dispose();
        world.dispose();
        debugRenderer.dispose();
    }

    @Override
    public void render() {
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
        sendBallGoal(sendBallGoalAL);
        sendBatFunction(myBat);
        sendBallKineticsAL=new ArrayList<Integer>(Arrays.asList(new Integer[]{}));
        sendBallScreenChangeAL=new ArrayList<Integer>(Arrays.asList(new Integer[]{}));
        sendBallGoalAL=new ArrayList<Integer>(Arrays.asList(new Integer[]{}));

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

    void sendBallGoal(ArrayList<Integer> AL) {
        if (AL.size()>0) {
            sendBallGoal.ballNumbers = AL.toArray(new Integer[0]);
            sendBallGoal.playerScores=globalVariables.getGameVariables().playerScores;

            globalVariables.getNetworkVariables().connectionList.get(0).sendTCP(sendBallGoal);
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

    float[] vecToFloatArray(Vector2[] vectorArray) {
        float[] floatArray = new float[vectorArray.length*2];
        for(int i=0;i<vectorArray.length;i++) {
            floatArray[2*i]=vectorArray[i].x;
            floatArray[2*i+1]=vectorArray[i].y;
        }
        return(floatArray);
    }

    Vector2[] transformVectorArray(Vector2[] vectorArray, float scale, float degrees) {
        for(Vector2 vector : vectorArray) {
            vector.scl(scale).rotate(degrees);
        }
        return(vectorArray);
    }

    void drawShapes() {
        shapeRenderer.setProjectionMatrix(camera.combined);

        gameField.display();

        myBat.display();
        otherBat.display();

        for(Ball ball : balls) {
            ball.display();
        }

        touches.drawTouchPoints();

        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        //show fps
        font.draw(spriteBatch,"fps: "+Float.toString(Gdx.graphics.getFramesPerSecond()), width /3, -20);
        font.draw(spriteBatch,Integer.toString(globalVariables.getGameVariables().playerScores[globalVariables.getSettingsVariables().myPlayerScreen])+" : "+
                Integer.toString(globalVariables.getGameVariables().playerScores[(globalVariables.getSettingsVariables().myPlayerScreen+1)%2]), 0, -20);

        spriteBatch.end();

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
            ballFixtureDef.friction=0f;
            //set collision filters
            ballFixtureDef.filter.categoryBits = CATEGORY_BALL;
            ballFixtureDef.filter.maskBits = MASK_BALL;

            this.ballBody.createFixture(ballFixtureDef);
            ballShape.dispose();

            this.ballBody.setLinearVelocity(velocity_);
        }

        void doPhysics() {
            //if ball on my screen apply forces etc. else update position and velocity by the ones stored globally and received from other player
            if(globalVariables.getGameVariables().ballDisplayStates[this.ballNumber]) {
                if (globalVariables.getGameVariables().ballsPlayerScreens[this.ballNumber] == globalVariables.getSettingsVariables().myPlayerScreen) {
                    if(!this.checkGoal()) {
                        this.checkPlayerField();
                        //Gdx.app.debug("ClassicGame", "ball "+Integer.toString(this.ballNumber)+" computed");
                        this.ballBody.setType(BodyDef.BodyType.DynamicBody);


                    }
                } else {
                    //Gdx.app.debug("ClassicGame", "ball "+Integer.toString(this.ballNumber)+" NOT computed");
                    if (frameNumber % sendFrameSkip == 0) {
                        this.ballBody.setType(BodyDef.BodyType.KinematicBody);

                        this.ballBody.setTransform(new Vector2(globalVariables.getGameVariables().ballsPositions[this.ballNumber].x * width / PIXELS_TO_METERS,
                                globalVariables.getGameVariables().ballsPositions[this.ballNumber].y * height / PIXELS_TO_METERS), 0);
                        this.ballBody.setLinearVelocity(new Vector2(globalVariables.getGameVariables().ballsVelocities[this.ballNumber].x * width / PIXELS_TO_METERS,
                                globalVariables.getGameVariables().ballsVelocities[this.ballNumber].y * height / PIXELS_TO_METERS));
                    }
                }
                if (ballUpdateCounter % this.ballPositionFrameSkip == 0 && globalVariables.getGameVariables().ballDisplayStates[this.ballNumber]) {
                    //add position for balltrace
                    this.ballPositionArrayList.addLast(this.ballBody.getPosition().cpy().scl(PIXELS_TO_METERS));
                }
                this.ballUpdateCounter++;
            } else {
                this.destroyBall();
            }
        }

        void display() {
            //color depending on playerfield
            if(globalVariables.getGameVariables().ballDisplayStates[this.ballNumber]) {
                if (globalVariables.getGameVariables().ballsPlayerScreens[ballNumber] == 0) {
                    this.ballColor[0] = 62 / 255f;
                    this.ballColor[1] = 143 / 255f;
                    this.ballColor[2] = 215 / 255f;

                } else {
                    this.ballColor[0] = 186 / 255f;
                    this.ballColor[1] = 106 / 255f;
                    this.ballColor[2] = 133 / 255f;
                }
                shapeRenderer.begin(ShapeType.Filled);
                shapeRenderer.setColor(this.ballColor[0], this.ballColor[1], this.ballColor[2], 1f);

                //display trace
                for (Vector2 pos : this.ballPositionArrayList) {
                    shapeRenderer.circle(pos.x, pos.y, 5);
                    //Gdx.app.debug("ClassicGame", "pos x " +Float.toString(pos.x)+" y "+ Float.toString(pos.y));
                }
                shapeRenderer.circle(this.ballBody.getPosition().x * PIXELS_TO_METERS, this.ballBody.getPosition().y * PIXELS_TO_METERS, this.ballRadius * PIXELS_TO_METERS, 30);
                shapeRenderer.end();
            }
        }

        void checkPlayerField() {
            //TODO generalize to more players
            //check in which field ball is contained
            if (gameField.playerFieldPolygons[(globalVariables.getSettingsVariables().myPlayerScreen + 1) % 2].contains(this.ballBody.getPosition().cpy().scl(PIXELS_TO_METERS))) {
                sendBallScreenChangeAL.add(this.ballNumber);
                //Gdx.app.debug("ClassicGame", "ball "+Integer.toString(this.ballNumber)+" on other screen");
            } else {
                sendBallKineticsAL.add(this.ballNumber);
            }
        }

        boolean checkGoal() {
            if (gameField.goalPolygons[globalVariables.getSettingsVariables().myPlayerScreen].contains(this.ballBody.getPosition().cpy().scl(PIXELS_TO_METERS))) {
                Gdx.app.debug("ClassicGame", "ball " + Integer.toString(this.ballNumber) + " in my goal");
                this.destroyBall();
                globalVariables.getGameVariables().playerScores[globalVariables.getSettingsVariables().myPlayerScreen]--;
                sendBallGoalAL.add(this.ballNumber);
                return(true);
            }
            return(false);
        }

        void destroyBall() {
            if(this.ballBody!=null) {
                globalVariables.getGameVariables().ballDisplayStates[this.ballNumber] = false;
                world.destroyBody(this.ballBody);
                this.ballBody.setUserData(null);
                this.ballBody = null;
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
            batFixtureDef.friction=0f;
            batFixtureDef.restitution=1f;
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
                if(gameField.playerFieldPolygons[globalVariables.getSettingsVariables().myPlayerScreen].contains(position)) {
                    this.newPos = position;

                    //Schläger wird in Y position festgehalten
                    this.newPos = new Vector2(position.x,-height + height/8);
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
            shapeRenderer.begin(ShapeType.Filled);
            shapeRenderer.setColor(196/255f, 106/255f, 78/255f, 1);
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
        private Body fieldLineBody;
        private Body[] borderBodies;
        private Polygon[] playerFieldPolygons;
        private Polygon[] goalPolygons;

        private Vector2[] gameFieldVertices;

        private PolygonSprite[] playerFieldSprites;
        private PolygonSprite[] goalSprites;
        GameField() {
            //TODO generalize to more players
            this.gameFieldVertices = new Vector2[14];
            this.playerFieldPolygons = new Polygon[2];
            this.goalPolygons = new Polygon[playerFieldPolygons.length];
            EdgeShape fieldLineShape = new EdgeShape();
            PolygonShape[] borderShapes = new PolygonShape[8];

            for (int i=0;i<playerFieldPolygons.length;i++) {
                this.playerFieldPolygons[i]=new Polygon();
                this.goalPolygons[i] = new Polygon();
            }

            for (int i=0;i<borderShapes.length;i++) {
                borderShapes[i] = new PolygonShape();
            }
            this.borderBodies = new Body[borderShapes.length];

            //TODO eliminate tunneling outside field
            //following has to be done cleaner with one set of vertices and rotations


            float borderThickness=200;
            float heightPart=0.9f;

            this.gameFieldVertices[0] = new Vector2(-width/2,0);
            this.gameFieldVertices[1] = new Vector2(-width/2,-height*heightPart);
            this.gameFieldVertices[2] = new Vector2(-width/4,-height*0.97f);
            this.gameFieldVertices[3] = new Vector2(-width/4,-height-borderThickness);
            this.gameFieldVertices[4] = new Vector2(width/4,-height-borderThickness);
            this.gameFieldVertices[5] = new Vector2(width/4,-height*0.97f);
            this.gameFieldVertices[6] = new Vector2(width/2,-height*heightPart);
            this.gameFieldVertices[7] = new Vector2(width/2,0);
            this.gameFieldVertices[8] = new Vector2(-width/2-borderThickness,0);
            this.gameFieldVertices[9] = new Vector2(-width/2-borderThickness,-height*heightPart);
            this.gameFieldVertices[10] = new Vector2(-width/2-borderThickness,-height-borderThickness);
            this.gameFieldVertices[11] = new Vector2(width/2+borderThickness,0);
            this.gameFieldVertices[12] = new Vector2(width/2+borderThickness,-height*heightPart);
            this.gameFieldVertices[13] = new Vector2(width/2+borderThickness,-height-borderThickness);

            this.gameFieldVertices = transformVectorArray(gameFieldVertices,1/PIXELS_TO_METERS,0).clone();
            fieldLineShape.set(gameFieldVertices[0],gameFieldVertices[7]);
            this.gameFieldVertices = transformVectorArray(gameFieldVertices,PIXELS_TO_METERS,0).clone();

            //rotate for perspective of player
            this.gameFieldVertices = transformVectorArray(gameFieldVertices,1,180f*globalVariables.getSettingsVariables().myPlayerScreen).clone();

            for(int i = 0; i< playerFieldPolygons.length; i++) {
                this.gameFieldVertices = transformVectorArray(this.gameFieldVertices,1,180f*i).clone();
                this.playerFieldPolygons[i] = new Polygon(vecToFloatArray(new Vector2[]{this.gameFieldVertices[0],this.gameFieldVertices[1],this.gameFieldVertices[2],this.gameFieldVertices[5],this.gameFieldVertices[6],this.gameFieldVertices[7]}));
                this.goalPolygons[i] = new Polygon(vecToFloatArray(new Vector2[]{this.gameFieldVertices[2],this.gameFieldVertices[3],this.gameFieldVertices[4],this.gameFieldVertices[5]}));
                this.gameFieldVertices = transformVectorArray(this.gameFieldVertices,1/PIXELS_TO_METERS,0).clone();
                borderShapes[0+i*4].set(new Vector2[]{this.gameFieldVertices[8],this.gameFieldVertices[0],this.gameFieldVertices[1],this.gameFieldVertices[9]});
                borderShapes[1+i*4].set(new Vector2[]{this.gameFieldVertices[9],this.gameFieldVertices[1],this.gameFieldVertices[2],this.gameFieldVertices[3],this.gameFieldVertices[10]});
                borderShapes[2+i*4].set(new Vector2[]{this.gameFieldVertices[5],this.gameFieldVertices[6],this.gameFieldVertices[12],this.gameFieldVertices[13],this.gameFieldVertices[4]});
                borderShapes[3+i*4].set(new Vector2[]{this.gameFieldVertices[7],this.gameFieldVertices[11],this.gameFieldVertices[6],this.gameFieldVertices[12]});
                //borderShapes[4+i*4].set(new Vector2[]{this.gameFieldVertices[3],this.gameFieldVertices[4]});
                this.gameFieldVertices = transformVectorArray(this.gameFieldVertices,PIXELS_TO_METERS,0).clone();
            }

            BodyDef borderBodyDef= new BodyDef();
            borderBodyDef.type = BodyDef.BodyType.StaticBody;
            borderBodyDef.position.set(0,0);
            FixtureDef borderFixtureDef=new FixtureDef();
            borderFixtureDef.restitution = 1f;
            borderFixtureDef.friction=0f;
            borderFixtureDef.filter.categoryBits=CATEGORY_BORDER;
            borderFixtureDef.filter.maskBits=MASK_BORDER;

            FixtureDef fieldLineFixtureDef=new FixtureDef();
            fieldLineFixtureDef.filter.categoryBits=CATEGORY_FIELDLINE;
            fieldLineFixtureDef.filter.maskBits=MASK_FIELDLINE;

            for(int i = 0; i< this.borderBodies.length; i++) {
                this.borderBodies[i] = world.createBody(borderBodyDef);
                borderFixtureDef.shape = borderShapes[i];
                this.borderBodies[i].createFixture(borderFixtureDef);
            }

            this.fieldLineBody =world.createBody(borderBodyDef);
            fieldLineFixtureDef.shape = fieldLineShape;
            this.fieldLineBody.createFixture(fieldLineFixtureDef);

            for (int i=0;i<borderShapes.length;i++) {
                borderShapes[i].dispose();
            }
            fieldLineShape.dispose();

            this.playerFieldSprites = new PolygonSprite[playerFieldPolygons.length];
            this.goalSprites = new PolygonSprite[playerFieldPolygons.length];
            for(int i=0; i<playerFieldPolygons.length;i++) {
                this.playerFieldSprites[i]=createFilledPolygon(this.playerFieldPolygons[i].getVertices(),new short[] {0,1,5,1,4,5,1,2,4,2,3,4},89/255f, 89/255f, 89/255f, 1f);
                this.goalSprites[i]=createFilledPolygon(this.goalPolygons[i].getVertices(),new short[] {0,1,3,1,2,3},1f, 1f, 1f, 1f);
            }
        }

        void display() {
            //background
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            //field background
            polygonSpriteBatch.setProjectionMatrix(camera.combined);
            polygonSpriteBatch.begin();
            for(int i=0; i<playerFieldPolygons.length;i++) {
                this.playerFieldSprites[i].draw(polygonSpriteBatch);
                this.goalSprites[i].draw(polygonSpriteBatch);
            }
            polygonSpriteBatch.end();

            shapeRenderer.begin(ShapeType.Filled);

            //field line
            shapeRenderer.setColor(128/255f,143/255f,133/255f,1);
            shapeRenderer.rect(-width/2, -5, width, 10);
            shapeRenderer.setColor(128/255f,143/255f,133/255f,1);
            shapeRenderer.rect(-width/2, -5, width, 10);
            shapeRenderer.end();




        }

        PolygonSprite createFilledPolygon(float [] verticesXY, short [] triangleIndices, float r, float g, float b, float a) {
            PolygonSprite poly;

            // Creating the color filling (but textures would work the same way)
            Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pix.setColor(r,g,b,a); // DE is red, AD is green and BE is blue.
            pix.fill();
            Texture textureSolid = new Texture(pix);
            PolygonRegion polyRegion = new PolygonRegion(new TextureRegion(textureSolid), verticesXY,triangleIndices);
            poly = new PolygonSprite(polyRegion);
            return(poly);
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