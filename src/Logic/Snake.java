package Logic;

import Protobuf.SnakesProto;

import java.util.ArrayList;

public class Snake {
    private final int playerId;
    private final int fieldHeight;
    private final int fieldWidth;

    private boolean hasEaten = false;

    private final ArrayList<Point> snakeBody;

    private SnakesProto.Direction prevMovement;
    private SnakesProto.GameState.Snake.SnakeState snakeState;

    public Snake(
            SnakesProto.GameState.Snake snakeMessage,
            SnakesProto.GameConfig gameConfig
    ) {

        this.snakeBody = new ArrayList<>();

        this.fieldWidth = gameConfig.getWidth();
        this.fieldHeight = gameConfig.getHeight();

        decodeSnakeFromMessage(snakeMessage);

        this.snakeState = snakeMessage.getState();
        this.prevMovement = snakeMessage.getHeadDirection();

        playerId = snakeMessage.getPlayerId();
    }

    public Snake(
            ArrayList<Point> snakeBody,
            int fieldLength,
            int fieldWidth,
            int playerId,
            SnakesProto.GameState.Snake.SnakeState snakeState
    ) {
        this.snakeBody = snakeBody;
        this.fieldHeight = fieldLength;
        this.fieldWidth = fieldWidth;
        this.playerId = playerId;
        this.snakeState = snakeState;

        prevMovement = getPrevMovementFromStart();
    }

    private SnakesProto.Direction getPrevMovementFromStart() {
        if (snakeBody.size() < 2) return SnakesProto.Direction.RIGHT;

        Point p1 = snakeBody.get(0);
        Point p2 = snakeBody.get(1);

        int diffX = p1.getX() - p2.getX();
        int diffY = p1.getY() - p2.getY();

        if (diffX != 0) {
            return (diffX > 0) ? SnakesProto.Direction.RIGHT : SnakesProto.Direction.LEFT;
        }

        if (diffY != 0) {
            return (diffY > 0) ? SnakesProto.Direction.DOWN : SnakesProto.Direction.UP;
        }

        return SnakesProto.Direction.UP;
    }

    void increaseSnake() {
        hasEaten = true;
    }

    boolean canMove(SnakesProto.Direction m) {
        SnakesProto.Direction reverseMove;
        switch (prevMovement) {
            case UP: {
                reverseMove = SnakesProto.Direction.DOWN;
                break;
            }
            case RIGHT: {
                reverseMove = SnakesProto.Direction.LEFT;
                break;
            }
            case DOWN: {
                reverseMove = SnakesProto.Direction.UP;
                break;
            }
            case LEFT: {
                reverseMove = SnakesProto.Direction.RIGHT;
                break;
            }
            default:
                reverseMove = SnakesProto.Direction.UP;
        }

        return m != reverseMove;
    }

    public void setSnakeState(SnakesProto.GameState.Snake.SnakeState snakeState) {
        this.snakeState = snakeState;
    }

    public SnakesProto.GameState.Snake.SnakeState getSnakeState() {
        return snakeState;
    }

    void moveSnake(SnakesProto.Direction move) {
        if (hasEaten) {
            snakeBody.add(new Point(snakeBody.get(snakeBody.size() - 1)));
        }

        for (int i = snakeBody.size() - ((hasEaten) ? 1 : 0) - 1; i > 0; --i) {
            snakeBody.get(i).setValues(snakeBody.get(i - 1));
        }

        switch (move) {
            case UP: {
                snakeBody.get(0).setY(snakeBody.get(0).getY() - 1);
                break;
            }
            case DOWN: {
                snakeBody.get(0).setY(snakeBody.get(0).getY() + 1);
                break;
            }
            case LEFT: {
                snakeBody.get(0).setX(snakeBody.get(0).getX() - 1);
                break;
            }
            case RIGHT: {
                snakeBody.get(0).setX(snakeBody.get(0).getX() + 1);
                break;
            }
        }

        normalizeSnake();
        prevMovement = move;
        hasEaten = false;
    }

    private void normalizeSnake() {
        Point p;
        for (Point aSnake : snakeBody) {
            p = aSnake;

            int sX = p.getX();
            int sY = p.getY();

            sX %= fieldWidth;
            while (sX < 0) sX += fieldWidth;

            sY %= fieldHeight;
            while (sY < 0) sY += fieldHeight;

            p.setX(sX);
            p.setY(sY);
        }
    }

