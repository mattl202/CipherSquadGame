package game;


import maze.Line;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.Log;

import maze.*;

import java.util.Random;

/**
 * Created by Ethan Halsall on 3/30/2015.
 */
public class Game {

    //game data
    private Maze maze;
    private RecursiveBacktrackerMazeGenerator mazeGen;
    private MazeLineArray mazeLineArray;
    public boolean isMultiPlayer;
    private int level;

    //maze line array data
    private int screenWidth;
    private int screenHeight;

    //graphics
    public Bitmap mazeBitmap;

    //maze data
    private int cellWidth;
    private int cellHeight;
    private int width;
    private int height;
    private int mazeType;


    private long currentTime;
    private int levelPointRelationship;
    public Mouse playerMouse;
    private Mouse[] opponentMice;
    private PowerUpMap powerUpMap;

    private int numOpponents;
    private int aiDifficulty;
    private boolean isNetworked;

    private Random rand;

    //mouse data
    private Bitmap[] miceImageArray;
    private int oldCellWidth;
    private int oldCellHeight;

    //power up images
    private Bitmap[] powerUpImageArray;


    private Point mouseStartPos;
    private final float START_ANGLE = 45;


    //default game settings

    //maze dimensions
    public static final int HEIGHT = 5;
    public static final int WIDTH = 5;

    //player settings
    public static final int NUM_OPPONENTS = 3;

    //single or multiplayer
    public static final boolean MULTI_PLAYER = true;

    //AI settings
    public static final int AI_DIFFICULTY = 50;

    //network settings
    public static final boolean IS_NETWORKED = false;


    //creates a new game with the standard game data defined above in the final fields
    public Game(Bitmap[] miceImageArray, Bitmap[] powerUpImageArray) {
        initializeGame(WIDTH, HEIGHT, miceImageArray, powerUpImageArray, MULTI_PLAYER, NUM_OPPONENTS, AI_DIFFICULTY, IS_NETWORKED);
    }

    public Game(int width, int height, Bitmap[] miceImageArray, Bitmap[] powerUpImageArray, int numOpponents) {
        if (numOpponents <= 0) isMultiPlayer = false;
        initializeGame(width, height, miceImageArray, powerUpImageArray, isMultiPlayer, numOpponents, AI_DIFFICULTY, IS_NETWORKED);
    }

    private void initializeGame(int mazeWidth, int mazeHeight, Bitmap[] miceImageArray, Bitmap[] powerUpImageArray, boolean isMultiPlayer, int numOpponents, int AIDifficulty, boolean isNetworked) {
        height = mazeHeight;
        width = mazeWidth;
        maze = new Maze(mazeWidth, mazeHeight);
        mazeGen = new RecursiveBacktrackerMazeGenerator(maze);
        this.numOpponents = numOpponents;
        this.aiDifficulty = AIDifficulty;
        this.miceImageArray = miceImageArray;
        this.powerUpImageArray = powerUpImageArray;
        rand = new Random();
        initSounds();

        //start the game
        playerMouse = new PlayerMouse();
        level = 1;
        levelPointRelationship = 1000;

        //adds additional mice to represent other players
        this.isNetworked = isNetworked;
        if (isMultiPlayer) {
            if (isNetworked) {
                opponentMice = new NetworkedMouse[numOpponents];
            } else {
                opponentMice = new AIMouse[numOpponents];
            }

            for (int i = 0; i < numOpponents; i++) {
                if (isNetworked) {
                    opponentMice[i] = new NetworkedMouse();
                } else {
                    opponentMice[i] = new AIMouse(maze);
                }
            }
        }

        // mazeWalls = new MazeLineArray(maze, screenWidth, screenHeight);
    }

    public void mouseFinished(Mouse mouse) {
        //keeps track of each player's points and adds points depending on the level they are on and the time they completed the maze
        int points = levelPointRelationship - (int) currentTime / 60000;
        mouse.setTotalTime(currentTime);
        if (points > 0) {
            mouse.addPoints(points);
        }
        mouse.setFinished(true);

    }

    public boolean levelUp() {
        //display player stats
        //reset the game with a larger maze
        if (!playerMouse.getFinished()) { //add for loop here for each mouse if we have multiple players
            return false;
        }

        if (isMultiPlayer) {
            for (Mouse m : opponentMice) { // TODO fix the AI mice
                if (!m.getFinished()) {
                    return false;
                }
            }
        }
        //MainGameView.setHighScore(); //save score before level change

        height = height + 1;
        width = width + 1;
        maze = new Maze(width, height);
        mazeGen = new RecursiveBacktrackerMazeGenerator(maze);

        //reset playermouse location
        playerMouse.setMouseAngle(START_ANGLE);
        playerMouse.moveMouse(mouseStartPos.x, mouseStartPos.y);
        playerMouse.setFinished(false);

        if (isMultiPlayer) {
            //reset opponent mouse
            for (Mouse m : opponentMice) { // TODO fix the AI mice
                m.setMouseAngle(START_ANGLE);
                m.moveMouse(mouseStartPos.x, mouseStartPos.y);
                m.setFinished(false);
                if (!isNetworked) {
                    m.levelUp(maze);
                }
            }
        }

        level++;
        levelPointRelationship *= 2;
        setTime(0);

        return true;
    }

    public void setTime(long t) {
        currentTime = t;
    }

    public boolean isNetworked() {
        return isNetworked;
    }

    public boolean movePlayerMouse(int newX, int newY) {
        if (playerMouse.getFinished()) {
            return false;
        }

        int prevMazeX = playerMouse.getPosX() / cellWidth;
        int prevMazeY = playerMouse.getPosY() / cellHeight;
        int newMazeX = newX / cellWidth;
        int newMazeY = newY / cellHeight;

        //will check if mouse has reached the end of the maze prior to moving
        if (maze.getEnd().x + 1 == newMazeX && maze.getEnd().y == prevMazeY) {
            mouseFinished(playerMouse);
            return true;
        }
        //moves the mouse on the screen if it has not moved into a new maze cell
        if (prevMazeX == newMazeX && prevMazeY == newMazeY) {
            playerMouse.moveMouse(newX, newY);
            return true;
        }
        //prevents the mouse from hopping walls and moving outside of the maze
        if (Math.abs(newMazeX - prevMazeX) + Math.abs(newMazeY - prevMazeY) != 1 || !(maze.checkLocation(new Point(newMazeX, newMazeY)))) {
            return false;
        }
        //moves the mouse into a new maze cell
        int direction = maze.getDirection(prevMazeX, prevMazeY, newMazeX, newMazeY);

        if (!maze.isWallPresent(new Point(prevMazeX, prevMazeY), direction)) {
            playerMouse.moveMouse(newX, newY);
            playerMouse.setMazePos(new Point(newMazeX, newMazeY));
            assignPowerUp(playerMouse,newMazeX,newMazeY);
            return true;
        }
        return false;
    }

    public void moveAIMice() {
        if (isMultiPlayer) {
            int randomMouse = rand.nextInt(numOpponents);
            Log.i("random mouse: ", "" + randomMouse);
            moveAIMouse((AIMouse) opponentMice[randomMouse]);
        }
    }

    private boolean moveAIMouse(AIMouse mouse) {
        Point prevMazeCell = mouse.getMazePos();
        Point nextMazeCell = mouse.aiPath.peek(); //used for direction

        Point prevScreenPos = new Point(mouse.getPosX(), mouse.getPosY());
        Point newScreenPos;

        //moves the mouse on the screen if it has not moved into a new maze cell
        int direction = maze.getDirection(prevMazeCell.x, prevMazeCell.y, nextMazeCell.x, nextMazeCell.y);
        int length = rand.nextInt(aiDifficulty) + aiDifficulty / 2; //randomizes the length of movement
        switch (direction) {
            case Maze.N:
                newScreenPos = new Point(prevScreenPos.x, prevScreenPos.y - length % cellHeight);
                break;
            case Maze.E:
                newScreenPos = new Point(prevScreenPos.x + length % cellWidth, prevScreenPos.y);
                break;
            case Maze.S:
                newScreenPos = new Point(prevScreenPos.x, prevScreenPos.y + length % cellHeight);
                break;
            case Maze.W:
                newScreenPos = new Point(prevScreenPos.x - length % cellWidth, prevScreenPos.y);
                break;
            default:
                newScreenPos = prevScreenPos;
                break;
        }
        Point newMazeCell = new Point(newScreenPos.x / cellWidth, newScreenPos.y / cellHeight);

        //will check if mouse has reached the end of the maze prior to moving
        if (maze.getEnd().x + 1 == newMazeCell.x && maze.getEnd().y == newMazeCell.y) {
            mouseFinished(mouse);
            return true;
        }

        //moves the mouse on the screen if the mouse has not traversed a maze cell
        if (prevMazeCell.x == newMazeCell.y && prevMazeCell.y == newMazeCell.y) {
            mouse.moveMouse(newScreenPos.x, newScreenPos.y);
            return true;
        }


        //prevents the mouse from hopping walls and moving outside of the maze
        if (Math.abs(nextMazeCell.x - prevMazeCell.x) + Math.abs(nextMazeCell.x - prevMazeCell.y) != 1 || !(maze.checkLocation(nextMazeCell))) {
            mouse.aiPath.pop();
            return false;
        }


        //moves the mouse to the new maze cell
            mouse.moveMouse(newScreenPos.x, newScreenPos.y);
        mouse.setMazePos(mouse.aiPath.pop());
            return true;

    }