    public Point getHead() {
        return snakeBody.get(0);
    }

    public ArrayList<Point> getSnakeBody() {
        return snakeBody;
    }

    public int getSnakeSize() {
        return snakeBody.size();
    }

    public SnakesProto.Direction getPrevMovement() {
        return prevMovement;
    }

    public SnakesProto.GameState.Snake encodeSnakeToMessage() {
        SnakesProto.GameState.Snake.Builder snakeBuilder = SnakesProto.GameState.Snake.newBuilder();

        snakeBuilder.setPlayerId(playerId);
        snakeBuilder.setState(snakeState);
        snakeBuilder.setHeadDirection(prevMovement);

        Point head = getHead();

        snakeBuilder.addPoints(SnakesProto.GameState.Coord.newBuilder()
                .setX(head.getX())
                .setY(head.getY())
        );


        Point buffer = new Point();

        for (int i = 1; i < snakeBody.size(); ++i) {
            Point p1 = snakeBody.get(i - 1);

            Point currPoint = snakeBody.get(i);

            int newXShift = currPoint.getX() - p1.getX();
            int newYShift = currPoint.getY() - p1.getY();

            if (newYShift > 0) {
                if (newYShift > 1) {
                    newYShift = -1;
                }
            } else {
                if (newYShift < -1) {
                    newYShift = 1;
                }
            }

            if (newXShift > 0) {
                if (newXShift > 1) {
                    newXShift = -1;
                }
            } else {
                if (newXShift < -1) {
                    newXShift = 1;
                }
            }

            buffer.setY(newYShift + buffer.getY());
            buffer.setX(newXShift + buffer.getX());

            if (i == snakeBody.size() - 1) {
                snakeBuilder.addPoints(SnakesProto.GameState.Coord.newBuilder()
                        .setX(buffer.getX())
                        .setY(buffer.getY())
                );
                break;
            }

            Point p2 = snakeBody.get(i + 1);

            int xShift = p1.getX() - p2.getX();
            int yShift = p1.getY() - p2.getY();


            if (Math.abs(xShift) != 0 && Math.abs(yShift) != 0) {

                snakeBuilder.addPoints(SnakesProto.GameState.Coord.newBuilder()
                        .setX(buffer.getX())
                        .setY(buffer.getY())
                );

                buffer = new Point();
            }

        }


        return snakeBuilder.build();
    }

    public void loadSnake(SnakesProto.GameState.Snake snakeMessage) {
        snakeBody.clear();

        decodeSnakeFromMessage(snakeMessage);

        prevMovement = snakeMessage.getHeadDirection();
    }


    public int getPlayerId() {
        return playerId;
    }

    private void decodeSnakeFromMessage(SnakesProto.GameState.Snake snakeMessage) {
        SnakesProto.GameState.Coord head = snakeMessage.getPointsList().get(0);

        snakeBody.add(new Point(head.getX(), head.getY()));

        for (int i = 1; i < snakeMessage.getPointsCount(); ++i) {
            Point prevPoint = snakeBody.get(snakeBody.size() - 1);
            SnakesProto.GameState.Coord shift = snakeMessage.getPoints(i);

            int yDirection = 0;
            int xDirection = 0;
            int numOfPoints = 0;

            if (shift.getX() == 0) {
                if (shift.getY() == 0) {
                    System.out.println("ERROR: shift point with coord (0, 0)");
                } else if (shift.getY() > 0) {
                    numOfPoints = shift.getY();
                    yDirection = 1;
                } else {
                    numOfPoints = -shift.getY();
                    yDirection = -1;
                }
            } else if (shift.getX() > 0) {
                numOfPoints = shift.getX();
                xDirection = 1;
            } else {
                numOfPoints = -shift.getX();
                xDirection = -1;
            }

            for (int j = 0; j < numOfPoints; ++j) {
                Point newPoint = getNormalizePoint(prevPoint.getX() + (j + 1) * xDirection,
                        prevPoint.getY() + (j + 1) * yDirection);

                snakeBody.add(newPoint);
            }
        }
    }

    private Point getNormalizePoint(int x, int y) {
        while (y <= 0) y += fieldHeight;
        while (x <= 0) x += fieldWidth;

        return new Point(x % fieldWidth, y % fieldHeight);
    }

}