    private Bitmap generateMazeLineArrayBitmap(Paint p, int screenW, int screenH) {
        mazeLineArray = new MazeLineArray(maze, screenW, screenH);
        screenWidth = mazeLineArray.getScreenWidth();
        screenHeight = mazeLineArray.getScreenHeight();
        cellWidth = mazeLineArray.getWSpacing();
        cellHeight = mazeLineArray.getHSpacing();
        p.setStrokeWidth(cellWidth / 6);

        mazeBitmap = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(mazeBitmap);

        for (int i = 0; i < mazeLineArray.getSize(); i++) {
            Line line = mazeLineArray.getLineAtIndex(i);
            c.drawLine(line.startX, line.startY, line.endX, line.endY, p);
            //Log.i("line", line.startX + " " + line.startY + " " + line.endX + " " +line.endY);
        }
        return mazeBitmap;
    }

    public void paintMaze(Canvas c, Paint p, int screenWidth, int screenHeight) {
        //Cell start = maze.getStart();
        //Cell end = maze.getEnd();

        //check to see if the maze line array needs to be generated or regenerated.
        if (mazeLineArray == null) {
            generateMazeLineArrayBitmap(p, screenWidth, screenHeight);
            //Log.i("mazeLineArrayGenerator", "Null");
        } else if (screenHeight != mazeLineArray.getScreenHeight() || screenWidth != mazeLineArray.getScreenWidth()) {
            generateMazeLineArrayBitmap(p, screenWidth, screenHeight);
            //Log.i("mazeLineArrayGenerator", "Screen size change");
        } else if (maze != mazeLineArray.getMaze()) {
            generateMazeLineArrayBitmap(p, screenWidth, screenHeight);
            Log.i("mazeLineArrayGenerator", "Equals method");
        }
        c.drawBitmap(mazeBitmap, 0, 0, p);
    }

    public void drawMice(Canvas c, int screenWidth, int screenHeight) {
        //check to see if the mice images need to be generated or regenerated
        if (playerMouse.getImage() == null) {
            //scale and add the mouse images
            createMiceBitmaps();
        } else if (cellWidth != oldCellWidth || cellHeight != oldCellHeight) {
            createMiceBitmaps();
        }

        if (isMultiPlayer) {
            for (Mouse m : opponentMice) {
                rotateMouseImage(m);
                c.drawBitmap(m.getRotatedImage(), m.getPosX() - (m.getRotatedImage().getWidth() / 2), m.getPosY() - (m.getRotatedImage().getHeight() / 2), null);
            }
        }
        rotateMouseImage(playerMouse);
        c.drawBitmap(playerMouse.getRotatedImage(), playerMouse.getPosX() - (playerMouse.getRotatedImage().getWidth() / 2), playerMouse.getPosY() - (playerMouse.getRotatedImage().getHeight() / 2), null);

    }

    private void createMiceBitmaps() {
        int scaleWidth = (cellWidth) / 4;
        int scaleHeight = (cellWidth) / 4;
        //make bitmap for the playermouse
        mouseStartPos = new Point(cellWidth / 2, cellHeight / 2);
        playerMouse.moveMouse(mouseStartPos.x, mouseStartPos.y);
        Bitmap mouseImage = miceImageArray[0].createScaledBitmap(miceImageArray[0], cellWidth + scaleWidth, cellWidth + scaleWidth, false);
        playerMouse.setMouseImage(mouseImage);
        playerMouse.setRotatedImage(mouseImage);
        playerMouse.setMouseAngle(START_ANGLE);

        if (isMultiPlayer) {
            //make bitmaps for all the opponents
            int opponent = 1;
            for (Mouse m : opponentMice) {
                mouseImage = miceImageArray[opponent].createScaledBitmap(miceImageArray[opponent], cellWidth + scaleWidth, cellWidth + scaleWidth, false);
                m.setMouseImage(mouseImage);
                m.setRotatedImage(mouseImage);
                m.setMouseAngle(START_ANGLE);
                opponent++;
            }
        }
        oldCellWidth = mazeLineArray.getWSpacing();
        oldCellHeight = mazeLineArray.getHSpacing();
    }

    private void rotateMouseImage(Mouse mouse) {
        Point posAtLastRotate = mouse.getPosAtLastRotate();
        if (mouse.rotate()) {
            double deltaX = mouse.getPosX() - posAtLastRotate.x;
            double deltaY = mouse.getPosY() - posAtLastRotate.y;
            if (deltaX != 0 || deltaY != 0) {
                float angle = mouse.getAngle();
                angle = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
                Matrix matrix = new Matrix();
                matrix.setRotate(angle);
                Bitmap targetBitmap = Bitmap.createBitmap(mouse.getImage().getWidth(), mouse.getImage().getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(targetBitmap);
                matrix.setRotate(angle, mouse.getImage().getWidth() / 2, mouse.getImage().getHeight() / 2);
                canvas.drawBitmap(mouse.getImage(), matrix, new Paint());
                mouse.setRotatedImage(targetBitmap);
            }
        }
    }

    // draws the powerups, but first creates the powerup map if it has not been created yet
    public void drawPowerUps(Canvas c, int screenWidth, int screenHeight) {
        //generates or regenerates the powerup map if it doesn't exist or the maze has changed
        if (powerUpMap == null || powerUpMap.getMaze() != maze) {
            Bitmap[] scaledPowerUpImages = new Bitmap[powerUpImageArray.length];
            int scaledImage = 0;
            for (Bitmap i : powerUpImageArray) {
                scaledPowerUpImages[scaledImage] = Bitmap.createScaledBitmap(i, screenWidth / width, screenHeight / height, true);
                scaledImage++;
            }
            powerUpMap = new PowerUpMap(maze, scaledPowerUpImages);
        }
        powerUpMap.displayPowerUps(c, screenWidth, screenHeight);
    }

    public void assignPowerUp(Mouse mouse, int mazeX, int mazeY) {

        // This array list I get does not seem to work properly. Fix when can. -Matt
        //ArrayList powerUpList = powerUps.getPowerUpList();

        for (int i = 0; i < powerUpMap.powerUpList.size(); i++) {
            if (powerUpMap.powerUpList.get(i).getMazeX() == mazeX && powerUpMap.powerUpList.get(i).getMazeY() == mazeY) {
                mouse.addPoints(1000);
                mouse.addPowerUp(powerUpMap.addPowerUpToMouse(i));
            }
        }
    }


    //sounds
    public final int SQUEAK_SOUND = 0;
    public final int CHEESE_SOUND = 1;
    public final int GARBAGE_SOUND = 2;
    public final int BREAD_SOUND = 3;
    public final int LEVEL_UP_SOUND = 4;

    public static final int NUM_SOUNDS = 5;

    public boolean[] playSound;

    private void initSounds() {
        playSound = new boolean[NUM_SOUNDS];
    }


    public boolean soundsToPlay(int sound) {
        boolean tempSound = false;
        switch (sound) {
            case SQUEAK_SOUND:
                tempSound = playSound[SQUEAK_SOUND];
                playSound[SQUEAK_SOUND] = false;
                break;
            case CHEESE_SOUND:
                tempSound = playSound[CHEESE_SOUND];
                playSound[CHEESE_SOUND] = false;
                break;
            case GARBAGE_SOUND:
                tempSound = playSound[GARBAGE_SOUND];
                playSound[GARBAGE_SOUND] = false;
                break;
            case BREAD_SOUND:
                tempSound = playSound[BREAD_SOUND];
                playSound[BREAD_SOUND] = false;
                break;
            case LEVEL_UP_SOUND:
                tempSound = playSound[LEVEL_UP_SOUND];
                playSound[LEVEL_UP_SOUND] = false;
                break;
        }
        return tempSound;
    }

}
